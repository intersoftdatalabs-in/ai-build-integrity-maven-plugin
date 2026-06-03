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
import java.nio.file.FileSystem;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
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
 * Generates companion hash files for AI instruction resources (e.g. AGENTS.md, SKILL.md).
 *
 * <p>This mojo walks a base directory using NIO {@code Files.walkFileTree}, finds files matching
 * the configured include globs, and writes a companion hash sidecar file alongside each matched
 * file. The hash captures the file content at build time so that the verify mojo can later detect
 * any unauthorized modifications.
 *
 * <p><b>Security rationale:</b> AI agent instructions must not change after the build begins or
 * after the artifact is shipped. Generating hashes at build time creates a tamper-evident seal on
 * all instruction files.
 *
 * <p><b>Performance:</b> Uses {@code Files.walkFileTree} for a single-pass directory traversal with
 * directory pruning, a 64 KiB streaming hash buffer, and a lookup-table hex encoder. Handles both
 * single-module projects and large multi-module projects efficiently.
 */
@Mojo(name = "generate-hashes", defaultPhase = LifecyclePhase.VALIDATE, requiresProject = true)
public class HashGeneratorMojo extends AbstractMojo {

  /** Default constructor for {@link HashGeneratorMojo}. */
  public HashGeneratorMojo() {}

  /** Current Maven project instance. */
  @Parameter(defaultValue = "${project}", readonly = true, required = true)
  private MavenProject project;

  /**
   * If true, skips the execution of the mojo. Accepts both {@code -Dai.integrity.skip=true} and the
   * Maven-conventional {@code -Dskip.ai.integrity=true}.
   */
  @Parameter(property = "ai.integrity.skip", defaultValue = "false")
  private boolean skip;

  /** Alternate Maven-conventional skip flag (e.g. -Dskip.ai.integrity=true). */
  @Parameter(property = "skip.ai.integrity", defaultValue = "false")
  private boolean skipAlt;

  /** Target build directory for central hash files. */
  @Parameter(defaultValue = "${project.build.directory}", readonly = true, required = true)
  private String buildDirectory;

  /** If true, Normalizes CRLF to LF in memory before hashing, enabling cross-OS git hashes. */
  @Parameter(property = "ai.integrity.normalizeLineEndings", defaultValue = "false")
  private boolean normalizeLineEndings;

  /** Strategy for storing generated hashes (SIDECAR or CENTRAL). */
  @Parameter(property = "ai.integrity.hashFileMode", defaultValue = "SIDECAR")
  private HashFileMode hashFileMode;

  /** If true, the mojo will only execute in the reactor's execution root project. */
  @Parameter(property = "ai.integrity.executionRootOnly", defaultValue = "false")
  private boolean executionRootOnly;

  /**
   * Hash algorithm bit width. Determines both the algorithm (SHA-256, SHA-384, SHA-512) and the
   * default output extension (.sha256, .sha384, .sha512).
   */
  @Parameter(defaultValue = "256", property = "ai.integrity.algorithm.bits")
  private int algorithmBits;

  /** Comma-separated glob patterns for resource files to hash (e.g. {@code ** /*.md}). */
  @Parameter(property = "ai.integrity.includes", defaultValue = "**/*.md")
  private String includes;

  /** Comma-separated glob patterns for files to strictly exclude from hashing. */
  @Parameter(
      property = "ai.integrity.excludes",
      defaultValue = "**/*.sha256,**/*.sha384,**/*.sha512")
  private String excludes;

  /** Base directory to scan; defaults to {@code ${project.basedir}}. */
  @Parameter(property = "ai.integrity.baseDir", defaultValue = "${project.basedir}")
  private String baseDir;

  /**
   * Output extension for hash sidecar files. When set to {@code "auto"} (the default), the
   * extension is derived from {@code algorithmBits} (e.g. {@code .sha256}).
   */
  @Parameter(property = "ai.integrity.outputExtension", defaultValue = "auto")
  private String outputExtension;

  /** If {@code true}, skip generating hashes for files that already have a sidecar file. */
  @Parameter(property = "ai.integrity.skipExisting", defaultValue = "false")
  private boolean skipExisting;

  /** If true, natively parse .gitignore files during traversal to auto-exclude paths. */
  @Parameter(property = "ai.integrity.gitignoreAutoExclude", defaultValue = "false")
  private boolean gitignoreAutoExclude;

  /** Comma-separated directory names to skip during traversal (in addition to {@code target}). */
  @Parameter(property = "ai.integrity.skipDirs", defaultValue = "target,.git,node_modules,.tmp")
  private String skipDirs;

  /** Comma-separated glob patterns for files that MUST be processed, bypassing .gitignore rules. */
  @Parameter(property = "ai.integrity.forceIncludes", defaultValue = "")
  private String forceIncludes;

  /** If true, natively hides the generated hash sidecar files across all operating systems. */
  @Parameter(property = "ai.integrity.hideHashFiles", defaultValue = "true")
  private boolean hideHashFiles;

  /**
   * Explicit path to the central hash ledger file. When set, overrides the default {@code
   * target/ai-integrity.<ext>} location and enables child modules in a multi-module project to
   * write to the same single shared ledger as the root module.
   */
  @Parameter(property = "ai.integrity.centralHashFile")
  private String centralHashFile;

