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
import java.nio.file.Files;
import java.nio.file.Path;
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

  @Mock private MavenProject project;
  @Mock private MavenSession session;
  @Mock private Log log;
  @TempDir Path tempDir;

  private HashGeneratorMojo generatorMojo;
  private HashVerifyMojo verifyMojo;

  @BeforeEach
  void setUp() throws Exception {
    generatorMojo = new HashGeneratorMojo();
    generatorMojo.setLog(log);
    setField(generatorMojo, "project", project);
    setField(generatorMojo, "session", session);
    setField(generatorMojo, "algorithmBits", 256);
    setField(generatorMojo, "includes", "**/*.md");
    setField(generatorMojo, "excludes", "**/*.sha256,**/*.sha384,**/*.sha512");
    setField(generatorMojo, "baseDir", tempDir.toString());
    setField(generatorMojo, "outputExtension", "auto");
    setField(generatorMojo, "skipExisting", false);
    setField(generatorMojo, "skipDirs", "target,.git,node_modules,.tmp");
    setField(generatorMojo, "hideHashFiles", false);

    verifyMojo = new HashVerifyMojo();
    verifyMojo.setLog(log);
    setField(verifyMojo, "project", project);
    setField(verifyMojo, "session", session);
    setField(verifyMojo, "failOnError", true);
    setField(verifyMojo, "algorithmBits", 256);
    setField(verifyMojo, "baseDir", tempDir.toString());
    setField(verifyMojo, "outputExtension", "auto");
    setField(verifyMojo, "skipDirs", "target,.git,node_modules,.tmp");
  }

  @Test
  @DisplayName("Resume build should accept intentional edits without triggering tamper detection")
  void testResumeBuildAcceptsModifiedFiles() throws Exception {
    when(project.getArtifactId()).thenReturn("module-b");
    when(project.getBasedir()).thenReturn(tempDir.toFile());

    Path mdFile = tempDir.resolve("AGENTS.md");
    Files.writeString(mdFile, "original content");

    setField(generatorMojo, "resumeFromModule", "module-b");
    generatorMojo.execute();
    assertTrue(Files.exists(tempDir.resolve("AGENTS.md.sha256")));

    Files.writeString(mdFile, "intentionally modified during failed build");

    MavenExecutionRequest mockRequest = mock(MavenExecutionRequest.class);
    when(session.getRequest()).thenReturn(mockRequest);
    when(mockRequest.getResumeFrom()).thenReturn("module-b");

    Files.writeString(mdFile, "original content");
    generatorMojo.execute();

    setField(verifyMojo, "resumeFromModule", "module-b");

    assertDoesNotThrow(() -> verifyMojo.execute());
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
