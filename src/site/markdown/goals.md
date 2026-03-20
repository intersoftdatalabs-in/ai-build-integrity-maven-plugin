# Goals

The plugin binds to standard Maven lifecycles seamlessly.

## `ai-build-integrity:generate-hashes`

Walks the project base directory and captures cryptographic fingerprints of every file matching the include patterns.

* **Default phase:** `validate`
* **Default includes:** `**/*.md`
* **Default exclusions:** `**/*.sha256` (and other hash extensions)
* **Default bypasses:** `target`, `.git`, `node_modules`, `.tmp`

## `ai-build-integrity:verify-hashes`

Reads the cryptographic ledger generated at T=0, recomputes the hashes for every source file, and asserts equality.

By default, if any mismatch is detected, the plugin **fails the build immediately**.

* **Default phase:** `test`
* **Auditing mode:** Use `<failOnError>false</failOnError>` to suppress the build-failure and instead emit severe warnings for SIEM processing.
* **Reporting:** Use `<generateAuditReport>true</generateAuditReport>` to push the output validation graph to `target/ai-integrity-report.json`.

## `ai-build-integrity:clean-hashes`

Safely deletes the cryptographic ledger (or scattered sidecar files if using legacy `SIDECAR` mode).

* **Default phase:** `clean`

