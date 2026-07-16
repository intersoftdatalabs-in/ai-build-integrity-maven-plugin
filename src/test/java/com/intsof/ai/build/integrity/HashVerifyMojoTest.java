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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("HashVerifyMojo")
class HashVerifyMojoTest {

  @Mock private MavenProject project;
  @Mock private MavenSession session;
  @Mock private Log log;
  @TempDir Path tempDir;

  private HashVerifyMojo mojo;

  @BeforeEach
  void setUp() throws Exception {
    mojo = new HashVerifyMojo();
    mojo.setLog(log);
    setField(mojo, "project", project);
    setField(mojo, "session", session);
    setField(mojo, "failOnError", true);
    setField(mojo, "algorithmBits", 256);
    setField(mojo, "baseDir", tempDir.toString());
    setField(mojo, "outputExtension", "auto");
    setField(mojo, "skipDirs", "target,.git,node_modules,.tmp");
  }

  @Nested
  @DisplayName("execute")
  class ExecuteTests {

    @Test
    @DisplayName("should pass verification when hashes match")
    void shouldPassWhenHashesMatch() throws Exception {
      // Given
      Path mdFile = tempDir.resolve("AGENTS.md");
      Files.write(mdFile, "# AI Agent Instructions".getBytes(StandardCharsets.UTF_8));
      String hash = HashUtils.computeHash(mdFile, "SHA-256", false);
      Files.write(
          tempDir.resolve("AGENTS.md.sha256"),
          (hash + "  AGENTS.md\n").getBytes(StandardCharsets.UTF_8));

      // When/Then
      assertDoesNotThrow(() -> mojo.execute());
    }

    @Test
    @DisplayName("should fail verification when hash does not match (tampered file)")
    void shouldFailWhenHashMismatch() throws Exception {
      // Given
      Files.write(
          tempDir.resolve("AGENTS.md"), "# AI Agent Instructions".getBytes(StandardCharsets.UTF_8));
      Files.write(
          tempDir.resolve("AGENTS.md.sha256"),
          "0000000000000000000000000000000000000000000000000000000000000000  AGENTS.md\n"
              .getBytes(StandardCharsets.UTF_8));

      // When/Then
      MojoExecutionException ex = assertThrows(MojoExecutionException.class, () -> mojo.execute());
      assertTrue(ex.getMessage().contains("FAILED"));
      assertTrue(ex.getMessage().contains("tampered"));
      // Exception message stays short; recovery guidance is logged separately
      assertFalse(ex.getMessage().contains("mvn validate"));
      verify(log).error(contains("If these changes were intentional"));
      verify(log).error(contains("mvn validate"));
      verify(log).error(contains("-Dai.integrity.skip=true"));
      verify(log).error(contains("-Dskip.ai.integrity=true"));
    }

    @Test
    @DisplayName("should fail when source file is missing for a hash file")
    void shouldFailWhenSourceFileMissing() throws Exception {
      // Given
      Files.write(
          tempDir.resolve("MISSING.md.sha256"),
          "somehash  MISSING.md\n".getBytes(StandardCharsets.UTF_8));

      // When/Then
      MojoExecutionException ex = assertThrows(MojoExecutionException.class, () -> mojo.execute());
      assertTrue(ex.getMessage().contains("FAILED"));
      verify(log).error(contains("If these changes were intentional"));
      verify(log).error(contains("mvn validate"));
    }

    @Test
    @DisplayName("should log recovery advice when failOnError is false")
    void shouldLogRecoveryAdviceWhenFailOnErrorFalse() throws Exception {
      setField(mojo, "failOnError", false);
      Files.write(
          tempDir.resolve("AGENTS.md"), "# AI Agent Instructions".getBytes(StandardCharsets.UTF_8));
      Files.write(
          tempDir.resolve("AGENTS.md.sha256"),
          "0000000000000000000000000000000000000000000000000000000000000000  AGENTS.md\n"
              .getBytes(StandardCharsets.UTF_8));

      assertDoesNotThrow(() -> mojo.execute());
      verify(log).error(contains("If these changes were intentional"));
      verify(log).error(contains("mvn validate"));
      verify(log).error(contains("-Dai.integrity.skip=true"));
      verify(log).error(contains("AI BUILD INTEGRITY WARNING"));
    }

