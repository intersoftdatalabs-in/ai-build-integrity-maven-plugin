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
 * <p>This mojo finds all companion hash files (default {@code .sha256}) under the base directory,
 * recomputes the hash of the corresponding source file, and compares the two. If any mismatch is
 * detected, the build fails with a {@link MojoExecutionException}.
 *
 * <p><b>Security rationale:</b> AI agent instructions must not change once a build has begun or
 * after the artifact is shipped. This verification step ensures that no instruction file has been
 * tampered with between the generate phase and the verification phase.
 */
@Mojo(name = "verify-hashes", defaultPhase = LifecyclePhase.TEST, requiresProject = true)
public class HashVerifyMojo extends AbstractMojo {

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

  @Override
  public void execute() throws MojoExecutionException {
    String algorithm = HashUtils.resolveAlgorithm(algorithmBits);

    Path basePath = resolveBasePath();
    getLog().info("Verifying " + algorithm + " hashes for AI resources in: " + basePath);

    if (!Files.exists(basePath)) {
      getLog().warn("Base directory does not exist: " + basePath);
      return;
    }

    List<Path> hashFiles = new ArrayList<>();
    findHashFiles(basePath, hashFiles);

    if (hashFiles.isEmpty()) {
      getLog().warn("No hash files found to verify.");
      return;
    }

    getLog().info("Found " + hashFiles.size() + " hash files to verify.");

    int verified = 0;
    int failed = 0;

    for (Path hashFile : hashFiles) {
      String hashFileName = hashFile.toString();
      int extIndex = hashFileName.lastIndexOf(outputExtension);
      Path sourceFile = Paths.get(hashFileName.substring(0, extIndex));

      if (!Files.exists(sourceFile)) {
        getLog().error("Source file missing for hash: " + sourceFile);
        failed++;
        continue;
      }

      try {
        String storedHash = Files.readString(hashFile).split("\\s+")[0];
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
          "Hash verification FAILED: "
              + failed
              + " file(s) have been modified or tampered with!");
    }
  }

  private Path resolveBasePath() {
    if (baseDir != null && !baseDir.isEmpty()) {
      return Paths.get(baseDir);
    }
    return project.getBasedir().toPath();
  }

  private void findHashFiles(Path basePath, List<Path> result) throws MojoExecutionException {
    String hashPattern = "**/*" + outputExtension;
    String rootHashPattern = "*" + outputExtension;

    FileSystem fs = basePath.getFileSystem();
    List<PathMatcher> hashMatchers = new ArrayList<>();
    hashMatchers.add(fs.getPathMatcher("glob:" + hashPattern));
    hashMatchers.add(fs.getPathMatcher("glob:" + rootHashPattern));

    try {
      walkForHashFiles(basePath, basePath, hashMatchers, result);
    } catch (IOException e) {
      throw new MojoExecutionException("Error walking directory: " + basePath, e);
    }
  }

  private void walkForHashFiles(
      Path basePath,
      Path currentPath,
      List<PathMatcher> hashMatchers,
      List<Path> result)
      throws IOException {
    if (!Files.isDirectory(currentPath)) {
      return;
    }

    if (currentPath.getFileName() != null
        && "target".equals(currentPath.getFileName().toString())) {
      getLog().debug("Skipping target directory: " + currentPath);
      return;
    }

    try (DirectoryStream<Path> stream = Files.newDirectoryStream(currentPath)) {
      for (Path entry : stream) {
        if (Files.isDirectory(entry)) {
          walkForHashFiles(basePath, entry, hashMatchers, result);
        } else if (Files.isRegularFile(entry)) {
          Path relativePath = basePath.relativize(entry);

          for (PathMatcher matcher : hashMatchers) {
            if (matcher.matches(relativePath)) {
              result.add(entry);
              break;
            }
          }
        }
      }
    }
  }
}
