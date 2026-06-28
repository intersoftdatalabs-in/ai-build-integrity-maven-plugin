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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("ArtifactDigestsUtils")
class ArtifactDigestsUtilsTest {

  @Nested
  @DisplayName("isCompromisedAlgorithm")
  class IsCompromisedAlgorithmTests {

    @Test
    @DisplayName("should return true for MD5")
    void shouldReturnTrueForMd5() {
      assertTrue(ArtifactDigestsUtils.isCompromisedAlgorithm("MD5"));
    }

    @Test
    @DisplayName("should return true for SHA-1")
    void shouldReturnTrueForSha1() {
      assertTrue(ArtifactDigestsUtils.isCompromisedAlgorithm("SHA-1"));
    }

    @Test
    @DisplayName("should return false for SHA-256")
    void shouldReturnFalseForSha256() {
      assertFalse(ArtifactDigestsUtils.isCompromisedAlgorithm("SHA-256"));
    }

    @Test
    @DisplayName("should return false for SHA-512")
    void shouldReturnFalseForSha512() {
      assertFalse(ArtifactDigestsUtils.isCompromisedAlgorithm("SHA-512"));
    }
  }

  @Nested
  @DisplayName("validateAlgorithm")
  class ValidateAlgorithmTests {

    @Test
    @DisplayName("should not throw for SHA-256")
    void shouldNotThrowForSha256() {
      assertDoesNotThrow(() -> ArtifactDigestsUtils.validateAlgorithm("SHA-256"));
    }

    @Test
    @DisplayName("should not throw for MD5")
    void shouldNotThrowForMd5() {
      assertDoesNotThrow(() -> ArtifactDigestsUtils.validateAlgorithm("MD5"));
    }

    @Test
    @DisplayName("should throw NoSuchAlgorithmException for invalid algorithm")
    void shouldThrowForInvalidAlgorithm() {
      assertThrows(
          NoSuchAlgorithmException.class,
          () -> ArtifactDigestsUtils.validateAlgorithm("INVALID-ALGORITHM"));
    }
  }

  @Nested
  @DisplayName("extensionForAlgorithm")
  class ExtensionForAlgorithmTests {

    @Test
    @DisplayName("should return .sha256 for SHA-256")
    void shouldReturnSha256Extension() {
      assertEquals(".sha256", ArtifactDigestsUtils.extensionForAlgorithm("SHA-256"));
    }

    @Test
    @DisplayName("should return .sha512 for SHA-512")
    void shouldReturnSha512Extension() {
      assertEquals(".sha512", ArtifactDigestsUtils.extensionForAlgorithm("SHA-512"));
    }

    @Test
    @DisplayName("should return .sha384 for SHA-384")
    void shouldReturnSha384Extension() {
      assertEquals(".sha384", ArtifactDigestsUtils.extensionForAlgorithm("SHA-384"));
    }

    @Test
    @DisplayName("should return .md5 for MD5")
    void shouldReturnMd5Extension() {
      assertEquals(".md5", ArtifactDigestsUtils.extensionForAlgorithm("MD5"));
    }

    @Test
    @DisplayName("should return .sha1 for SHA-1")
    void shouldReturnSha1Extension() {
      assertEquals(".sha1", ArtifactDigestsUtils.extensionForAlgorithm("SHA-1"));
    }
  }

  @Nested
  @DisplayName("computeHashStreaming")
  class ComputeHashStreamingTests {

    @TempDir Path tempDir;

    @Test
    @DisplayName("should compute correct SHA-256 for known content")
    void shouldComputeCorrectSha256() throws IOException, NoSuchAlgorithmException {
      Path file = tempDir.resolve("test.jar");
      Files.write(file, "Hello, World!".getBytes(StandardCharsets.UTF_8));

      String hash = ArtifactDigestsUtils.computeHashStreaming(file, "SHA-256");

      assertEquals("dffd6021bb2bd5b0af676290809ec3a53191dd81c7f70a4b28688a362182986f", hash);
    }

    @Test
    @DisplayName("should compute correct SHA-512 for known content")
    void shouldComputeCorrectSha512() throws IOException, NoSuchAlgorithmException {
      Path file = tempDir.resolve("test.jar");
      Files.write(file, "Hello, World!".getBytes(StandardCharsets.UTF_8));

      String hash = ArtifactDigestsUtils.computeHashStreaming(file, "SHA-512");

      assertEquals(128, hash.length()); // SHA-512 produces 128 hex chars
    }

    @Test
    @DisplayName("should compute same hash for identical binary content")
    void shouldComputeSameHashForIdenticalContent() throws IOException, NoSuchAlgorithmException {
      Path file1 = tempDir.resolve("file1.jar");
      Path file2 = tempDir.resolve("file2.jar");
      Files.write(file1, "Same content".getBytes(StandardCharsets.UTF_8));
      Files.write(file2, "Same content".getBytes(StandardCharsets.UTF_8));

      assertEquals(
          ArtifactDigestsUtils.computeHashStreaming(file1, "SHA-256"),
          ArtifactDigestsUtils.computeHashStreaming(file2, "SHA-256"));
    }

