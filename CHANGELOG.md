# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.12.0] - 2026-07-10

### Added

- **Reactor-scoped hash generation**: On partial multi-module builds (`-pl`, child-module builds against a multi-module `baseDir`), `generate-hashes` walks only the selected module seal roots instead of rehashing the entire reactor tree. Full reactor builds still seal the full configured `baseDir`.
- **`ai.integrity.reactorScope`**: `AUTO` (default), `FULL`, or `REACTOR` to control full-tree vs reactor-scoped sealing.
- **CENTRAL ledger merge on partial seals**: Existing out-of-scope ledger entries are preserved; only paths under seal roots are refreshed.
- Design mini-spec: `docs/superpowers/specs/2026-07-10-reactor-scoped-hash-generation-design.md`.

## [0.11.1] - 2026-07-01

### Fixed

- **Windows Path Comparison**: Fixed cross-platform path comparison in `validateArtifactPath()` that caused path traversal validation to fail on Windows due to hardcoded `/` separators in string-based path comparison.

## [0.11.0] - 2026-06-28

### Added

- **Artifact Digests**: New mojos for generating, verifying, and cleaning cryptographic digests for build artifacts (JARs, WARs, ZIPs).
  - `generate-artifact-digests` - Generates SHA-256/384/512 digests at `package` phase
  - `verify-artifact-digests` - Verifies artifact integrity at `verify` phase
  - `clean-artifact-digests` - Removes digest files at `clean` phase
  - Streaming-only hash computation (never loads full files into memory)
  - Path traversal protection via canonical path validation
  - Support for `CENTRAL` and `SIDECAR` storage modes
  - Optional aggregate digest for quick nightly verification
  - Warnings for compromised algorithms (MD5, SHA-1)

### Changed

- **Build Tooling**: Spotless Maven plugin now requires Java 11+; use Java 21 for builds while maintaining Java 8 target compatibility.

### Added

- **Resume-Safe Hash Regeneration**: When resuming a failed multi-module build with `-rf :module-name`, the plugin now regenerates hashes only for the resuming module, allowing intentional edits made during the failed build to pass verification without triggering tamper detection.
- **Javadoc & JaCoCo Reports**: Integrated API documentation and visual test coverage reports into the Maven site.
- **Reference Documentation**: Added a formal specification for all plugin inputs (configuration) and outputs (ledger/audit reports).
- **Maintenance Policy**: Formally documented the project's maintenance and update schedule in `CONTRIBUTING.md`.
- **English Language Disclosure**: Added explicit commitment to providing documentation and accepting reports/comments in English.
- **Interim Review Process**: Documented the use of feature branches and Pull Requests for transparent, iterative development.
- **Badges**: Added Build Status, Test Coverage (Codecov), and OpenSSF Best Practices badges to the `README.md`.

### Changed

- **Java Compatibility**: Updated minimum JDK requirement from 11 to 8, replacing Java 11+
  APIs (`String.strip()`, `Files.writeString()`, `Files.readString()`, `Set.of()`) with
  Java 8 equivalents (`String.trim()`, `Files.write()`/`Files.readAllBytes()`, `new HashSet<>()`).
- **Maven Compatibility**: Updated minimum Maven prerequisite from 3.9.0 to 3.8.0 and
  aligned Maven API dependencies (`maven-plugin-api`, `maven-core`) from 3.9.16 to 3.8.8.
- **Audit Reports**: Standardized the `file` field in JSON audit reports to use relative paths consistently across both `CENTRAL` and `SIDECAR` modes.
- **Documentation**: Renamed all instances of "monorepo" to "multi-module project" for better alignment with Maven terminology.

## [0.9.0] - 2026-03-20

### Added

- Initial public release to Maven Central.
- `generate-hashes`, `verify-hashes`, and `clean-hashes` mojos.
- Support for `CENTRAL` and `SIDECAR` hash storage modes.
- Cross-OS line ending normalization.
- .gitignore awareness and automatic directory pruning.
- Machine-readable JSON audit reports for SIEM integration.

[0.12.0]: https://github.com/intersoftdatalabs-in/ai-build-integrity-maven-plugin/compare/v0.11.1...HEAD
[0.11.1]: https://github.com/intersoftdatalabs-in/ai-build-integrity-maven-plugin/compare/v0.11.0...v0.11.1
[0.11.0]: https://github.com/intersoftdatalabs-in/ai-build-integrity-maven-plugin/compare/v0.10.0...v0.11.0
[0.10.0]: https://github.com/intersoftdatalabs-in/ai-build-integrity-maven-plugin/compare/v0.9.0...v0.10.0
[0.9.0]: https://github.com/intersoftdatalabs-in/ai-build-integrity-maven-plugin/releases/tag/v0.9.0

