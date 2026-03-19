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
  @DisplayName("computeHash")
  class ComputeHashTests {

    @TempDir Path tempDir;

    @Test
    @DisplayName("should compute correct SHA-256 for known content")
    void shouldComputeCorrectSha256() throws IOException, NoSuchAlgorithmException {
      // Given: a file with known content
      Path file = tempDir.resolve("test.md");
      Files.writeString(file, "Hello, World!");

      // When: computing the hash
      String hash = HashUtils.computeHash(file, "SHA-256");

      // Then: the hash matches the known SHA-256 of "Hello, World!"
      assertEquals(
          "dffd6021bb2bd5b0af676290809ec3a53191dd81c7f70a4b28688a362182986f", hash);
    }

    @Test
    @DisplayName("should compute different hashes for different content")
    void shouldComputeDifferentHashes() throws IOException, NoSuchAlgorithmException {
      Path file1 = tempDir.resolve("file1.md");
      Path file2 = tempDir.resolve("file2.md");
      Files.writeString(file1, "Content A");
      Files.writeString(file2, "Content B");

      String hash1 = HashUtils.computeHash(file1, "SHA-256");
      String hash2 = HashUtils.computeHash(file2, "SHA-256");

      assertNotEquals(hash1, hash2);
    }

    @Test
    @DisplayName("should produce same hash for identical content")
    void shouldProduceSameHashForIdenticalContent()
        throws IOException, NoSuchAlgorithmException {
      Path file1 = tempDir.resolve("file1.md");
      Path file2 = tempDir.resolve("file2.md");
      Files.writeString(file1, "Same content");
      Files.writeString(file2, "Same content");

      String hash1 = HashUtils.computeHash(file1, "SHA-256");
      String hash2 = HashUtils.computeHash(file2, "SHA-256");

      assertEquals(hash1, hash2);
    }

    @Test
    @DisplayName("should compute hash for empty file")
    void shouldComputeHashForEmptyFile() throws IOException, NoSuchAlgorithmException {
      Path file = tempDir.resolve("empty.md");
      Files.writeString(file, "");

      String hash = HashUtils.computeHash(file, "SHA-256");

      // SHA-256 of empty input
      assertEquals(
          "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", hash);
    }

    @Test
    @DisplayName("should throw NoSuchAlgorithmException for invalid algorithm")
    void shouldThrowForInvalidAlgorithm() throws IOException {
      Path file = tempDir.resolve("test.md");
      Files.writeString(file, "content");

      assertThrows(
          NoSuchAlgorithmException.class,
          () -> HashUtils.computeHash(file, "SHA-INVALID"));
    }

    @Test
    @DisplayName("should throw IOException for non-existent file")
    void shouldThrowForNonExistentFile() {
      Path file = tempDir.resolve("nonexistent.md");

      assertThrows(IOException.class, () -> HashUtils.computeHash(file, "SHA-256"));
    }
  }

  @Nested
  @DisplayName("parsePatterns")
  class ParsePatternsTests {

    @Test
    @DisplayName("should return empty set for null input")
    void shouldReturnEmptyForNull() {
      Set<String> result = HashUtils.parsePatterns(null);
      assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("should return empty set for empty string")
    void shouldReturnEmptyForEmptyString() {
      Set<String> result = HashUtils.parsePatterns("");
      assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("should parse single pattern")
    void shouldParseSinglePattern() {
      Set<String> result = HashUtils.parsePatterns("**/*.md");
      assertEquals(Set.of("**/*.md"), result);
    }

    @Test
    @DisplayName("should parse comma-separated patterns")
    void shouldParseCommaSeparatedPatterns() {
      Set<String> result = HashUtils.parsePatterns("**/*.md,**/*.txt,**/*.yaml");
      assertEquals(Set.of("**/*.md", "**/*.txt", "**/*.yaml"), result);
    }

    @Test
    @DisplayName("should trim whitespace from patterns")
    void shouldTrimWhitespace() {
      Set<String> result = HashUtils.parsePatterns("  **/*.md , **/*.txt  ");
      assertEquals(Set.of("**/*.md", "**/*.txt"), result);
    }

    @Test
    @DisplayName("should skip empty entries from double commas")
    void shouldSkipEmptyEntries() {
      Set<String> result = HashUtils.parsePatterns("**/*.md,,**/*.txt");
      assertEquals(Set.of("**/*.md", "**/*.txt"), result);
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
