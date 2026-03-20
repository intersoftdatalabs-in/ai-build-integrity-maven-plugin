/*
 * Copyright 2026 Intersoft Data Labs, Inc.
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("HashUtils")
class HashUtilsTest {

  @Nested
  @DisplayName("resolveAlgorithm")
  class ResolveAlgorithmTests {

    @Test
    @DisplayName("should return SHA-256 for 256 bits")
    void shouldReturnSha256() {
      assertEquals("SHA-256", HashUtils.resolveAlgorithm(256));
    }

    @Test
    @DisplayName("should return SHA-512 for 512 bits")
    void shouldReturnSha512() {
      assertEquals("SHA-512", HashUtils.resolveAlgorithm(512));
    }

    @Test
    @DisplayName("should return SHA-384 for 384 bits")
    void shouldReturnSha384() {
      assertEquals("SHA-384", HashUtils.resolveAlgorithm(384));
    }
  }

  @Nested
  @DisplayName("extensionForBits")
  class ExtensionForBitsTests {

    @Test
    @DisplayName("should return .sha256 for 256 bits")
    void shouldReturnSha256Extension() {
      assertEquals(".sha256", HashUtils.extensionForBits(256));
    }

    @Test
    @DisplayName("should return .sha512 for 512 bits")
    void shouldReturnSha512Extension() {
      assertEquals(".sha512", HashUtils.extensionForBits(512));
    }

    @Test
    @DisplayName("should return .sha384 for 384 bits")
    void shouldReturnSha384Extension() {
      assertEquals(".sha384", HashUtils.extensionForBits(384));
    }
  }

  @Nested
  @DisplayName("computeHash")
  class ComputeHashTests {

    @TempDir Path tempDir;

    @Test
    @DisplayName("should compute correct SHA-256 for known content")
    void shouldComputeCorrectSha256() throws IOException, NoSuchAlgorithmException {
      // Given
      Path file = tempDir.resolve("test.md");
      Files.writeString(file, "Hello, World!");

      // When
      String hash = HashUtils.computeHash(file, "SHA-256", false);

      // Then
      assertEquals("dffd6021bb2bd5b0af676290809ec3a53191dd81c7f70a4b28688a362182986f", hash);
    }

    @Test
    @DisplayName("should compute correct SHA-512 for known content")
    void shouldComputeCorrectSha512() throws IOException, NoSuchAlgorithmException {
      // Given
      Path file = tempDir.resolve("test.md");
      Files.writeString(file, "Hello, World!");

      // When
      String hash = HashUtils.computeHash(file, "SHA-512", false);

      // Then: SHA-512 produces 128 hex chars
      assertEquals(128, hash.length());
    }

    @Test
    @DisplayName("should compute different hashes for different content")
    void shouldComputeDifferentHashes() throws IOException, NoSuchAlgorithmException {
      Path file1 = tempDir.resolve("file1.md");
      Path file2 = tempDir.resolve("file2.md");
      Files.writeString(file1, "Content A");
      Files.writeString(file2, "Content B");

      String hash1 = HashUtils.computeHash(file1, "SHA-256", false);
      String hash2 = HashUtils.computeHash(file2, "SHA-256", false);

      assertNotEquals(hash1, hash2);
    }

    @Test
    @DisplayName("should produce same hash for identical content")
    void shouldProduceSameHashForIdenticalContent() throws IOException, NoSuchAlgorithmException {
      Path file1 = tempDir.resolve("file1.md");
      Path file2 = tempDir.resolve("file2.md");
      Files.writeString(file1, "Same content");
      Files.writeString(file2, "Same content");

      assertEquals(
          HashUtils.computeHash(file1, "SHA-256", false),
          HashUtils.computeHash(file2, "SHA-256", false));
    }

    @Test
    @DisplayName("should compute hash for empty file")
    void shouldComputeHashForEmptyFile() throws IOException, NoSuchAlgorithmException {
      Path file = tempDir.resolve("empty.md");
      Files.writeString(file, "");

      assertEquals(
          "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
          HashUtils.computeHash(file, "SHA-256", false));
    }

    @Test
    @DisplayName("should throw NoSuchAlgorithmException for invalid algorithm")
    void shouldThrowForInvalidAlgorithm() throws IOException {
      Path file = tempDir.resolve("test.md");
      Files.writeString(file, "content");

      assertThrows(
          NoSuchAlgorithmException.class, () -> HashUtils.computeHash(file, "SHA-INVALID", false));
    }

    @Test
    @DisplayName("should throw IOException for non-existent file")
    void shouldThrowForNonExistentFile() {
      Path file = tempDir.resolve("nonexistent.md");

      assertThrows(IOException.class, () -> HashUtils.computeHash(file, "SHA-256", false));
    }
  }

  @Nested
  @DisplayName("parsePatterns")
  class ParsePatternsTests {

    @Test
    @DisplayName("should return empty set for null input")
    void shouldReturnEmptyForNull() {
      assertTrue(HashUtils.parsePatterns(null).isEmpty());
    }

    @Test
    @DisplayName("should return empty set for empty string")
    void shouldReturnEmptyForEmptyString() {
      assertTrue(HashUtils.parsePatterns("").isEmpty());
    }

    @Test
    @DisplayName("should parse single pattern")
    void shouldParseSinglePattern() {
      assertEquals(Set.of("**/*.md"), HashUtils.parsePatterns("**/*.md"));
    }

    @Test
    @DisplayName("should parse comma-separated patterns")
    void shouldParseCommaSeparatedPatterns() {
      assertEquals(
          Set.of("**/*.md", "**/*.txt", "**/*.yaml"),
          HashUtils.parsePatterns("**/*.md,**/*.txt,**/*.yaml"));
    }

    @Test
    @DisplayName("should trim whitespace from patterns")
    void shouldTrimWhitespace() {
      assertEquals(
          Set.of("**/*.md", "**/*.txt"), HashUtils.parsePatterns("  **/*.md , **/*.txt  "));
    }

    @Test
    @DisplayName("should skip empty entries from double commas")
    void shouldSkipEmptyEntries() {
      assertEquals(Set.of("**/*.md", "**/*.txt"), HashUtils.parsePatterns("**/*.md,,**/*.txt"));
    }
  }

  @Nested
  @DisplayName("bytesToHex")
  class BytesToHexTests {

    @Test
    @DisplayName("should encode empty array as empty string")
    void shouldEncodeEmptyArray() {
      assertEquals("", HashUtils.bytesToHex(new byte[0]));
    }

    @Test
    @DisplayName("should encode bytes to lowercase hex")
    void shouldEncodeBytesToLowercaseHex() {
      byte[] bytes = {(byte) 0xAB, (byte) 0xCD, (byte) 0xEF};
      assertEquals("abcdef", HashUtils.bytesToHex(bytes));
    }

    @Test
    @DisplayName("should pad single-digit hex values with leading zero")
    void shouldPadWithLeadingZero() {
      byte[] bytes = {(byte) 0x01, (byte) 0x0A};
      assertEquals("010a", HashUtils.bytesToHex(bytes));
    }
  }
}
