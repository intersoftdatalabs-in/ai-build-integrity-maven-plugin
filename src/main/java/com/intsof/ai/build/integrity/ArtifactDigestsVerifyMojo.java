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
import java.time.Instant;
import java.util.ArrayList;
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
 * Verifies that build artifacts have not been modified since their digests were generated.
 *
 * <p>This mojo reads either sidecar digest files or a central ledger, recomputes the digest of each
 * artifact, and compares against the stored value. If any mismatch is detected and {@code
 * failOnError} is true, the build fails.
 *
 * <p><b>Security rationale:</b> Artifact integrity must be verified before deployment to detect any
 * tampering that occurred after packaging.
 *
 * <p><b>Performance:</b> Uses streaming hash computation with 64 KiB buffers. For large artifact
 * sets, verification adds measurable but acceptable time to the build.
 */
@Mojo(
    name = "verify-artifact-digests",
    defaultPhase = LifecyclePhase.VERIFY,
    requiresProject = true)
public class ArtifactDigestsVerifyMojo extends AbstractMojo {

  /** Maximum allowed size for a digest file (8 KiB). Prevents reading maliciously large files. */
  static final long MAX_DIGEST_FILE_SIZE = 8 * 1024;

  /** Default constructor for {@link ArtifactDigestsVerifyMojo}. */
  public ArtifactDigestsVerifyMojo() {}

  /** Current Maven project instance. */
  @Parameter(defaultValue = "${project}", readonly = true, required = true)
  private MavenProject project;

  /** Target build directory where artifacts and digests are located. */
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

  /** Array of algorithms to verify. Must match what was used during generation. */
  @Parameter(property = "ai.integrity.algorithms", defaultValue = "SHA-256")
  private String[] algorithms;

  /** Strategy for storing generated digests (SIDECAR or CENTRAL). */
  @Parameter(property = "ai.integrity.hashFileMode", defaultValue = "SIDECAR")
  private HashFileMode hashFileMode;

  /** If false, validation failures will only log errors and will not break the build. */
  @Parameter(property = "ai.integrity.failOnError", defaultValue = "true")
  private boolean failOnError;

  /** If true, generates a machine-readable JSON audit report for SIEM systems. */
  @Parameter(property = "ai.integrity.generateAuditReport", defaultValue = "true")
  private boolean generateAuditReport;

  /** Explicit path to the central digest ledger file. */
  @Parameter(property = "ai.integrity.centralDigestFile")
  private String centralDigestFile;

  /** Explicit path to the central audit report file. */
  @Parameter(property = "ai.integrity.centralReportFile")
  private String centralReportFile;

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

    int verified = 0;
    int failed = 0;
    int missing = 0;
    List<String> auditEntries = new ArrayList<>();
    long verifyStart = System.currentTimeMillis();

