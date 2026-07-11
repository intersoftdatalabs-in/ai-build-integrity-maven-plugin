# Reference: External Interfaces

This document provides technical reference for the inputs (configuration) and outputs (artifacts) of the AI Build Integrity Maven Plugin, serving as the official interface specification.

## 📥 Inputs: Configuration Parameters

All configuration parameters can be passed via the `<configuration>` block in your `pom.xml` or via System Properties (using the `-D` flag).

|       Parameter        |   Type    |             Default             |                                                                                          Description                                                                                          |
|------------------------|-----------|---------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `algorithmBits`        | `int`     | `256`                           | Strength of the cryptographic digest: `256`, `384`, or `512`.                                                                                                                                 |
| `hashFileMode`         | `enum`    | `SIDECAR`                       | `CENTRAL` for a single ledger, or `SIDECAR` for hidden files next to sources.                                                                                                                 |
| `baseDir`              | `String`  | `${project.basedir}`            | Root directory to scan for files.                                                                                                                                                             |
| `includes`             | `String`  | `**/*.md`                       | Comma-separated list of glob patterns to include.                                                                                                                                             |
| `excludes`             | `String`  | (various)                       | Comma-separated list of glob patterns to exclude.                                                                                                                                             |
| `skipDirs`             | `String`  | `target,.git,node_modules,.tmp` | Directories to prune entirely from traversal.                                                                                                                                                 |
| `normalizeLineEndings` | `boolean` | `false`                         | If `true`, normalizes CRLF to LF in-memory before hashing.                                                                                                                                    |
| `failOnError`          | `boolean` | `true`                          | (Verify only) If `false`, build continues on validation failure.                                                                                                                              |
| `generateAuditReport`  | `boolean` | `true`                          | (Verify only) Generates the machine-readable JSON report.                                                                                                                                     |
| `executionRootOnly`    | `boolean` | `false`                         | If `true`, only executes on the root module of a build.                                                                                                                                       |
| `reactorScope`         | `String`  | `AUTO`                          | (`generate-hashes`) `AUTO` seals full tree on full reactors and selected modules on partial builds; `FULL` always walks `baseDir`; `REACTOR` always uses seal roots from the current reactor. |
| `skip`                 | `boolean` | `false`                         | Bypasses all plugin logic.                                                                                                                                                                    |

---

## 📤 Outputs: Generated Artifacts

### 1. Central Hash Ledger (`ai-integrity.sha256`)

When `hashFileMode` is set to `CENTRAL`, a single plain-text ledger is produced.

* **Format**: Standard BSD-style checksum format.
* **Structure**: Each line contains the hex-encoded hash followed by the relative path to the file.
* **Example**:

  ```text
  e3b0c442...8fc1 src/main/resources/AGENTS.md
  c1248421...112a docs/SECURITY.md
  ```

### 2. Audit Report (`ai-integrity-report.json`)

A machine-readable JSON file intended for ingestion into SIEM platforms (Splunk, Datadog) or security dashboards.

* **Location**: `target/ai-integrity-report.json` (Default).
* **Schema**:
  * `timestamp`: ISO-8601 UTC timestamp of the verification execution.
  * `totalChecked`: Total count of files processed.
  * `totalFailed`: Count of files that failed validation.
  * `files`: Array of individual validation entries.
    * `file`: Relative path to the original source file.
    * `status`: One of `VERIFIED`, `TAMPERED`, or `MISSING`.
    * `hash`: The recomputed hex-encoded hash of the file.
* **Example Output**:

  ```json
  {
    "timestamp": "2026-03-21T15:10:00.000Z",
    "totalChecked": 42,
    "totalFailed": 1,
    "files": [
      {
        "file": "AGENTS.md",
        "status": "VERIFIED",
        "hash": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
      },
      {
        "file": "src/main/resources/instructions.md",
        "status": "TAMPERED",
        "hash": "c5dae3d82d5d6d3cbd3c3c4d5e6f7a8b9cad0e1f2031a2f082d72a2b28100818"
      }
    ]
  }
  ```

