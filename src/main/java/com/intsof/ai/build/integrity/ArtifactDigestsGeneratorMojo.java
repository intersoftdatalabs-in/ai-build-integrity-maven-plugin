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
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 * Generates cryptographic digest files for build artifacts (JARs, WARs, ZIPs).
 *
 * <p>This mojo scans the project's build directory for artifacts matching the configured include
 * patterns, computes cryptographic digests using streaming hash computation (never loading full
 * artifact files into heap memory), and writes either sidecar digest files or a central ledger.
 *
 * <p><b>Security rationale:</b> Build artifacts must be hashed at package time so that the verify
 * mojo can detect any tampering before deployment.
 *
 * <p><b>Performance:</b> Uses 64 KiB streaming hash buffers for low heap pressure even on large
 * JAR/WAR files. Handles multi-module projects efficiently.
 *
 * <p><b>Algorithm constraints:</b> Supports SHA-256 (default), SHA-384, SHA-512. MD5 and SHA-1 are
 * available via explicit opt-in but emit build warnings as they are compromised.
 */
@Mojo(
    name = "generate-artifact-digests",
    defaultPhase = LifecyclePhase.PACKAGE,
    requiresProject = true)
public class ArtifactDigestsGeneratorMojo extends AbstractMojo {

  /** Default constructor for {@link ArtifactDigestsGeneratorMojo}. */
  public ArtifactDigestsGeneratorMojo() {}

  /** Current Maven project instance. */
  @Parameter(defaultValue = "${project}", readonly = true, required = true)
  private MavenProject project;

  /** Target build directory where artifacts are created. */
  @Parameter(defaultValue = "${project.build.directory}", readonly = true, required = true)
  private String buildDirectory;

  /**
   * If true, skips the execution of the mojo. Accepts both {@code -Dai.integrity.skip=true} and the
   * Maven-conventional {@code -Dskip.ai.integrity=true}.
   */
  @Parameter(property = "ai.integrity.skip", defaultValue = "false")
  private boolean skip;

  /** Alternate Maven-conventional skip flag (e.g. -Dskip.ai.integrity=true). */
  @Parameter(property = "skip.ai.integrity", defaultValue = "false")
  private boolean skipAlt;

  /** Comma-separated glob patterns for artifacts to hash (e.g. **\/*.jar,**\/*.war,**\/*.zip). */
  @Parameter(
      property = "ai.integrity.artifactIncludes",
      defaultValue = "**/*.jar,**/*.war,**/*.zip")
  private String artifactIncludes;

  /** Comma-separated glob patterns for artifacts to exclude from hashing. */
  @Parameter(
      property = "ai.integrity.artifactExcludes",
      defaultValue = "**/*-sources.jar,**/*-javadoc.jar")
  private String artifactExcludes;

  /**
   * If true, include attached artifacts (sources, javadoc, test jars) in addition to primary
   * artifacts.
   */
  @Parameter(property = "ai.integrity.includeAttachedArtifacts", defaultValue = "false")
  private boolean includeAttachedArtifacts;

  /** Array of algorithms to compute. Supports SHA-256, SHA-384, SHA-512, and opt-in MD5, SHA-1. */
  @Parameter(property = "ai.integrity.algorithms", defaultValue = "SHA-256")
  private String[] algorithms;

  /** Strategy for storing generated digests (SIDECAR or CENTRAL). */
  @Parameter(property = "ai.integrity.hashFileMode", defaultValue = "SIDECAR")
  private HashFileMode hashFileMode;

  /**
   * Explicit path to the central digest ledger file. When set, overrides the default and enables
   * child modules in a multi-module project to write to a shared ledger.
   */
  @Parameter(property = "ai.integrity.centralDigestFile")
  private String centralDigestFile;

  /** Encoding for digest files. */
  @Parameter(property = "ai.integrity.outputEncoding", defaultValue = "UTF-8")
  private String outputEncoding;

  /**
   * If true, compute a SHA-256 hash of all artifact digests for quick nightly verification. Output
   * file: {@code ai-integrity-artifacts-aggregate.sha256}
   */
  @Parameter(property = "ai.integrity.generateAggregateDigest", defaultValue = "false")
  private boolean generateAggregateDigest;

  /** If true, emit a build WARNING when MD5 or SHA-1 is used. Recommended to keep enabled. */
  @Parameter(property = "ai.integrity.warnOnCompromisedAlgorithm", defaultValue = "true")
  private boolean warnOnCompromisedAlgorithm;

