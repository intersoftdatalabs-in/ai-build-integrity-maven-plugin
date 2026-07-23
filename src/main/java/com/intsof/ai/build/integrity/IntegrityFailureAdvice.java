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
 *
 * <p>Recovery text is both logged ({@link #logHashVerificationRecovery(Log)}) and included in the
 * {@code MojoExecutionException} message via {@link #formatHashVerificationRecovery()} so it
 * appears in Maven's final {@code Failed to execute goal} summary — the block users typically read
 * after a long multi-module build.
 */
public final class IntegrityFailureAdvice {

  /** Primary skip system property documented for end users. */
  public static final String SKIP_PROPERTY = "ai.integrity.skip";

  /** Alternate Maven-conventional skip system property. */
  public static final String SKIP_PROPERTY_ALT = "skip.ai.integrity";

  private static final String BANNER =
      "------------------------------------------------------------------------";

  /**
   * Non-empty spacer between advice blocks. Empty {@code log.error("")} lines are dropped by some
   * Maven / SLF4J backends and CI shippers, which collapses the intended visual gap.
   */
  private static final String SPACER = " ";

  private static final String HASH_INTENTIONAL_LEAD =
      "If these changes were intentional, re-seal the project by regenerating hashes:";

  private static final String HASH_REGENERATE_COMMAND = "  mvn validate";

  private static final String ARTIFACT_INTENTIONAL_LEAD =
      "If these changes were intentional, re-generate artifact digests (e.g. re-run packaging):";

  private static final String ARTIFACT_REGENERATE_COMMAND = "  mvn package";

  private static final String SKIP_LEAD =
      "To temporarily skip integrity verification for this build:";

  private IntegrityFailureAdvice() {}

  /**
   * Logs recovery advice for AI resource hash verification failures ({@code verify-hashes}).
   *
   * @param log the Maven plugin logger; no-op when {@code null}
   */
  public static void logHashVerificationRecovery(Log log) {
    logRecovery(log, HASH_INTENTIONAL_LEAD, HASH_REGENERATE_COMMAND, SKIP_LEAD);
  }

  /**
   * Formats recovery advice for AI resource hash verification failures for inclusion in a {@code
   * MojoExecutionException} message.
   *
   * @return multi-line recovery text (no trailing newline)
   */
  public static String formatHashVerificationRecovery() {
    return formatRecovery(HASH_INTENTIONAL_LEAD, HASH_REGENERATE_COMMAND, SKIP_LEAD);
  }

  /**
   * Logs recovery advice for artifact digest verification failures ({@code
   * verify-artifact-digests}).
   *
   * @param log the Maven plugin logger; no-op when {@code null}
   */
  public static void logArtifactDigestVerificationRecovery(Log log) {
    logRecovery(log, ARTIFACT_INTENTIONAL_LEAD, ARTIFACT_REGENERATE_COMMAND, SKIP_LEAD);
  }

  /**
   * Formats recovery advice for artifact digest verification failures for inclusion in a {@code
   * MojoExecutionException} message.
   *
   * @return multi-line recovery text (no trailing newline)
   */
  public static String formatArtifactDigestVerificationRecovery() {
    return formatRecovery(ARTIFACT_INTENTIONAL_LEAD, ARTIFACT_REGENERATE_COMMAND, SKIP_LEAD);
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
    log.error(SPACER);
    log.error(skipLead);
    log.error("  -D" + SKIP_PROPERTY + "=true");
    log.error("  (also accepted: -D" + SKIP_PROPERTY_ALT + "=true)");
    log.error(BANNER);
  }

  /**
   * Formats the same recovery content used by {@link #logRecovery} as a single multi-line string
   * suitable for exception messages (no banner lines). Includes the same non-empty {@link #SPACER}
   * between regenerate and skip blocks so the visual gap matches the log output.
   *
   * @param intentionalLead lead sentence for intentional edits
   * @param regenerateCommand example Maven command to re-seal (already indented)
   * @param skipLead lead sentence before skip properties
   * @return multi-line recovery text
   */
  static String formatRecovery(String intentionalLead, String regenerateCommand, String skipLead) {
    return intentionalLead
        + "\n"
        + regenerateCommand
        + "\n"
        + SPACER
        + "\n"
        + skipLead
        + "\n"
        + "  -D"
        + SKIP_PROPERTY
        + "=true"
        + "\n"
        + "  (also accepted: -D"
        + SKIP_PROPERTY_ALT
        + "=true)";
  }
}