    @Test
    @DisplayName("should log scan complete when no hash files are found")
    void shouldWarnWhenNoHashFiles() throws Exception {
      // Given
      Files.write(tempDir.resolve("README.md"), "content".getBytes(StandardCharsets.UTF_8));

      // When/Then
      assertDoesNotThrow(() -> mojo.execute());
      verify(log).info(contains("Directory scan complete:"));
    }

    @Test
    @DisplayName("should verify multiple files and report aggregate results")
    void shouldVerifyMultipleFiles() throws Exception {
      // Given
      Path file1 = tempDir.resolve("AGENTS.md");
      Path file2 = tempDir.resolve("SKILL.md");
      Files.write(file1, "Agent content".getBytes(StandardCharsets.UTF_8));
      Files.write(file2, "Skill content".getBytes(StandardCharsets.UTF_8));
      Files.write(
          tempDir.resolve("AGENTS.md.sha256"),
          (HashUtils.computeHash(file1, "SHA-256", false) + "  AGENTS.md\n")
              .getBytes(StandardCharsets.UTF_8));
      Files.write(
          tempDir.resolve("SKILL.md.sha256"),
          (HashUtils.computeHash(file2, "SHA-256", false) + "  SKILL.md\n")
              .getBytes(StandardCharsets.UTF_8));

      // When/Then
      assertDoesNotThrow(() -> mojo.execute());
    }

    @Test
    @DisplayName("should detect tampering in nested directories")
    void shouldDetectTamperingInSubdirectories() throws Exception {
      // Given
      Path subDir = tempDir.resolve("skills");
      Files.createDirectory(subDir);
      Path skillFile = subDir.resolve("SKILL.md");
      Files.write(skillFile, "Original content".getBytes(StandardCharsets.UTF_8));
      String correctHash = HashUtils.computeHash(skillFile, "SHA-256", false);
      Files.write(
          subDir.resolve("SKILL.md.sha256"),
          (correctHash + "  SKILL.md\n").getBytes(StandardCharsets.UTF_8));

      // Tamper
      Files.write(skillFile, "Tampered content".getBytes(StandardCharsets.UTF_8));

      // When/Then
      MojoExecutionException ex = assertThrows(MojoExecutionException.class, () -> mojo.execute());
      assertTrue(ex.getMessage().contains("FAILED"));
    }

    @Test
    @DisplayName("should warn and return when base directory does not exist")
    void shouldWarnForMissingBaseDir() throws Exception {
      // Given
      setField(mojo, "baseDir", tempDir.resolve("nonexistent").toString());

      // When/Then
      assertDoesNotThrow(() -> mojo.execute());
      verify(log).warn(contains("Base directory does not exist"));
    }

    @Test
    @DisplayName("should skip target directories during verification")
    void shouldSkipTargetDirectories() throws Exception {
      // Given
      Path targetDir = tempDir.resolve("target");
      Files.createDirectory(targetDir);
      Files.write(targetDir.resolve("generated.md"), "generated".getBytes(StandardCharsets.UTF_8));
      Files.write(
          targetDir.resolve("generated.md.sha256"),
          "badhash  generated.md\n".getBytes(StandardCharsets.UTF_8));

      Path mdFile = tempDir.resolve("AGENTS.md");
      Files.write(mdFile, "content".getBytes(StandardCharsets.UTF_8));
      String hash = HashUtils.computeHash(mdFile, "SHA-256", false);
      Files.write(
          tempDir.resolve("AGENTS.md.sha256"),
          (hash + "  AGENTS.md\n").getBytes(StandardCharsets.UTF_8));

      // When/Then
      assertDoesNotThrow(() -> mojo.execute());
    }

    @Test
    @DisplayName("should use project basedir when baseDir is not set")
    void shouldUseProjectBasedir() throws Exception {
      // Given
      setField(mojo, "baseDir", "");
      when(project.getBasedir()).thenReturn(tempDir.toFile());
      Path mdFile = tempDir.resolve("test.md");
      Files.write(mdFile, "content".getBytes(StandardCharsets.UTF_8));
      String hash = HashUtils.computeHash(mdFile, "SHA-256", false);
      Files.write(
          tempDir.resolve("test.md.sha256"),
          (hash + "  test.md\n").getBytes(StandardCharsets.UTF_8));

      // When/Then
      assertDoesNotThrow(() -> mojo.execute());
    }