  /** JVM-level lock object for synchronized central ledger writes. */
  private static final Object CENTRAL_LEDGER_LOCK = new Object();

  @Override
  public void execute() throws MojoExecutionException {
    if (skip || skipAlt) {
      getLog().info("Skipping execution.");
      return;
    }

    Log log = getLog();
    Path buildDir = Paths.get(buildDirectory);

    if (!Files.exists(buildDir)) {
      log.warn("Build directory does not exist: " + buildDir);
      return;
    }

    // Validate all algorithms upfront
    List<String> validatedAlgorithms = validateAlgorithms(log);

    // Discover artifacts
    List<Path> artifacts = discoverArtifacts(buildDir, log);

    if (artifacts.isEmpty()) {
      log.info("No artifacts found to hash.");
      return;
    }

    log.info("Found " + artifacts.size() + " artifacts to hash.");

    // Compute and write digests
    int hashed = 0;
    int failed = 0;
    long hashStart = System.currentTimeMillis();

    if (hashFileMode == HashFileMode.CENTRAL) {
      writeCentralLedger(buildDir, artifacts, validatedAlgorithms, log);
    } else {
      for (Path artifact : artifacts) {
        for (String algorithm : validatedAlgorithms) {
          if (writeSidecarDigest(artifact, algorithm, log)) {
            hashed++;
          } else {
            failed++;
          }
        }
      }
    }

    // Generate aggregate digest if requested
    if (generateAggregateDigest) {
      generateAggregateDigestFile(buildDir, log);
    }

    long hashMs = System.currentTimeMillis() - hashStart;
    log.info(
        "Artifact digest generation complete: "
            + hashed
            + " created, "
            + failed
            + " failed in "
            + hashMs
            + " ms");
  }

  /**
   * Validates all configured algorithms are available in the JVM and emits warnings for compromised
   * algorithms.
   *
   * @return list of validated algorithm names
   */
  private List<String> validateAlgorithms(Log log) throws MojoExecutionException {
    List<String> validated = new ArrayList<>();
    for (String algorithm : algorithms) {
      try {
        ArtifactDigestsUtils.validateAlgorithm(algorithm);
        validated.add(algorithm);
      } catch (NoSuchAlgorithmException e) {
        throw new MojoExecutionException(
            "Algorithm not available in this JVM: "
                + algorithm
                + ". Supported: SHA-256, SHA-384, SHA-512, MD5, SHA-1");
      }

      if (warnOnCompromisedAlgorithm && ArtifactDigestsUtils.isCompromisedAlgorithm(algorithm)) {
        log.warn("");
        log.warn("!!! WARNING: Using compromised algorithm " + algorithm + " !!!");
        log.warn("!!! This algorithm is for CORRUPTION DETECTION ONLY, not tamper detection. !!!");
        log.warn("!!! DO NOT use for supply-chain integrity verification. !!!");
        log.warn("");
      }
    }
    return validated;
  }

  /**
   * Discovers artifact files in the build directory matching include/exclude patterns.
   *
   * @param buildDir the build directory
   * @param log the logger
   * @return list of artifact paths
   */
  private List<Path> discoverArtifacts(Path buildDir, Log log) {
    List<Path> artifacts = new ArrayList<>();
    Set<String> includePatterns = parsePatterns(artifactIncludes);
    Set<String> excludePatterns = parsePatterns(artifactExcludes);

    FileSystem fs = buildDir.getFileSystem();
    List<PathMatcher> includeMatchers = buildMatchers(fs, includePatterns);
    List<PathMatcher> excludeMatchers = buildMatchers(fs, excludePatterns);

    // Walk the entire build directory tree
    try {
      Files.walk(buildDir)
          .filter(Files::isRegularFile)
          .forEach(
              file -> {
                Path fileName = file.getFileName();
                if (matchesAny(fileName, includeMatchers)
                    && !matchesAny(fileName, excludeMatchers)) {
                  if (isValidArtifact(file, buildDir, log)) {
                    artifacts.add(file);
                  }
                }
              });
    } catch (IOException e) {
      log.warn("Error walking build directory: " + e.getMessage());
    }

    return artifacts;
  }

