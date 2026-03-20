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

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
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
 * <p>This mojo finds all companion hash sidecar files under the base directory using NIO {@code
 * Files.walkFileTree}, recomputes the hash of the corresponding source file, and compares the two.
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

  /** If true, skips the execution of the mojo. */
  @Parameter(property = "ai.integrity.skip", defaultValue = "false")
  private boolean skip;

  /** Target build directory for central hash files. */
  @Parameter(defaultValue = "${project.build.directory}", readonly = true, required = true)
  private String buildDirectory;

  /** If true, Normalizes CRLF to LF in memory before hashing, enabling cross-OS git hashes. */
  @Parameter(property = "ai.integrity.normalizeLineEndings", defaultValue = "false")
  private boolean normalizeLineEndings;

  /** If true, generates a machine-readable JSON bill of materials for SIEM systems. */
  @Parameter(property = "ai.integrity.generateAuditReport", defaultValue = "false")
  private boolean generateAuditReport;

  /** If false, validation failures will only log errors and will not break the build. */
  @Parameter(property = "ai.integrity.failOnError", defaultValue = "true")
  private boolean failOnError;

  /** Strategy for storing generated hashes (SIDECAR or CENTRAL). */
  @Parameter(property = "ai.integrity.hashFileMode", defaultValue = "SIDECAR")
  private HashFileMode hashFileMode;

  /** If true, the mojo will only execute in the reactor's execution root project. */
  @Parameter(property = "ai.integrity.executionRootOnly", defaultValue = "false")
  private boolean executionRootOnly;

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

  /** If true, natively parse .gitignore files during traversal to auto-exclude paths. */
  @Parameter(property = "ai.integrity.gitignoreAutoExclude", defaultValue = "false")
  private boolean gitignoreAutoExclude;

  /** Comma-separated directory names to skip during traversal. */
  @Parameter(property = "ai.integrity.skipDirs", defaultValue = "target,.git,node_modules,.tmp")
  private String skipDirs;

  /** Comma-separated glob patterns for files that MUST be processed, bypassing .gitignore rules. */
  @Parameter(property = "ai.integrity.forceIncludes", defaultValue = "")
  private String forceIncludes;

  /** If true, natively hides the generated hash sidecar files across all operating systems. */
  @Parameter(property = "ai.integrity.hideHashFiles", defaultValue = "true")
  private boolean hideHashFiles;

  @Override
  public void execute() throws MojoExecutionException {
    if (skip) {
      getLog().info("Skipping execution.");
      return;
    }

    if (executionRootOnly && !project.isExecutionRoot()) {
      getLog().info("Skipping HashVerifyMojo execution in non-root project.");
      return;
    }

    String algorithm = HashUtils.resolveAlgorithm(algorithmBits);
    String ext = resolveExtension();

    Path basePath = resolveBasePath();
    getLog().info("Verifying " + algorithm + " hashes for AI resources in: " + basePath);

    if (!Files.exists(basePath)) {
      getLog().warn("Base directory does not exist: " + basePath);
      return;
    }

    int verified = 0;
    int failed = 0;
    List<String> auditEntries = new ArrayList<>();

    if (hashFileMode == HashFileMode.CENTRAL) {
      Path centralFile = Paths.get(buildDirectory, "ai-integrity" + ext);
      if (!Files.exists(centralFile)) {
        getLog().warn("Central hash file not found: " + centralFile);
        return;
      }

      try {
        List<String> lines = Files.readAllLines(centralFile);
        if (lines.isEmpty()) {
          getLog().warn("Central hash file is empty.");
          return;
        }

        getLog().info("Found " + lines.size() + " hashes to verify in central ledger.");

        for (String line : lines) {
          if (line.trim().isEmpty()) {
            continue;
          }

          String[] parts = line.trim().split("\\s+", 2);
          if (parts.length < 2) {
            continue;
          }

          String storedHash = parts[0];
          String relPath = parts[1].trim();
          Path sourceFile = basePath.resolve(relPath);

          if (!Files.exists(sourceFile)) {
            getLog().error("Source file missing for hash: " + sourceFile);
            failed++;
            if (generateAuditReport) {
              auditEntries.add(
                  String.format(
                      "    {\n      \"file\": \"%s\",\n      \"status\": \"MISSING\",\n      \"hash\": \"null\"\n    }",
                      relPath));
            }
            continue;
          }

          String computedHash = HashUtils.computeHash(sourceFile, algorithm, normalizeLineEndings);

          if (storedHash.equals(computedHash)) {
            getLog().debug("Verified: " + relPath);
            verified++;
            if (generateAuditReport) {
              auditEntries.add(
                  String.format(
                      "    {\n      \"file\": \"%s\",\n      \"status\": \"VERIFIED\",\n      \"hash\": \"%s\"\n    }",
                      relPath, computedHash));
            }
          } else {
            getLog().error("HASH MISMATCH: " + relPath + " - file may have been tampered with!");
            failed++;
            if (generateAuditReport) {
              auditEntries.add(
                  String.format(
                      "    {\n      \"file\": \"%s\",\n      \"status\": \"TAMPERED\",\n      \"hash\": \"%s\"\n    }",
                      relPath, computedHash));
            }
          }
        }
      } catch (IOException | NoSuchAlgorithmException e) {
        throw new MojoExecutionException("Failed to verify central hashes from " + centralFile, e);
      }
    } else {
      Set<String> skipSet = parseSkipDirs();
      List<PathMatcher> hashMatchers = buildHashMatchers(basePath, ext);
      List<PathMatcher> forceIncludeMatchers =
          buildMatchers(basePath, HashUtils.parsePatterns(forceIncludes));

      List<Path> hashFiles = new ArrayList<>();
      try {
        Files.walkFileTree(
            basePath,
            new GitIgnoreAwareFileVisitor(
                basePath, gitignoreAutoExclude, skipSet, forceIncludeMatchers, getLog()) {
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

      for (Path hashFile : hashFiles) {
        String hashFileName = hashFile.toString();
        int extIndex = hashFileName.lastIndexOf(ext);
        Path sourceFile = Paths.get(hashFileName.substring(0, extIndex));

        if (!Files.exists(sourceFile)) {
          Path fileNamePath = sourceFile.getFileName();
          if (fileNamePath != null && fileNamePath.toString().startsWith(".")) {
            String originalName = fileNamePath.toString().substring(1);
            Path alternateSource = sourceFile.resolveSibling(originalName);
            if (Files.exists(alternateSource)) {
              sourceFile = alternateSource;
            }
          }
        }

        if (!Files.exists(sourceFile)) {
          getLog().error("Source file missing for hash: " + sourceFile);
          failed++;
          if (generateAuditReport) {
            auditEntries.add(
                String.format(
                    "    {\n      \"file\": \"%s\",\n      \"status\": \"MISSING\",\n      \"hash\": \"null\"\n    }",
                    sourceFile.getFileName()));
          }
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

          String computedHash = HashUtils.computeHash(sourceFile, algorithm, normalizeLineEndings);

          if (storedHash.equals(computedHash)) {
            getLog().debug("Verified: " + sourceFile.getFileName());
            verified++;
            if (generateAuditReport) {
              auditEntries.add(
                  String.format(
                      "    {\n      \"file\": \"%s\",\n      \"status\": \"VERIFIED\",\n      \"hash\": \"%s\"\n    }",
                      sourceFile.getFileName(), computedHash));
            }
          } else {
            getLog()
                .error(
                    "HASH MISMATCH: "
                        + sourceFile.getFileName()
                        + " - file may have been tampered with!");
            failed++;
            if (generateAuditReport) {
              auditEntries.add(
                  String.format(
                      "    {\n      \"file\": \"%s\",\n      \"status\": \"TAMPERED\",\n      \"hash\": \"%s\"\n    }",
                      sourceFile.getFileName(), computedHash));
            }
          }
        } catch (IOException | NoSuchAlgorithmException e) {
          getLog().error("Failed to verify " + sourceFile + ": " + e.getMessage());
          failed++;
        }
      }
    }

    getLog().info("Hash verification complete: " + verified + " verified, " + failed + " failed.");

    if (generateAuditReport) {
      Path reportFile = Paths.get(buildDirectory, "ai-integrity-report.json");
      try {
        Files.createDirectories(reportFile.getParent());
        StringBuilder report = new StringBuilder();
        report.append("{\n");
        report
            .append("  \"timestamp\": \"")
            .append(java.time.Instant.now().toString())
            .append("\",\n");
        report.append("  \"totalChecked\": ").append(verified + failed).append(",\n");
        report.append("  \"totalFailed\": ").append(failed).append(",\n");
        report.append("  \"files\": [\n");
        report.append(String.join(",\n", auditEntries));
        report.append("\n  ]\n");
        report.append("}\n");
        Files.writeString(reportFile, report.toString());
        getLog().info("Audit report generated: " + reportFile);
      } catch (IOException e) {
        getLog().error("Failed to write audit report: " + e.getMessage());
      }
    }

    if (failed > 0) {
      String msg =
          "Hash verification FAILED: " + failed + " file(s) have been modified or tampered with!";
      if (failOnError) {
        throw new MojoExecutionException(msg);
      } else {
        getLog().error("------------------------------------------------------------------------");
        getLog().error("AI BUILD INTEGRITY WARNING: " + msg);
        getLog()
            .error(
                "Build is configured to continue despite validation failures (failOnError=false).");
        getLog().error("------------------------------------------------------------------------");
      }
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
   * Builds NIO {@link PathMatcher}s for the given glob patterns.
   *
   * @param basePath the base directory to provide the filesystem
   * @param patterns the glob patterns to convert
   * @return a list of compiled matchers
   */
  private List<PathMatcher> buildMatchers(Path basePath, Set<String> patterns) {
    FileSystem fs = basePath.getFileSystem();
    List<PathMatcher> matchers = new ArrayList<>();
    for (String pattern : patterns) {
      matchers.add(fs.getPathMatcher("glob:" + pattern));
      if (pattern.startsWith("**/")) {
        matchers.add(fs.getPathMatcher("glob:" + pattern.substring(3)));
      }
    }
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
