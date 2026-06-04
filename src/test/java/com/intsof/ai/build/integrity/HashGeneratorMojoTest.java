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

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("HashGeneratorMojo")
class HashGeneratorMojoTest {

  @Mock private MavenProject project;
  @Mock private MavenSession session;
  @Mock private Log log;
  @TempDir Path tempDir;

  private HashGeneratorMojo mojo;

  @BeforeEach
  void setUp() throws Exception {
    mojo = new HashGeneratorMojo();
    mojo.setLog(log);
    setField(mojo, "project", project);
    setField(mojo, "session", session);
    setField(mojo, "algorithmBits", 256);
    setField(mojo, "includes", "**/*.md");
    setField(mojo, "excludes", "**/*.sha256,**/*.sha384,**/*.sha512");
    setField(mojo, "baseDir", tempDir.toString());
    setField(mojo, "outputExtension", "auto");
    setField(mojo, "skipExisting", false);
    setField(mojo, "skipDirs", "target,.git,node_modules,.tmp");
  }

  @Nested
  @DisplayName("execute")
  class ExecuteTests {

    @Test
    @DisplayName("should generate .sha256 hash files for matching .md files")
    void shouldGenerateHashFiles() throws Exception {
      // Given
      Path mdFile = tempDir.resolve("AGENTS.md");
      Files.writeString(mdFile, "# AI Agent Instructions\nDo the thing.");

      // When
      mojo.execute();

      // Then
      Path hashFile = tempDir.resolve("AGENTS.md.sha256");
      assertTrue(Files.exists(hashFile), "Hash file should exist");

      String hashContent = Files.readString(hashFile);
      assertTrue(hashContent.contains("AGENTS.md"));
      String hashValue = hashContent.split("\\s+")[0];
      assertEquals(64, hashValue.length(), "SHA-256 hex should be 64 chars");
    }

    @Test
    @DisplayName("should generate .sha512 files when algorithmBits is 512")
    void shouldGenerateSha512Files() throws Exception {
      // Given
      setField(mojo, "algorithmBits", 512);
      Files.writeString(tempDir.resolve("AGENTS.md"), "content");

      // When
      mojo.execute();

      // Then: .sha512 extension used (auto mode)
      Path hashFile = tempDir.resolve("AGENTS.md.sha512");
      assertTrue(Files.exists(hashFile), ".sha512 file should exist");
      String hashValue = Files.readString(hashFile).split("\\s+")[0];
      assertEquals(128, hashValue.length(), "SHA-512 hex should be 128 chars");
    }

    @Test
    @DisplayName("should handle empty directory with no matching files")
    void shouldHandleEmptyDirectory() throws Exception {
      assertDoesNotThrow(() -> mojo.execute());
    }

    @Test
    @DisplayName("should generate hashes for files in nested directories")
    void shouldGenerateHashesForMultipleFiles() throws Exception {
      // Given
      Files.writeString(tempDir.resolve("README.md"), "# Readme");
      Files.writeString(tempDir.resolve("AGENTS.md"), "# Agents");
      Path subDir = tempDir.resolve("sub");
      Files.createDirectory(subDir);
      Files.writeString(subDir.resolve("SKILL.md"), "# Skill");

      // When
      mojo.execute();

      // Then
      assertTrue(Files.exists(tempDir.resolve("README.md.sha256")));
      assertTrue(Files.exists(tempDir.resolve("AGENTS.md.sha256")));
      assertTrue(Files.exists(subDir.resolve("SKILL.md.sha256")));
    }

    @Test
    @DisplayName("should not create .sha256.sha256 files")
    void shouldExcludeHashFiles() throws Exception {
      // Given
      Files.writeString(tempDir.resolve("AGENTS.md"), "content");
      Files.writeString(tempDir.resolve("AGENTS.md.sha256"), "oldhash  AGENTS.md\n");

      // When
      mojo.execute();

      // Then
      assertFalse(Files.exists(tempDir.resolve("AGENTS.md.sha256.sha256")));
    }

