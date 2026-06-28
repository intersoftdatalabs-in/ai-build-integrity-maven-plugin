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
import org.apache.maven.plugin.MojoExecutionException;
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
@DisplayName("ArtifactDigestsVerifyMojo")
class ArtifactDigestsVerifyMojoTest {

  @Mock private MavenProject project;
  @TempDir Path tempDir;

  private ArtifactDigestsVerifyMojo mojo;

  @BeforeEach
  void setUp() throws Exception {
    mojo = new ArtifactDigestsVerifyMojo();
    mojo.setLog(new org.apache.maven.plugin.logging.SystemStreamLog());
    setField(mojo, "project", project);
    setField(mojo, "buildDirectory", tempDir.toString());
    setField(mojo, "algorithms", new String[] {"SHA-256"});
    setField(mojo, "hashFileMode", HashFileMode.SIDECAR);
    setField(mojo, "failOnError", true);
    setField(mojo, "generateAuditReport", false);
  }

  @Nested
  @DisplayName("execute")
  class ExecuteTests {

    @Test
    @DisplayName("should verify valid sidecar digest")
    void shouldVerifyValidSidecarDigest() throws Exception {
      // Given - create artifact and its digest
      Path jarFile = tempDir.resolve("myapp-1.0.0.jar");
      Files.write(jarFile, "jar content".getBytes(StandardCharsets.UTF_8));

      String hash = ArtifactDigestsUtils.computeHashStreaming(jarFile, "SHA-256");
      Path digestFile = tempDir.resolve("myapp-1.0.0.jar.sha256");
      Files.write(digestFile, (hash + "  myapp-1.0.0.jar\n").getBytes(StandardCharsets.UTF_8));

      // When/Then - should not throw
      assertDoesNotThrow(() -> mojo.execute());
    }

    @Test
    @DisplayName("should fail when artifact is tampered")
    void shouldFailWhenArtifactIsTampered() throws Exception {
      // Given - create digest for original content
      Path jarFile = tempDir.resolve("myapp-1.0.0.jar");
      Files.write(jarFile, "original content".getBytes(StandardCharsets.UTF_8));

      String hash = ArtifactDigestsUtils.computeHashStreaming(jarFile, "SHA-256");
      Path digestFile = tempDir.resolve("myapp-1.0.0.jar.sha256");
      Files.write(digestFile, (hash + "  myapp-1.0.0.jar\n").getBytes(StandardCharsets.UTF_8));

      // Tamper with the artifact
      Files.write(jarFile, "tampered content".getBytes(StandardCharsets.UTF_8));

      // When/Then - should throw
      assertThrows(MojoExecutionException.class, () -> mojo.execute());
    }

    @Test
    @DisplayName("should fail when artifact is missing")
    void shouldFailWhenArtifactIsMissing() throws Exception {
      // Given - create digest but no artifact
      Path digestFile = tempDir.resolve("myapp-1.0.0.jar.sha256");
      Files.write(digestFile, ("abc123def456  myapp-1.0.0.jar\n").getBytes(StandardCharsets.UTF_8));

      // When/Then - should throw
      assertThrows(MojoExecutionException.class, () -> mojo.execute());
    }

    @Test
    @DisplayName("should not throw when failOnError is false and verification fails")
    void shouldNotThrowWhenFailOnErrorIsFalse() throws Exception {
      // Given
      setField(mojo, "failOnError", false);
      Path jarFile = tempDir.resolve("myapp-1.0.0.jar");
      Files.write(jarFile, "original content".getBytes(StandardCharsets.UTF_8));

      String hash = ArtifactDigestsUtils.computeHashStreaming(jarFile, "SHA-256");
      Path digestFile = tempDir.resolve("myapp-1.0.0.jar.sha256");
      Files.write(digestFile, (hash + "  myapp-1.0.0.jar\n").getBytes(StandardCharsets.UTF_8));

      // Tamper with the artifact
      Files.write(jarFile, "tampered content".getBytes(StandardCharsets.UTF_8));

      // When/Then - should not throw even though verification fails
      assertDoesNotThrow(() -> mojo.execute());
    }

    @Test
    @DisplayName("should skip when build directory does not exist")
    void shouldSkipWhenBuildDirectoryDoesNotExist() throws Exception {
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
    @DisplayName("should verify artifacts from central ledger")
    void shouldVerifyArtifactsFromCentralLedger() throws Exception {
      // Given
      setField(mojo, "hashFileMode", HashFileMode.CENTRAL);
      Path jarFile = tempDir.resolve("myapp-1.0.0.jar");
      Files.write(jarFile, "jar content".getBytes(StandardCharsets.UTF_8));

      String hash = ArtifactDigestsUtils.computeHashStreaming(jarFile, "SHA-256");
      Path centralFile = tempDir.resolve("ai-integrity-artifacts.sha256");
      Files.write(centralFile, (hash + "  myapp-1.0.0.jar\n").getBytes(StandardCharsets.UTF_8));

      // When/Then
      assertDoesNotThrow(() -> mojo.execute());
    }

    @Test
    @DisplayName("should fail verification from central ledger when artifact is tampered")
    void shouldFailCentralLedgerVerificationWhenTampered() throws Exception {
      // Given
      setField(mojo, "hashFileMode", HashFileMode.CENTRAL);
      Path jarFile = tempDir.resolve("myapp-1.0.0.jar");
      Files.write(jarFile, "original content".getBytes(StandardCharsets.UTF_8));

      String hash = ArtifactDigestsUtils.computeHashStreaming(jarFile, "SHA-256");
      Path centralFile = tempDir.resolve("ai-integrity-artifacts.sha256");
      Files.write(centralFile, (hash + "  myapp-1.0.0.jar\n").getBytes(StandardCharsets.UTF_8));

      // Tamper
      Files.write(jarFile, "tampered content".getBytes(StandardCharsets.UTF_8));

      // When/Then
      assertThrows(MojoExecutionException.class, () -> mojo.execute());
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

      // When/Then
      assertDoesNotThrow(() -> mojo.execute());
    }

    @Test
    @DisplayName("should skip execution when skipAlt property is true")
    void shouldSkipWhenSkipAltIsTrue() throws Exception {
      // Given
      setField(mojo, "skipAlt", true);

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
