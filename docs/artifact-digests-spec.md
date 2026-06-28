# Artifact Digests Feature — Plan & Specification

**Project:** ai-build-integrity-maven-plugin
**Author:** Daedalus (The Architect) — Technical Vision & System Design; reviewed by Hephaestus, DevOps, Security
**Date:** 2026-06-28
**Status:** APPROVED — Ready for Implementation Handoff
**Version:** 3 (Final)

---

## 1. Problem Statement

The ai-build-integrity-maven-plugin currently generates cryptographic hashes for **AI instruction files** (AGENTS.md, SKILL.md, etc.) to detect tampering after a build begins. However, the ROADMAP.md identifies a short-term planned feature: generating integrity hashes for **final build artifacts** (JARs, WARs, ZIPs) in addition to source resources.

Users need a way to generate artifact digests (SHA-256, SHA-512, etc.) for:
- **Supply-chain integrity**: Ensuring shipped artifacts match what was built and verified
- **Reproducible builds**: Verifying artifact integrity across environments
- **CI/CD gates**: Blocking deployment of tampered or unverified artifacts
- **Dependency verification**: Allowing downstream consumers to verify artifact checksums

This feature aligns with what the [checksum-maven-plugin](https://github.com/nicoulaj/checksum-maven-plugin) provides, but with:
1. Java 8 OpenJDK compatibility (no compromised or unavailable algorithms)
2. Integration with the existing ai-build-integrity ledger and reporting framework
3. Unified plugin experience for both instruction-file hashing and artifact hashing

---

## 2. Scope

### In Scope

- New Maven goal: `generate-artifact-digests` — generates digest files for build artifacts
- New Maven goal: `verify-artifact-digests` — verifies artifact digests against a stored ledger
- New Maven goal: `clean-artifact-digests` — removes generated digest files
- Support for SHA-256, SHA-384, SHA-512 (the three algorithms available in Java 8 OpenJDK's `MessageDigest`)
- Integration with the existing `CENTRAL` ledger architecture for multi-module projects
- Integration with the existing audit report JSON format for SIEM ingestion
- Filter out compromised algorithms (MD5, SHA-1) by default with explicit opt-in

### Out of Scope

- Sigstore integration (documented separately in ROADMAP.md as medium-term)
- Native signature generation (GPG/ASCII-armored signatures) — this is separate from checksums
- Graphical Maven Site reports (documented separately in ROADMAP.md)
- Support for algorithms not available in Java 8 OpenJDK (BLAKE2, SHA-3/KECCAK, GOST, RIPEMD, SKEIN, SM3, TIGER, WHIRLPOOL)

---

## 3. Algorithm Support — Java 8 OpenJDK Constraints

### Available in Java 8 OpenJDK (`java.security.MessageDigest`)

|  Algorithm  |                      Notes                       |
|-------------|--------------------------------------------------|
| **SHA-256** | Recommended default. No known practical attacks. |
| **SHA-384** | Recommended for high-security environments.      |
| **SHA-512** | Recommended for high-security environments.      |

### Excluded Algorithms

|       Algorithm       |                                   Reason for Exclusion                                   |
|-----------------------|------------------------------------------------------------------------------------------|
| MD5                   | **Compromised.** Vulnerable to collision attacks. Do not use for security.               |
| SHA-1                 | **Compromised.** Deprecated by NIST, vulnerable to collision attacks.                    |
| MD2                   | Deprecated, vulnerable                                                                   |
| BLAKE2B/BLAKE3        | Not available in Java 8 built-in providers                                               |
| SHA-3 / KECCAK        | Not available in Java 8 built-in providers (SHA-3 added in Java 9)                       |
| GOST3411              | Not available in standard Java 8 providers                                               |
| RIPEMD128/160/256/320 | Not available in standard Java 8 providers                                               |
| SKEIN                 | Not available in standard Java 8 providers                                               |
| SM3                   | Not available in standard Java 8 providers                                               |
| TIGER                 | Not available in standard Java 8 providers                                               |
| WHIRLPOOL             | Not available in standard Java 8 providers                                               |
| CRC32                 | Not a cryptographic hash; available via `java.util.zip.CRC32` but not in `MessageDigest` |

### Algorithm Recommendations

1. **SHA-256** (default) — Best balance of speed and security for most use cases
2. **SHA-512** — Preferred for artifacts requiring long-term integrity guarantees (64-byte block size is more resistant to length-extension attacks)
3. **SHA-384** — A viable alternative when SHA-512 output size is a concern

---

## 4. Proposed Maven Goals

### 4.1 `generate-artifact-digests`

**Phase Binding:** `package` (after artifacts are created)

Generates digest files for build artifacts produced by the project.

|          Parameter           |   Type   |                          Default                           |                                               Description                                               |
|------------------------------|----------|------------------------------------------------------------|---------------------------------------------------------------------------------------------------------|
| `artifactIncludes`           | String   | `**/*.jar,**/*.war,**/*.zip`                               | Comma-separated glob patterns for artifacts to hash                                                     |
| `artifactExcludes`           | String   | `**/*-sources.jar,**/*-javadoc.jar`                        | Patterns to exclude                                                                                     |
| `includeAttachedArtifacts`   | boolean  | `false`                                                    | If `true`, include attached artifacts (sources, javadoc, test jars). Default is primary artifacts only. |
| `algorithms`                 | String[] | `["SHA-256"]`                                              | Array of algorithms to compute. Supports SHA-256, SHA-384, SHA-512, and opt-in MD5, SHA-1.              |
| `hashFileMode`               | Enum     | `SIDECAR`                                                  | `SIDECAR` (`.jar.sha256`) or `CENTRAL` (ledger file)                                                    |
| `centralDigestFile`          | String   | `${project.build.directory}/ai-integrity-artifacts.sha256` | Path to central ledger                                                                                  |
| `outputEncoding`             | String   | `UTF-8`                                                    | Encoding for digest files                                                                               |
| `generateAggregateDigest`    | boolean  | `false`                                                    | If `true`, compute a SHA-256 hash of all artifact digests for quick nightly verification.               |
| `warnOnCompromisedAlgorithm` | boolean  | `true`                                                     | If `true`, emit a build WARNING when MD5 or SHA-1 is used. Recommended to keep enabled.                 |
| `skip`                       | boolean  | `false`                                                    | Skip this goal entirely                                                                                 |

**Output Format (sidecar):**

```
# For each artifact foo.jar, create foo.jar.sha256 containing:
<sha256-hex-digest>  foo.jar
```

**Output Format (central ledger):**

```
<sha256-hex-digest>  path/to/foo.jar
<sha256-hex-digest>  path/to/bar.war
```

### 4.2 `verify-artifact-digests`

**Phase Binding:** `verify` (before artifact deployment)

Re-computes artifact digests and compares against the stored ledger.

|       Parameter       |  Type   |                             Default                             |       Description       |
|-----------------------|---------|-----------------------------------------------------------------|-------------------------|
| `failOnError`         | boolean | `true`                                                          | Block build on mismatch |
| `generateAuditReport` | boolean | `true`                                                          | Emit JSON audit report  |
| `centralReportFile`   | String  | `${project.build.directory}/ai-integrity-artifacts-report.json` | Path to audit report    |

### 4.3 `clean-artifact-digests`

**Phase Binding:** `clean` (each module)

Removes generated digest files and aggregate digests from each module's `target/` directory.

|        Parameter        |  Type   | Default |                                        Description                                        |
|-------------------------|---------|---------|-------------------------------------------------------------------------------------------|
| `artifactDigestsClean`  | boolean | `true`  | Enable cleaning                                                                           |
| `cleanAggregateDigests` | boolean | `true`  | If `true`, also remove aggregate digest files (`ai-integrity-artifacts-aggregate.sha256`) |

---

## 5. Multi-Module Project Architecture

For Maven reactor builds, artifact digest generation must occur **once per module** (at `package` phase), but verification should occur **before deployment** (at `verify` phase).

|            Goal             |      Scope      |   Phase   |                      Purpose                       |
|-----------------------------|-----------------|-----------|----------------------------------------------------|
| `generate-artifact-digests` | Each module     | `package` | Hash the module's artifacts                        |
| `verify-artifact-digests`   | Each module     | `verify`  | Ensure artifacts haven't changed                   |
| `clean-artifact-digests`    | **Each module** | `clean`   | Remove all digest files from each module's target/ |

> **Note on verification scope:** `verify-artifact-digests` is a **per-module self-check**. It verifies that the artifacts produced by each specific module have not been tampered with since packaging. It is **not** a global reactor integrity gate. For a global gate, activate `verify-artifact-digests` in a dedicated verification build profile after the full reactor completes.

### Central Ledger for Multi-Module

When `hashFileMode=CENTRAL`, each module writes to a shared ledger in the reactor root:

```xml
<configuration>
    <hashFileMode>CENTRAL</hashFileMode>
    <centralDigestFile>${maven.multiModuleProjectDirectory}/target/ai-integrity-artifacts.sha256</centralDigestFile>
</configuration>
```

#### Central Ledger Concurrency (Critical)

**Warning:** `hashFileMode=CENTRAL` is **not safe for parallel Maven builds** (`-T` flag) without the following guard:

- Each module writes its entries to a **module-scoped temp file** first
- Entries are then **appended** to the central ledger via a **synchronized block** (JVM-level lock on a well-known object) or **file-based lock**
- Alternatively, use an **atomic rename** pattern: write to `${centralDigestFile}.tmp.${moduleName}`, then rename over the central file (requires the central file to support multi-line appends atomically)
- The implementation must handle `IOException` gracefully if concurrent writes cause race conditions

For **parallel Maven builds**, prefer `hashFileMode=SIDECAR` which writes per-artifact sidecar files with no concurrency risk.

---

## 6. Integration with Existing Framework

### 6.1 Reuse of Existing Components

The new artifact digest goals will reuse:

- **`HashUtils`** — Already handles streaming hash computation with 64 KiB buffers
- **`GitIgnoreAwareFileVisitor`** — Not needed for artifacts (we operate on explicit artifact paths, not file walking)
- **`HashFileMode` enum** — Reused for `SIDECAR` vs `CENTRAL` behavior
- **Audit report JSON format** — Extend existing `ai-integrity-report.json` schema or create separate `ai-integrity-artifacts-report.json`

### 6.2 New Components Required

1. **`ArtifactDigestsGeneratorMojo`** — Scans `project.getBuild().getDirectory()` for artifacts matching `artifactIncludes`, computes digests
2. **`ArtifactDigestsVerifyMojo`** — Re-computes digests and compares against ledger
3. **`ArtifactDigestsCleanMojo`** — Cleanup of digest files
4. **`ArtifactDigestsUtils`** — Utility for artifact discovery and file writing (may reuse `HashUtils`)

### 6.3 Parallel Execution with Existing Goals

The existing `generate-hashes` goal runs at `VALIDATE` phase (before compilation). The new `generate-artifact-digests` goal runs at `PACKAGE` phase (after artifacts are created). These are orthogonal and can execute in the same build without conflict.

```
VALIDATE → generate-hashes (AI instruction files)
...
PACKAGE  → generate-artifact-digests (JARs, WARs, ZIPs)
VERIFY   → verify-artifact-digests (before deployment)
```

---

## 7. Security Considerations

1. **Algorithm choice**: Default to SHA-256. MD5 and SHA-1 are intentionally excluded due to known vulnerabilities.
2. **Line-ending normalization**: Not applicable to binary artifacts — disable normalization for artifact hashing. The implementation must use a **streaming-only path** that never loads an artifact file into memory in its entirety. `HashUtils.computeHash()` with `normalizeLineEndings=true` loads entire files into heap — this must be avoided for binary artifacts.
3. **Path traversal**: Artifact paths must be validated using **canonical path enforcement**:
   - Use `Path.toRealPath(LinkOption.NOFOLLOW_LINKS)` to resolve the artifact path without following symlinks, then verify the result is a descendant of `${project.build.directory}`
   - If the path is a symlink, use `Files.readSymbolicLink()` to inspect the link target, then verify the resolved link target is also a descendant of `${project.build.directory}`
   - Reject any artifact whose canonical path escapes the build directory
   - On IO exception during path resolution, reject the artifact and log an error
   - **Do NOT use `toRealPath()` with symlink-following** as the primary check — the descendant check after resolution is what catches traversal, but following symlinks during resolution can expose TOCTOU race conditions between check and use
4. **Runtime algorithm availability check**: The implementation must verify algorithm availability via `MessageDigest.getInstance(algorithm)` and fail fast with a clear error message if the algorithm is not available in the JVM.
5. **Compromised algorithm guardrails**: When MD5 or SHA-1 is explicitly enabled:
   - Emit a build **WARNING** (not just info) that these algorithms are compromised
   - Document clearly that MD5/SHA-1 digests are for **corruption detection only**, not tampering detection or supply-chain integrity
   - The `failOnError` parameter applies equally to all algorithms; a mismatch on MD5/SHA-1 is still a blocking failure if `failOnError=true`
6. **Audit report integrity**: Report tampering is **out of scope** for this iteration. The report is written to disk before SIEM ingestion. Future iterations may address report signing or direct SIEM push. Users should configure CI/CD to write the report to a write-only location immediately after generation.
7. **Secrets in artifact names**: Artifact filenames are derived from Maven coordinates. Users are responsible for ensuring artifact filenames do not contain secrets, as digests appear in ledger files. The plugin does not scrub filenames.

---

## 8. Backward Compatibility

- Existing configurations for `generate-hashes`, `verify-hashes`, `clean-hashes` remain unchanged.
- New goals are opt-in; existing users are not affected.
- No breaking changes to the existing ledger format.

---

## 9. Deliverables

|              Artifact               |                               Description                                |
|-------------------------------------|--------------------------------------------------------------------------|
| `ArtifactDigestsGeneratorMojo.java` | New Mojo for generating artifact digests                                 |
| `ArtifactDigestsVerifyMojo.java`    | New Mojo for verifying artifact digests                                  |
| `ArtifactDigestsCleanMojo.java`     | New Mojo for cleaning artifact digests                                   |
| `ArtifactDigestsUtils.java`         | Utility class for artifact discovery; exposes streaming-only hash method |
| `ArtifactDigestsIT.java`            | Integration test with real Maven project                                 |
| `docs/artifact-digests-spec.md`     | This document                                                            |
| Update `ARCHITECTURE.md`            | Add new components to architecture diagram                               |
| Update `ROADMAP.md`                 | Mark "Shipped Artifacts Ledger" as in-progress                           |
| Update `usage.md`                   | Document new goals and configuration                                     |

---

## 10. Audit Report JSON Schema

The `verify-artifact-digests` goal emits `ai-integrity-artifacts-report.json` for SIEM ingestion.

```json
{
  "schemaVersion": "1.0",
  "reportType": "artifact-integrity",
  "generatedAt": "2026-06-28T14:30:00Z",
  "generator": "ai-build-integrity-maven-plugin",
  "buildContext": {
    "projectName": "${project.name}",
    "projectVersion": "${project.version}",
    "buildTimestamp": "${build.timestamp}",
    "reactorPath": "${maven.multiModuleProjectDirectory}"
  },
  "summary": {
    "totalArtifacts": 5,
    "verified": 5,
    "failed": 0,
    "skipped": 0
  },
  "artifacts": [
    {
      "artifactPath": "target/myapp-1.0.0.jar",
      "algorithms": ["SHA-256"],
      "digests": {
        "SHA-256": "abc123..."
      },
      "status": "VERIFIED",
      "verifiedAt": "2026-06-28T14:30:01Z"
    }
  ],
  "errors": []
}
```

|             Field             |   Type   | Required |                  Description                   |
|-------------------------------|----------|----------|------------------------------------------------|
| `schemaVersion`               | String   | Yes      | Schema version for SIEM pipeline compatibility |
| `reportType`                  | String   | Yes      | Always `artifact-integrity` for this report    |
| `generatedAt`                 | ISO-8601 | Yes      | Timestamp with timezone (UTC preferred)        |
| `generator`                   | String   | Yes      | Plugin identity and version                    |
| `buildContext.projectName`    | String   | Yes      | Maven `${project.name}`                        |
| `buildContext.projectVersion` | String   | Yes      | Maven `${project.version}`                     |
| `buildContext.buildTimestamp` | String   | No       | Build timestamp if available                   |
| `buildContext.reactorPath`    | String   | No       | Reactor root for multi-module builds           |
| `summary.totalArtifacts`      | Integer  | Yes      | Total artifacts processed                      |
| `summary.verified`            | Integer  | Yes      | Artifacts that passed verification             |
| `summary.failed`              | Integer  | Yes      | Artifacts that failed verification             |
| `artifacts[].artifactPath`    | String   | Yes      | Relative or absolute path to artifact          |
| `artifacts[].algorithms`      | String[] | Yes      | Algorithms computed for this artifact          |
| `artifacts[].digests`         | Object   | Yes      | Map of algorithm → hex digest                  |
| `artifacts[].status`          | String   | Yes      | `VERIFIED`, `FAILED`, `SKIPPED`                |
| `artifacts[].verifiedAt`      | ISO-8601 | Yes      | Per-artifact verification timestamp            |
| `errors[]`                    | Array    | Yes      | Any processing errors (empty if none)          |

---

## 11. Aggregate Digest Specification

When `generateAggregateDigest=true`, the plugin computes a **hash of all artifact digests** for quick nightly verification.

### Computation Method

1. Collect all ledger lines for the current module (sorted by artifact path using `String.CASE_INSENSITIVE_ORDER` for locale-independent determinism)
2. Concatenate all lines as-is (including the digest and filename), with a **trailing newline** after the last line
3. Compute the SHA-256 hash of the concatenated content
4. Output to `${project.build.directory}/ai-integrity-artifacts-aggregate.sha256`

### Output Format

```
<sha256-hex-digest>  aggregate
```

### Sidecar Mode Behavior

When `hashFileMode=SIDECAR`, the aggregate digest is computed by reading all sidecar hash files (`.sha256`, etc.) from the module's artifact directory, extracting the digest lines, sorting by filename, and hashing the concatenated content.

### Behavior

- Aggregate digest is computed **per module**, not across the entire reactor
- For multi-module builds, each module produces its own aggregate digest
- The aggregate digest verifies that **no artifact changed** since generation — it does not identify which artifact changed
- Aggregate digests are **not verified** automatically; they are a manual CI gate tool

---

## 12. References

- [checksum-maven-plugin](https://github.com/nicoulaj/checksum-maven-plugin) — Reference implementation
- [NIST SP 800-131A](https://csrc.nist.gov/pubs/sp/800/131/a/r2/final) — Transitioning to stronger algorithms
- [IETF Hash Function Transparency](https://github.com/ietf-wg-jsonpath/draft-ietf-jsonpath-hash) — Context on hash transparency
- [Java 8 MessageDigest](https://docs.oracle.com/javase/8/docs/technotes/guides/security/crypto/CryptoSpec.html#AlgorithmEx) — Supported algorithms

---

## 13. Decisions (Resolved)

| # |             Question              |                                                                                    Decision                                                                                     |
|---|-----------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1 | Attached vs primary artifacts     | **Configuration option** with primary artifacts only as default. Users can opt to include attached artifacts (sources, javadoc, test jars) via `includeAttachedArtifacts=true`. |
| 2 | Opt-in for compromised algorithms | **Allow explicit opt-in** for MD5 and SHA-1. Users who need legacy system compatibility can explicitly enable these, but they are excluded by default.                          |
| 3 | Separate vs merged audit report   | **Separate report** — `ai-integrity-artifacts-report.json` distinct from `ai-integrity-report.json` (AI instruction report).                                                    |
| 4 | Flattened vs nested output        | **Separate files** (one per artifact per algorithm) — required for standard digest verification workflows.                                                                      |
| 5 | Aggregate digests                 | **Supported as a configuration option** (`generateAggregateDigest=true`) — creates a single hash of all artifact hashes for quick nightly verification.                         |

---

## 14. Review Cycle

|         Reviewer          |         Role          |                               Focus Areas                                |
|---------------------------|-----------------------|--------------------------------------------------------------------------|
| **Hephaestus (TheSmith)** | Implementation        | Algorithm feasibility, Java 8 compatibility, Mojo structure, testability |
| **DevOps**                | Delivery & Operations | Multi-module behavior, CI/CD integration, performance at scale           |
| **Security**              | Security & Trust      | Algorithm soundness, path traversal risks, audit report integrity        |

### Review Status

|  Reviewer  |        Round 1         |    Round 2    | Round 3 (Final) |
|------------|------------------------|---------------|-----------------|
| Hephaestus | ✅ APPROVED (with recs) | ✅ APPROVED    | ✅ **APPROVED**  |
| DevOps     | ⚠️ REQUEST_CHANGES     | ⚠️ STILL OPEN | ✅ **APPROVED**  |
| Security   | ⚠️ REQUEST_CHANGES     | ⚠️ STILL OPEN | ✅ **APPROVED**  |

#### Review History

|  Round  |                 Issues Raised                 |          Resolution          |
|---------|-----------------------------------------------|------------------------------|
| Round 1 | 10 total (Hephaestus 3, DevOps 3, Security 4) | Addressed in Rev 2           |
| Round 2 | 8 total (new issues from Rev 2)               | Addressed in Rev 3           |
| Round 3 | 1 minor editorial (DevOps)                    | Acknowledged — not a blocker |

#### Round 2 Resolutions (carried into final)

|                       Issue                        |     Raised By      |                      Resolution                      |
|----------------------------------------------------|--------------------|------------------------------------------------------|
| JSON typo `" reactorPath"`                         | DevOps             | Fixed — removed leading space                        |
| `generateAggregateDigest` missing from §4.1        | Hephaestus, DevOps | Added to §4.1 parameter table                        |
| Symlink resolution approach incomplete             | DevOps             | Clarified NOFOLLOW_LINKS approach and TOCTOU risk    |
| Aggregate digest CENTRAL mode behavior ambiguous   | DevOps             | Specified per-module, not cross-reactor              |
| `warnOnCompromisedAlgorithm` parameter not exposed | Security           | Added `warnOnCompromisedAlgorithm` parameter to §4.1 |
| Aggregate digest sort order not deterministic      | Security           | Specified `String.CASE_INSENSITIVE_ORDER`            |
| Aggregate digest line termination missing          | Security           | Specified trailing newline required                  |
| Clean scope for aggregate digests missing          | Security           | Added `cleanAggregateDigests` parameter to §4.3      |

---

*This document is a living spec. Update as implementation learnings accumulate.*
