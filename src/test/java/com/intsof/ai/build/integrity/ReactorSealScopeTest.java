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

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
@DisplayName("ReactorSealScope")
class ReactorSealScopeTest {

  @Mock private MavenSession session;
  @TempDir Path tempDir;

  @Nested
  @DisplayName("parseMode")
  class ParseModeTests {

    @Test
    @DisplayName("defaults blank to AUTO")
    void defaultsBlankToAuto() {
      assertEquals(ReactorSealScope.Mode.AUTO, ReactorSealScope.parseMode(null));
      assertEquals(ReactorSealScope.Mode.AUTO, ReactorSealScope.parseMode("  "));
    }

    @Test
    @DisplayName("parses known modes case-insensitively")
    void parsesKnownModes() {
      assertEquals(ReactorSealScope.Mode.FULL, ReactorSealScope.parseMode("full"));
      assertEquals(ReactorSealScope.Mode.REACTOR, ReactorSealScope.parseMode("Reactor"));
      assertEquals(ReactorSealScope.Mode.AUTO, ReactorSealScope.parseMode("AUTO"));
    }

    @Test
    @DisplayName("rejects unknown modes")
    void rejectsUnknown() {
      assertThrows(IllegalArgumentException.class, () -> ReactorSealScope.parseMode("maybe"));
    }
  }

  @Nested
  @DisplayName("isPartialReactor")
  class IsPartialReactorTests {

    @Test
    @DisplayName("false when session is null")
    void falseWhenSessionNull() {
      assertFalse(ReactorSealScope.isPartialReactor(null, tempDir));
    }

    @Test
    @DisplayName("true when projects is proper subset of allProjects")
    void trueWhenSubsetOfAllProjects() {
      MavenProject a = projectAt(tempDir.resolve("a"));
      MavenProject b = projectAt(tempDir.resolve("b"));
      when(session.getProjects()).thenReturn(Collections.singletonList(a));
      when(session.getAllProjects()).thenReturn(Arrays.asList(a, b));

      assertTrue(ReactorSealScope.isPartialReactor(session, tempDir));
    }

    @Test
    @DisplayName("false when full reactor (projects equals allProjects)")
    void falseWhenFullReactor() {
      MavenProject root = projectAt(tempDir);
      MavenProject a = projectAt(tempDir.resolve("a"));
      when(session.getProjects()).thenReturn(Arrays.asList(root, a));
      when(session.getAllProjects()).thenReturn(Arrays.asList(root, a));

      assertFalse(ReactorSealScope.isPartialReactor(session, tempDir));
    }

    @Test
    @DisplayName("true when only child modules selected against multi-module baseDir")
    void trueWhenChildOnlyAgainstAncestorBaseDir() {
      MavenProject child = projectAt(tempDir.resolve("module-62"));
      when(session.getProjects()).thenReturn(Collections.singletonList(child));
      when(session.getAllProjects()).thenReturn(Collections.singletonList(child));

      assertTrue(ReactorSealScope.isPartialReactor(session, tempDir));
    }
  }

  @Nested
  @DisplayName("computeSealRoots")
  class ComputeSealRootsTests {

    @Test
    @DisplayName("prefers deepest selected basedirs when aggregator is also selected")
    void prefersDeepestBasedirs() {
      MavenProject root = projectAt(tempDir);
      MavenProject leaf = projectAt(tempDir.resolve("module-62"));
      when(session.getProjects()).thenReturn(Arrays.asList(root, leaf));

      List<Path> roots = ReactorSealScope.computeSealRoots(session, tempDir);
      assertEquals(1, roots.size());
      assertEquals(tempDir.resolve("module-62").toAbsolutePath().normalize(), roots.get(0));
    }

    @Test
    @DisplayName("returns multiple sibling seal roots")
    void returnsSiblingRoots() {
      MavenProject a = projectAt(tempDir.resolve("a"));
      MavenProject b = projectAt(tempDir.resolve("b"));
      when(session.getProjects()).thenReturn(Arrays.asList(a, b));

      List<Path> roots = ReactorSealScope.computeSealRoots(session, tempDir);
      assertEquals(2, roots.size());
      assertTrue(roots.contains(tempDir.resolve("a").toAbsolutePath().normalize()));
      assertTrue(roots.contains(tempDir.resolve("b").toAbsolutePath().normalize()));
    }
  }

