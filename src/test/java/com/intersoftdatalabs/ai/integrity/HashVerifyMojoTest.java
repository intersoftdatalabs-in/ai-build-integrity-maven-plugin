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
package com.intersoftdatalabs.ai.integrity;

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
    setField(mojo, "algorithmBits", 256);
    setField(mojo, "includes", "**/*.md");
    setField(mojo, "excludes", "**/*.sha256");
    setField(mojo, "baseDir", tempDir.toString());
    setField(mojo, "outputExtension", ".sha256");
  }

  @Nested
  @DisplayName("execute")
  class ExecuteTests {

    @Test
    @DisplayName("should pass verification when hashes match")
    void shouldPassWhenHashesMatch() throws Exception {
      // Given: a file and its correct hash
      Path mdFile = tempDir.resolve("AGENTS.md");
      Files.writeString(mdFile, "# AI Agent Instructions");

      String hash = HashUtils.computeHash(mdFile, "SHA-256");
      Path hashFile = tempDir.resolve("AGENTS.md.sha256");
      Files.writeString(hashFile, hash + "  AGENTS.md\n");

      // When/Then: verification passes
      assertDoesNotThrow(() -> mojo.execute());
    }

    @Test
    @DisplayName("should fail verification when hash does not match (tampered file)")
    void shouldFailWhenHashMismatch() throws Exception {
      // Given: a file with a mismatched hash (simulating tampering)
      Path mdFile = tempDir.resolve("AGENTS.md");
      Files.writeString(mdFile, "# AI Agent Instructions");

      Path hashFile = tempDir.resolve("AGENTS.md.sha256");
      Files.writeString(hashFile, "0000000000000000000000000000000000000000000000000000000000000000  AGENTS.md\n");

      // When/Then: verification fails
      MojoExecutionException exception =
          assertThrows(MojoExecutionException.class, () -> mojo.execute());
      assertTrue(exception.getMessage().contains("FAILED"));
      assertTrue(exception.getMessage().contains("tampered"));
    }

    @Test
    @DisplayName("should fail when source file is missing for a hash file")
    void shouldFailWhenSourceFileMissing() throws Exception {
      // Given: a hash file without a corresponding source file
      Path hashFile = tempDir.resolve("MISSING.md.sha256");
      Files.writeString(hashFile, "somehash  MISSING.md\n");

      // When/Then
      MojoExecutionException exception =
          assertThrows(MojoExecutionException.class, () -> mojo.execute());
      assertTrue(exception.getMessage().contains("FAILED"));
    }

    @Test
    @DisplayName("should warn and return when no hash files are found")
    void shouldWarnWhenNoHashFiles() throws Exception {
      // Given: directory with .md files but no .sha256 files
      Files.writeString(tempDir.resolve("README.md"), "content");

      // When/Then
      assertDoesNotThrow(() -> mojo.execute());
      verify(log).warn(contains("No hash files found"));
    }

    @Test
    @DisplayName("should verify multiple files and report aggregate results")
    void shouldVerifyMultipleFiles() throws Exception {
      // Given: multiple files with correct hashes
      Path file1 = tempDir.resolve("AGENTS.md");
      Path file2 = tempDir.resolve("SKILL.md");
      Files.writeString(file1, "Agent content");
      Files.writeString(file2, "Skill content");

      String hash1 = HashUtils.computeHash(file1, "SHA-256");
      String hash2 = HashUtils.computeHash(file2, "SHA-256");
      Files.writeString(tempDir.resolve("AGENTS.md.sha256"), hash1 + "  AGENTS.md\n");
      Files.writeString(tempDir.resolve("SKILL.md.sha256"), hash2 + "  SKILL.md\n");

      // When/Then
      assertDoesNotThrow(() -> mojo.execute());
    }

    @Test
    @DisplayName("should detect tampering in nested directories")
    void shouldDetectTamperingInSubdirectories() throws Exception {
      // Given: a nested file with a tampered hash
      Path subDir = tempDir.resolve("skills");
      Files.createDirectory(subDir);
      Path skillFile = subDir.resolve("SKILL.md");
      Files.writeString(skillFile, "Original content");

      String correctHash = HashUtils.computeHash(skillFile, "SHA-256");
      Files.writeString(subDir.resolve("SKILL.md.sha256"), correctHash + "  SKILL.md\n");

      // Now tamper with the file
      Files.writeString(skillFile, "Tampered content");

      // When/Then: verification detects the change
      MojoExecutionException exception =
          assertThrows(MojoExecutionException.class, () -> mojo.execute());
      assertTrue(exception.getMessage().contains("FAILED"));
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
      // Given: a hash file inside a target directory
      Path targetDir = tempDir.resolve("target");
      Files.createDirectory(targetDir);
      Files.writeString(targetDir.resolve("generated.md"), "generated");
      Files.writeString(
          targetDir.resolve("generated.md.sha256"), "badhash  generated.md\n");

      // And a valid hash outside target
      Path mdFile = tempDir.resolve("AGENTS.md");
      Files.writeString(mdFile, "content");
      String hash = HashUtils.computeHash(mdFile, "SHA-256");
      Files.writeString(tempDir.resolve("AGENTS.md.sha256"), hash + "  AGENTS.md\n");

      // When/Then: only the non-target file is verified (should pass)
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
      String hash = HashUtils.computeHash(mdFile, "SHA-256");
      Files.writeString(tempDir.resolve("test.md.sha256"), hash + "  test.md\n");

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
