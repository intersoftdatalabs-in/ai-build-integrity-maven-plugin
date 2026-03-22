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
@DisplayName("HashCleanMojo")
class HashCleanMojoTest {

  @Mock private MavenProject project;
  @Mock private Log log;
  @TempDir Path tempDir;

  private HashCleanMojo mojo;

  @BeforeEach
  void setUp() throws Exception {
    mojo = new HashCleanMojo();
    mojo.setLog(log);
    setField(mojo, "project", project);
    setField(mojo, "algorithmBits", 256);
    setField(mojo, "baseDir", tempDir.toString());
    setField(mojo, "outputExtension", "auto");
    setField(mojo, "skipDirs", "target,.git,node_modules,.tmp");
  }

  @Nested
  @DisplayName("execute")
  class ExecuteTests {

    @Test
    @DisplayName("should delete hash sidecar files")
    void shouldDeleteHashFiles() throws Exception {
      // Given
      Path hashFile1 = tempDir.resolve("AGENTS.md.sha256");
      Path hashFile2 = tempDir.resolve("SKILL.md.sha256");
      Files.writeString(hashFile1, "hash1");
      Files.writeString(hashFile2, "hash2");

      Path regularFile = tempDir.resolve("AGENTS.md");
      Files.writeString(regularFile, "content");

      // When/Then
      assertDoesNotThrow(() -> mojo.execute());

      assertFalse(Files.exists(hashFile1), "hashFile1 should be deleted");
      assertFalse(Files.exists(hashFile2), "hashFile2 should be deleted");
      assertTrue(Files.exists(regularFile), "regular file should NOT be deleted");
    }

    @Test
    @DisplayName("should warn and return when no hash files are found")
    void shouldWarnWhenNoHashFiles() throws Exception {
      // Given
      Files.writeString(tempDir.resolve("README.md"), "content");

      // When/Then
      assertDoesNotThrow(() -> mojo.execute());
      verify(log).info(contains("No hash files found to clean."));
    }

    @Test
    @DisplayName("should skip target directories during cleanup")
    void shouldSkipTargetDirectories() throws Exception {
      // Given
      Path targetDir = tempDir.resolve("target");
      Files.createDirectory(targetDir);
      Path skippedHash = targetDir.resolve("generated.md.sha256");
      Files.writeString(skippedHash, "hash");

      Path validHash = tempDir.resolve("AGENTS.md.sha256");
      Files.writeString(validHash, "hash");

      // When/Then
      assertDoesNotThrow(() -> mojo.execute());

      // Target hash should remain
      assertTrue(Files.exists(skippedHash), "target hash should NOT be deleted");
      // Valid hash should be deleted
      assertFalse(Files.exists(validHash), "valid hash should be deleted");
    }

    @Test
    @DisplayName("should use project basedir when baseDir is not set")
    void shouldUseProjectBasedir() throws Exception {
      // Given
      setField(mojo, "baseDir", "");
      when(project.getBasedir()).thenReturn(tempDir.toFile());
      Path hashFile = tempDir.resolve("test.md.sha256");
      Files.writeString(hashFile, "hash");

      // When/Then
      assertDoesNotThrow(() -> mojo.execute());
      assertFalse(Files.exists(hashFile), "hash file should be deleted");
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
    @DisplayName("should delete central ledger file")
    void shouldDeleteCentralLedger() throws Exception {
      Path centralFile = tempDir.resolve("ai-integrity.sha256");
      Files.writeString(centralFile, "hash  AGENTS.md\n");

      mojo.execute();

      assertFalse(Files.exists(centralFile), "Central file should be deleted");
    }

    @Test
    @DisplayName("should warn and return when central ledger is missing")
    void shouldWarnIfCentralLedgerMissing() throws Exception {
      assertDoesNotThrow(() -> mojo.execute());
      verify(log).info(contains("No central hash file found to clean."));
    }

    @Test
    @DisplayName("should use default central ledger path if none is specified")
    void shouldUseDefaultCentralLedger() throws Exception {
      setField(mojo, "centralHashFile", "");
      Path defaultCentralFile = tempDir.resolve("target").resolve("ai-integrity.sha256");
      Files.createDirectories(defaultCentralFile.getParent());
      Files.writeString(defaultCentralFile, "hash");

      mojo.execute();

      assertFalse(Files.exists(defaultCentralFile));
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
      verify(log).info("Skipping HashCleanMojo execution in non-root project.");
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
      Path hashFile = tempDir.resolve("AGENTS.md.customhash");
      Files.writeString(hashFile, "hash");

      mojo.execute();

      assertFalse(Files.exists(hashFile));
    }

    @Test
    @DisplayName("should handle empty or whitespace skipDirs")
    void shouldHandleEmptySkipDirs() throws Exception {
      setField(mojo, "skipDirs", " , \t,target,");
      Path mdFile = tempDir.resolve("AGENTS.md");
      Files.writeString(mdFile, "content");
      Path hashFile = tempDir.resolve("AGENTS.md.sha256");
      Files.writeString(hashFile, "hash");

      mojo.execute();

      assertFalse(Files.exists(hashFile));
    }

    @Test
    @DisplayName("should respect gitignore but allow forceIncludes")
    void shouldRespectGitIgnoreAndForceIncludes() throws Exception {
      setField(mojo, "gitignoreAutoExclude", true);
      setField(mojo, "forceIncludes", "**/*.txt.sha256");

      Files.writeString(tempDir.resolve(".gitignore"), "**/*.md.sha256\n");

      Path subdir = tempDir.resolve("subdir");
      Files.createDirectory(subdir);

      Path ignoredMdFile = subdir.resolve("AGENTS.md.sha256");
      Files.writeString(ignoredMdFile, "hash");
      Path forcedTxtFile = subdir.resolve("forced.txt.sha256");
      Files.writeString(forcedTxtFile, "hash");

      mojo.execute();

      assertTrue(
          Files.exists(subdir.resolve("AGENTS.md.sha256")),
          "ignored .md.sha256 file should NOT be deleted");
      assertFalse(
          Files.exists(subdir.resolve("forced.txt.sha256")),
          "forced .txt.sha256 file should be deleted");
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
