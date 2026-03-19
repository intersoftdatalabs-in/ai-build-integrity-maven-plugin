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
