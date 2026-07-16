/*
 * Copyright (c) 2026 Intersoft Data Labs, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intsof.ai.build.integrity;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;

/**
 * Helpers for Maven {@code -rf}/{@code --resume-from} builds.
 *
 * <p>Maven stores the resume selector on {@code MavenExecutionRequest.getResumeFrom()} in forms
 * such as {@code :artifactId}, {@code groupId:artifactId}, a bare {@code artifactId}, or a relative
 * path. This class normalizes matching so integrity goals can re-seal the resumed reactor without a
 * manual {@code -Dai.integrity.resumeFromModule} property.
 */
final class ResumeFrom {

  private ResumeFrom() {}

  /**
   * Returns the raw resume-from selector from the Maven session, or {@code null} when this is not a
   * resume build.
   *
   * @param session the Maven session (may be {@code null})
   * @return non-blank resume selector, or {@code null}
   */
  static String getResumeFromSelector(MavenSession session) {
    if (session == null || session.getRequest() == null) {
      return null;
    }
    String resumeFrom = session.getRequest().getResumeFrom();
    if (resumeFrom == null) {
      return null;
    }
    String trimmed = resumeFrom.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  /**
   * Whether the session is a Maven resume-from build ({@code -rf} / {@code --resume-from}).
   *
   * @param session the Maven session (may be {@code null})
   * @return {@code true} when a resume selector is present
   */
  static boolean isResumeBuild(MavenSession session) {
    return getResumeFromSelector(session) != null;
  }

  /**
   * Whether {@code project} is the first project in the current reactor list.
   *
   * <p>On {@code -rf}, Maven's project list starts at the resume-from module; that first entry is
   * the natural place to re-seal when {@code executionRootOnly} would otherwise skip non-root
   * modules.
   *
   * @param session the Maven session
   * @param project the project under consideration
   * @return {@code true} when {@code project} is the first reactor project
   */
  static boolean isFirstReactorProject(MavenSession session, MavenProject project) {
    if (session == null || project == null) {
      return false;
    }
    List<MavenProject> projects = session.getProjects();
    if (projects == null || projects.isEmpty()) {
      return false;
    }
    return sameProject(projects.get(0), project);
  }

  /**
   * Whether the given resume selector or explicit module override refers to {@code project}.
   *
   * <p>Supported selectors:
   *
   * <ul>
   *   <li>{@code :artifactId}
   *   <li>{@code groupId:artifactId}
   *   <li>bare {@code artifactId}
   *   <li>relative filesystem path matching the project basedir
   * </ul>
   *
   * @param selector Maven resume selector or {@code ai.integrity.resumeFromModule} value
   * @param project project to test
   * @return {@code true} when the selector matches the project
   */
  static boolean matchesProject(String selector, MavenProject project) {
    if (selector == null || project == null) {
      return false;
    }
    String trimmed = selector.trim();
    if (trimmed.isEmpty()) {
      return false;
    }

    String artifactId = project.getArtifactId();
    String groupId = project.getGroupId();

    // :artifactId
    if (trimmed.charAt(0) == ':') {
      return trimmed.substring(1).equals(artifactId);
    }

    // groupId:artifactId (exactly one colon, not starting with colon)
    int colon = trimmed.indexOf(':');
    boolean windowsAbsolutePath =
        colon == 1
            && trimmed.length() > 2
            && (trimmed.charAt(2) == '\\' || trimmed.charAt(2) == '/');
    if (colon > 0 && trimmed.indexOf(':', colon + 1) < 0 && !windowsAbsolutePath) {
      String g = trimmed.substring(0, colon);
      String a = trimmed.substring(colon + 1);
      if (!a.isEmpty() && g.equals(groupId) && a.equals(artifactId)) {
        return true;
      }
    }

    // bare artifactId
    if (trimmed.equals(artifactId)) {
      return true;
    }

    // relative / absolute path to project basedir
    File basedir = project.getBasedir();
    if (basedir != null) {
      Path projectPath = basedir.toPath().toAbsolutePath().normalize();
      try {
        Path relativeSelectorPath = java.nio.file.Paths.get(trimmed).normalize();
        Path absoluteSelectorPath = relativeSelectorPath.toAbsolutePath().normalize();
        if (projectPath.equals(absoluteSelectorPath)
            || projectPath.endsWith(relativeSelectorPath)) {
          return true;
        }
      } catch (Exception ignored) {
        // not a usable path selector
      }
      // also accept basedir name or suffix path segment match
      if (trimmed.equals(basedir.getName())) {
        return true;
      }
    }

    return false;
  }

  /**
   * Resolves whether the current project should perform hash regeneration on a resume build.
   *
   * <ul>
   *   <li>If {@code resumeFromModule} is set, only the matching project regenerates.
   *   <li>Otherwise, on {@code -rf}, the first reactor project regenerates (covers recommended
   *       multi-module {@code executionRootOnly} setups where the root is not in the resumed
   *       reactor).
   *   <li>When {@code executionRootOnly} is false and no explicit module is set, every module may
   *       regenerate its own tree (caller decides skip policy).
   * </ul>
   *
   * @param session Maven session
   * @param project current project
   * @param resumeFromModule optional explicit override ({@code ai.integrity.resumeFromModule})
   * @param executionRootOnly whether generate is limited to the execution root
   * @return {@code true} when this project should regenerate hashes for the resume
   */
  static boolean shouldRegenerateHashes(
      MavenSession session,
      MavenProject project,
      String resumeFromModule,
      boolean executionRootOnly) {
    boolean explicit = resumeFromModule != null && !resumeFromModule.trim().isEmpty();
    boolean resumeBuild = isResumeBuild(session) || explicit;
    if (!resumeBuild) {
      return false;
    }

    if (explicit) {
      return matchesProject(resumeFromModule, project);
    }

    // Auto -rf: first reactor project re-seals remaining modules (executionRootOnly path).
    // Without executionRootOnly, each module regenerates when it reaches generate-hashes.
    if (executionRootOnly) {
      return isFirstReactorProject(session, project);
    }
    return true;
  }

  /**
   * Whether {@code executionRootOnly} should be relaxed so a non-root project may generate hashes.
   *
   * @param session Maven session
   * @param project current project
   * @param resumeFromModule optional explicit override
   * @return {@code true} when a non-root project should still run generate-hashes
   */
  static boolean allowNonRootGeneration(
      MavenSession session, MavenProject project, String resumeFromModule) {
    boolean explicit = resumeFromModule != null && !resumeFromModule.trim().isEmpty();
    if (explicit) {
      return matchesProject(resumeFromModule, project);
    }
    return isResumeBuild(session) && isFirstReactorProject(session, project);
  }

  /**
   * Human-readable label for log messages (artifactId preferred).
   *
   * @param session Maven session
   * @param project current project
   * @param resumeFromModule optional override
   * @return short label for logs
   */
  static String describeResumeTarget(
      MavenSession session, MavenProject project, String resumeFromModule) {
    if (resumeFromModule != null && !resumeFromModule.trim().isEmpty()) {
      return resumeFromModule.trim();
    }
    String selector = getResumeFromSelector(session);
    if (selector != null) {
      return selector;
    }
    return project != null ? project.getArtifactId() : "unknown";
  }

  private static boolean sameProject(MavenProject a, MavenProject b) {
    if (a == b) {
      return true;
    }
    if (a == null || b == null) {
      return false;
    }
    if (a.getGroupId() != null
        && a.getArtifactId() != null
        && a.getGroupId().equals(b.getGroupId())
        && a.getArtifactId().equals(b.getArtifactId())) {
      return true;
    }
    File ab = a.getBasedir();
    File bb = b.getBasedir();
    if (ab != null && bb != null) {
      return ab.toPath()
          .toAbsolutePath()
          .normalize()
          .equals(bb.toPath().toAbsolutePath().normalize());
    }
    return false;
  }
}
