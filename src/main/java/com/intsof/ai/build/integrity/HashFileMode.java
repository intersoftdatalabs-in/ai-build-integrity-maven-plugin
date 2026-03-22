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
