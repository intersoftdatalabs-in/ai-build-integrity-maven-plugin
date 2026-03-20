package com.intsof.ai.build.integrity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

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

      // 1. Anchored to base directory (/foo)
      if (rule.startsWith("/")) {
        String sub = rule.substring(1);
        if (sub.endsWith("/")) {
          sub = sub.substring(0, sub.length() - 1);
        }
        final Path targetPath = baseDir.resolve(sub);
        predicates.add(p -> p.startsWith(targetPath));
        continue;
      }

      // 2. Suffix match (*.ext)
      if (rule.startsWith("*") && rule.indexOf('*', 1) == -1 && rule.indexOf('/') == -1) {
        final String suffix = rule.substring(1);
        predicates.add(
            p -> {
              Path name = p.getFileName();
              return name != null && name.toString().endsWith(suffix);
            });
        continue;
      }

      // 3. Exact name or directory (e.g., node_modules, target/)
      if ((rule.indexOf('*') == -1 && rule.indexOf('/') == -1)
          || (rule.indexOf('/') == rule.length() - 1 && rule.indexOf('*') == -1)) {
        final String name = rule.endsWith("/") ? rule.substring(0, rule.length() - 1) : rule;
        predicates.add(
            p -> {
              Path fileName = p.getFileName();
              return fileName != null && fileName.toString().equals(name);
            });
        continue;
      }

      // 4. Wildcard matching anywhere (e.g., **/*.log, *foo*)
      // Convert standard glob syntax to an efficient lightweight regex pattern
      String regex = rule.replace(".", "\\.").replace("*", ".*").replace("?", ".");
      if (!rule.contains("/")) {
        final java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(regex);
        predicates.add(
            p -> {
              Path fileName = p.getFileName();
              return fileName != null && pattern.matcher(fileName.toString()).matches();
            });
      } else {
        // Evaluate the regex against the sub-tree relative string path
        final java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(".*" + regex);
        predicates.add(
            p -> {
              if (!p.startsWith(baseDir)) return false; // Safety check boundary
              Path relative = baseDir.relativize(p);
              String relativeStr = relative.toString().replace('\\', '/');
              return pattern.matcher(relativeStr).matches();
            });
      }
    }
    return predicates;
  }
}