    @Test
    @DisplayName("should skip existing hash files when skipExisting is true")
    void shouldSkipExistingWhenConfigured() throws Exception {
      // Given
      setField(mojo, "skipExisting", true);
      Files.writeString(tempDir.resolve("AGENTS.md"), "content");
      Path hashFile = tempDir.resolve("AGENTS.md.sha256");
      Files.writeString(hashFile, "existinghash  AGENTS.md\n");

      // When
      mojo.execute();

      // Then
      assertTrue(Files.readString(hashFile).startsWith("existinghash"));
    }

    @Test
    @DisplayName("should overwrite existing hash files when skipExisting is false")
    void shouldOverwriteExistingWhenNotSkipping() throws Exception {
      // Given
      Files.writeString(tempDir.resolve("AGENTS.md"), "content");
      Path hashFile = tempDir.resolve("AGENTS.md.sha256");
      Files.writeString(hashFile, "existinghash  AGENTS.md\n");

      // When
      mojo.execute();

      // Then
      assertFalse(Files.readString(hashFile).startsWith("existinghash"));
    }

    @Test
    @DisplayName("should warn and return when base directory does not exist")
    void shouldWarnForMissingBaseDir() throws Exception {
      // Given
      setField(mojo, "baseDir", tempDir.resolve("nonexistent").toString());

      // When/Then
      assertDoesNotThrow(() -> mojo.execute());
      verify(log).warn(contains("Base directory does not exist"));
    }

    @Test
    @DisplayName("should skip target directories")
    void shouldSkipTargetDirectories() throws Exception {
      // Given
      Path targetDir = tempDir.resolve("target");
      Files.createDirectory(targetDir);
      Files.writeString(targetDir.resolve("generated.md"), "generated");
      Files.writeString(tempDir.resolve("AGENTS.md"), "content");

      // When
      mojo.execute();

      // Then
      assertTrue(Files.exists(tempDir.resolve("AGENTS.md.sha256")));
      assertFalse(Files.exists(targetDir.resolve("generated.md.sha256")));
    }

    @Test
    @DisplayName("should skip .git and node_modules directories")
    void shouldSkipConfiguredDirs() throws Exception {
      // Given
      Path gitDir = tempDir.resolve(".git");
      Files.createDirectory(gitDir);
      Files.writeString(gitDir.resolve("HEAD.md"), "ref");
      Path nmDir = tempDir.resolve("node_modules");
      Files.createDirectory(nmDir);
      Files.writeString(nmDir.resolve("package.md"), "pkg");
      Files.writeString(tempDir.resolve("AGENTS.md"), "content");

      // When
      mojo.execute();

      // Then
      assertTrue(Files.exists(tempDir.resolve("AGENTS.md.sha256")));
      assertFalse(Files.exists(gitDir.resolve("HEAD.md.sha256")));
      assertFalse(Files.exists(nmDir.resolve("package.md.sha256")));
    }

    @Test
    @DisplayName("should use project basedir when baseDir is not set")
    void shouldUseProjectBasedir() throws Exception {
      // Given
      setField(mojo, "baseDir", "");
      when(project.getBasedir()).thenReturn(tempDir.toFile());
      Files.writeString(tempDir.resolve("test.md"), "content");

      // When
      mojo.execute();

      // Then
      assertTrue(Files.exists(tempDir.resolve("test.md.sha256")));
    }

    @Test
    @DisplayName("should support explicit outputExtension override")
    void shouldSupportExplicitExtension() throws Exception {
      // Given
      setField(mojo, "outputExtension", ".hash");
      Files.writeString(tempDir.resolve("AGENTS.md"), "content");

      // When
      mojo.execute();

      // Then
      assertTrue(Files.exists(tempDir.resolve("AGENTS.md.hash")));
      assertFalse(Files.exists(tempDir.resolve("AGENTS.md.sha256")));
    }
  }

  @Nested
  @DisplayName("Central Mode Tests")
  class CentralModeTests {

