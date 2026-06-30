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
@DisplayName("ArtifactDigestsGeneratorMojo")
class ArtifactDigestsGeneratorMojoTest {

  @Mock private MavenProject project;
  @TempDir Path tempDir;

  private ArtifactDigestsGeneratorMojo mojo;

  @BeforeEach
  void setUp() throws Exception {
    mojo = new ArtifactDigestsGeneratorMojo();
    mojo.setLog(new org.apache.maven.plugin.logging.SystemStreamLog());
    setField(mojo, "project", project);
    setField(mojo, "buildDirectory", tempDir.toString());
    setField(mojo, "artifactIncludes", "**/*.jar,**/*.war,**/*.zip");
    setField(mojo, "artifactExcludes", "**/*-sources.jar,**/*-javadoc.jar");
    setField(mojo, "includeAttachedArtifacts", false);
    setField(mojo, "algorithms", new String[] {"SHA-256"});
    setField(mojo, "hashFileMode", HashFileMode.SIDECAR);
    setField(mojo, "outputEncoding", "UTF-8");
    setField(mojo, "generateAggregateDigest", false);
    setField(mojo, "warnOnCompromisedAlgorithm", false);
  }

  @Nested
  @DisplayName("execute")
  class ExecuteTests {

    @Test
    @DisplayName("should generate SHA-256 sidecar digest files for JAR artifacts")
    void shouldGenerateSha256SidecarForJars() throws Exception {
      // Given
      Path jarFile = tempDir.resolve("myapp-1.0.0.jar");
      Files.write(jarFile, "jar content".getBytes(StandardCharsets.UTF_8));

      // When
      mojo.execute();

      // Then
      Path digestFile = tempDir.resolve("myapp-1.0.0.jar.sha256");
      assertTrue(Files.exists(digestFile), "Digest file should exist");

      String digestContent = new String(Files.readAllBytes(digestFile), StandardCharsets.UTF_8);
      assertTrue(digestContent.contains("myapp-1.0.0.jar"));
      String hashValue = digestContent.split("\\s+")[0];
      assertEquals(64, hashValue.length(), "SHA-256 hex should be 64 chars");
    }

    @Test
    @DisplayName("should generate SHA-512 digest when configured")
    void shouldGenerateSha512Digest() throws Exception {
      // Given
      setField(mojo, "algorithms", new String[] {"SHA-512"});
      Path jarFile = tempDir.resolve("myapp-1.0.0.jar");
      Files.write(jarFile, "jar content".getBytes(StandardCharsets.UTF_8));

      // When
      mojo.execute();

      // Then
      Path digestFile = tempDir.resolve("myapp-1.0.0.jar.sha512");
      assertTrue(Files.exists(digestFile), "SHA-512 digest file should exist");
      String hashValue =
          new String(Files.readAllBytes(digestFile), StandardCharsets.UTF_8).split("\\s+")[0];
      assertEquals(128, hashValue.length(), "SHA-512 hex should be 128 chars");
    }

    @Test
    @DisplayName("should skip sources and javadoc JARs by default")
    void shouldSkipSourcesAndJavadocJars() throws Exception {
      // Given
      Path sourcesJar = tempDir.resolve("myapp-1.0.0-sources.jar");
      Path javadocJar = tempDir.resolve("myapp-1.0.0-javadoc.jar");
      Path mainJar = tempDir.resolve("myapp-1.0.0.jar");
      Files.write(sourcesJar, "sources".getBytes(StandardCharsets.UTF_8));
      Files.write(javadocJar, "javadoc".getBytes(StandardCharsets.UTF_8));
      Files.write(mainJar, "main".getBytes(StandardCharsets.UTF_8));

      // When
      mojo.execute();

      // Then
      assertFalse(
          Files.exists(tempDir.resolve("myapp-1.0.0-sources.jar.sha256")),
          "Sources JAR should not have digest");
      assertFalse(
          Files.exists(tempDir.resolve("myapp-1.0.0-javadoc.jar.sha256")),
          "Javadoc JAR should not have digest");
      assertTrue(
          Files.exists(tempDir.resolve("myapp-1.0.0.jar.sha256")), "Main JAR should have digest");
    }

