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

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 * Verifies that AI instruction resource files have not been modified since their hashes were
 * generated.
 *
 * <p>This mojo finds all companion hash sidecar files under the base directory using NIO {@link
 * Files#walkFileTree}, recomputes the hash of the corresponding source file, and compares the two.
 * If any mismatch is detected, the build fails with a {@link MojoExecutionException}.
 *
 * <p><b>Security rationale:</b> AI agent instructions must not change once a build has begun or
 * after the artifact is shipped. This verification step ensures that no instruction file has been
 * tampered with between the generate phase and the verification phase.
 *
 * <p><b>Performance:</b> Uses {@code Files.walkFileTree} for a single-pass directory traversal with
 * directory pruning. Handles both single-module projects and large monorepos efficiently.
 */
@Mojo(name = "verify-hashes", defaultPhase = LifecyclePhase.TEST, requiresProject = true)
public class HashVerifyMojo extends AbstractMojo {

  /**
   * Maximum allowed size for a hash sidecar file (8 KiB). Files larger than this are rejected to
   * prevent reading maliciously large files.
   */
  static final long MAX_HASH_FILE_SIZE = 8 * 1024;

  /** Current Maven project instance. */
  @Parameter(defaultValue = "${project}", readonly = true, required = true)
  private MavenProject project;

  /** Hash algorithm bit width. Must match what was used during generation. */
  @Parameter(defaultValue = "256", property = "ai.integrity.algorithm.bits")
  private int algorithmBits;

  /** Base directory to scan; defaults to {@code ${project.basedir}}. */
  @Parameter(property = "ai.integrity.baseDir", defaultValue = "${project.basedir}")
  private String baseDir;

  /**
   * Output extension for hash sidecar files. When set to {@code "auto"} (the default), the
   * extension is derived from {@code algorithmBits} (e.g. {@code .sha256}).
   */
  @Parameter(property = "ai.integrity.outputExtension", defaultValue = "auto")
  private String outputExtension;

  /** Comma-separated directory names to skip during traversal. */
  @Parameter(property = "ai.integrity.skipDirs", defaultValue = "target,.git,node_modules,.tmp")
  private String skipDirs;

  @Override
  public void execute() throws MojoExecutionException {
    String algorithm = HashUtils.resolveAlgorithm(algorithmBits);
    String ext = resolveExtension();

    Path basePath = resolveBasePath();
    getLog().info("Verifying " + algorithm + " hashes for AI resources in: " + basePath);

    if (!Files.exists(basePath)) {
      getLog().warn("Base directory does not exist: " + basePath);
      return;
    }

    Set<String> skipSet = parseSkipDirs();
    List<PathMatcher> hashMatchers = buildHashMatchers(basePath, ext);

    List<Path> hashFiles = new ArrayList<>();
    try {
      Files.walkFileTree(
          basePath,
          new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
              if (!dir.equals(basePath)) {
                String dirName = dir.getFileName().toString();
                if (skipSet.contains(dirName)) {
                  getLog().debug("Skipping directory: " + dir);
                  return FileVisitResult.SKIP_SUBTREE;
                }
              }
              return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
              Path rel = basePath.relativize(file);
              if (matchesAny(rel, hashMatchers)) {
                hashFiles.add(file);
              }
              return FileVisitResult.CONTINUE;
            }
          });
    } catch (IOException e) {
      throw new MojoExecutionException("Error walking directory: " + basePath, e);
    }

    if (hashFiles.isEmpty()) {
      getLog().warn("No hash files found to verify.");
      return;
    }

    getLog().info("Found " + hashFiles.size() + " hash files to verify.");

    int verified = 0;
    int failed = 0;

    for (Path hashFile : hashFiles) {
      String hashFileName = hashFile.toString();
      int extIndex = hashFileName.lastIndexOf(ext);
      Path sourceFile = Paths.get(hashFileName.substring(0, extIndex));

      if (!Files.exists(sourceFile)) {
        getLog().error("Source file missing for hash: " + sourceFile);
        failed++;
        continue;
      }

      try {
        long hashFileSize = Files.size(hashFile);
        if (hashFileSize > MAX_HASH_FILE_SIZE) {
          getLog()
              .error(
                  "Hash file too large ("
                      + hashFileSize
                      + " bytes, max "
                      + MAX_HASH_FILE_SIZE
                      + "): "
                      + hashFile);
          failed++;
          continue;
        }

        String content = Files.readString(hashFile);
        String[] parts = content.split("\\s+");
        String storedHash = parts[0];

        if (parts.length >= 2) {
          String embeddedFilename = parts[1].strip();
          String expectedFilename = sourceFile.getFileName().toString();
          if (!embeddedFilename.equals(expectedFilename)) {
            getLog()
                .error(
                    "FILENAME MISMATCH in hash file: expected '"
                        + expectedFilename
                        + "' but found '"
                        + embeddedFilename
                        + "' in "
                        + hashFile);
            failed++;
            continue;
          }
        }

        String computedHash = HashUtils.computeHash(sourceFile, algorithm);

        if (storedHash.equals(computedHash)) {
          getLog().debug("Verified: " + sourceFile.getFileName());
          verified++;
        } else {
          getLog()
              .error(
                  "HASH MISMATCH: "
                      + sourceFile.getFileName()
                      + " - file may have been tampered with!");
          failed++;
        }
      } catch (IOException | NoSuchAlgorithmException e) {
        getLog().error("Failed to verify " + sourceFile + ": " + e.getMessage());
        failed++;
      }
    }

    getLog().info("Hash verification complete: " + verified + " verified, " + failed + " failed.");

    if (failed > 0) {
      throw new MojoExecutionException(
          "Hash verification FAILED: " + failed + " file(s) have been modified or tampered with!");
    }
  }

  /**
   * Resolves the hash file extension based on configuration.
   *
   * @return the resolved extension (e.g. ".sha256")
   */
  private String resolveExtension() {
    if (outputExtension == null || "auto".equals(outputExtension)) {
      return HashUtils.extensionForBits(algorithmBits);
    }
    return outputExtension;
  }

  /**
   * Resolves the absolute path of the base directory to scan.
   *
   * @return the resolved path instance
   */
  private Path resolveBasePath() {
    if (baseDir != null && !baseDir.isEmpty()) {
      return Paths.get(baseDir);
    }
    return project.getBasedir().toPath();
  }

  /**
   * Parses the skipDirs property into a set of unique directory names.
   *
   * @return a set of directory names to prune during traversal
   */
  private Set<String> parseSkipDirs() {
    Set<String> result = new HashSet<>();
    if (skipDirs != null) {
      for (String dir : skipDirs.split(",")) {
        String trimmed = dir.strip();
        if (!trimmed.isEmpty()) {
          result.add(trimmed);
        }
      }
    }
    return result;
  }

  /**
   * Builds NIO {@link PathMatcher}s for hash sidecar files.
   *
   * @param basePath the base directory to provide the filesystem
   * @param ext the hash file extension to match (e.g. ".sha256")
   * @return a list of compiled matchers
   */
  private List<PathMatcher> buildHashMatchers(Path basePath, String ext) {
    FileSystem fs = basePath.getFileSystem();
    List<PathMatcher> matchers = new ArrayList<>();
    matchers.add(fs.getPathMatcher("glob:**/*" + ext));
    matchers.add(fs.getPathMatcher("glob:*" + ext));
    return matchers;
  }

  /**
   * Tests if the relative path matches any of the provided matchers.
   *
   * @param path the path to check
   * @param matchers the compile glob matchers
   * @return {@code true} if any matcher matches the path
   */
  private static boolean matchesAny(Path path, List<PathMatcher> matchers) {
    for (PathMatcher m : matchers) {
      if (m.matches(path)) {
        return true;
      }
    }
    return false;
  }
}
