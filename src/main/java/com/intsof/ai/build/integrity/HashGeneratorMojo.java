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
 * Generates companion hash files for AI instruction resources (e.g. AGENTS.md, SKILL.md).
 *
 * <p>This mojo walks a base directory using NIO {@link Files#walkFileTree}, finds files matching
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
 * single-module projects and large monorepos efficiently.
 */
@Mojo(name = "generate-hashes", defaultPhase = LifecyclePhase.VALIDATE, requiresProject = true)
public class HashGeneratorMojo extends AbstractMojo {

  /** Current Maven project instance. */
  @Parameter(defaultValue = "${project}", readonly = true, required = true)
  private MavenProject project;

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

  /** Comma-separated directory names to skip during traversal (in addition to {@code target}). */
  @Parameter(property = "ai.integrity.skipDirs", defaultValue = "target,.git,node_modules,.tmp")
  private String skipDirs;

  @Override
  public void execute() throws MojoExecutionException {
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

    List<Path> filesToHash = new ArrayList<>();
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
              if (matchesAny(rel, excludeMatchers)) {
                return FileVisitResult.CONTINUE;
              }
              if (matchesAny(rel, includeMatchers)) {
                filesToHash.add(file);
              }
              return FileVisitResult.CONTINUE;
            }
          });
    } catch (IOException e) {
      throw new MojoExecutionException("Error walking directory: " + basePath, e);
    }

    if (filesToHash.isEmpty()) {
      getLog().info("No files found to hash.");
      return;
    }

    getLog().info("Found " + filesToHash.size() + " files to hash.");

    int hashed = 0;
    int skipped = 0;

    for (Path file : filesToHash) {
      Path hashFile = file.resolveSibling(file.getFileName() + ext);

      if (skipExisting && Files.exists(hashFile)) {
        getLog().debug("Skipping existing hash file: " + hashFile);
        skipped++;
        continue;
      }

      try {
        String hash = HashUtils.computeHash(file, algorithm);
        String hashContent = hash + "  " + file.getFileName() + "\n";
        Files.writeString(hashFile, hashContent);
        getLog().debug("Hash written: " + hashFile);
        hashed++;
      } catch (IOException | NoSuchAlgorithmException e) {
        getLog().error("Failed to hash " + file + ": " + e.getMessage());
      }
    }

    getLog().info("Hash generation complete: " + hashed + " created, " + skipped + " skipped.");
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