    @Test
    @DisplayName("should verify .sha512 files when algorithmBits is 512")
    void shouldVerifySha512() throws Exception {
      // Given
      setField(mojo, "algorithmBits", 512);
      Path mdFile = tempDir.resolve("AGENTS.md");
      Files.write(mdFile, "content".getBytes(StandardCharsets.UTF_8));
      String hash = HashUtils.computeHash(mdFile, "SHA-512", false);
      Files.write(
          tempDir.resolve("AGENTS.md.sha512"),
          (hash + "  AGENTS.md\n").getBytes(StandardCharsets.UTF_8));

      // When/Then
      assertDoesNotThrow(() -> mojo.execute());
    }

    @Test
    @DisplayName("should fail when hash file exceeds max size (8 KiB)")
    void shouldFailWhenHashFileTooLarge() throws Exception {
      // Given
      Path mdFile = tempDir.resolve("AGENTS.md");
      Files.write(mdFile, "content".getBytes(StandardCharsets.UTF_8));
      // Create a hash file larger than 8 KiB
      StringBuilder oversized = new StringBuilder();
      for (int i = 0; i < 9000; i++) {
        oversized.append('a');
      }
      Files.write(
          tempDir.resolve("AGENTS.md.sha256"),
          oversized.toString().getBytes(StandardCharsets.UTF_8));

      // When/Then
      MojoExecutionException ex = assertThrows(MojoExecutionException.class, () -> mojo.execute());
      assertTrue(ex.getMessage().contains("FAILED"));
    }

    @Test
    @DisplayName("should fail when filename in hash file does not match source file")
    void shouldFailWhenFilenameMismatch() throws Exception {
      // Given
      Path mdFile = tempDir.resolve("AGENTS.md");
      Files.write(mdFile, "content".getBytes(StandardCharsets.UTF_8));
      String hash = HashUtils.computeHash(mdFile, "SHA-256", false);
      // Write hash file with wrong filename
      Files.write(
          tempDir.resolve("AGENTS.md.sha256"),
          (hash + "  WRONG_FILE.md\n").getBytes(StandardCharsets.UTF_8));

      // When/Then
      MojoExecutionException ex = assertThrows(MojoExecutionException.class, () -> mojo.execute());
      assertTrue(ex.getMessage().contains("FAILED"));
    }

    @Test
    @DisplayName("should pass when hash file contains hash only (no filename)")
    void shouldPassWhenHashOnlyFormat() throws Exception {
      // Given
      Path mdFile = tempDir.resolve("AGENTS.md");
      Files.write(mdFile, "content".getBytes(StandardCharsets.UTF_8));
      String hash = HashUtils.computeHash(mdFile, "SHA-256", false);
      // Hash-only format (no embedded filename)
      Files.write(
          tempDir.resolve("AGENTS.md.sha256"), (hash + "\n").getBytes(StandardCharsets.UTF_8));

      // When/Then
      assertDoesNotThrow(() -> mojo.execute());
    }
  }

  @Nested
  @DisplayName("Resume Mode Tests")
  class ResumeModeTests {

    @Test
    @DisplayName("should skip verification when module is resume target")
    void shouldSkipVerificationForResumeTarget() throws Exception {
      MavenExecutionRequest mockRequest = mock(MavenExecutionRequest.class);
      when(session.getRequest()).thenReturn(mockRequest);
      when(mockRequest.getResumeFrom()).thenReturn("module-a");

      when(project.getArtifactId()).thenReturn("module-a");
      setField(mojo, "resumeFromModule", "module-a");

      Path mdFile = tempDir.resolve("AGENTS.md");
      Files.write(mdFile, "content".getBytes(StandardCharsets.UTF_8));
      String hash = HashUtils.computeHash(mdFile, "SHA-256", false);
      Files.write(
          tempDir.resolve("AGENTS.md.sha256"),
          (hash + "  AGENTS.md\n").getBytes(StandardCharsets.UTF_8));

      mojo.execute();

      verify(log).info(contains("Resume mode: skipping verification for module-a"));
    }

