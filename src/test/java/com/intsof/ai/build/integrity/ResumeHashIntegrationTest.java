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
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ResumeHashIntegrationTest")
class ResumeHashIntegrationTest {

  @Mock private MavenProject rootProject;
  @Mock private MavenProject moduleB;
  @Mock private MavenProject moduleC;
  @Mock private MavenSession session;
  @Mock private MavenExecutionRequest request;
  @Mock private Log log;
  @TempDir Path tempDir;

  private Path moduleBDir;
  private Path moduleCDir;
  private Path centralLedger;

  @BeforeEach
  void setUp() throws Exception {
    moduleBDir = tempDir.resolve("module-b");
    moduleCDir = tempDir.resolve("module-c");
    Files.createDirectories(moduleBDir);
    Files.createDirectories(moduleCDir);
    centralLedger = tempDir.resolve("target").resolve("ai-integrity.sha256");
    Files.createDirectories(centralLedger.getParent());

    when(rootProject.getArtifactId()).thenReturn("root");
    when(rootProject.getGroupId()).thenReturn("com.example");
    when(rootProject.getBasedir()).thenReturn(tempDir.toFile());
    when(rootProject.isExecutionRoot()).thenReturn(true);

    when(moduleB.getArtifactId()).thenReturn("module-b");
    when(moduleB.getGroupId()).thenReturn("com.example");
    when(moduleB.getBasedir()).thenReturn(moduleBDir.toFile());
    when(moduleB.isExecutionRoot()).thenReturn(false);

    when(moduleC.getArtifactId()).thenReturn("module-c");
    when(moduleC.getGroupId()).thenReturn("com.example");
    when(moduleC.getBasedir()).thenReturn(moduleCDir.toFile());
    when(moduleC.isExecutionRoot()).thenReturn(false);

    when(session.getRequest()).thenReturn(request);
  }

  @Test
  @DisplayName("Plain -rf without resumeFromModule re-seals CENTRAL and verify passes after edits")
  void plainRfResealsCentralLedgerAndVerifyPasses() throws Exception {
    // Initial full seal as root would do
    Path agentsB = moduleBDir.resolve("AGENTS.md");
    Path agentsC = moduleCDir.resolve("AGENTS.md");
    Files.write(agentsB, "module-b original".getBytes(StandardCharsets.UTF_8));
    Files.write(agentsC, "module-c original".getBytes(StandardCharsets.UTF_8));

    HashGeneratorMojo rootGen = newGenerator(rootProject);
    setField(rootGen, "executionRootOnly", true);
    setField(rootGen, "hashFileMode", HashFileMode.CENTRAL);
    setField(rootGen, "baseDir", tempDir.toString());
    setField(rootGen, "centralHashFile", centralLedger.toString());
    setField(rootGen, "reactorScope", "AUTO");
    when(session.getProjects()).thenReturn(Arrays.asList(rootProject, moduleB, moduleC));
    when(session.getAllProjects()).thenReturn(Arrays.asList(rootProject, moduleB, moduleC));
    when(request.getResumeFrom()).thenReturn(null);
    rootGen.execute();
    assertTrue(Files.exists(centralLedger));
    String ledgerBefore = new String(Files.readAllBytes(centralLedger), StandardCharsets.UTF_8);
    assertTrue(ledgerBefore.contains("module-b/"));
    assertTrue(ledgerBefore.contains("module-c/"));

    // Intentional fix in module-b after a failed build
    Files.write(agentsB, "module-b fixed during failure".getBytes(StandardCharsets.UTF_8));

    // Resume reactor from module-b (root not in reactor)
    when(session.getProjects()).thenReturn(Arrays.asList(moduleB, moduleC));
    when(session.getAllProjects()).thenReturn(Arrays.asList(rootProject, moduleB, moduleC));
    when(request.getResumeFrom()).thenReturn(":module-b");

    HashGeneratorMojo resumeGen = newGenerator(moduleB);
    setField(resumeGen, "executionRootOnly", true);
    setField(resumeGen, "hashFileMode", HashFileMode.CENTRAL);
    setField(resumeGen, "baseDir", tempDir.toString());
    setField(resumeGen, "centralHashFile", centralLedger.toString());
    setField(resumeGen, "reactorScope", "AUTO");
    // no resumeFromModule property
    resumeGen.execute();

    verify(log, atLeastOnce()).info(contains("Resume mode: regenerating hashes"));
    String ledgerAfter = new String(Files.readAllBytes(centralLedger), StandardCharsets.UTF_8);
    assertTrue(ledgerAfter.contains("module-c/"), "module-c seal must be preserved");
    String hashB = HashUtils.computeHash(agentsB, "SHA-256", false);
    assertTrue(
        ledgerAfter.contains(hashB),
        "ledger must contain new hash for intentionally edited module-b file");

    // Verify as module-b would during TEST — must pass without skip
    HashVerifyMojo verify = newVerify(moduleB);
    setField(verify, "hashFileMode", HashFileMode.CENTRAL);
    setField(verify, "baseDir", tempDir.toString());
    setField(verify, "centralHashFile", centralLedger.toString());
    assertDoesNotThrow(() -> verify.execute());
    verify(log, atLeastOnce()).info(contains("Resume mode: verifying hashes after re-seal"));
  }

