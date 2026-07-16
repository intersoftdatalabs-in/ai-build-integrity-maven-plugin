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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ResumeFrom")
class ResumeFromTest {

  @Mock private MavenSession session;
  @Mock private MavenExecutionRequest request;
  @Mock private MavenProject project;
  @TempDir java.nio.file.Path tempDir;

  @Nested
  @DisplayName("isResumeBuild / getResumeFromSelector")
  class ResumeDetectionTests {

    @Test
    @DisplayName("false when session or request missing")
    void falseWhenMissing() {
      assertFalse(ResumeFrom.isResumeBuild(null));
      when(session.getRequest()).thenReturn(null);
      assertFalse(ResumeFrom.isResumeBuild(session));
      assertNull(ResumeFrom.getResumeFromSelector(session));
    }

    @Test
    @DisplayName("true when -rf selector present")
    void trueWhenSelectorPresent() {
      when(session.getRequest()).thenReturn(request);
      when(request.getResumeFrom()).thenReturn(":module-b");
      assertTrue(ResumeFrom.isResumeBuild(session));
      assertEquals(":module-b", ResumeFrom.getResumeFromSelector(session));
    }

    @Test
    @DisplayName("false for blank resume-from")
    void falseWhenBlank() {
      when(session.getRequest()).thenReturn(request);
      when(request.getResumeFrom()).thenReturn("  ");
      assertFalse(ResumeFrom.isResumeBuild(session));
    }
  }

  @Nested
  @DisplayName("matchesProject")
  class MatchesProjectTests {

    @Test
    @DisplayName("matches :artifactId, bare artifactId, and groupId:artifactId")
    void matchesCommonSelectors() {
      when(project.getArtifactId()).thenReturn("module-b");
      when(project.getGroupId()).thenReturn("com.example");

      assertTrue(ResumeFrom.matchesProject(":module-b", project));
      assertTrue(ResumeFrom.matchesProject("module-b", project));
      assertTrue(ResumeFrom.matchesProject("com.example:module-b", project));
      assertFalse(ResumeFrom.matchesProject(":module-a", project));
      assertFalse(ResumeFrom.matchesProject("com.other:module-b", project));
    }

    @Test
    @DisplayName("matches project basedir path")
    void matchesBasedirPath() {
      File basedir = tempDir.toFile();
      when(project.getArtifactId()).thenReturn("module-b");
      when(project.getGroupId()).thenReturn("com.example");
      when(project.getBasedir()).thenReturn(basedir);

      assertTrue(ResumeFrom.matchesProject(basedir.getAbsolutePath(), project));
      assertTrue(ResumeFrom.matchesProject(basedir.getName(), project));
    }
  }

  @Nested
  @DisplayName("shouldRegenerateHashes / allowNonRootGeneration")
  class RegenerationPolicyTests {

    @Test
    @DisplayName("first reactor project regenerates on plain -rf with executionRootOnly")
    void firstProjectRegeneratesOnAutoResume() {
      MavenProject first = mock(MavenProject.class);
      MavenProject second = mock(MavenProject.class);
      when(first.getArtifactId()).thenReturn("module-b");
      when(first.getGroupId()).thenReturn("com.example");
      when(second.getArtifactId()).thenReturn("module-c");
      when(second.getGroupId()).thenReturn("com.example");
      when(session.getRequest()).thenReturn(request);
      when(request.getResumeFrom()).thenReturn(":module-b");
      when(session.getProjects()).thenReturn(Arrays.asList(first, second));

      assertTrue(ResumeFrom.shouldRegenerateHashes(session, first, null, true));
      assertFalse(ResumeFrom.shouldRegenerateHashes(session, second, null, true));
      assertTrue(ResumeFrom.allowNonRootGeneration(session, first, null));
      assertFalse(ResumeFrom.allowNonRootGeneration(session, second, null));
    }

    @Test
    @DisplayName("explicit resumeFromModule selects matching project only")
    void explicitModuleOverride() {
      when(project.getArtifactId()).thenReturn("module-c");
      when(project.getGroupId()).thenReturn("com.example");
      when(session.getRequest()).thenReturn(request);
      when(request.getResumeFrom()).thenReturn(":module-b");
      when(session.getProjects()).thenReturn(Collections.singletonList(project));

      assertTrue(ResumeFrom.shouldRegenerateHashes(session, project, ":module-c", true));
      assertFalse(ResumeFrom.shouldRegenerateHashes(session, project, ":module-b", true));
      assertTrue(ResumeFrom.allowNonRootGeneration(session, project, "module-c"));
    }

    @Test
    @DisplayName("without executionRootOnly every module may regenerate on -rf")
    void allModulesWhenNotExecutionRootOnly() {
      when(project.getArtifactId()).thenReturn("module-c");
      when(session.getRequest()).thenReturn(request);
      when(request.getResumeFrom()).thenReturn(":module-b");

      assertTrue(ResumeFrom.shouldRegenerateHashes(session, project, null, false));
    }
  }
}