  @Override
  public void execute() throws MojoExecutionException {
    if (skip || skipAlt) {
      getLog().info("Skipping execution.");
      return;
    }

    if (executionRootOnly && !project.isExecutionRoot()) {
      getLog().info("Skipping HashGeneratorMojo execution in non-root project.");
      return;
    }

    String algorithm = HashUtils.resolveAlgorithm(algorithmBits);
    String ext = resolveExtension();

    Path basePath = resolveBasePath();
    getLog().info("Generating " + algorithm + " hashes for AI resources in: " + basePath);

    if (!Files.exists(basePath)) {
      getLog().warn("Base directory does not exist: " + basePath);
      return;
    }

    Set<String> skipSet = parseSkipDirs();
    List<PathMatcher> includeMatchers = buildMatchers(basePath, HashUtils.parsePatterns(includes));
    List<PathMatcher> excludeMatchers = buildMatchers(basePath, HashUtils.parsePatterns(excludes));
    List<PathMatcher> forceIncludeMatchers =
        buildMatchers(basePath, HashUtils.parsePatterns(forceIncludes));

    List<Path> filesToHash = new ArrayList<>();
    final long[] scanned = {0};
    long walkStart = System.currentTimeMillis();
    try {
      Files.walkFileTree(
          basePath,
          new GitIgnoreAwareFileVisitor(
              basePath, gitignoreAutoExclude, skipSet, forceIncludeMatchers, getLog()) {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
              scanned[0]++;
              if (scanned[0] % 1000 == 0) {
                getLog()
                    .info(
                        "  ... scanned "
                            + scanned[0]
                            + " files, "
                            + filesToHash.size()
                            + " matched so far");
              }
              Path rel = basePath.relativize(file);
              boolean gitIgnored = isIgnoredByGit(file);

              if (gitIgnored && !matchesAny(rel, forceIncludeMatchers)) {
                return FileVisitResult.CONTINUE;
              }
              if (matchesAny(rel, excludeMatchers)) {
                return FileVisitResult.CONTINUE;
              }
              if (matchesAny(rel, includeMatchers) || matchesAny(rel, forceIncludeMatchers)) {
                filesToHash.add(file);
              }
              return FileVisitResult.CONTINUE;
            }
          });
    } catch (IOException e) {
      throw new MojoExecutionException("Error walking directory: " + basePath, e);
    }
    long walkMs = System.currentTimeMillis() - walkStart;
    getLog()
        .info(
            "Directory scan complete: "
                + scanned[0]
                + " files scanned, "
                + filesToHash.size()
                + " matched in "
                + walkMs
                + " ms");

    if (filesToHash.isEmpty()) {
      getLog().info("No files found to hash.");
      return;
    }

    getLog().info("Found " + filesToHash.size() + " files to hash.");

    int hashed = 0;
    int skipped = 0;
    long hashStart = System.currentTimeMillis();

    if (hashFileMode == HashFileMode.CENTRAL) {
      Path centralFilePath =
          (centralHashFile != null && !centralHashFile.isEmpty())
              ? Paths.get(centralHashFile)
              : Paths.get(buildDirectory, "ai-integrity" + ext);
      try {
        Files.createDirectories(centralFilePath.getParent());
        StringBuilder sb = new StringBuilder();
        for (Path file : filesToHash) {
          try {
            String hash = HashUtils.computeHash(file, algorithm, normalizeLineEndings);
            // Use stable relative paths for the central ledger
            String relPath = basePath.relativize(file).toString().replace('\\', '/');
            sb.append(hash).append("  ").append(relPath).append("\n");
            hashed++;
          } catch (Exception e) {
            getLog().error("Failed to hash " + file + ": " + e.getMessage());
          }
        }
        Files.writeString(centralFilePath, sb.toString());
        getLog().info("Central hash file written: " + centralFilePath);
      } catch (IOException e) {
        throw new MojoExecutionException(
            "Failed to write central hash file: " + centralFilePath, e);
      }
    } else {
      for (Path file : filesToHash) {
        String name = file.getFileName().toString();
        String prefix = (hideHashFiles && !name.startsWith(".")) ? "." : "";
        Path hashFile = file.resolveSibling(prefix + name + ext);

        if (skipExisting && Files.exists(hashFile)) {
          getLog().debug("Skipping existing hash file: " + hashFile);
          skipped++;
          continue;
        }

        try {
          String hash = HashUtils.computeHash(file, algorithm, normalizeLineEndings);
          String hashContent = hash + "  " + file.getFileName() + "\n";
          Files.writeString(hashFile, hashContent);

          if (hideHashFiles) {
            try {
              Files.setAttribute(hashFile, "dos:hidden", true);
            } catch (UnsupportedOperationException | IllegalArgumentException ignored) {
              // Attribute naturally not supported on Mac/Linux configurations
            }
          }

          getLog().debug("Hash written: " + hashFile);
          hashed++;
        } catch (Exception e) {
          getLog().error("Failed to hash " + file + ": " + e.getMessage());
        }
      }
    }

    long hashMs = System.currentTimeMillis() - hashStart;
    getLog()
        .info(
            "Hash generation complete: "
                + hashed
                + " created, "
                + skipped
                + " skipped in "
                + hashMs
                + " ms (total wall time: "
                + (walkMs + hashMs)
                + " ms)");
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
   * Builds NIO {@link PathMatcher}s for the given glob patterns. To support patterns like {@code **
   * /*.md} matching root files consistently, this method expands such patterns to include their
   * base variants.
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
