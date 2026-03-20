package com.intsof.ai.build.integrity;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import org.apache.maven.plugin.logging.Log;

/**
 * Shared recursive file visitor that enforces generic traversal behaviors, such as evaluating
 * static `skipDirs` and dynamically parsing and enforcing local `.gitignore` rules in real-time.
 */
public abstract class GitIgnoreAwareFileVisitor extends SimpleFileVisitor<Path> {
  private final Path basePath;
  private final boolean gitignoreAutoExclude;
  private final Set<String> skipDirNames;
  private final List<PathMatcher> forceIncludeMatchers;
  private final Log log;

  // Real-time tracking stack of all executing gitignore predicates relative to directory depth
  private final Deque<List<Predicate<Path>>> gitIgnoreStack = new ArrayDeque<>();

  /**
   * Constructs a new visitor with the specified configuration.
   *
   * @param basePath the base directory being scanned
   * @param gitignoreAutoExclude whether to automatically read and apply .gitignore rules
   * @param skipDirNames a set of directory names to always skip (e.g. "target", ".git")
   * @param forceIncludeMatchers a list of matchers for files that must be included regardless of
   *     exclusions
   * @param log the Maven logger
   */
  public GitIgnoreAwareFileVisitor(
      Path basePath,
      boolean gitignoreAutoExclude,
      Set<String> skipDirNames,
      List<PathMatcher> forceIncludeMatchers,
      Log log) {
    this.basePath = basePath;
    this.gitignoreAutoExclude = gitignoreAutoExclude;
    this.skipDirNames = skipDirNames;
    this.forceIncludeMatchers = forceIncludeMatchers;
    this.log = log;
  }

  @Override
  public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
    if (!dir.equals(basePath)) {
      Path fileName = dir.getFileName();
      if (fileName != null && skipDirNames.contains(fileName.toString())) {
        return FileVisitResult.SKIP_SUBTREE; // Native static pruning takes maximum priority
      }
    }

    boolean ignoredByGit = false;
    for (List<Predicate<Path>> ignores : gitIgnoreStack) {
      for (Predicate<Path> ignore : ignores) {
        if (ignore.test(dir)) {
          ignoredByGit = true;
          break;
        }
      }
      if (ignoredByGit) break;
    }

    List<Predicate<Path>> localIgnores = new ArrayList<>();
    if (gitignoreAutoExclude) {
      Path gitignore = dir.resolve(".gitignore");
      if (Files.exists(gitignore)) {
        try {
          localIgnores.addAll(GitIgnoreParser.parse(gitignore));
        } catch (Exception e) {
          log.warn("Failed to parse " + gitignore + ": " + e.getMessage());
        }

        for (Predicate<Path> ignore : localIgnores) {
          if (ignore.test(dir)) {
            ignoredByGit = true;
            break;
          }
        }
      }
    }

    if (ignoredByGit) {
      // If forceIncludes are provided, we cannot safely prune the ignored branch natively
      // because deeply nested files may have been explicitly overridden.
      if (forceIncludeMatchers == null || forceIncludeMatchers.isEmpty()) {
        return FileVisitResult.SKIP_SUBTREE;
      }
    }

    gitIgnoreStack.push(localIgnores);
    return FileVisitResult.CONTINUE;
  }

  @Override
  public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
    gitIgnoreStack.pop();
    return FileVisitResult.CONTINUE;
  }

  /**
   * Let subclass mojos efficiently check if a matched file is globally ignored by Git.
   *
   * @param file the path to check
   * @return true if the file is ignored by any .gitignore rule in the current traversal stack
   */
  protected boolean isIgnoredByGit(Path file) {
    if (!gitignoreAutoExclude || gitIgnoreStack.isEmpty()) {
      return false;
    }
    for (List<Predicate<Path>> ignores : gitIgnoreStack) {
      for (Predicate<Path> ignore : ignores) {
        if (ignore.test(file)) {
          return true;
        }
      }
    }
    return false;
  }
}