    @Test
    @DisplayName("should verify normally when not the resume target")
    void shouldVerifyWhenNotResumeTarget() throws Exception {
      MavenExecutionRequest mockRequest = mock(MavenExecutionRequest.class);
      when(session.getRequest()).thenReturn(mockRequest);
      when(mockRequest.getResumeFrom()).thenReturn("module-a");

      when(project.getArtifactId()).thenReturn("module-b");
      setField(mojo, "resumeFromModule", "module-b");

      Path mdFile = tempDir.resolve("AGENTS.md");
      Files.write(mdFile, "content".getBytes(StandardCharsets.UTF_8));
      String hash = HashUtils.computeHash(mdFile, "SHA-256", false);
      Files.write(
          tempDir.resolve("AGENTS.md.sha256"),
          (hash + "  AGENTS.md\n").getBytes(StandardCharsets.UTF_8));

      assertDoesNotThrow(() -> mojo.execute());
    }
  }

  @Nested
  @DisplayName("Skip and Reactor Tests")
  class SkipAndReactorTests {

    @Test
    @DisplayName("should skip execution when skip property is true")
    void shouldSkipWhenSkipIsTrue() throws Exception {
      setField(mojo, "skip", true);
      mojo.execute();
      verify(log).info("Skipping execution.");
    }

    @Test
    @DisplayName("should skip execution when skipAlt property is true")
    void shouldSkipWhenSkipAltIsTrue() throws Exception {
      setField(mojo, "skipAlt", true);
      mojo.execute();
      verify(log).info("Skipping execution.");
    }

    @Test
    @DisplayName("should skip execution in non-root project when executionRootOnly is true")
    void shouldSkipInNonRootProject() throws Exception {
      setField(mojo, "executionRootOnly", true);
      when(project.isExecutionRoot()).thenReturn(false);
      mojo.execute();
      verify(log).info("Skipping HashVerifyMojo execution in non-root project.");
    }

    @Test
    @DisplayName("should render reactor progress bar for multi-module builds")
    void shouldRenderReactorProgressBar() throws Exception {
      MavenProject proj1 = mock(MavenProject.class);
      MavenProject proj2 = mock(MavenProject.class);

      when(proj1.getArtifactId()).thenReturn("module-a");
      when(project.getArtifactId()).thenReturn("module-a"); // We are building module-a

      when(session.getProjects()).thenReturn(java.util.Arrays.asList(proj1, proj2));

      // Given a valid file to pass traversal
      Path mdFile = tempDir.resolve("AGENTS.md");
      Files.write(mdFile, "content".getBytes(StandardCharsets.UTF_8));
      String hash = HashUtils.computeHash(mdFile, "SHA-256", false);
      Files.write(
          tempDir.resolve("AGENTS.md.sha256"),
          (hash + "  AGENTS.md\n").getBytes(StandardCharsets.UTF_8));

      mojo.execute();

      verify(log).info(contains("Repo-wide integrity checkpoint for: module-a"));
      verify(log).info(contains("Reactor Integrity: |"));
    }
  }

  @Nested
  @DisplayName("Configuration Edge Case Tests")
  class ConfigurationEdgeCaseTests {

    @Test
    @DisplayName("should use custom output extension")
    void shouldUseCustomOutputExtension() throws Exception {
      setField(mojo, "outputExtension", ".customhash");
      Path mdFile = tempDir.resolve("AGENTS.md");
      Files.write(mdFile, "content".getBytes(StandardCharsets.UTF_8));
      String hash = HashUtils.computeHash(mdFile, "SHA-256", false);
      Files.write(
          tempDir.resolve("AGENTS.md.customhash"),
          (hash + "  AGENTS.md\n").getBytes(StandardCharsets.UTF_8));

      assertDoesNotThrow(() -> mojo.execute());
    }

    @Test
    @DisplayName("should use project basedir when baseDir is empty")
    void shouldUseProjectBasedir() throws Exception {
      setField(mojo, "baseDir", "");
      when(project.getBasedir()).thenReturn(tempDir.toFile());
      Path mdFile = tempDir.resolve("AGENTS.md");
      Files.write(mdFile, "content".getBytes(StandardCharsets.UTF_8));
      String hash = HashUtils.computeHash(mdFile, "SHA-256", false);
      Files.write(
          tempDir.resolve("AGENTS.md.sha256"),
          (hash + "  AGENTS.md\n").getBytes(StandardCharsets.UTF_8));

      assertDoesNotThrow(() -> mojo.execute());
    }

