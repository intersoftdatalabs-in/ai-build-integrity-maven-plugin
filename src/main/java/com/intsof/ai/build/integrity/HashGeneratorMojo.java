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
import java.nio.file.FileSystem;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.maven.execution.MavenSession;
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

  /** Current Maven session, used to detect resume-from flag. */
  @Parameter(defaultValue = "${session}", readonly = true, required = true)
  private MavenSession session;

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

  /**
   * Optional explicit module selector for resume-from hash regeneration ({@code :artifactId},
   * {@code groupId:artifactId}, or bare {@code artifactId}). When unset, a Maven {@code -rf} /
   * {@code --resume-from} build automatically re-seals the resumed reactor (first reactor project
   * when {@code executionRootOnly} is true; each module when false).
   */
  @Parameter(property = "ai.integrity.resumeFromModule")
  private String resumeFromModule;

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

  /**
   * Controls whether hash generation walks the full configured {@code baseDir} or only modules in
   * the current Maven reactor. {@code AUTO} (default) uses a full-tree seal for full reactor builds
   * and seal-root walks for partial builds ({@code -pl}, child-module builds). {@code FULL} always
   * walks {@code baseDir}. {@code REACTOR} always walks seal roots from {@code
   * session.getProjects()}.
   */
  @Parameter(property = "ai.integrity.reactorScope", defaultValue = "AUTO")
  private String reactorScope;

  @Override
  public void execute() throws MojoExecutionException {
    if (skip || skipAlt) {
      getLog().info("Skipping execution.");
      return;
    }

    boolean explicitResumeModule = resumeFromModule != null && !resumeFromModule.trim().isEmpty();
    boolean resumeBuild = ResumeFrom.isResumeBuild(session) || explicitResumeModule;

    if (executionRootOnly && !project.isExecutionRoot()) {
      if (!ResumeFrom.allowNonRootGeneration(session, project, resumeFromModule)) {
        if (resumeBuild) {
          getLog()
              .info(
                  "Skipping hash regeneration for "
                      + project.getArtifactId()
                      + " (resume target is "
                      + ResumeFrom.describeResumeTarget(session, project, resumeFromModule)
                      + ")");
        } else {
          getLog().info("Skipping HashGeneratorMojo execution in non-root project.");
        }
        return;
      }
      getLog()
          .info(
              "Resume mode: allowing hash generation in non-root project "
                  + project.getArtifactId()
                  + " (execution root not in resumed reactor or explicit resume target)");
    }

    // On resume with executionRootOnly, only the designated project regenerates (first reactor
    // project or explicit ai.integrity.resumeFromModule). Other modules that are the execution
    // root still use shouldRegenerateHashes for skip decisions.
    if (resumeBuild && executionRootOnly) {
      if (!ResumeFrom.shouldRegenerateHashes(
          session, project, resumeFromModule, executionRootOnly)) {
        getLog()
            .info(
                "Skipping hash regeneration for "
                    + project.getArtifactId()
                    + " (resume target is "
                    + ResumeFrom.describeResumeTarget(session, project, resumeFromModule)
                    + ")");
        return;
      }
      getLog()
          .info(
              "Resume mode: regenerating hashes for "
                  + ResumeFrom.describeResumeTarget(session, project, resumeFromModule));
    } else if (resumeBuild && explicitResumeModule) {
      if (!ResumeFrom.matchesProject(resumeFromModule, project)) {
        getLog()
            .info(
                "Skipping hash regeneration for "
                    + project.getArtifactId()
                    + " (resume target is "
                    + resumeFromModule.trim()
                    + ")");
        return;
      }
      getLog().info("Resume mode: regenerating hashes for " + resumeFromModule.trim());
    } else if (resumeBuild) {
      getLog()
          .info(
              "Resume mode: regenerating hashes for "
                  + ResumeFrom.describeResumeTarget(session, project, resumeFromModule));
    }

    final ReactorSealScope.Mode scopeMode;
    try {
      scopeMode = ReactorSealScope.parseMode(reactorScope);
    } catch (IllegalArgumentException e) {
      throw new MojoExecutionException(e.getMessage(), e);
    }

    String algorithm = HashUtils.resolveAlgorithm(algorithmBits);
    String ext = resolveExtension();

    Path basePath = resolveBasePath().toAbsolutePath().normalize();
    getLog().info("Generating " + algorithm + " hashes for AI resources in: " + basePath);

    if (!Files.exists(basePath)) {
      getLog().warn("Base directory does not exist: " + basePath);
      return;
    }

    // Resume builds re-seal only the remaining reactor modules and merge into CENTRAL.
    boolean partial = ReactorSealScope.isPartialReactor(session, basePath) || resumeBuild;
    boolean mergeCentral = ReactorSealScope.shouldMergeCentral(scopeMode, partial) || resumeBuild;
    List<Path> walkRoots = ReactorSealScope.resolveWalkRoots(basePath, session, scopeMode, partial);
    List<Path> sealRootsForMerge =
        mergeCentral ? ReactorSealScope.computeSealRoots(session, basePath) : walkRoots;

    if (mergeCentral) {
      int selected =
          session != null && session.getProjects() != null ? session.getProjects().size() : 0;
      int all =
          session != null && session.getAllProjects() != null
              ? session.getAllProjects().size()
              : selected;
      String label =
          partial || scopeMode == ReactorSealScope.Mode.AUTO
              ? "Partial reactor detected"
              : "Reactor-scoped sealing";
      getLog()
          .info(
              label
                  + " ("
                  + selected
                  + " of "
                  + all
                  + " projects); walking "
                  + walkRoots.size()
                  + " seal root(s).");
    }

    Set<String> skipSet = parseSkipDirs();
    List<PathMatcher> includeMatchers = buildMatchers(basePath, HashUtils.parsePatterns(includes));
    List<PathMatcher> excludeMatchers = buildMatchers(basePath, HashUtils.parsePatterns(excludes));
    List<PathMatcher> forceIncludeMatchers =
        buildMatchers(basePath, HashUtils.parsePatterns(forceIncludes));

    List<Path> filesToHash = new ArrayList<>();
    final long[] scanned = {0};
    long walkStart = System.currentTimeMillis();
    for (Path walkRoot : walkRoots) {
      if (!Files.exists(walkRoot)) {
        getLog().warn("Seal root does not exist, skipping: " + walkRoot);
        continue;
      }
      try {
        Files.walkFileTree(
            walkRoot,
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
                Path rel = basePath.relativize(file.toAbsolutePath().normalize());
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
        throw new MojoExecutionException("Error walking directory: " + walkRoot, e);
      }
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

    if (filesToHash.isEmpty() && !mergeCentral) {
      getLog().info("No files found to hash.");
      return;
    }

    if (!filesToHash.isEmpty()) {
      getLog().info("Found " + filesToHash.size() + " files to hash.");
    } else {
      getLog().info("No files found to hash under seal roots; refreshing central ledger scope.");
    }

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
        Map<String, String> newEntries = new LinkedHashMap<>();
        for (Path file : filesToHash) {
          try {
            String hash = HashUtils.computeHash(file, algorithm, normalizeLineEndings);
            String relPath =
                basePath
                    .relativize(file.toAbsolutePath().normalize())
                    .toString()
                    .replace('\\', '/');
            newEntries.put(relPath, hash);
            hashed++;
          } catch (Exception e) {
            getLog().error("Failed to hash " + file + ": " + e.getMessage());
          }
        }

        final String ledgerContent;
        if (mergeCentral) {
          List<Path> roots = sealRootsForMerge.isEmpty() ? walkRoots : sealRootsForMerge;
          String existing = "";
          if (Files.exists(centralFilePath)) {
            existing = new String(Files.readAllBytes(centralFilePath), StandardCharsets.UTF_8);
          }
          ledgerContent =
              ReactorSealScope.mergeCentralLedger(existing, basePath, roots, newEntries);
          getLog().info("Central hash file merged (partial seal): " + centralFilePath);
        } else {
          StringBuilder sb = new StringBuilder();
          for (Map.Entry<String, String> e : newEntries.entrySet()) {
            sb.append(e.getValue()).append("  ").append(e.getKey()).append('\n');
          }
          ledgerContent = sb.toString();
          getLog().info("Central hash file written: " + centralFilePath);
        }
        Files.write(centralFilePath, ledgerContent.getBytes(StandardCharsets.UTF_8));
      } catch (IOException e) {
        throw new MojoExecutionException(
            "Failed to write central hash file: " + centralFilePath, e);
      }
    } else {
      if (filesToHash.isEmpty()) {
        getLog().info("No files found to hash.");
        return;
      }
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
          Files.write(hashFile, hashContent.getBytes(StandardCharsets.UTF_8));

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
        String trimmed = dir.trim();
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
