package com.intsof.ai.build.integrity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/** Lightweight, zero-dependency parser for .gitignore files natively inside the Maven runtime. */
public final class GitIgnoreParser {

  private GitIgnoreParser() {
    // Utility class
  }

  /**
   * Parses a .gitignore file and returns a list of predicates to test paths against. The predicates
   * are anchored to the directory of the .gitignore file.
   *
   * @param gitignoreFile Path to the .gitignore file.
   * @return List of evaluating predicates.
   * @throws IOException if the file cannot be read.
   */
  public static List<Predicate<Path>> parse(Path gitignoreFile) throws IOException {
    List<Predicate<Path>> predicates = new ArrayList<>();
    Path baseDir = gitignoreFile.getParent();

    List<String> lines = Files.readAllLines(gitignoreFile, StandardCharsets.UTF_8);
    for (String line : lines) {
      line = line.trim();
      if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) {
        continue; // Skip comments, empty lines, and unsupported negations
      }

      String rule = line;

      // 1. Exact name optimization (fastest, e.g. target, node_modules)
      if (rule.indexOf('*') == -1 && rule.indexOf('/') == -1 && rule.indexOf('?') == -1) {
        final String name = rule;
        predicates.add(
            p -> {
              Path fileName = p.getFileName();
              return fileName != null && fileName.toString().equals(name);
            });
        continue;
      }

      // 2. Suffix optimization (fastest for *.class, *.log)
      if (rule.startsWith("*")
          && rule.indexOf('*', 1) == -1
          && rule.indexOf('/') == -1
          && rule.indexOf('?') == -1) {
        final String suffix = rule.substring(1);
        predicates.add(
            p -> {
              Path fileName = p.getFileName();
              return fileName != null && fileName.toString().endsWith(suffix);
            });
        continue;
      }

      // 3. Prefix optimization (fastest for temp_*, build_*)
      if (rule.endsWith("*")
          && rule.indexOf('*') == rule.length() - 1
          && rule.indexOf('/') == -1
          && rule.indexOf('?') == -1) {
        final String prefix = rule.substring(0, rule.length() - 1);
        predicates.add(
            p -> {
              Path fileName = p.getFileName();
              return fileName != null && fileName.toString().startsWith(prefix);
            });
        continue;
      }

      // 4. Safe Regex equivalent for Globs
      boolean anchored = rule.startsWith("/");
      if (anchored) {
        rule = rule.substring(1);
      }

      // Safely escape all regex metacharacters EXCEPT *, ?, and /
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < rule.length(); i++) {
        char c = rule.charAt(i);
        if ("\\()[]{}|+^$.".indexOf(c) != -1) {
          sb.append('\\').append(c);
        } else {
          sb.append(c);
        }
      }
      String regex = sb.toString();

      regex = regex.replace("**", "\0"); // Temporarily hide cross-dir wildcards
      regex = regex.replace("*", "[^/]*"); // Single-dir wildcards cannot cross slashes
      regex = regex.replace("\0", ".*"); // Restore cross-dir wildcards safely
      regex = regex.replace("?", "[^/]");

      if (anchored || rule.contains("/")) {
        // Matches exact relative path trajectory
        final Pattern pattern = Pattern.compile(regex);
        predicates.add(
            p -> {
              if (!p.startsWith(baseDir) || p.equals(baseDir)) return false;
              Path relative = baseDir.relativize(p);
              String relativeStr = relative.toString().replace('\\', '/');
              return pattern.matcher(relativeStr).matches();
            });
      } else {
        // Matches any path component universally
        final Pattern pattern = Pattern.compile(regex);
        predicates.add(
            p -> {
              Path fileName = p.getFileName();
              return fileName != null && pattern.matcher(fileName.toString()).matches();
            });
      }
    }
    return predicates;
  }
}