    @Test
    @DisplayName("should handle empty or whitespace skipDirs")
    void shouldHandleEmptySkipDirs() throws Exception {
      setField(mojo, "skipDirs", " , \t,target,");
      Path mdFile = tempDir.resolve("AGENTS.md");
      Files.write(mdFile, "content".getBytes(StandardCharsets.UTF_8));
      String hash = HashUtils.computeHash(mdFile, "SHA-256", false);
      Files.write(
          tempDir.resolve("AGENTS.md.sha256"),
          (hash + "  AGENTS.md\n").getBytes(StandardCharsets.UTF_8));

      assertDoesNotThrow(() -> mojo.execute());
    }

    @Test
    @DisplayName("should respect gitignore but allow forceIncludes")
    void shouldRespectGitIgnoreAndForceIncludes() throws Exception {
      setField(mojo, "gitignoreAutoExclude", true);
      setField(mojo, "forceIncludes", "**/*.txt.sha256");

      Files.write(
          tempDir.resolve(".gitignore"), ("**/*.md.sha256\n").getBytes(StandardCharsets.UTF_8));

      Path subdir = tempDir.resolve("subdir");
      Files.createDirectory(subdir);

      Path ignoredMdFile = subdir.resolve("AGENTS.md.sha256");
      Files.write(ignoredMdFile, ("invalid-hash").getBytes(StandardCharsets.UTF_8));
      Path forcedTxtFile = subdir.resolve("forced.txt");
      Files.write(forcedTxtFile, "content".getBytes(StandardCharsets.UTF_8));
      String hash = HashUtils.computeHash(forcedTxtFile, "SHA-256", false);
      Files.write(
          subdir.resolve("forced.txt.sha256"),
          (hash + "  forced.txt\n").getBytes(StandardCharsets.UTF_8));

      // The ignored file should be skipped, so it won't throw an execution exception from an
      // invalid hash
      assertDoesNotThrow(() -> mojo.execute());
    }
  }

  @Nested
  @DisplayName("Central Mode Tests")
  class CentralModeTests {

    @BeforeEach
    void setUpCentral() throws Exception {
      setField(mojo, "hashFileMode", HashFileMode.CENTRAL);
      setField(mojo, "centralHashFile", tempDir.resolve("ai-integrity.sha256").toString());
    }

    @Test
    @DisplayName("should pass verification when central hashes match")
    void shouldPassCentralMatch() throws Exception {
      Path mdFile = tempDir.resolve("AGENTS.md");
      Files.write(mdFile, "content".getBytes(StandardCharsets.UTF_8));
      String hash = HashUtils.computeHash(mdFile, "SHA-256", false);

      Path centralFile = tempDir.resolve("ai-integrity.sha256");
      Files.write(centralFile, (hash + "  AGENTS.md\n").getBytes(StandardCharsets.UTF_8));

      setField(mojo, "generateAuditReport", true);
      setField(mojo, "buildDirectory", tempDir.resolve("target").toString());

      assertDoesNotThrow(() -> mojo.execute());
      assertTrue(Files.exists(tempDir.resolve("target").resolve("ai-integrity-report.json")));
    }

    @Test
    @DisplayName("should fail when tampered in central mode")
    void shouldFailCentralMismatch() throws Exception {
      Path mdFile = tempDir.resolve("AGENTS.md");
      Files.write(mdFile, "tampered".getBytes(StandardCharsets.UTF_8));

      Path centralFile = tempDir.resolve("ai-integrity.sha256");
      Files.write(centralFile, "00000000000  AGENTS.md\n".getBytes(StandardCharsets.UTF_8));

      setField(mojo, "generateAuditReport", true);
      setField(mojo, "buildDirectory", tempDir.resolve("target").toString());

      MojoExecutionException ex = assertThrows(MojoExecutionException.class, () -> mojo.execute());
      assertTrue(ex.getMessage().contains("FAILED"));
      assertTrue(Files.exists(tempDir.resolve("target").resolve("ai-integrity-report.json")));
    }