    if (hashFileMode == HashFileMode.CENTRAL) {
      Path centralFilePath = resolveCentralDigestFile(buildDir);
      if (!Files.exists(centralFilePath)) {
        log.warn("Central digest file not found: " + centralFilePath);
        return;
      }

      try {
        List<String> lines = Files.readAllLines(centralFilePath);
        if (lines.isEmpty()) {
          log.warn("Central digest file is empty.");
          return;
        }

        log.info("Found " + lines.size() + " digests to verify in central ledger.");

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
          Path artifactPath = buildDir.resolve(relPath);

          VerificationResult result = verifyArtifact(artifactPath, storedHash, algorithms[0], log);
          switch (result) {
            case VERIFIED:
              verified++;
              auditEntries.add(createAuditEntry(relPath, "VERIFIED", storedHash));
              break;
            case FAILED:
              failed++;
              auditEntries.add(createAuditEntry(relPath, "FAILED", storedHash));
              break;
            case MISSING:
              missing++;
              auditEntries.add(createAuditEntry(relPath, "MISSING", null));
              break;
          }
        }
      } catch (IOException | NoSuchAlgorithmException e) {
        throw new MojoExecutionException(
            "Failed to verify central digests from " + centralFilePath, e);
      }
    } else {
      // SIDECAR mode - find all digest files and verify corresponding artifacts
      List<Path> digestFiles = discoverDigestFiles(buildDir, log);

      if (digestFiles.isEmpty()) {
        log.info("No digest files found to verify.");
        return;
      }

      log.info("Found " + digestFiles.size() + " digest files to verify.");

      for (Path digestFile : digestFiles) {
        try {
          VerificationResult result = verifySidecarDigest(digestFile, buildDir, log);
          switch (result) {
            case VERIFIED:
              verified++;
              break;
            case FAILED:
              failed++;
              break;
            case MISSING:
              missing++;
              break;
          }
        } catch (IOException | NoSuchAlgorithmException e) {
          log.error("Failed to verify " + digestFile + ": " + e.getMessage());
          failed++;
        }
      }
    }

    long verifyMs = System.currentTimeMillis() - verifyStart;
    log.info(
        "Artifact digest verification complete: "
            + verified
            + " verified, "
            + failed
            + " failed, "
            + missing
            + " missing in "
            + verifyMs
            + " ms");

    // Generate audit report
    if (generateAuditReport) {
      generateAuditReport(buildDir, verified, failed, missing, auditEntries, log);
    }

    if (failed > 0 || missing > 0) {
      String msg =
          "Artifact digest verification FAILED: " + failed + " failed, " + missing + " missing.";
      if (failOnError) {
        throw new MojoExecutionException(msg);
      } else {
        log.error("------------------------------------------------------------------------");
        log.error("AI BUILD INTEGRITY WARNING: " + msg);
        log.error(
            "Build is configured to continue despite validation failures (failOnError=false).");
        log.error("------------------------------------------------------------------------");
      }
    }
  }

  /**
   * Resolves the central digest file path.
   *
   * @param buildDir the build directory
   * @return the resolved path
   */
  private Path resolveCentralDigestFile(Path buildDir) {
    if (centralDigestFile != null && !centralDigestFile.isEmpty()) {
      return Paths.get(centralDigestFile);
    }
    String ext = ArtifactDigestsUtils.extensionForAlgorithm(algorithms[0]);
    return buildDir.resolve("ai-integrity-artifacts" + ext);
  }

  /**
   * Verifies a single artifact against a stored hash.
   *
   * @param artifactPath the artifact path
   * @param storedHash the stored hash
   * @param algorithm the algorithm to use
   * @param log the logger
   * @return verification result
   */
  private VerificationResult verifyArtifact(
      Path artifactPath, String storedHash, String algorithm, Log log)
      throws IOException, NoSuchAlgorithmException {
    if (!Files.exists(artifactPath)) {
      log.error("Artifact missing: " + artifactPath);
      return VerificationResult.MISSING;
    }

    String computedHash = ArtifactDigestsUtils.computeHashStreaming(artifactPath, algorithm);

    if (storedHash.equals(computedHash)) {
      log.debug("Verified: " + artifactPath);
      return VerificationResult.VERIFIED;
    } else {
      log.error("DIGEST MISMATCH: " + artifactPath + " - artifact may have been tampered with!");
      log.error("  Expected: " + storedHash);
      log.error("  Computed: " + computedHash);
      return VerificationResult.FAILED;
    }
  }

  /**
   * Verifies a sidecar digest file.
   *
   * @param digestFile the sidecar digest file
   * @param buildDir the build directory
   * @param log the logger
   * @return verification result
   */
  private VerificationResult verifySidecarDigest(Path digestFile, Path buildDir, Log log)
      throws IOException, NoSuchAlgorithmException {
    // Determine the artifact path from the digest filename
    String digestFileName = digestFile.getFileName().toString();

    // Detect algorithm from extension
    String algorithm = detectAlgorithmFromExtension(digestFileName);
    if (algorithm == null) {
      algorithm = algorithms[0]; // Fall back to configured default
    }

    // Read digest file
    long fileSize = Files.size(digestFile);
    if (fileSize > MAX_DIGEST_FILE_SIZE) {
      log.error("Digest file too large: " + digestFile + " (" + fileSize + " bytes)");
      return VerificationResult.FAILED;
    }

    String content = new String(Files.readAllBytes(digestFile), StandardCharsets.UTF_8);
    String[] parts = content.split("\\s+");
    if (parts.length < 2) {
      log.error("Invalid digest file format: " + digestFile);
      return VerificationResult.FAILED;
    }

    String storedHash = parts[0];
    String embeddedFilename = parts[1].trim();

    // Find the artifact - it should be a sibling of the digest file
    Path artifactPath = digestFile.resolveSibling(embeddedFilename);
    Path relPath = buildDir.relativize(artifactPath);

    if (!Files.exists(artifactPath)) {
      log.error("Artifact missing for digest: " + artifactPath);
      return VerificationResult.MISSING;
    }

    String computedHash = ArtifactDigestsUtils.computeHashStreaming(artifactPath, algorithm);

    if (storedHash.equals(computedHash)) {
      log.debug("Verified: " + relPath);
      return VerificationResult.VERIFIED;
    } else {
      log.error("DIGEST MISMATCH: " + relPath + " - artifact may have been tampered with!");
      return VerificationResult.FAILED;
    }
  }

  /**
   * Detects the algorithm from a digest filename extension.
   *
   * @param filename the digest filename
   * @return the algorithm name, or null if unknown
   */
  private String detectAlgorithmFromExtension(String filename) {
    if (filename.endsWith(".sha256")) {
      return "SHA-256";
    } else if (filename.endsWith(".sha384")) {
      return "SHA-384";
    } else if (filename.endsWith(".sha512")) {
      return "SHA-512";
    } else if (filename.endsWith(".md5")) {
      return "MD5";
    } else if (filename.endsWith(".sha1")) {
      return "SHA-1";
    }
    return null;
  }

  /**
   * Discovers all sidecar digest files in the build directory.
   *
   * @param buildDir the build directory
   * @param log the logger
   * @return list of digest file paths
   */
  private List<Path> discoverDigestFiles(Path buildDir, Log log) {
    List<Path> digestFiles = new ArrayList<>();
    FileSystem fs = buildDir.getFileSystem();

    // Build patterns for all supported algorithm extensions
    Set<String> patterns = new HashSet<>();
    patterns.add("**/*.sha256");
    patterns.add("**/*.sha384");
    patterns.add("**/*.sha512");
    patterns.add("**/*.md5");
    patterns.add("**/*.sha1");

    List<PathMatcher> matchers = new ArrayList<>();
    for (String pattern : patterns) {
      matchers.add(fs.getPathMatcher("glob:" + pattern));
      // Add fallback for root-level files
      if (pattern.startsWith("**/")) {
        matchers.add(fs.getPathMatcher("glob:" + pattern.substring(3)));
      }
    }

    collectDigestFiles(buildDir, matchers, digestFiles);
    return digestFiles;
  }

  /**
   * Recursively collects digest files.
   *
   * @param dir the directory to scan
   * @param matchers path matchers for digest files
   * @param results output list
   */
  private void collectDigestFiles(Path dir, List<PathMatcher> matchers, List<Path> results) {
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
      for (Path entry : stream) {
        if (Files.isDirectory(entry)) {
          collectDigestFiles(entry, matchers, results);
        } else {
          if (matchesAny(entry.getFileName(), matchers)) {
            results.add(entry);
          }
        }
      }
    } catch (IOException e) {
      // Skip directories we can't read
    }
  }

  /**
   * Creates a JSON audit entry for an artifact.
   *
   * @param artifactPath the artifact path
   * @param status the verification status
   * @param hash the computed hash
   * @return JSON string
   */
  private String createAuditEntry(String artifactPath, String status, String hash) {
    return String.format(
        "    {\n      \"artifactPath\": \"%s\",\n      \"status\": \"%s\",\n      \"hash\": \"%s\"\n    }",
        artifactPath, status, hash != null ? hash : "null");
  }

  /**
   * Generates the JSON audit report.
   *
   * @param buildDir the build directory
   * @param verified count of verified artifacts
   * @param failed count of failed verifications
   * @param missing count of missing artifacts
   * @param entries audit entries
   * @param log the logger
   */
  private void generateAuditReport(
      Path buildDir, int verified, int failed, int missing, List<String> entries, Log log) {
    Path reportFile =
        (centralReportFile != null && !centralReportFile.isEmpty())
            ? Paths.get(centralReportFile)
            : buildDir.resolve("ai-integrity-artifacts-report.json");

    try {
      Files.createDirectories(reportFile.getParent());

      StringBuilder report = new StringBuilder();
      report.append("{\n");
      report.append("  \"schemaVersion\": \"1.0\",\n");
      report.append("  \"reportType\": \"artifact-integrity\",\n");
      report.append("  \"generatedAt\": \"").append(Instant.now().toString()).append("\",\n");
      report.append("  \"generator\": \"ai-build-integrity-maven-plugin\",\n");
      report.append("  \"buildContext\": {\n");
      report
          .append("    \"projectName\": \"")
          .append(escapeJson(project.getName()))
          .append("\",\n");
      report
          .append("    \"projectVersion\": \"")
          .append(escapeJson(project.getVersion()))
          .append("\"\n");
      report.append("  },\n");
      report.append("  \"summary\": {\n");
      report.append("    \"totalArtifacts\": ").append(verified + failed + missing).append(",\n");
      report.append("    \"verified\": ").append(verified).append(",\n");
      report.append("    \"failed\": ").append(failed).append(",\n");
      report.append("    \"skipped\": ").append(missing).append("\n");
      report.append("  },\n");
      report.append("  \"artifacts\": [\n");
      report.append(String.join(",\n", entries));
      report.append("\n  ],\n");
      report.append("  \"errors\": []\n");
      report.append("}\n");

      Files.write(reportFile, report.toString().getBytes(StandardCharsets.UTF_8));
      log.info("Audit report generated: " + reportFile);
    } catch (IOException e) {
      log.error("Failed to write audit report: " + e.getMessage());
    }
  }

  /**
   * Escapes special characters for JSON string values.
   *
   * @param value the string to escape
   * @return escaped string
   */
  private static String escapeJson(String value) {
    if (value == null) {
      return "";
    }
    return value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t");
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

  /** Verification result enumeration. */
  private enum VerificationResult {
    VERIFIED,
    FAILED,
    MISSING
  }
}
