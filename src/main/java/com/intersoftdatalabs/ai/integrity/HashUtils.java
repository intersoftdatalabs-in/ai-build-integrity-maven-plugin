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
 * generate and verify mojos.
 */
public final class HashUtils {

  private static final int BUFFER_SIZE = 8192;

  private HashUtils() {
    // utility class
  }

  /**
   * Resolves the SHA algorithm name from the configured bit width.
   *
   * @param algorithmBits the bit width (e.g. 256, 384, 512)
   * @return the JCA algorithm name (e.g. "SHA-256")
   */
  public static String resolveAlgorithm(int algorithmBits) {
    return "SHA-" + algorithmBits;
  }

  /**
   * Computes the hash of a file using the specified algorithm.
   *
   * @param file the file to hash
   * @param algorithm the JCA algorithm name (e.g. "SHA-256")
   * @return the lowercase hex-encoded hash string
   * @throws IOException if the file cannot be read
   * @throws NoSuchAlgorithmException if the algorithm is not available
   */
  public static String computeHash(Path file, String algorithm)
      throws IOException, NoSuchAlgorithmException {
    MessageDigest digest = MessageDigest.getInstance(algorithm);
    byte[] buffer = new byte[BUFFER_SIZE];
    try (InputStream is = Files.newInputStream(file)) {
      int read;
      while ((read = is.read(buffer)) != -1) {
        digest.update(buffer, 0, read);
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
    for (String pattern : patterns.split(",")) {
      String trimmed = pattern.strip();
      if (!trimmed.isEmpty()) {
        result.add(trimmed);
      }
    }
    return result;
  }

  /**
   * Converts a byte array to a lowercase hexadecimal string.
   *
   * @param bytes the bytes to encode
   * @return the hex-encoded string
   */
  public static String bytesToHex(byte[] bytes) {
    StringBuilder sb = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }
}
