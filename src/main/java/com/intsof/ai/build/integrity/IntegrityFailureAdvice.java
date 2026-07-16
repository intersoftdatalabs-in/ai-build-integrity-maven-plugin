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

import org.apache.maven.plugin.logging.Log;

/**
 * Formats and logs actionable recovery guidance when integrity verification fails.
 *
 * <p>Verification still fails the build when {@code failOnError} is true; this class only improves
 * the developer-facing failure UX so intentional edits are easy to re-seal or temporarily skip.
 */
public final class IntegrityFailureAdvice {

  /** Primary skip system property documented for end users. */
  public static final String SKIP_PROPERTY = "ai.integrity.skip";

  /** Alternate Maven-conventional skip system property. */
  public static final String SKIP_PROPERTY_ALT = "skip.ai.integrity";

  private static final String BANNER =
      "------------------------------------------------------------------------";

  private IntegrityFailureAdvice() {}

  /**
   * Logs recovery advice for AI resource hash verification failures ({@code verify-hashes}).
   *
   * @param log the Maven plugin logger; no-op when {@code null}
   */
  public static void logHashVerificationRecovery(Log log) {
    logRecovery(
        log,
        "If these changes were intentional, re-seal the project by regenerating hashes:",
        "  mvn validate",
        "To temporarily skip integrity verification for this build:");
  }

  /**
   * Logs recovery advice for artifact digest verification failures ({@code
   * verify-artifact-digests}).
   *
   * @param log the Maven plugin logger; no-op when {@code null}
   */
  public static void logArtifactDigestVerificationRecovery(Log log) {
    logRecovery(
        log,
        "If these changes were intentional, re-generate artifact digests (e.g. re-run packaging):",
        "  mvn package",
        "To temporarily skip integrity verification for this build:");
  }

  /**
   * Shared multi-line recovery footer. Skip flags are identical for all integrity goals.
   *
   * @param log the Maven plugin logger
   * @param intentionalLead lead sentence for intentional edits
   * @param regenerateCommand example Maven command to re-seal (already indented)
   * @param skipLead lead sentence before skip properties
   */
  static void logRecovery(
      Log log, String intentionalLead, String regenerateCommand, String skipLead) {
    if (log == null) {
      return;
    }
    log.error(BANNER);
    log.error(intentionalLead);
    log.error(regenerateCommand);
    log.error("");
    log.error(skipLead);
    log.error("  -D" + SKIP_PROPERTY + "=true");
    log.error("  (also accepted: -D" + SKIP_PROPERTY_ALT + "=true)");
    log.error(BANNER);
  }
}
