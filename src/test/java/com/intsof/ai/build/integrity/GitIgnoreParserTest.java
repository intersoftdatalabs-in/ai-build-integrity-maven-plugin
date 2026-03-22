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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitIgnoreParserTest {

  @TempDir Path tempDir;

  @Test
  void testParseIgnoresCommentsAndEmptyLines() throws IOException {
    Path gitignore = tempDir.resolve(".gitignore");
    Files.writeString(
        gitignore,
        "# This is a comment\n" + "\n" + "   \n" + "!negated_rule_not_supported\n" + "target\n");

    List<Predicate<Path>> predicates = GitIgnoreParser.parse(gitignore);

    assertEquals(1, predicates.size(), "Should only parse the active rule 'target'");
    assertTrue(predicates.get(0).test(tempDir.resolve("target")));
  }

  @Test
  void testExactNameMatch() throws IOException {
    Path gitignore = tempDir.resolve(".gitignore");
    Files.writeString(gitignore, "node_modules\n");

    List<Predicate<Path>> predicates = GitIgnoreParser.parse(gitignore);
    assertEquals(1, predicates.size());
    Predicate<Path> p = predicates.get(0);

    assertTrue(p.test(tempDir.resolve("node_modules")));
    assertTrue(p.test(tempDir.resolve("backend/node_modules")));
    assertFalse(p.test(tempDir.resolve("node_modules_old")));
  }

  @Test
  void testSuffixMatch() throws IOException {
    Path gitignore = tempDir.resolve(".gitignore");
    Files.writeString(gitignore, "*.class\n");

    List<Predicate<Path>> predicates = GitIgnoreParser.parse(gitignore);
    assertEquals(1, predicates.size());
    Predicate<Path> p = predicates.get(0);

    assertTrue(p.test(tempDir.resolve("Test.class")));
    assertTrue(p.test(tempDir.resolve("com/example/Main.class")));
    assertFalse(p.test(tempDir.resolve("Test.java")));
  }

  @Test
  void testPrefixMatch() throws IOException {
    Path gitignore = tempDir.resolve(".gitignore");
    Files.writeString(gitignore, "temp_*\n");

    List<Predicate<Path>> predicates = GitIgnoreParser.parse(gitignore);
    assertEquals(1, predicates.size());
    Predicate<Path> p = predicates.get(0);

    assertTrue(p.test(tempDir.resolve("temp_file.txt")));
    assertTrue(p.test(tempDir.resolve("foo/temp_dir")));
    assertFalse(p.test(tempDir.resolve("atemp_file.txt")));
  }

  @Test
  void testAnchoredMatch() throws IOException {
    Path gitignore = tempDir.resolve(".gitignore");
    // Should match exact path relative to .gitignore
    Files.writeString(gitignore, "/build/\n/test.log\n");

    List<Predicate<Path>> predicates = GitIgnoreParser.parse(gitignore);
    assertEquals(2, predicates.size());

    Path buildDir = tempDir.resolve("build");
    Path subBuildDir = tempDir.resolve("sub/build");
    Files.createDirectories(buildDir);
    Files.createDirectories(subBuildDir);

    Predicate<Path> buildRule = predicates.get(0);
    // Should match build folder directly inside tempDir
    assertTrue(buildRule.test(buildDir));
    // Should NOT match a nested build folder
    assertFalse(buildRule.test(subBuildDir));

    Path tempDirSub = tempDir.resolve("sub");
    Path testLog = tempDir.resolve("test.log");
    Path subTestLog = tempDirSub.resolve("test.log");
    Files.createDirectories(tempDirSub);
    Files.createFile(testLog);
    Files.createFile(subTestLog);

    Predicate<Path> logRule = predicates.get(1);
    assertTrue(logRule.test(testLog));
    assertFalse(logRule.test(subTestLog));
  }

  @Test
  void testWildcardMatches() throws IOException {
    Path gitignore = tempDir.resolve(".gitignore");
    Files.writeString(gitignore, "foo/**/*.log\n");

    List<Predicate<Path>> predicates = GitIgnoreParser.parse(gitignore);
    assertEquals(1, predicates.size());
    Predicate<Path> p = predicates.get(0);

    Path fooBarBaz = tempDir.resolve("foo/bar/baz.log");
    Path barFooTest = tempDir.resolve("bar/foo/test.log");
    Files.createDirectories(fooBarBaz.getParent());
    Files.createDirectories(barFooTest.getParent());
    Files.createFile(fooBarBaz);
    Files.createFile(barFooTest);

    assertTrue(p.test(fooBarBaz));
    assertFalse(p.test(barFooTest));
  }

  @Test
  void testPatternEscaping() throws IOException {
    Path gitignore = tempDir.resolve(".gitignore");
    // Testing characters that need escaping in regex
    Files.writeString(gitignore, "some.file[1]\n");

    List<Predicate<Path>> predicates = GitIgnoreParser.parse(gitignore);
    assertEquals(1, predicates.size());
    Predicate<Path> p = predicates.get(0);

    assertTrue(p.test(tempDir.resolve("some.file[1]")));
    assertTrue(p.test(tempDir.resolve("dir/some.file[1]")));
    assertFalse(p.test(tempDir.resolve("some.filex1]")));
  }
}
