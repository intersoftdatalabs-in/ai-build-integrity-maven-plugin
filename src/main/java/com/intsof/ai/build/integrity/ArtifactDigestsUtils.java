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
 * Utility methods for artifact digest computation, artifact discovery, and path validation.
 *
 * <p>Unlike {@link HashUtils} which supports line-ending normalization for text files, this class
 * provides <b>streaming-only</b> hash computation suitable for binary artifacts (JARs, WARs, ZIPs).
 * Binary artifacts must never be loaded into heap memory in their entirety.
 *
 * <p>All methods are designed to be fast and allocation-light for use in large multi-module project
 * builds.
 */
public final class ArtifactDigestsUtils {

  /** I/O buffer size — 64 KiB balances syscall overhead vs memory on large artifact files. */
  private static final int BUFFER_SIZE = 65_536;

  /** Pre-computed hex lookup table — avoids String.format per byte. */
  private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

  /** Algorithms considered compromised and subject to opt-in warning. */
  private static final Set<String> COMPROMISED_ALGORITHMS = new HashSet<>();

  static {
    COMPROMISED_ALGORITHMS.add("MD5");
    COMPROMISED_ALGORITHMS.add("SHA-1");
  }

  /** Utility class: no instantiation allowed. */
  private ArtifactDigestsUtils() {
    // utility class
  }

  /**
   * Checks whether a given algorithm name is considered compromised.
   *
   * @param algorithm the algorithm name (e.g. "MD5", "SHA-1")
   * @return true if the algorithm is compromised and requires explicit opt-in
   */
  public static boolean isCompromisedAlgorithm(String algorithm) {
    return COMPROMISED_ALGORITHMS.contains(algorithm);
  }

  /**
   * Validates that an algorithm is available in the current JVM's MessageDigest provider.
   *
   * @param algorithm the JCA algorithm name (e.g. "SHA-256", "MD5")
   * @throws NoSuchAlgorithmException if the algorithm is not available
   */
  public static void validateAlgorithm(String algorithm) throws NoSuchAlgorithmException {
    MessageDigest.getInstance(algorithm);
  }

  /**
   * Returns the conventional file extension for a given hash algorithm (e.g. ".sha256", ".md5").
   *
   * @param algorithm the JCA algorithm name (e.g. "SHA-256", "MD5")
   * @return the extension string including the leading dot (e.g. ".sha256")
   */
  public static String extensionForAlgorithm(String algorithm) {
    return "." + algorithm.toLowerCase().replace("-", "").replace("/", "");
  }

