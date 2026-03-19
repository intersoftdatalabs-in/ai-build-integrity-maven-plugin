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
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
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
 * <p>This mojo walks a base directory, finds files matching the configured include globs, and
 * writes a companion hash file (default {@code .sha256}) alongside each matched file. The hash
 * captures the file's content at build time so that the verify mojo can later detect any
 * unauthorized modifications.
 *
 * <p><b>Security rationale:</b> AI agent instructions must not change after the build begins or
 * after the artifact is shipped. Generating hashes at build time creates a tamper-evident seal on
 * all instruction files.
 */
@Mojo(name = "generate-hashes", defaultPhase = LifecyclePhase.VALIDATE, requiresProject = true)
public class HashGeneratorMojo extends AbstractMojo {

  @Parameter(defaultValue = "${project}", readonly = true, required = true)
  private MavenProject project;

  @Parameter(defaultValue = "256", property = "ai.integrity.algorithm.bits")
  private int algorithmBits;

  @Parameter(property = "ai.integrity.includes", defaultValue = "**/*.md")
  private String includes;

  @Parameter(property = "ai.integrity.excludes", defaultValue = "**/*.sha256")
  private String excludes;

  @Parameter(property = "ai.integrity.baseDir", defaultValue = "${project.basedir}")
  private String baseDir;

  @Parameter(property = "ai.integrity.outputExtension", defaultValue = ".sha256")
  private String outputExtension;

  @Parameter(property = "ai.integrity.skipExisting", defaultValue = "false")
  private boolean skipExisting;

  @Override
  public void execute() throws MojoExecutionException {
    String algorithm = HashUtils.resolveAlgorithm(algorithmBits);

    Path basePath = resolveBasePath();
    getLog().info("Generating " + algorithm + " hashes for AI resources in: " + basePath);

    if (!Files.exists(basePath)) {
      getLog().warn("Base directory does not exist: " + basePath);
      return;
    }

    List<Path> filesToHash = new ArrayList<>();
    findFilesToHash(basePath, filesToHash);

    if (filesToHash.isEmpty()) {
      getLog().info("No files found to hash.");
      return;
    }

    getLog().info("Found " + filesToHash.size() + " files to hash.");

    int hashed = 0;
    int skipped = 0;

    for (Path file : filesToHash) {
      Path hashFile = Paths.get(file + outputExtension);

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

  private Path resolveBasePath() {
    if (baseDir != null && !baseDir.isEmpty()) {
      return Paths.get(baseDir);
    }
    return project.getBasedir().toPath();
  }

  private void findFilesToHash(Path basePath, List<Path> result) throws MojoExecutionException {
    Set<String> excludeSet = HashUtils.parsePatterns(excludes);
    Set<String> includeSet = HashUtils.parsePatterns(includes);

    FileSystem fs = basePath.getFileSystem();
    List<PathMatcher> includeMatchers = new ArrayList<>();
    List<PathMatcher> excludeMatchers = new ArrayList<>();

    for (String pattern : includeSet) {
      includeMatchers.add(fs.getPathMatcher("glob:" + pattern));
      // **/*.ext won't match files at the root (e.g. "AGENTS.md"), so add a root-level variant
      if (pattern.startsWith("**/")) {
        includeMatchers.add(fs.getPathMatcher("glob:" + pattern.substring(3)));
      }
    }
    for (String pattern : excludeSet) {
      excludeMatchers.add(fs.getPathMatcher("glob:" + pattern));
      if (pattern.startsWith("**/")) {
        excludeMatchers.add(fs.getPathMatcher("glob:" + pattern.substring(3)));
      }
    }

    try {
      walkDirectory(basePath, basePath, includeMatchers, excludeMatchers, result);
    } catch (IOException e) {
      throw new MojoExecutionException("Error walking directory: " + basePath, e);
    }
  }

  private void walkDirectory(
      Path basePath,
      Path currentPath,
      List<PathMatcher> includeMatchers,
      List<PathMatcher> excludeMatchers,
      List<Path> result)
      throws IOException {
    if (!Files.isDirectory(currentPath)) {
      return;
    }

    if (currentPath.getFileName() != null && "target".equals(currentPath.getFileName().toString())) {
      getLog().debug("Skipping target directory: " + currentPath);
      return;
    }

    try (DirectoryStream<Path> stream = Files.newDirectoryStream(currentPath)) {
      for (Path entry : stream) {
        if (Files.isDirectory(entry)) {
          walkDirectory(basePath, entry, includeMatchers, excludeMatchers, result);
        } else if (Files.isRegularFile(entry)) {
          Path relativePath = basePath.relativize(entry);

          boolean isExcluded = false;
          for (PathMatcher matcher : excludeMatchers) {
            if (matcher.matches(relativePath)) {
              isExcluded = true;
              break;
            }
          }
          if (isExcluded) {
            continue;
          }

          boolean isIncluded = false;
          for (PathMatcher matcher : includeMatchers) {
            if (matcher.matches(relativePath)) {
              isIncluded = true;
              break;
            }
          }
          if (isIncluded) {
            result.add(entry);
          }
        }
      }
    }
  }
}
