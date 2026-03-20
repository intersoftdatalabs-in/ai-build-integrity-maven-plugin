/*
 * Copyright 2026 Intersoft Data Labs, LLC.
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
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intsof.ai.build.integrity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.maven.plugin.MojoExecutionException;
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
@DisplayName("HashVerifyMojo")
class HashVerifyMojoTest {

  @Mock private MavenProject project;
  @Mock private Log log;
  @TempDir Path tempDir;

  private HashVerifyMojo mojo;

  @BeforeEach
  void setUp() throws Exception {
    mojo = new HashVerifyMojo();
    mojo.setLog(log);
    setField(mojo, "project", project);
    setField(mojo, "failOnError", true);
    setField(mojo, "algorithmBits", 256);
    setField(mojo, "baseDir", tempDir.toString());
    setField(mojo, "outputExtension", "auto");
    setField(mojo, "skipDirs", "target,.git,node_modules,.tmp");
  }

  @Nested
  @DisplayName("execute")
  class ExecuteTests {

    @Test
    @DisplayName("should pass verification when hashes match")
    void shouldPassWhenHashesMatch() throws Exception {
      // Given
      Path mdFile = tempDir.resolve("AGENTS.md");
      Files.writeString(mdFile, "# AI Agent Instructions");
      String hash = HashUtils.computeHash(mdFile, "SHA-256", false);
      Files.writeString(tempDir.resolve("AGENTS.md.sha256"), hash + "  AGENTS.md\n");

      // When/Then
      assertDoesNotThrow(() -> mojo.execute());
    }

    @Test
    @DisplayName("should fail verification when hash does not match (tampered file)")
    void shouldFailWhenHashMismatch() throws Exception {
      // Given
      Files.writeString(tempDir.resolve("AGENTS.md"), "# AI Agent Instructions");
      Files.writeString(
          tempDir.resolve("AGENTS.md.sha256"),
          "0000000000000000000000000000000000000000000000000000000000000000  AGENTS.md\n");

      // When/Then
      MojoExecutionException ex = assertThrows(MojoExecutionException.class, () -> mojo.execute());
      assertTrue(ex.getMessage().contains("FAILED"));
      assertTrue(ex.getMessage().contains("tampered"));
    }

    @Test
    @DisplayName("should fail when source file is missing for a hash file")
    void shouldFailWhenSourceFileMissing() throws Exception {
      // Given
      Files.writeString(tempDir.resolve("MISSING.md.sha256"), "somehash  MISSING.md\n");

      // When/Then
      MojoExecutionException ex = assertThrows(MojoExecutionException.class, () -> mojo.execute());
      assertTrue(ex.getMessage().contains("FAILED"));
    }

    @Test
    @DisplayName("should warn and return when no hash files are found")
    void shouldWarnWhenNoHashFiles() throws Exception {
      // Given
      Files.writeString(tempDir.resolve("README.md"), "content");

      // When/Then
      assertDoesNotThrow(() -> mojo.execute());
      verify(log).warn(contains("No hash files found"));
    }

    @Test
    @DisplayName("should verify multiple files and report aggregate results")
    void shouldVerifyMultipleFiles() throws Exception {
      // Given
      Path file1 = tempDir.resolve("AGENTS.md");
      Path file2 = tempDir.resolve("SKILL.md");
      Files.writeString(file1, "Agent content");
      Files.writeString(file2, "Skill content");
      Files.writeString(
          tempDir.resolve("AGENTS.md.sha256"),
          HashUtils.computeHash(file1, "SHA-256", false) + "  AGENTS.md\n");
      Files.writeString(
          tempDir.resolve("SKILL.md.sha256"),
          HashUtils.computeHash(file2, "SHA-256", false) + "  SKILL.md\n");

      // When/Then
      assertDoesNotThrow(() -> mojo.execute());
    }

    @Test
    @DisplayName("should detect tampering in nested directories")
    void shouldDetectTamperingInSubdirectories() throws Exception {
      // Given
      Path subDir = tempDir.resolve("skills");
      Files.createDirectory(subDir);
      Path skillFile = subDir.resolve("SKILL.md");
      Files.writeString(skillFile, "Original content");
      String correctHash = HashUtils.computeHash(skillFile, "SHA-256", false);
      Files.writeString(subDir.resolve("SKILL.md.sha256"), correctHash + "  SKILL.md\n");

      // Tamper
      Files.writeString(skillFile, "Tampered content");

      // When/Then
      MojoExecutionException ex = assertThrows(MojoExecutionException.class, () -> mojo.execute());
      assertTrue(ex.getMessage().contains("FAILED"));
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
    @DisplayName("should skip target directories during verification")
    void shouldSkipTargetDirectories() throws Exception {
      // Given
      Path targetDir = tempDir.resolve("target");
      Files.createDirectory(targetDir);
      Files.writeString(targetDir.resolve("generated.md"), "generated");
      Files.writeString(targetDir.resolve("generated.md.sha256"), "badhash  generated.md\n");

      Path mdFile = tempDir.resolve("AGENTS.md");
      Files.writeString(mdFile, "content");
      String hash = HashUtils.computeHash(mdFile, "SHA-256", false);
      Files.writeString(tempDir.resolve("AGENTS.md.sha256"), hash + "  AGENTS.md\n");

      // When/Then
      assertDoesNotThrow(() -> mojo.execute());
    }

    @Test
    @DisplayName("should use project basedir when baseDir is not set")
    void shouldUseProjectBasedir() throws Exception {
      // Given
      setField(mojo, "baseDir", "");
      when(project.getBasedir()).thenReturn(tempDir.toFile());
      Path mdFile = tempDir.resolve("test.md");
      Files.writeString(mdFile, "content");
      String hash = HashUtils.computeHash(mdFile, "SHA-256", false);
      Files.writeString(tempDir.resolve("test.md.sha256"), hash + "  test.md\n");

      // When/Then
      assertDoesNotThrow(() -> mojo.execute());
    }

    @Test
    @DisplayName("should verify .sha512 files when algorithmBits is 512")
    void shouldVerifySha512() throws Exception {
      // Given
      setField(mojo, "algorithmBits", 512);
      Path mdFile = tempDir.resolve("AGENTS.md");
      Files.writeString(mdFile, "content");
      String hash = HashUtils.computeHash(mdFile, "SHA-512", false);
      Files.writeString(tempDir.resolve("AGENTS.md.sha512"), hash + "  AGENTS.md\n");

      // When/Then
      assertDoesNotThrow(() -> mojo.execute());
    }

    @Test
    @DisplayName("should fail when hash file exceeds max size (8 KiB)")
    void shouldFailWhenHashFileTooLarge() throws Exception {
      // Given
      Path mdFile = tempDir.resolve("AGENTS.md");
      Files.writeString(mdFile, "content");
      // Create a hash file larger than 8 KiB
      StringBuilder oversized = new StringBuilder();
      for (int i = 0; i < 9000; i++) {
        oversized.append('a');
      }
      Files.writeString(tempDir.resolve("AGENTS.md.sha256"), oversized.toString());

      // When/Then
      MojoExecutionException ex = assertThrows(MojoExecutionException.class, () -> mojo.execute());
      assertTrue(ex.getMessage().contains("FAILED"));
    }

    @Test
    @DisplayName("should fail when filename in hash file does not match source file")
    void shouldFailWhenFilenameMismatch() throws Exception {
      // Given
      Path mdFile = tempDir.resolve("AGENTS.md");
      Files.writeString(mdFile, "content");
      String hash = HashUtils.computeHash(mdFile, "SHA-256", false);
      // Write hash file with wrong filename
      Files.writeString(tempDir.resolve("AGENTS.md.sha256"), hash + "  WRONG_FILE.md\n");

      // When/Then
      MojoExecutionException ex = assertThrows(MojoExecutionException.class, () -> mojo.execute());
      assertTrue(ex.getMessage().contains("FAILED"));
    }

    @Test
    @DisplayName("should pass when hash file contains hash only (no filename)")
    void shouldPassWhenHashOnlyFormat() throws Exception {
      // Given
      Path mdFile = tempDir.resolve("AGENTS.md");
      Files.writeString(mdFile, "content");
      String hash = HashUtils.computeHash(mdFile, "SHA-256", false);
      // Hash-only format (no embedded filename)
      Files.writeString(tempDir.resolve("AGENTS.md.sha256"), hash + "\n");

      // When/Then
      assertDoesNotThrow(() -> mojo.execute());
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
