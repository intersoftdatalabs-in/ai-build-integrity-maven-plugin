package com.intsof.ai.build.integrity;

/** Defines the strategy for storing generated file hashes. */
public enum HashFileMode {
  /** Hashes are saved adjacent to the source file as hidden sidecars (e.g., AGENTS.md.sha256). */
  SIDECAR,

  /**
   * All generated hashes are aggregated into a single centralized ledger inside the target/
   * directory (e.g., target/ai-integrity.sha256).
   */
  CENTRAL
}
