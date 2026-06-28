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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
@DisplayName("ArtifactDigestsCleanMojo")
class ArtifactDigestsCleanMojoTest {

  @Mock private MavenProject project;
  @TempDir Path tempDir;

  private ArtifactDigestsCleanMojo mojo;

  @BeforeEach
  void setUp() throws Exception {
    mojo = new ArtifactDigestsCleanMojo();
    mojo.setLog(new org.apache.maven.plugin.logging.SystemStreamLog());
    setField(mojo, "project", project);
    setField(mojo, "buildDirectory", tempDir.toString());
    setField(mojo, "algorithms", new String[] {"SHA-256"});
    setField(mojo, "hashFileMode", HashFileMode.SIDECAR);
    setField(mojo, "cleanAggregateDigests", true);
  }

  @Nested
  @DisplayName("execute")
  class ExecuteTests {

    @Test
    @DisplayName("should delete sidecar digest files")
    void shouldDeleteSidecarDigestFiles() throws Exception {
      // Given
      Path jarFile = tempDir.resolve("myapp-1.0.0.jar");
      Path digestFile = tempDir.resolve("myapp-1.0.0.jar.sha256");
      Files.write(jarFile, "jar content".getBytes(StandardCharsets.UTF_8));
      Files.write(digestFile, "hash  myapp-1.0.0.jar\n".getBytes(StandardCharsets.UTF_8));

      // When
      mojo.execute();

      // Then
      assertFalse(Files.exists(digestFile), "Digest file should be deleted");
      assertTrue(Files.exists(jarFile), "Artifact should NOT be deleted");
    }

    @Test
    @DisplayName("should delete multiple sidecar digest files")
    void shouldDeleteMultipleSidecarDigestFiles() throws Exception {
      // Given
      Path jar1 = tempDir.resolve("myapp-1.0.0.jar");
      Path jar2 = tempDir.resolve("myapp-1.0.0.war");
      Files.write(jar1, "jar content".getBytes(StandardCharsets.UTF_8));
      Files.write(jar2, "war content".getBytes(StandardCharsets.UTF_8));
      Files.write(
          tempDir.resolve("myapp-1.0.0.jar.sha256"),
          "hash1  myapp-1.0.0.jar\n".getBytes(StandardCharsets.UTF_8));
      Files.write(
          tempDir.resolve("myapp-1.0.0.war.sha256"),
          "hash2  myapp-1.0.0.war\n".getBytes(StandardCharsets.UTF_8));

      // When
      mojo.execute();

      // Then
      assertFalse(Files.exists(tempDir.resolve("myapp-1.0.0.jar.sha256")));
      assertFalse(Files.exists(tempDir.resolve("myapp-1.0.0.war.sha256")));
    }

    @Test
    @DisplayName("should delete nested sidecar digest files")
    void shouldDeleteNestedSidecarDigestFiles() throws Exception {
      // Given
      Path classesDir = tempDir.resolve("classes");
      Files.createDirectory(classesDir);
      Path jarFile = classesDir.resolve("myapp-1.0.0.jar");
      Path digestFile = classesDir.resolve("myapp-1.0.0.jar.sha256");
      Files.write(jarFile, "jar content".getBytes(StandardCharsets.UTF_8));
      Files.write(digestFile, "hash  myapp-1.0.0.jar\n".getBytes(StandardCharsets.UTF_8));

      // When
      mojo.execute();

      // Then
      assertFalse(Files.exists(digestFile), "Nested digest file should be deleted");
    }

    @Test
    @DisplayName("should not throw when no digest files exist")
    void shouldNotThrowWhenNoDigestFilesExist() throws Exception {
      // Given - only the jar, no digest
      Path jarFile = tempDir.resolve("myapp-1.0.0.jar");
      Files.write(jarFile, "jar content".getBytes(StandardCharsets.UTF_8));

      // When/Then - should not throw
      assertDoesNotThrow(() -> mojo.execute());
    }