  /**
   * Computes the hash of a file using the specified algorithm using <b>streaming only</b>. This
   * method never loads the entire file into memory, making it safe for binary artifacts of any
   * size.
   *
   * <p><b>Security note:</b> This method does not perform line-ending normalization. For binary
   * artifacts (JAR, WAR, ZIP), this is the correct behavior.
   *
   * @param file the file to hash
   * @param algorithm the JCA algorithm name (e.g. "SHA-256", "MD5")
   * @return the lowercase hex-encoded hash string
   * @throws IOException if the file cannot be read
   * @throws NoSuchAlgorithmException if the algorithm is not available
   */
  public static String computeHashStreaming(Path file, String algorithm)
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
   * Validates that an artifact path is within the project's build directory using canonical path
   * enforcement. This prevents path traversal attacks where an artifact symlink points outside the
   * intended directory.
   *
   * <p>Implementation:
   *
   * <ol>
   *   <li>Resolve the artifact path using {@code NOFOLLOW_LINKS} to avoid following symlinks during
   *       resolution
   *   <li>If the path is a symlink, inspect the link target using {@link Files#readSymbolicLink}
   *   <li>Verify the canonical path is a descendant of {@code buildDirectory}
   * </ol>
   *
   * @param artifactPath the path to the artifact
   * @param buildDirectory the project's build directory (e.g. {@code target/})
   * @return the validated canonical path
   * @throws PathTraversalException if the path escapes the build directory
   * @throws IOException if an I/O error occurs during path resolution
   */
  public static Path validateArtifactPath(Path artifactPath, Path buildDirectory)
      throws IOException {
    // First, get the real path without following symlinks
    Path realPath;
    try {
      realPath = artifactPath.toRealPath(java.nio.file.LinkOption.NOFOLLOW_LINKS);
    } catch (IOException e) {
      throw new PathTraversalException(
          "Cannot resolve artifact path (may be broken symlink): " + artifactPath, e);
    }

    // Check if the resolved path is within the build directory
    Path canonicalBuildDir = buildDirectory.toRealPath(java.nio.file.LinkOption.NOFOLLOW_LINKS);

    // The real path must be inside the build directory
    // Check for exact match or proper subdirectory (with separator to prevent sibling bypass)
    String canonicalBuildDirStr = canonicalBuildDir.toString();
    boolean isInsideBuildDir =
        realPath.equals(canonicalBuildDir)
            || realPath.toString().startsWith(canonicalBuildDirStr + "/");
    if (!isInsideBuildDir) {
      throw new PathTraversalException(
          "Artifact path escapes build directory: "
              + artifactPath
              + " resolved to "
              + realPath
              + " which is not under "
              + canonicalBuildDir);
    }

    // If the original path is a symlink, also verify the link target is safe
    if (Files.isSymbolicLink(artifactPath)) {
      Path linkTarget = Files.readSymbolicLink(artifactPath);
      // If the link is absolute, check it resolves inside the build directory
      if (linkTarget.isAbsolute()) {
        Path resolvedLinkTarget;
        try {
          resolvedLinkTarget = linkTarget.toRealPath(java.nio.file.LinkOption.NOFOLLOW_LINKS);
        } catch (IOException e) {
          throw new PathTraversalException("Cannot resolve symlink target: " + linkTarget, e);
        }
        boolean linkTargetInside =
            resolvedLinkTarget.equals(canonicalBuildDir)
                || resolvedLinkTarget.toString().startsWith(canonicalBuildDirStr + "/");
        if (!linkTargetInside) {
          throw new PathTraversalException(
              "Symlink target escapes build directory: "
                  + linkTarget
                  + " resolves to "
                  + resolvedLinkTarget
                  + " which is not under "
                  + canonicalBuildDir);
        }
      } else {
        // Relative symlink — verify it resolves to inside the build directory
        Path parentDir = artifactPath.getParent();
        if (parentDir != null) {
          Path resolvedRelative = parentDir.resolve(linkTarget);
          Path realResolvedRelative;
          try {
            realResolvedRelative =
                resolvedRelative.toRealPath(java.nio.file.LinkOption.NOFOLLOW_LINKS);
          } catch (IOException e) {
            throw new PathTraversalException(
                "Cannot resolve relative symlink target: " + resolvedRelative, e);
          }
          boolean relativeTargetInside =
              realResolvedRelative.equals(canonicalBuildDir)
                  || realResolvedRelative.toString().startsWith(canonicalBuildDirStr + "/");
          if (!relativeTargetInside) {
            throw new PathTraversalException(
                "Relative symlink target escapes build directory: "
                    + linkTarget
                    + " resolves to "
                    + realResolvedRelative
                    + " which is not under "
                    + canonicalBuildDir);
          }
        }
      }
    }

    return realPath;
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

  /** Exception thrown when a path traversal attack is detected. */
  public static class PathTraversalException extends IOException {
    /**
     * Constructs a new PathTraversalException with the specified message.
     *
     * @param message the detail message
     */
    public PathTraversalException(String message) {
      super(message);
    }

    /**
     * Constructs a new PathTraversalException with the specified message and cause.
     *
     * @param message the detail message
     * @param cause the cause
     */
    public PathTraversalException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