    @Test
    @DisplayName("should compute different hashes for different content")
    void shouldComputeDifferentHashes() throws IOException, NoSuchAlgorithmException {
      Path file1 = tempDir.resolve("file1.jar");
      Path file2 = tempDir.resolve("file2.jar");
      Files.write(file1, "Content A".getBytes(StandardCharsets.UTF_8));
      Files.write(file2, "Content B".getBytes(StandardCharsets.UTF_8));

      assertNotEquals(
          ArtifactDigestsUtils.computeHashStreaming(file1, "SHA-256"),
          ArtifactDigestsUtils.computeHashStreaming(file2, "SHA-256"));
    }

    @Test
    @DisplayName("should compute hash for large binary file")
    void shouldComputeHashForLargeFile() throws IOException, NoSuchAlgorithmException {
      // Create a file larger than the 64KB buffer to test streaming
      Path largeFile = tempDir.resolve("large.jar");
      byte[] chunk = new byte[1024];
      java.util.Arrays.fill(chunk, (byte) 'A');
      try (java.io.OutputStream os = Files.newOutputStream(largeFile)) {
        // Write 100 chunks of 1KB = 100KB total
        for (int i = 0; i < 100; i++) {
          os.write(chunk);
        }
      }

      String hash = ArtifactDigestsUtils.computeHashStreaming(largeFile, "SHA-256");
      assertEquals(64, hash.length()); // SHA-256 produces 64 hex chars
    }

    @Test
    @DisplayName("should throw NoSuchAlgorithmException for invalid algorithm")
    void shouldThrowForInvalidAlgorithm() throws IOException {
      Path file = tempDir.resolve("test.jar");
      Files.write(file, "content".getBytes(StandardCharsets.UTF_8));

      assertThrows(
          NoSuchAlgorithmException.class,
          () -> ArtifactDigestsUtils.computeHashStreaming(file, "INVALID"));
    }

    @Test
    @DisplayName("should throw IOException for non-existent file")
    void shouldThrowForNonExistentFile() {
      Path file = tempDir.resolve("nonexistent.jar");

      assertThrows(
          IOException.class, () -> ArtifactDigestsUtils.computeHashStreaming(file, "SHA-256"));
    }
  }

  @Nested
  @DisplayName("validateArtifactPath")
  class ValidateArtifactPathTests {

    @TempDir Path tempDir;

    @Test
    @DisplayName("should accept artifact inside build directory")
    void shouldAcceptArtifactInsideBuildDirectory() throws IOException {
      Path buildDir = tempDir.resolve("target");
      Files.createDirectory(buildDir);
      Path artifact = buildDir.resolve("myapp-1.0.jar");
      Files.write(artifact, "jar content".getBytes(StandardCharsets.UTF_8));

      Path result = ArtifactDigestsUtils.validateArtifactPath(artifact, buildDir);

      assertEquals(artifact.toRealPath(), result);
    }

    @Test
    @DisplayName("should accept artifact in nested subdirectory of build directory")
    void shouldAcceptArtifactInNestedSubdirectory() throws IOException {
      Path buildDir = tempDir.resolve("target");
      Files.createDirectories(buildDir.resolve("classes"));
      Path artifact = buildDir.resolve("classes").resolve("MyClass.class");
      Files.write(artifact, "class content".getBytes(StandardCharsets.UTF_8));

      Path result = ArtifactDigestsUtils.validateArtifactPath(artifact, buildDir);

      assertEquals(artifact.toRealPath(), result);
    }

    @Test
    @DisplayName("should reject artifact outside build directory")
    void shouldRejectArtifactOutsideBuildDirectory() {
      Path buildDir = tempDir.resolve("target");
      Path artifact = tempDir.resolve("outside.jar");

      assertThrows(
          ArtifactDigestsUtils.PathTraversalException.class,
          () -> ArtifactDigestsUtils.validateArtifactPath(artifact, buildDir));
    }

    @Test
    @DisplayName("should reject artifact with path traversal sequence")
    void shouldRejectPathTraversalSequence() throws IOException {
      Path buildDir = tempDir.resolve("target");
      Files.createDirectory(buildDir);
      // Create a file outside buildDir using path traversal
      Path parentDir = tempDir.resolve("parent");
      Files.createDirectory(parentDir);
      Path artifact = buildDir.resolve("../parent/traversal.jar");
      Files.write(parentDir.resolve("traversal.jar"), "content".getBytes(StandardCharsets.UTF_8));

      assertThrows(
          ArtifactDigestsUtils.PathTraversalException.class,
          () -> ArtifactDigestsUtils.validateArtifactPath(artifact, buildDir));
    }
  }

  @Nested
  @DisplayName("bytesToHex")
  class BytesToHexTests {

    @Test
    @DisplayName("should encode empty array as empty string")
    void shouldEncodeEmptyArray() {
      assertEquals("", ArtifactDigestsUtils.bytesToHex(new byte[0]));
    }

    @Test
    @DisplayName("should encode bytes to lowercase hex")
    void shouldEncodeBytesToLowercaseHex() {
      byte[] bytes = {(byte) 0xAB, (byte) 0xCD, (byte) 0xEF};
      assertEquals("abcdef", ArtifactDigestsUtils.bytesToHex(bytes));
    }

    @Test
    @DisplayName("should pad single-digit hex values with leading zero")
    void shouldPadWithLeadingZero() {
      byte[] bytes = {(byte) 0x01, (byte) 0x0A};
      assertEquals("010a", ArtifactDigestsUtils.bytesToHex(bytes));
    }
  }
}
