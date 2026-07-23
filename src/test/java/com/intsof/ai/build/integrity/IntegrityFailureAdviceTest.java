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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.apache.maven.plugin.logging.Log;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("IntegrityFailureAdvice")
class IntegrityFailureAdviceTest {

  @Test
  @DisplayName("logHashVerificationRecovery should advise mvn validate and skip -D options")
  void logHashVerificationRecoveryEmitsExpectedGuidance() {
    Log log = mock(Log.class);

    IntegrityFailureAdvice.logHashVerificationRecovery(log);

    verify(log).error(contains("If these changes were intentional"));
    verify(log).error(contains("mvn validate"));
    verify(log).error(contains("-D" + IntegrityFailureAdvice.SKIP_PROPERTY + "=true"));
    verify(log).error(contains("-D" + IntegrityFailureAdvice.SKIP_PROPERTY_ALT + "=true"));
    // Spacer must be non-empty so backends that drop blank lines still keep the visual gap
    verify(log).error(" ");
  }

  @Test
  @DisplayName(
      "logArtifactDigestVerificationRecovery should advise mvn package and skip -D options")
  void logArtifactDigestVerificationRecoveryEmitsExpectedGuidance() {
    Log log = mock(Log.class);

    IntegrityFailureAdvice.logArtifactDigestVerificationRecovery(log);

    verify(log).error(contains("If these changes were intentional"));
    verify(log).error(contains("mvn package"));
    verify(log).error(contains("-D" + IntegrityFailureAdvice.SKIP_PROPERTY + "=true"));
    verify(log).error(contains("-D" + IntegrityFailureAdvice.SKIP_PROPERTY_ALT + "=true"));
  }

  @Test
  @DisplayName("formatHashVerificationRecovery includes validate and skip -D options")
  void formatHashVerificationRecoveryEmitsExpectedGuidance() {
    String text = IntegrityFailureAdvice.formatHashVerificationRecovery();

    assertTrue(text.contains("If these changes were intentional"));
    assertTrue(text.contains("mvn validate"));
    assertTrue(text.contains("-D" + IntegrityFailureAdvice.SKIP_PROPERTY + "=true"));
    assertTrue(text.contains("-D" + IntegrityFailureAdvice.SKIP_PROPERTY_ALT + "=true"));
    assertFalse(text.contains("mvn package"));
    // Spacer line must match logRecovery (non-empty) between regenerate and skip blocks
    assertTrue(
        text.contains("mvn validate\n \nTo temporarily skip"),
        "formatRecovery must include SPACER between regenerate command and skip lead");
  }

  @Test
  @DisplayName("formatArtifactDigestVerificationRecovery includes package and skip -D options")
  void formatArtifactDigestVerificationRecoveryEmitsExpectedGuidance() {
    String text = IntegrityFailureAdvice.formatArtifactDigestVerificationRecovery();

    assertTrue(text.contains("If these changes were intentional"));
    assertTrue(text.contains("mvn package"));
    assertTrue(text.contains("-D" + IntegrityFailureAdvice.SKIP_PROPERTY + "=true"));
    assertTrue(text.contains("-D" + IntegrityFailureAdvice.SKIP_PROPERTY_ALT + "=true"));
    assertFalse(text.contains("mvn validate"));
    assertTrue(
        text.contains("mvn package\n \nTo temporarily skip"),
        "formatRecovery must include SPACER between regenerate command and skip lead");
  }

  @Test
  @DisplayName("null log is a no-op")
  void nullLogIsNoOp() {
    IntegrityFailureAdvice.logHashVerificationRecovery(null);
    IntegrityFailureAdvice.logArtifactDigestVerificationRecovery(null);
  }

  @Test
  @DisplayName("hash recovery advice does not mention packaging")
  void hashAdviceDoesNotMentionPackage() {
    Log log = mock(Log.class);

    IntegrityFailureAdvice.logHashVerificationRecovery(log);

    verify(log, never()).error(contains("mvn package"));
  }
}