    @BeforeEach
    void setUpCentral() throws Exception {
      setField(mojo, "hashFileMode", HashFileMode.CENTRAL);
      setField(mojo, "centralHashFile", tempDir.resolve("ai-integrity.sha256").toString());
      setField(mojo, "buildDirectory", tempDir.resolve("target").toString());
    }

    @Test
    @DisplayName("should generate a central ledger file")
    void shouldGenerateCentralLedger() throws Exception {
      Path mdFile = tempDir.resolve("AGENTS.md");
      Files.writeString(mdFile, "content");

      mojo.execute();

      Path centralFile = tempDir.resolve("ai-integrity.sha256");
      assertTrue(Files.exists(centralFile));
      String content = Files.readString(centralFile);
      assertTrue(content.contains("AGENTS.md"));
      assertFalse(Files.exists(tempDir.resolve("AGENTS.md.sha256")), "Should not create sidecar");
    }

    @Test
    @DisplayName("should use default central ledger path if none is specified")
    void shouldUseDefaultCentralLedger() throws Exception {
      setField(mojo, "centralHashFile", "");
      Path mdFile = tempDir.resolve("AGENTS.md");
      Files.writeString(mdFile, "content");

      mojo.execute();

      Path defaultCentralFile = tempDir.resolve("target").resolve("ai-integrity.sha256");
      assertTrue(Files.exists(defaultCentralFile));
    }
  }

  @Nested
  @DisplayName("Resume Mode Tests")
  class ResumeModeTests {

    @Test
    @DisplayName("should skip hash generation when this module is not the resume target")
    void shouldSkipWhenNotResumeTarget() throws Exception {
      MavenExecutionRequest mockRequest = mock(MavenExecutionRequest.class);
      when(session.getRequest()).thenReturn(mockRequest);
      when(mockRequest.getResumeFrom()).thenReturn(":module-b");

      when(project.getArtifactId()).thenReturn("module-a");
      setField(mojo, "resumeFromModule", "module-b");
      Files.writeString(tempDir.resolve("AGENTS.md"), "content");

      mojo.execute();

      verify(log).info(contains("Skipping hash regeneration for module-a"));
      assertFalse(Files.exists(tempDir.resolve("AGENTS.md.sha256")));
    }

    @Test
    @DisplayName("should generate hashes when this module is the resume target")
    void shouldGenerateWhenResumeTarget() throws Exception {
      MavenExecutionRequest mockRequest = mock(MavenExecutionRequest.class);
      when(session.getRequest()).thenReturn(mockRequest);
      when(mockRequest.getResumeFrom()).thenReturn(":module-a");

      when(project.getArtifactId()).thenReturn("module-a");
      setField(mojo, "resumeFromModule", "module-a");
      Files.writeString(tempDir.resolve("AGENTS.md"), "content");

      mojo.execute();

      verify(log).info(contains("Resume mode: regenerating hashes for module-a"));
      assertTrue(Files.exists(tempDir.resolve("AGENTS.md.sha256")));
    }
  }

  @Nested
  @DisplayName("Skip and Reactor Tests")
  class SkipAndReactorTests {

    @Test
    @DisplayName("should skip execution when skip property is true")
    void shouldSkipWhenSkipIsTrue() throws Exception {
      setField(mojo, "skip", true);
      mojo.execute();
      verify(log).info("Skipping execution.");
    }

    @Test
    @DisplayName("should skip execution when skipAlt property is true")
    void shouldSkipWhenSkipAltIsTrue() throws Exception {
      setField(mojo, "skipAlt", true);
      mojo.execute();
      verify(log).info("Skipping execution.");
    }

    @Test
    @DisplayName("should skip execution in non-root project when executionRootOnly is true")
    void shouldSkipInNonRootProject() throws Exception {
      setField(mojo, "executionRootOnly", true);
      when(project.isExecutionRoot()).thenReturn(false);
      mojo.execute();
      verify(log).info("Skipping HashGeneratorMojo execution in non-root project.");
    }

