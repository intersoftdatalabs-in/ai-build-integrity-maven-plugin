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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;

/**
 * Resolves which directories {@code generate-hashes} should walk for multi-module reactors, and
 * merges partial CENTRAL ledgers without dropping entries outside the current seal roots.
 *
 * <p>Full reactor builds keep a single walk of configured {@code baseDir}. Partial reactors (e.g.
 * {@code -pl}, child-module builds against a multi-module {@code baseDir}) walk only the deepest
 * selected module basedirs ("seal roots").
 */
final class ReactorSealScope {

  /** How aggressively to scope hash generation to the current reactor. */
  enum Mode {
    /** Detect partial vs full reactor automatically. */
    AUTO,
    /** Always walk configured {@code baseDir} (legacy full-tree seal). */
    FULL,
    /** Always walk seal roots derived from {@code session.getProjects()}. */
    REACTOR
  }

  private ReactorSealScope() {}

  /**
   * Parses the {@code ai.integrity.reactorScope} configuration value.
   *
   * @param value raw configuration string (may be null)
   * @return the resolved mode
   * @throws IllegalArgumentException if the value is non-blank and not a known mode
   */
  static Mode parseMode(String value) {
    if (value == null || value.trim().isEmpty()) {
      return Mode.AUTO;
    }
    String normalized = value.trim().toUpperCase(Locale.ROOT);
    try {
      return Mode.valueOf(normalized);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          "Invalid ai.integrity.reactorScope '" + value + "'. Expected AUTO, FULL, or REACTOR.", e);
    }
  }

  /**
   * Detects whether the current Maven session is building a proper subset of a larger multi-module
   * project (or a child module against an ancestor {@code baseDir}).
   *
   * @param session the Maven session (may be null)
   * @param basePath configured scan base directory
   * @return {@code true} when generation should use seal-root walks under {@link Mode#AUTO}
   */
  static boolean isPartialReactor(MavenSession session, Path basePath) {
    if (session == null || basePath == null) {
      return false;
    }

    List<MavenProject> projects = session.getProjects();
    if (projects == null || projects.isEmpty()) {
      return false;
    }

    List<MavenProject> allProjects = session.getAllProjects();
    if (allProjects != null && !allProjects.isEmpty() && projects.size() < allProjects.size()) {
      return true;
    }

    Path normalizedBase = basePath.toAbsolutePath().normalize();
    boolean anyEqualsBase = false;
    boolean anyStrictChild = false;
    for (MavenProject p : projects) {
      if (p == null || p.getBasedir() == null) {
        continue;
      }
      Path bd = p.getBasedir().toPath().toAbsolutePath().normalize();
      if (bd.equals(normalizedBase)) {
        anyEqualsBase = true;
      } else if (bd.startsWith(normalizedBase)) {
        anyStrictChild = true;
      }
    }

    // Child-directory or -pl-only builds against multi-module baseDir: no selected project owns
    // the base directory itself, but at least one lives underneath it.
    return !anyEqualsBase && anyStrictChild;
  }

  /**
   * Resolves the directories to walk for file discovery.
   *
   * @param basePath configured base directory (ledger-relative root)
   * @param session current Maven session
   * @param mode reactor scope mode
   * @param partial whether AUTO detection classified this build as partial
   * @return one or more existing directories to walk; never empty if {@code basePath} exists
   */
  static List<Path> resolveWalkRoots(
      Path basePath, MavenSession session, Mode mode, boolean partial) {
    if (basePath == null) {
      return Collections.emptyList();
    }
    if (mode == Mode.FULL || (mode == Mode.AUTO && !partial)) {
      return Collections.singletonList(basePath.toAbsolutePath().normalize());
    }
    List<Path> sealRoots = computeSealRoots(session, basePath);
    if (sealRoots.isEmpty()) {
      return Collections.singletonList(basePath.toAbsolutePath().normalize());
    }
    return sealRoots;
  }

  /**
   * Computes deepest selected project basedirs under {@code basePath} (seal roots).
   *
   * @param session current Maven session
   * @param basePath configured base directory
   * @return seal roots ordered by path string; empty if none could be resolved
   */
  static List<Path> computeSealRoots(MavenSession session, Path basePath) {
    if (session == null || basePath == null) {
      return Collections.emptyList();
    }
    List<MavenProject> projects = session.getProjects();
    if (projects == null || projects.isEmpty()) {
      return Collections.emptyList();
    }

    Path normalizedBase = basePath.toAbsolutePath().normalize();
    List<Path> candidates = new ArrayList<>();
    for (MavenProject p : projects) {
      if (p == null || p.getBasedir() == null) {
        continue;
      }
      Path bd = p.getBasedir().toPath().toAbsolutePath().normalize();
      if (bd.equals(normalizedBase) || bd.startsWith(normalizedBase)) {
        candidates.add(bd);
      }
    }
    if (candidates.isEmpty()) {
      return Collections.emptyList();
    }

    // A basedir is a seal root if no other selected basedir is a proper child of it.
    List<Path> sealRoots = new ArrayList<>();
    for (Path candidate : candidates) {
      boolean hasSelectedChild = false;
      for (Path other : candidates) {
        if (!candidate.equals(other) && other.startsWith(candidate)) {
          hasSelectedChild = true;
          break;
        }
      }
      if (!hasSelectedChild) {
        sealRoots.add(candidate);
      }
    }

    // Deduplicate while preserving order
    Set<Path> unique = new LinkedHashSet<>(sealRoots);
    return new ArrayList<>(unique);
  }

  /**
   * Returns {@code true} if absolute {@code file} lies under any of the seal roots (inclusive).
   *
   * @param file absolute or relative file path
   * @param sealRoots seal root directories
   * @return whether the file is in scope for partial sealing
   */
  static boolean isUnderAnySealRoot(Path file, List<Path> sealRoots) {
    if (file == null || sealRoots == null || sealRoots.isEmpty()) {
      return false;
    }
    Path normalized = file.toAbsolutePath().normalize();
    for (Path root : sealRoots) {
      if (root == null) {
        continue;
      }
      Path nr = root.toAbsolutePath().normalize();
      if (normalized.equals(nr) || normalized.startsWith(nr)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Merges a partial seal into an existing CENTRAL ledger.
   *
   * <p>Entries whose relative path resolves under any seal root are removed, then {@code
   * newEntries} (hash → relative path pairs are stored as path → hash) are appended.
   *
   * @param existingLedger full text of the existing ledger, or {@code null}/empty if none
   * @param basePath ledger path root
   * @param sealRoots directories being re-sealed
   * @param newEntries map of relative path (forward slashes) to hex hash
   * @return merged ledger text ending with a trailing newline when non-empty
   */
  static String mergeCentralLedger(
      String existingLedger, Path basePath, List<Path> sealRoots, Map<String, String> newEntries) {
    Map<String, String> merged = new LinkedHashMap<>();

    if (existingLedger != null && !existingLedger.isEmpty()) {
      String[] lines = existingLedger.split("\n", -1);
      for (String line : lines) {
        if (line == null || line.trim().isEmpty()) {
          continue;
        }
        String[] parts = line.trim().split("\\s+", 2);
        if (parts.length < 2) {
          continue;
        }
        String hash = parts[0];
        String relPath = parts[1].trim().replace('\\', '/');
        Path abs = basePath.resolve(relPath).toAbsolutePath().normalize();
        if (!isUnderAnySealRoot(abs, sealRoots)) {
          merged.put(relPath, hash);
        }
      }
    }

    if (newEntries != null) {
      for (Map.Entry<String, String> e : newEntries.entrySet()) {
        if (e.getKey() == null || e.getValue() == null) {
          continue;
        }
        String rel = e.getKey().replace('\\', '/');
        merged.put(rel, e.getValue());
      }
    }

    if (merged.isEmpty()) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<String, String> e : merged.entrySet()) {
      sb.append(e.getValue()).append("  ").append(e.getKey()).append('\n');
    }
    return sb.toString();
  }

  /**
   * Whether generation should use merge semantics for the CENTRAL ledger (partial seal).
   *
   * @param mode reactor scope mode
   * @param partial AUTO partial detection result
   * @return {@code true} when the central ledger should be merged rather than overwritten
   */
  static boolean shouldMergeCentral(Mode mode, boolean partial) {
    if (mode == Mode.FULL) {
      return false;
    }
    if (mode == Mode.REACTOR) {
      return true;
    }
    return partial;
  }
}
