# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- **Resume-Safe Hash Regeneration**: When resuming a failed multi-module build with `-rf :module-name`, the plugin now regenerates hashes only for the resuming module, allowing intentional edits made during the failed build to pass verification without triggering tamper detection.
- **Javadoc & JaCoCo Reports**: Integrated API documentation and visual test coverage reports into the Maven site.
- **Reference Documentation**: Added a formal specification for all plugin inputs (configuration) and outputs (ledger/audit reports).
- **Maintenance Policy**: Formally documented the project's maintenance and update schedule in `CONTRIBUTING.md`.
- **English Language Disclosure**: Added explicit commitment to providing documentation and accepting reports/comments in English.
- **Interim Review Process**: Documented the use of feature branches and Pull Requests for transparent, iterative development.
- **Badges**: Added Build Status, Test Coverage (Codecov), and OpenSSF Best Practices badges to the `README.md`.

### Changed

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

[Unreleased]: https://github.com/intersoftdatalabs-in/ai-build-integrity-maven-plugin/compare/v0.9.0...HEAD
[0.9.0]: https://github.com/intersoftdatalabs-in/ai-build-integrity-maven-plugin/releases/tag/v0.9.0