    @Test
    @DisplayName("should hide hash files when hideHashFiles is true")
    void shouldHideHashFiles() throws Exception {
      setField(mojo, "hideHashFiles", true);
      Path mdFile = tempDir.resolve("AGENTS.md");
      Files.writeString(mdFile, "content");

      mojo.execute();

      assertTrue(Files.exists(tempDir.resolve(".AGENTS.md.sha256")));
    }
  }

  @Nested
  @DisplayName("Configuration Edge Case Tests")
  class ConfigurationEdgeCaseTests {

    @Test
    @DisplayName("should use custom output extension")
    void shouldUseCustomOutputExtension() throws Exception {
      setField(mojo, "outputExtension", ".customhash");
      Path mdFile = tempDir.resolve("AGENTS.md");
      Files.writeString(mdFile, "content");

      assertDoesNotThrow(() -> mojo.execute());

      assertTrue(Files.exists(tempDir.resolve("AGENTS.md.customhash")));
    }

    @Test
    @DisplayName("should use project basedir when baseDir is empty")
    void shouldUseProjectBasedir() throws Exception {
      setField(mojo, "baseDir", "");
      when(project.getBasedir()).thenReturn(tempDir.toFile());
      Path mdFile = tempDir.resolve("AGENTS.md");
      Files.writeString(mdFile, "content");

      assertDoesNotThrow(() -> mojo.execute());

      assertTrue(Files.exists(tempDir.resolve("AGENTS.md.sha256")));
    }

    @Test
    @DisplayName("should handle empty or whitespace skipDirs")
    void shouldHandleEmptySkipDirs() throws Exception {
      setField(mojo, "skipDirs", " , \t,target,");
      Path mdFile = tempDir.resolve("AGENTS.md");
      Files.writeString(mdFile, "content");

      assertDoesNotThrow(() -> mojo.execute());

      assertTrue(Files.exists(tempDir.resolve("AGENTS.md.sha256")));
    }

    @Test
    @DisplayName("should handle patterns that do not start with **/")
    void shouldHandleNonGlobbingPatterns() throws Exception {
      setField(mojo, "includes", "src/test/*.md");
      Path srcTest = tempDir.resolve("src").resolve("test");
      Files.createDirectories(srcTest);
      Path mdFile = srcTest.resolve("AGENTS.md");
      Files.writeString(mdFile, "content");

      assertDoesNotThrow(() -> mojo.execute());

      assertTrue(Files.exists(srcTest.resolve("AGENTS.md.sha256")));
    }

    @Test
    @DisplayName("should respect gitignore but allow forceIncludes")
    void shouldRespectGitIgnoreAndForceIncludes() throws Exception {
      setField(mojo, "gitignoreAutoExclude", true);
      setField(mojo, "forceIncludes", "**/*.txt");

      Files.writeString(tempDir.resolve(".gitignore"), "*.md\n");

      Path ignoredMdFile = tempDir.resolve("AGENTS.md");
      Files.writeString(ignoredMdFile, "content");
      Path forcedTxtFile = tempDir.resolve("forced.txt");
      Files.writeString(forcedTxtFile, "content");

      mojo.execute();

      assertFalse(
          Files.exists(tempDir.resolve("AGENTS.md.sha256")),
          "ignored .md file should not be hashed");
      assertTrue(
          Files.exists(tempDir.resolve("forced.txt.sha256")), "forced .txt file should be hashed");
    }
  }

  private static void setField(Object target, String fieldName, Object value) throws Exception {
    Field field = findField(target.getClass(), fieldName);
    field.setAccessible(true);
    field.set(target, value);
  }

  private static Field findField(Class<?> clazz, String fieldName) throws NoSuchFieldException {
    Class<?> current = clazz;
    while (current != null) {
      try {
        return current.getDeclaredField(fieldName);
      } catch (NoSuchFieldException e) {
        current = current.getSuperclass();
      }
    }
    throw new NoSuchFieldException(fieldName);
  }
}
