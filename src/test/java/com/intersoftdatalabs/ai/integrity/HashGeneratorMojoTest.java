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

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
@DisplayName("HashGeneratorMojo")
class HashGeneratorMojoTest {

  @Mock private MavenProject project;

  @Mock private Log log;

  @TempDir Path tempDir;

  private HashGeneratorMojo mojo;

  @BeforeEach
  void setUp() throws Exception {
    mojo = new HashGeneratorMojo();
    mojo.setLog(log);
    setField(mojo, "project", project);
    setField(mojo, "algorithmBits", 256);
    setField(mojo, "includes", "**/*.md");
    setField(mojo, "excludes", "**/*.sha256");
    setField(mojo, "baseDir", tempDir.toString());
    setField(mojo, "outputExtension", ".sha256");
    setField(mojo, "skipExisting", false);
  }

  @Nested
  @DisplayName("execute")
  class ExecuteTests {

    @Test
    @DisplayName("should generate hash files for matching .md files")
    void shouldGenerateHashFiles() throws Exception {
      // Given: a markdown file exists
      Path mdFile = tempDir.resolve("AGENTS.md");
      Files.writeString(mdFile, "# AI Agent Instructions\nDo the thing.");

      // When: the mojo executes
      mojo.execute();

      // Then: a .sha256 companion file is created
      Path hashFile = tempDir.resolve("AGENTS.md.sha256");
      assertTrue(Files.exists(hashFile), "Hash file should exist");

      String hashContent = Files.readString(hashFile);
      assertTrue(hashContent.contains("AGENTS.md"), "Hash file should reference the source file");
      String hashValue = hashContent.split("\\s+")[0];
      assertEquals(64, hashValue.length(), "SHA-256 hex string should be 64 characters");
    }

    @Test
    @DisplayName("should handle empty directory with no matching files")
    void shouldHandleEmptyDirectory() throws Exception {
      // Given: an empty directory (tempDir has no .md files)

      // When/Then: execute completes without error
      assertDoesNotThrow(() -> mojo.execute());
    }

    @Test
    @DisplayName("should generate hashes for multiple files")
    void shouldGenerateHashesForMultipleFiles() throws Exception {
      // Given: multiple markdown files
      Files.writeString(tempDir.resolve("README.md"), "# Readme");
      Files.writeString(tempDir.resolve("AGENTS.md"), "# Agents");
      Path subDir = tempDir.resolve("sub");
      Files.createDirectory(subDir);
      Files.writeString(subDir.resolve("SKILL.md"), "# Skill");

      // When
      mojo.execute();

      // Then: each file has a companion hash
      assertTrue(Files.exists(tempDir.resolve("README.md.sha256")));
      assertTrue(Files.exists(tempDir.resolve("AGENTS.md.sha256")));
      assertTrue(Files.exists(subDir.resolve("SKILL.md.sha256")));
    }

    @Test
    @DisplayName("should exclude .sha256 files from hashing")
    void shouldExcludeSha256Files() throws Exception {
      // Given: a .md file and an existing .sha256 file
      Files.writeString(tempDir.resolve("AGENTS.md"), "content");
      Files.writeString(tempDir.resolve("AGENTS.md.sha256"), "oldhash  AGENTS.md\n");

      // When
      mojo.execute();

      // Then: no .sha256.sha256 file created
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

      // Then: the existing hash file is not overwritten
      String content = Files.readString(hashFile);
      assertTrue(content.startsWith("existinghash"));
    }

    @Test
    @DisplayName("should overwrite existing hash files when skipExisting is false")
    void shouldOverwriteExistingWhenNotSkipping() throws Exception {
      // Given
      setField(mojo, "skipExisting", false);
      Files.writeString(tempDir.resolve("AGENTS.md"), "content");
      Path hashFile = tempDir.resolve("AGENTS.md.sha256");
      Files.writeString(hashFile, "existinghash  AGENTS.md\n");

      // When
      mojo.execute();

      // Then: the hash file is overwritten with the correct hash
      String content = Files.readString(hashFile);
      assertFalse(content.startsWith("existinghash"));
    }

    @Test
    @DisplayName("should warn and return when base directory does not exist")
    void shouldWarnForMissingBaseDir() throws Exception {
      // Given: a non-existent base directory
      setField(mojo, "baseDir", tempDir.resolve("nonexistent").toString());

      // When/Then
      assertDoesNotThrow(() -> mojo.execute());
      verify(log).warn(contains("Base directory does not exist"));
    }

    @Test
    @DisplayName("should skip target directories")
    void shouldSkipTargetDirectories() throws Exception {
      // Given: a target directory with .md files
      Path targetDir = tempDir.resolve("target");
      Files.createDirectory(targetDir);
      Files.writeString(targetDir.resolve("generated.md"), "generated");

      // Also create a normal .md file
      Files.writeString(tempDir.resolve("AGENTS.md"), "content");

      // When
      mojo.execute();

      // Then: only the non-target file gets a hash
      assertTrue(Files.exists(tempDir.resolve("AGENTS.md.sha256")));
      assertFalse(Files.exists(targetDir.resolve("generated.md.sha256")));
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