  /**
   * Recursively discovers artifacts within a directory. (Deprecated - use Files.walk instead)
   *
   * @param dir the directory to scan
   * @param includeMatchers path matchers for inclusion
   * @param excludeMatchers path matchers for exclusion
   * @param artifacts output list of discovered artifacts
   */
  @Deprecated
  private void discoverArtifactsRecursive(
      Path dir,
      List<PathMatcher> includeMatchers,
      List<PathMatcher> excludeMatchers,
      List<Path> artifacts) {
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
      for (Path entry : stream) {
        if (Files.isDirectory(entry)) {
          discoverArtifactsRecursive(entry, includeMatchers, excludeMatchers, artifacts);
        } else {
          Path fileName = entry.getFileName();
          if (matchesAny(fileName, includeMatchers) && !matchesAny(fileName, excludeMatchers)) {
            if (isValidArtifact(entry, dir.getParent() != null ? dir.getParent() : dir, getLog())) {
              artifacts.add(entry);
            }
          }
        }
      }
    } catch (IOException e) {
      // Skip directories we can't read
    }
  }

  /**
   * Validates that an artifact path is within the build directory (path traversal check).
   *
   * @param artifactPath the path to validate
   * @param buildDir the build directory
   * @param log the logger
   * @return true if valid
   */
  private boolean isValidArtifact(Path artifactPath, Path buildDir, Log log) {
    try {
      ArtifactDigestsUtils.validateArtifactPath(artifactPath, buildDir);
      return true;
    } catch (ArtifactDigestsUtils.PathTraversalException e) {
      log.warn("Skipping artifact outside build directory: " + artifactPath);
      return false;
    } catch (IOException e) {
      log.warn("Could not validate artifact path " + artifactPath + ": " + e.getMessage());
      return false;
    }
  }

  /**
   * Writes sidecar digest files for a single artifact.
   *
   * @param artifact the artifact path
   * @param algorithm the algorithm to use
   * @param log the logger
   * @return true if successful
   */
  private boolean writeSidecarDigest(Path artifact, String algorithm, Log log) {
    String ext = ArtifactDigestsUtils.extensionForAlgorithm(algorithm);
    Path digestFile = artifact.resolveSibling(artifact.getFileName() + ext);

    try {
      String hash = ArtifactDigestsUtils.computeHashStreaming(artifact, algorithm);
      String content = hash + "  " + artifact.getFileName() + "\n";
      Files.write(digestFile, content.getBytes(StandardCharsets.UTF_8));
      log.debug("Digest written: " + digestFile);
      return true;
    } catch (NoSuchAlgorithmException e) {
      log.error("Algorithm not available: " + algorithm);
      return false;
    } catch (IOException e) {
      log.error("Failed to write digest for " + artifact + ": " + e.getMessage());
      return false;
    }
  }

  /**
   * Writes a central ledger containing all artifact digests.
   *
   * @param buildDir the build directory
   * @param artifacts the artifacts to hash
   * @param algorithms the algorithms to use
   * @param log the logger
   */
  private void writeCentralLedger(
      Path buildDir, List<Path> artifacts, List<String> algorithms, Log log)
      throws MojoExecutionException {
    Path centralFilePath =
        (centralDigestFile != null && !centralDigestFile.isEmpty())
            ? Paths.get(centralDigestFile)
            : buildDir.resolve("ai-integrity-artifacts.sha256");

    // For central mode, use the first algorithm for the filename
    String ext = ArtifactDigestsUtils.extensionForAlgorithm(algorithms.get(0));
    if (centralDigestFile == null || centralDigestFile.isEmpty()) {
      centralFilePath = buildDir.resolve("ai-integrity-artifacts" + ext);
    } else {
      centralFilePath = Paths.get(centralDigestFile);
    }

try {
      Path parent = centralFilePath.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }

      // Use synchronized block for thread-safe writes
      synchronized (CENTRAL_LEDGER_LOCK) {
        StringBuilder sb = new StringBuilder();
        for (Path artifact : artifacts) {
          for (String algorithm : algorithms) {
            try {
              String hash = ArtifactDigestsUtils.computeHashStreaming(artifact, algorithm);
              // Use relative path from build directory
              String relPath = buildDir.relativize(artifact).toString().replace('\\', '/');
              sb.append(hash).append("  ").append(relPath).append("\n");
            } catch (NoSuchAlgorithmException e) {
              log.error("Algorithm not available: " + algorithm);
            } catch (IOException e) {
              log.error("Failed to hash " + artifact + ": " + e.getMessage());
            }
          }
        }
        Files.write(centralFilePath, sb.toString().getBytes(StandardCharsets.UTF_8));
      }

      log.info("Central digest ledger written: " + centralFilePath);
    } catch (IOException e) {
      throw new MojoExecutionException(
          "Failed to write central digest ledger: " + centralFilePath, e);
    }
  }

  /**
   * Generates an aggregate digest file containing a hash of all artifact digests.
   *
   * @param buildDir the build directory
   * @param log the logger
   */
  private void generateAggregateDigestFile(Path buildDir, Log log) {
    if (algorithms == null || algorithms.length == 0) {
      return;
    }
    String ext = ArtifactDigestsUtils.extensionForAlgorithm(algorithms[0]);
    Path aggregateFile = buildDir.resolve("ai-integrity-artifacts-aggregate" + ext);

    try {
      List<String> lines = new ArrayList<>();

      // Collect all digest lines
      if (hashFileMode == HashFileMode.CENTRAL) {
        Path centralFilePath = buildDir.resolve("ai-integrity-artifacts" + ext);
        if (Files.exists(centralFilePath)) {
          List<String> ledgerLines = Files.readAllLines(centralFilePath);
          lines.addAll(ledgerLines);
        }
      } else {
        // Read all sidecar files
        FileSystem fs = buildDir.getFileSystem();
        Set<String> sidecarPatterns = new HashSet<>();
        for (String algorithm : algorithms) {
          sidecarPatterns.add("*" + ArtifactDigestsUtils.extensionForAlgorithm(algorithm));
        }
        List<PathMatcher> matchers = buildMatchers(fs, sidecarPatterns);

        collectSidecarLines(buildDir, matchers, lines);
      }

      // Sort lines for deterministic output (using CASE_INSENSITIVE_ORDER per spec)
      Collections.sort(lines, String.CASE_INSENSITIVE_ORDER);

      // Concatenate with trailing newline
      StringBuilder content = new StringBuilder();
      for (String line : lines) {
        content.append(line).append("\n");
      }

      // Compute aggregate hash (SHA-256)
      Path tempFile = Files.createTempFile("aggregate", ".tmp");
      Files.write(tempFile, content.toString().getBytes(StandardCharsets.UTF_8));
      String aggregateHash = ArtifactDigestsUtils.computeHashStreaming(tempFile, "SHA-256");
      Files.delete(tempFile);

      // Write aggregate digest
      String digestContent = aggregateHash + "  aggregate\n";
      Files.write(aggregateFile, digestContent.getBytes(StandardCharsets.UTF_8));
      log.info("Aggregate digest written: " + aggregateFile);

    } catch (NoSuchAlgorithmException e) {
      log.error("SHA-256 not available for aggregate digest: " + e.getMessage());
    } catch (IOException e) {
      log.error("Failed to generate aggregate digest: " + e.getMessage());
    }
  }

  /**
   * Recursively collects digest lines from sidecar files.
   *
   * @param dir the directory to scan
   * @param matchers path matchers for sidecar files
   * @param lines output list of digest lines
   */
  private void collectSidecarLines(Path dir, List<PathMatcher> matchers, List<String> lines) {
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
      for (Path entry : stream) {
        if (Files.isDirectory(entry)) {
          collectSidecarLines(entry, matchers, lines);
        } else {
          if (matchesAny(entry.getFileName(), matchers)) {
            try {
              List<String> fileLines = Files.readAllLines(entry);
              lines.addAll(fileLines);
            } catch (IOException e) {
              // Skip files we can't read
            }
          }
        }
      }
    } catch (IOException e) {
      // Skip directories we can't read
    }
  }

  /**
   * Parses a comma-separated pattern string into a set of trimmed, non-empty patterns.
   *
   * @param patterns comma-separated glob patterns (may be {@code null})
   * @return a set of individual pattern strings
   */
  private Set<String> parsePatterns(String patterns) {
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
   * Builds NIO PathMatchers for the given glob patterns. For patterns starting with the literal
   * two-star-slash (matches any subdirectory), a fallback pattern is also added to match root-level
   * files.
   *
   * @param fs the filesystem to create matchers for
   * @param patterns the glob patterns to convert
   * @return a list of compiled matchers
   */
  private List<PathMatcher> buildMatchers(FileSystem fs, Set<String> patterns) {
    List<PathMatcher> matchers = new ArrayList<>();
    for (String pattern : patterns) {
      matchers.add(fs.getPathMatcher("glob:" + pattern));
      // Add fallback for root-level files (patterns like **/*.jar should also match *.jar)
      if (pattern.startsWith("**/")) {
        matchers.add(fs.getPathMatcher("glob:" + pattern.substring(3)));
      }
    }
    return matchers;
  }

  /**
   * Tests if a path matches any of the provided matchers.
   *
   * @param path the path to check
   * @param matchers the compiled glob matchers
   * @return true if any matcher matches
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