    @Test
    @DisplayName("should skip empty and invalid lines in central mode")
    void shouldSkipEmptyLines() throws Exception {
      Path centralFile = tempDir.resolve("ai-integrity.sha256");
      Files.write(centralFile, "\n   \nONLY_HASH\n".getBytes(StandardCharsets.UTF_8));
      assertDoesNotThrow(() -> mojo.execute());
    }

    @Test
    @DisplayName("should report missing file in central mode audit")
    void shouldReportMissingFileCentral() throws Exception {
      setField(mojo, "generateAuditReport", true);
      setField(mojo, "buildDirectory", tempDir.resolve("target").toString());

      Path centralFile = tempDir.resolve("ai-integrity.sha256");
      Files.write(centralFile, "00000000  MISSING.md\n".getBytes(StandardCharsets.UTF_8));

      MojoExecutionException ex = assertThrows(MojoExecutionException.class, () -> mojo.execute());
      assertTrue(ex.getMessage().contains("FAILED"));

      Path reportFile = tempDir.resolve("target").resolve("ai-integrity-report.json");
      String content = new String(Files.readAllBytes(reportFile), StandardCharsets.UTF_8);
      assertTrue(content.contains("\"status\": \"MISSING\""));
    }

    @Test
    @DisplayName("should skip execution if central file not found")
    void shouldSkipIfCentralFileNotFound() throws Exception { // It warns and returns
      assertDoesNotThrow(() -> mojo.execute());
      verify(log).warn(contains("Central hash file not found"));
    }
  }

  @Nested
  @DisplayName("Audit Report")
  class AuditReportTests {

    @BeforeEach
    void setUpAudit() throws Exception {
      setField(mojo, "generateAuditReport", true);
      setField(mojo, "buildDirectory", tempDir.resolve("target").toString());
    }

    @Test
    @DisplayName("should generate audit report by default in target directory")
    void shouldGenerateReportByDefault() throws Exception {
      // Given
      Path mdFile = tempDir.resolve("AGENTS.md");
      Files.write(mdFile, "content".getBytes(StandardCharsets.UTF_8));
      String hash = HashUtils.computeHash(mdFile, "SHA-256", false);
      Files.write(
          tempDir.resolve("AGENTS.md.sha256"),
          (hash + "  AGENTS.md\n").getBytes(StandardCharsets.UTF_8));

      // When
      mojo.execute();

      // Then
      Path reportFile = tempDir.resolve("target").resolve("ai-integrity-report.json");
      assertTrue(Files.exists(reportFile), "Audit report should exist");
      String content = new String(Files.readAllBytes(reportFile), StandardCharsets.UTF_8);
      assertTrue(
          content.contains("\"status\": \"VERIFIED\""), "Report should contain verified status");
    }

    @Test
    @DisplayName("should override audit report path with centralReportFile")
    void shouldOverrideReportPath() throws Exception {
      // Given
      Path customReport = tempDir.resolve("custom-report.json");
      setField(mojo, "centralReportFile", customReport.toString());

      Path mdFile = tempDir.resolve("AGENTS.md");
      Files.write(mdFile, "content".getBytes(StandardCharsets.UTF_8));
      String hash = HashUtils.computeHash(mdFile, "SHA-256", false);
      Files.write(
          tempDir.resolve("AGENTS.md.sha256"),
          (hash + "  AGENTS.md\n").getBytes(StandardCharsets.UTF_8));

      // When
      mojo.execute();

      // Then
      assertTrue(Files.exists(customReport), "Custom audit report should exist at " + customReport);
      assertFalse(
          Files.exists(tempDir.resolve("target").resolve("ai-integrity-report.json")),
          "Default report should NOT exist");
    }
  }

  private static void setField(Object target, String fieldName, Object value) throws Exception {
    Field field = findField(target.getClass(), fieldName);
    field.setAccessible(true);
    field.set(target, value);
  }

  private static Field findField(Class<?> clazz, String fieldName) throws NoSuchFieldException {
    Class<?> current = clazz;
    while (current != null) {
      try {
        return current.getDeclaredField(fieldName);
      } catch (NoSuchFieldException e) {
        current = current.getSuperclass();
      }
    }
    throw new NoSuchFieldException(fieldName);
  }
}