  @Test
  @DisplayName("Resume build with SIDECAR accepts intentional edits without resumeFromModule")
  void sidecarResumeAcceptsModifiedFiles() throws Exception {
    Path mdFile = moduleBDir.resolve("AGENTS.md");
    Files.write(mdFile, "original content".getBytes(StandardCharsets.UTF_8));

    when(session.getProjects()).thenReturn(Collections.singletonList(moduleB));
    when(session.getAllProjects()).thenReturn(Arrays.asList(rootProject, moduleB));
    when(request.getResumeFrom()).thenReturn(null);

    HashGeneratorMojo generator = newGenerator(moduleB);
    setField(generator, "baseDir", moduleBDir.toString());
    setField(generator, "hashFileMode", HashFileMode.SIDECAR);
    setField(generator, "executionRootOnly", false);
    generator.execute();
    assertTrue(Files.exists(moduleBDir.resolve("AGENTS.md.sha256")));

    Files.write(
        mdFile, "intentionally modified during failed build".getBytes(StandardCharsets.UTF_8));

    when(request.getResumeFrom()).thenReturn(":module-b");
    generator.execute();

    HashVerifyMojo verify = newVerify(moduleB);
    setField(verify, "baseDir", moduleBDir.toString());
    setField(verify, "hashFileMode", HashFileMode.SIDECAR);
    assertDoesNotThrow(() -> verify.execute());
  }

  @Test
  @DisplayName("Non-first resumed module with executionRootOnly does not re-run generate")
  void laterResumeModuleSkipsGenerateWhenExecutionRootOnly() throws Exception {
    when(session.getProjects()).thenReturn(Arrays.asList(moduleB, moduleC));
    when(session.getAllProjects()).thenReturn(Arrays.asList(rootProject, moduleB, moduleC));
    when(request.getResumeFrom()).thenReturn(":module-b");

    HashGeneratorMojo gen = newGenerator(moduleC);
    setField(gen, "executionRootOnly", true);
    setField(gen, "hashFileMode", HashFileMode.CENTRAL);
    setField(gen, "baseDir", tempDir.toString());
    setField(gen, "centralHashFile", centralLedger.toString());
    Files.write(moduleCDir.resolve("AGENTS.md"), "c".getBytes(StandardCharsets.UTF_8));

    gen.execute();

    verify(log).info(contains("Skipping hash regeneration for module-c"));
    assertFalse(Files.exists(centralLedger));
  }

  private HashGeneratorMojo newGenerator(MavenProject project) throws Exception {
    HashGeneratorMojo mojo = new HashGeneratorMojo();
    mojo.setLog(log);
    setField(mojo, "project", project);
    setField(mojo, "session", session);
    setField(mojo, "algorithmBits", 256);
    setField(mojo, "includes", "**/*.md");
    setField(mojo, "excludes", "**/*.sha256,**/*.sha384,**/*.sha512");
    setField(mojo, "outputExtension", "auto");
    setField(mojo, "skipExisting", false);
    setField(mojo, "skipDirs", "target,.git,node_modules,.tmp");
    setField(mojo, "hideHashFiles", false);
    setField(mojo, "buildDirectory", tempDir.resolve("target").toString());
    setField(mojo, "reactorScope", "AUTO");
    return mojo;
  }

  private HashVerifyMojo newVerify(MavenProject project) throws Exception {
    HashVerifyMojo mojo = new HashVerifyMojo();
    mojo.setLog(log);
    setField(mojo, "project", project);
    setField(mojo, "session", session);
    setField(mojo, "failOnError", true);
    setField(mojo, "algorithmBits", 256);
    setField(mojo, "outputExtension", "auto");
    setField(mojo, "skipDirs", "target,.git,node_modules,.tmp");
    setField(mojo, "generateAuditReport", false);
    setField(mojo, "buildDirectory", tempDir.resolve("target").toString());
    return mojo;
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