  @Nested
  @DisplayName("resolveWalkRoots")
  class ResolveWalkRootsTests {

    @Test
    @DisplayName("AUTO full uses baseDir only")
    void autoFullUsesBaseDir() {
      MavenProject root = projectAt(tempDir);
      when(session.getProjects()).thenReturn(Collections.singletonList(root));

      List<Path> roots =
          ReactorSealScope.resolveWalkRoots(tempDir, session, ReactorSealScope.Mode.AUTO, false);
      assertEquals(Collections.singletonList(tempDir.toAbsolutePath().normalize()), roots);
    }

    @Test
    @DisplayName("AUTO partial uses seal roots")
    void autoPartialUsesSealRoots() {
      MavenProject leaf = projectAt(tempDir.resolve("m"));
      when(session.getProjects()).thenReturn(Collections.singletonList(leaf));

      List<Path> roots =
          ReactorSealScope.resolveWalkRoots(tempDir, session, ReactorSealScope.Mode.AUTO, true);
      assertEquals(
          Collections.singletonList(tempDir.resolve("m").toAbsolutePath().normalize()), roots);
    }

    @Test
    @DisplayName("FULL forces baseDir even when partial")
    void fullForcesBaseDir() {
      MavenProject leaf = projectAt(tempDir.resolve("m"));
      when(session.getProjects()).thenReturn(Collections.singletonList(leaf));

      List<Path> roots =
          ReactorSealScope.resolveWalkRoots(tempDir, session, ReactorSealScope.Mode.FULL, true);
      assertEquals(Collections.singletonList(tempDir.toAbsolutePath().normalize()), roots);
    }
  }

  @Nested
  @DisplayName("mergeCentralLedger")
  class MergeCentralLedgerTests {

    @Test
    @DisplayName("preserves out-of-scope entries and refreshes seal roots")
    void preservesAndRefreshes() {
      Path seal = tempDir.resolve("m62").toAbsolutePath().normalize();
      String existing =
          "aaa  other/module/AGENTS.md\n" + "bbb  m62/AGENTS.md\n" + "ccc  m62/nested/SKILL.md\n";

      Map<String, String> neu = new LinkedHashMap<>();
      neu.put("m62/AGENTS.md", "NEWHASH");

      String merged =
          ReactorSealScope.mergeCentralLedger(
              existing, tempDir, Collections.singletonList(seal), neu);

      assertTrue(merged.contains("aaa  other/module/AGENTS.md"));
      assertTrue(merged.contains("NEWHASH  m62/AGENTS.md"));
      assertFalse(merged.contains("bbb  m62/AGENTS.md"));
      assertFalse(merged.contains("ccc  m62/nested/SKILL.md"));
    }

    @Test
    @DisplayName("empty new entries still drops seal-root paths")
    void emptyNewEntriesDropsScope() {
      Path seal = tempDir.resolve("m62").toAbsolutePath().normalize();
      String existing = "aaa  other/x.md\nbbb  m62/y.md\n";
      String merged =
          ReactorSealScope.mergeCentralLedger(
              existing, tempDir, Collections.singletonList(seal), Collections.emptyMap());
      assertEquals("aaa  other/x.md\n", merged);
    }
  }

  @Nested
  @DisplayName("shouldMergeCentral")
  class ShouldMergeCentralTests {

    @Test
    @DisplayName("FULL never merges")
    void fullNeverMerges() {
      assertFalse(ReactorSealScope.shouldMergeCentral(ReactorSealScope.Mode.FULL, true));
    }

    @Test
    @DisplayName("REACTOR always merges")
    void reactorAlwaysMerges() {
      assertTrue(ReactorSealScope.shouldMergeCentral(ReactorSealScope.Mode.REACTOR, false));
    }

    @Test
    @DisplayName("AUTO merges only when partial")
    void autoMergesWhenPartial() {
      assertTrue(ReactorSealScope.shouldMergeCentral(ReactorSealScope.Mode.AUTO, true));
      assertFalse(ReactorSealScope.shouldMergeCentral(ReactorSealScope.Mode.AUTO, false));
    }
  }

  private static MavenProject projectAt(Path basedir) {
    MavenProject p = mock(MavenProject.class);
    when(p.getBasedir()).thenReturn(basedir.toFile());
    return p;
  }
}