    @Test
    @DisplayName("should not throw when build directory does not exist")
    void shouldNotThrowWhenBuildDirectoryDoesNotExist() throws Exception {
      // Given
      setField(mojo, "buildDirectory", tempDir.resolve("nonexistent").toString());

      // When/Then - should not throw
      assertDoesNotThrow(() -> mojo.execute());
    }
  }

  @Nested
  @DisplayName("Central Mode Tests")
  class CentralModeTests {

    @Test
    @DisplayName("should delete central digest ledger")
    void shouldDeleteCentralDigestLedger() throws Exception {
      // Given
      setField(mojo, "hashFileMode", HashFileMode.CENTRAL);
      Path centralFile = tempDir.resolve("ai-integrity-artifacts.sha256");
      Files.write(centralFile, "hash  myapp-1.0.0.jar\n".getBytes(StandardCharsets.UTF_8));

      // When
      mojo.execute();

      // Then
      assertFalse(Files.exists(centralFile), "Central ledger should be deleted");
    }

    @Test
    @DisplayName("should use custom central digest file path")
    void shouldUseCustomCentralDigestFile() throws Exception {
      // Given
      setField(mojo, "hashFileMode", HashFileMode.CENTRAL);
      setField(mojo, "centralDigestFile", tempDir.resolve("custom-digests.sha256").toString());
      Path centralFile = tempDir.resolve("custom-digests.sha256");
      Files.write(centralFile, "hash  myapp-1.0.0.jar\n".getBytes(StandardCharsets.UTF_8));

      // When
      mojo.execute();

      // Then
      assertFalse(Files.exists(centralFile), "Custom central ledger should be deleted");
    }
  }

  @Nested
  @DisplayName("Aggregate Digest Tests")
  class AggregateDigestTests {

    @Test
    @DisplayName("should delete aggregate digest file when enabled")
    void shouldDeleteAggregateDigestFileWhenEnabled() throws Exception {
      // Given
      setField(mojo, "cleanAggregateDigests", true);
      Path aggregateFile = tempDir.resolve("ai-integrity-artifacts-aggregate.sha256");
      Files.write(aggregateFile, "aggregatehash  aggregate\n".getBytes(StandardCharsets.UTF_8));

      // When
      mojo.execute();

      // Then
      assertFalse(Files.exists(aggregateFile), "Aggregate digest file should be deleted");
    }

    @Test
    @DisplayName("should not delete aggregate digest file when disabled")
    void shouldNotDeleteAggregateDigestFileWhenDisabled() throws Exception {
      // Given
      setField(mojo, "cleanAggregateDigests", false);
      Path aggregateFile = tempDir.resolve("ai-integrity-artifacts-aggregate.sha256");
      Files.write(aggregateFile, "aggregatehash  aggregate\n".getBytes(StandardCharsets.UTF_8));

      // When
      mojo.execute();

      // Then
      assertTrue(Files.exists(aggregateFile), "Aggregate digest file should NOT be deleted");
    }
  }

  @Nested
  @DisplayName("Skip Tests")
  class SkipTests {

    @Test
    @DisplayName("should skip execution when skip property is true")
    void shouldSkipWhenSkipIsTrue() throws Exception {
      // Given
      setField(mojo, "skip", true);
      Path digestFile = tempDir.resolve("myapp-1.0.0.jar.sha256");
      Files.write(digestFile, "hash  myapp-1.0.0.jar\n".getBytes(StandardCharsets.UTF_8));

      // When
      mojo.execute();

      // Then
      assertTrue(Files.exists(digestFile), "Digest file should NOT be deleted when skipped");
    }

    @Test
    @DisplayName("should skip execution when skipAlt property is true")
    void shouldSkipWhenSkipAltIsTrue() throws Exception {
      // Given
      setField(mojo, "skipAlt", true);
      Path digestFile = tempDir.resolve("myapp-1.0.0.jar.sha256");
      Files.write(digestFile, "hash  myapp-1.0.0.jar\n".getBytes(StandardCharsets.UTF_8));

      // When
      mojo.execute();

      // Then
      assertTrue(Files.exists(digestFile), "Digest file should NOT be deleted when skipped");
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
