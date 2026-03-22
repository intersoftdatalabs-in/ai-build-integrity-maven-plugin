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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.maven.plugin.logging.Log;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitIgnoreAwareFileVisitorTest {

  @TempDir Path tempDir;
  private Log log;

  @BeforeEach
  void setUp() {
    log = mock(Log.class);
  }

  @Test
  void testStaticSkipDirs() throws IOException {
    Set<String> skipDirs = new HashSet<>(Collections.singletonList("target"));
    GitIgnoreAwareFileVisitor visitor =
        new GitIgnoreAwareFileVisitor(tempDir, false, skipDirs, Collections.emptyList(), log) {
          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            return FileVisitResult.CONTINUE;
          }
        };

    Path targetDir = tempDir.resolve("target");
    Files.createDirectory(targetDir);

    FileVisitResult result = visitor.preVisitDirectory(targetDir, mock(BasicFileAttributes.class));
    assertEquals(FileVisitResult.SKIP_SUBTREE, result, "Should skip target directory natively");
  }

  @Test
  void testGitIgnoreParsingAndExclusion() throws IOException {
    // Create nested structure:
    // tempDir/
    //   .gitignore (contains "ignored_dir/")
    //   ignored_dir/
    //     file.txt
    //   valid_dir/
    //     file.txt

    Files.writeString(tempDir.resolve(".gitignore"), "ignored_dir/\n");

    Path ignoredDir = tempDir.resolve("ignored_dir");
    Path validDir = tempDir.resolve("valid_dir");
    Files.createDirectory(ignoredDir);
    Files.createDirectory(validDir);

    Path ignoredFile = ignoredDir.resolve("file.txt");
    Path validFile = validDir.resolve("file.txt");
    Files.writeString(ignoredFile, "ignored");
    Files.writeString(validFile, "valid");

    GitIgnoreAwareFileVisitor visitor =
        new GitIgnoreAwareFileVisitor(
            tempDir, true, Collections.emptySet(), Collections.emptyList(), log) {
          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            return FileVisitResult.CONTINUE;
          }
        };

    // Simulate Files.walkFileTree visiting the root directory
    visitor.preVisitDirectory(tempDir, mock(BasicFileAttributes.class));

    // The visitor should skip the ignored_dir
    FileVisitResult ignoredResult =
        visitor.preVisitDirectory(ignoredDir, mock(BasicFileAttributes.class));
    assertEquals(FileVisitResult.SKIP_SUBTREE, ignoredResult);

    // After preVisitDirectory, checking isIgnoredByGit on the file inside should return true
    assertTrue(visitor.isIgnoredByGit(ignoredFile), "File inside ignored dir should be ignored");

    // Pop the physical directory to simulate natural traversal end
    visitor.postVisitDirectory(ignoredDir, null);

    // The visitor should CONTINUE for the valid_dir
    FileVisitResult validResult =
        visitor.preVisitDirectory(validDir, mock(BasicFileAttributes.class));
    assertEquals(FileVisitResult.CONTINUE, validResult);

    // The file inside valid_dir should NOT be ignored
    assertFalse(visitor.isIgnoredByGit(validFile), "File in valid dir should NOT be ignored");

    // Pop the physical directory
    visitor.postVisitDirectory(validDir, null);

    // Pop the root directory
    visitor.postVisitDirectory(tempDir, null);
  }

  @Test
  void testForceIncludesOverridesGitIgnore() throws IOException {
    Files.writeString(tempDir.resolve(".gitignore"), "*.log\n");

    Path logFile = tempDir.resolve("application.log");
    Files.writeString(logFile, "log");

    // Create a PathMatcher that matches *.log
    PathMatcher logMatcher = tempDir.getFileSystem().getPathMatcher("glob:**/*.log");
    List<PathMatcher> forceIncludes = Collections.singletonList(logMatcher);

    GitIgnoreAwareFileVisitor visitor =
        new GitIgnoreAwareFileVisitor(tempDir, true, Collections.emptySet(), forceIncludes, log) {
          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            return FileVisitResult.CONTINUE;
          }
        };

    visitor.preVisitDirectory(tempDir, mock(BasicFileAttributes.class));

    // Even though it's ignored by Git, the existence of forceIncludes means we MUST NOT prune dirs
    // completely
    // Wait, the test is that isIgnoredByGit returns true, but preVisitDirectory doesn't
    // SKIP_SUBTREE!

    // Let's create an ignored directory
    Files.writeString(tempDir.resolve(".gitignore"), "logs/\n");
    Path logsDir = tempDir.resolve("logs");
    Files.createDirectory(logsDir);

    FileVisitResult dirResult = visitor.preVisitDirectory(logsDir, mock(BasicFileAttributes.class));

    // Because forceIncludes exist, we DO NOT prune the Git-ignored logs directory!
    assertEquals(
        FileVisitResult.CONTINUE, dirResult, "Should CONTINUE because forceIncludes is present");

    visitor.postVisitDirectory(logsDir, null);
    visitor.postVisitDirectory(tempDir, null);
  }

  @Test
  void testGitIgnoreParsingFailsGracefully() throws IOException {
    // Create a directory named .gitignore so parsing it as a file throws an exception
    Path gitignoreDir = tempDir.resolve(".gitignore");
    Files.createDirectory(gitignoreDir);

    GitIgnoreAwareFileVisitor visitor =
        new GitIgnoreAwareFileVisitor(
            tempDir, true, Collections.emptySet(), Collections.emptyList(), log) {
          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            return FileVisitResult.CONTINUE;
          }
        };

    // The exception should be caught and logged, not thrown
    FileVisitResult result = visitor.preVisitDirectory(tempDir, mock(BasicFileAttributes.class));
    assertEquals(FileVisitResult.CONTINUE, result);
    // Note: Mockito verify(log).warn(...) could be added here if needed
  }
}
