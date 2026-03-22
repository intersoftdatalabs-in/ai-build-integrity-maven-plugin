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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Set;

/**
 * Shared utility methods for hash computation, pattern parsing, and hex encoding used by both the
 * generate and verify mojos. All methods are designed to be fast and allocation-light for use in
 * large multi-module project traversals.
 */
public final class HashUtils {

  /** I/O buffer size — 64 KiB balances syscall overhead vs memory on large file trees. */
  private static final int BUFFER_SIZE = 65_536;

  /** Pre-computed hex lookup table — avoids String.format per byte. */
  private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

  /** Utility class: no instantiation allowed. */
  private HashUtils() {
    // utility class
  }

  /**
   * Resolves the SHA algorithm name from the configured bit width.
   *
   * @param algorithmBits the bit width (supported: 256, 384, 512)
   * @return the JCA algorithm name (e.g. "SHA-256")
   */
  public static String resolveAlgorithm(int algorithmBits) {
    return "SHA-" + algorithmBits;
  }

  /**
   * Returns the conventional file extension for a given hash algorithm bit width (e.g. ".sha256",
   * ".sha512").
   *
   * @param algorithmBits the bit width (supported: 256, 384, 512)
   * @return the extension string including the leading dot
   */
  public static String extensionForBits(int algorithmBits) {
    return ".sha" + algorithmBits;
  }

  /**
   * Computes the hash of a file using the specified algorithm.
   *
   * <p>Uses a 64 KiB buffer for streaming reads, keeping heap pressure low even on large files.
   *
   * @param file the file to hash
   * @param algorithm the JCA algorithm name (e.g. "SHA-256")
   * @param normalizeLineEndings if true, normalizes CRLF to LF in memory before hashing
   * @return the lowercase hex-encoded hash string
   * @throws IOException if the file cannot be read
   * @throws NoSuchAlgorithmException if the algorithm is not available
   */
  public static String computeHash(Path file, String algorithm, boolean normalizeLineEndings)
      throws IOException, NoSuchAlgorithmException {
    MessageDigest digest = MessageDigest.getInstance(algorithm);

    if (normalizeLineEndings) {
      String content = Files.readString(file, java.nio.charset.StandardCharsets.UTF_8);
      String normalized = content.replace("\r\n", "\n");
      digest.update(normalized.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    } else {
      byte[] buffer = new byte[BUFFER_SIZE];
      try (InputStream is = Files.newInputStream(file)) {
        int read;
        while ((read = is.read(buffer)) != -1) {
          digest.update(buffer, 0, read);
        }
      }
    }
    return bytesToHex(digest.digest());
  }

  /**
   * Parses a comma-separated pattern string into a set of trimmed, non-empty patterns.
   *
   * @param patterns comma-separated glob patterns (may be {@code null})
   * @return a set of individual pattern strings
   */
  public static Set<String> parsePatterns(String patterns) {
    Set<String> result = new HashSet<>();
    if (patterns == null || patterns.isEmpty()) {
      return result;
    }
    for (String pattern : patterns.split("[\\s,]+")) {
      if (!pattern.isEmpty()) {
        result.add(pattern);
      }
    }
    return result;
  }

  /**
   * Converts a byte array to a lowercase hexadecimal string using a lookup table. This is
   * significantly faster than {@code String.format("%02x")} per byte.
   *
   * @param bytes the bytes to encode
   * @return the hex-encoded string
   */
  public static String bytesToHex(byte[] bytes) {
    char[] hex = new char[bytes.length * 2];
    for (int i = 0; i < bytes.length; i++) {
      int v = bytes[i] & 0xFF;
      hex[i * 2] = HEX_CHARS[v >>> 4];
      hex[i * 2 + 1] = HEX_CHARS[v & 0x0F];
    }
    return new String(hex);
  }
}