    @Test
    @DisplayName("should handle empty build directory with no artifacts")
    void shouldHandleEmptyBuildDirectory() throws Exception {
      // When/Then - should not throw
      assertDoesNotThrow(() -> mojo.execute());
    }

    @Test
    @DisplayName("should skip when build directory does not exist")
    void shouldSkipWhenBuildDirectoryDoesNotExist() throws Exception {
      // Given
      setField(mojo, "buildDirectory", tempDir.resolve("nonexistent").toString());

      // When/Then - should not throw, just warn
      assertDoesNotThrow(() -> mojo.execute());
    }

    @Test
    @DisplayName("should discover artifacts in nested directories")
    void shouldDiscoverArtifactsInNestedDirectories() throws Exception {
      // Given
      Path classesDir = tempDir.resolve("classes");
      Files.createDirectory(classesDir);
      Path jarFile = classesDir.resolve("myapp-1.0.0.jar");
      Files.write(jarFile, "jar content".getBytes(StandardCharsets.UTF_8));

      // When
      mojo.execute();

      // Then
      assertTrue(
          Files.exists(classesDir.resolve("myapp-1.0.0.jar.sha256")),
          "Digest file in nested directory should exist");
    }
  }

  @Nested
  @DisplayName("Central Mode Tests")
  class CentralModeTests {

    @Test
    @DisplayName("should generate central digest ledger")
    void shouldGenerateCentralLedger() throws Exception {
      // Given
      setField(mojo, "hashFileMode", HashFileMode.CENTRAL);
      Path jarFile = tempDir.resolve("myapp-1.0.0.jar");
      Files.write(jarFile, "jar content".getBytes(StandardCharsets.UTF_8));

      // When
      mojo.execute();

      // Then
      Path centralFile = tempDir.resolve("ai-integrity-artifacts.sha256");
      assertTrue(Files.exists(centralFile), "Central ledger should exist");
      String content = new String(Files.readAllBytes(centralFile), StandardCharsets.UTF_8);
      assertTrue(content.contains("myapp-1.0.0.jar"));
    }

    @Test
    @DisplayName("should use custom central digest file path")
    void shouldUseCustomCentralDigestFile() throws Exception {
      // Given
      setField(mojo, "hashFileMode", HashFileMode.CENTRAL);
      setField(mojo, "centralDigestFile", tempDir.resolve("custom-digests.sha256").toString());
      Path jarFile = tempDir.resolve("myapp-1.0.0.jar");
      Files.write(jarFile, "jar content".getBytes(StandardCharsets.UTF_8));

      // When
      mojo.execute();

      // Then
      Path centralFile = tempDir.resolve("custom-digests.sha256");
      assertTrue(Files.exists(centralFile), "Custom central ledger should exist");
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
      Path jarFile = tempDir.resolve("myapp-1.0.0.jar");
      Files.write(jarFile, "jar content".getBytes(StandardCharsets.UTF_8));

      // When
      mojo.execute();

      // Then
      assertFalse(
          Files.exists(tempDir.resolve("myapp-1.0.0.jar.sha256")),
          "No digest should be generated when skipped");
    }

    @Test
    @DisplayName("should skip execution when skipAlt property is true")
    void shouldSkipWhenSkipAltIsTrue() throws Exception {
      // Given
      setField(mojo, "skipAlt", true);
      Path jarFile = tempDir.resolve("myapp-1.0.0.jar");
      Files.write(jarFile, "jar content".getBytes(StandardCharsets.UTF_8));

      // When
      mojo.execute();

      // Then
      assertFalse(
          Files.exists(tempDir.resolve("myapp-1.0.0.jar.sha256")),
          "No digest should be generated when skipped");
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
