# Troubleshooting

If the `ai-build-integrity-maven-plugin` fails your build, it means the plugin has successfully detected a condition where an AI instruction file was modified, tampered with, or appears to be missing.

This page covers the most common failures developers encounter and how to resolve them.

## Common Errors

### `Hash verification FAILED: 1 file(s) have been modified or tampered with!`

**Why it happened:**
The cryptographic hash of the file listed in the error output no longer matches the `.sha256` (or `.sha512`) sidecar file that was generated previously.

**How to fix it:**
1. **Did you legitimately edit the file?**
If you intentionally modified the file (e.g., updating a system prompt in `AGENTS.md`), the old hash seal is now instantly invalid. You must re-sign the file by generating a new hash and committing **both** the updated `.md` file and the new `.sha256` file to Git:

```bash
# From the project root, resign all files:
mvn ai-build-integrity:generate-hashes
```

*(If you are developing inside a local module in a large Monorepo, you can target just your module by appending `-pl your-module`).*

2. **Did a formatting tool rewrite the file?**
   If a plugin like `spotless:apply` rewrote the Markdown layout, injected a license header, or altered line-endings mid-build, the hash will change and verification will fail. Ensure your formatters are configured to run *before* `generate-hashes` in the Maven lifecycle (see [Usage](./usage.html)). Alternatively, run your formatters manually followed by `mvn ai-build-integrity:generate-hashes` locally before committing.

3. **Was the file actually tampered with mid-build?**
   If you did not intentionally edit the file and no formatter ran, congratulations—the plugin just stopped a potential Time-of-Check-to-Time-of-Use (TOCTOU) pipeline tampering attack! This means an AI agent, rogue script, or attacker modified the file *after* the build started but *before* it was verified.

   **How to identify the culprit:**
   * **Check the Timestamp (`mtime`):** Run `stat AGENTS.md` (or `ls -l --time-style=full-iso` on Linux/macOS) to get the exact millisecond the file was modified. Cross-reference this timestamp with your CI/CD or Maven build logs to see exactly which process, agent, or Maven plugin was executing at that instant.
   * **Inspect the Payload (`git diff`):** Run `git diff` to see exactly what was injected into the instruction file. The nature of the payload (e.g., an automated formatting header, a specific AI agent's prompt injection, or a malicious command) will often make the culprit obvious.
   * **System Auditing:** If the tampering is intermittent and you still cannot track down the agent, you can use OS-level file monitoring (like `auditd` or `inotifywait` on Linux, or `fs_usage` on macOS) to log the exact Process ID (PID) that modifies the file during the build runner's execution.

---

### `FAILED: Source file missing for hash ...`

**Why it happened:**
An orphaned `.sha256` companion file exists in the directory, but the original source file (e.g. `SKILL.md`) was deleted or renamed by a developer.

**How to fix it:**
If you legitimately deleted the source file, simply delete the orphaned `.sha256` file as well. Alternatively, you can have the plugin automatically clean up all hashes in the repository by running:

```bash
mvn clean
# OR implicitly directly:
mvn ai-build-integrity:clean-hashes
```

---

### `No hash files found to verify` (Warning)

**Why it happened:**
The `verify-hashes` goal executed, but it couldn't find any `.sha256` files in the scoped directory. This is usually just a warning, and the build proceeds successfully.

**How to fix it:**
1. Did you forget to run `generate-hashes`? If building locally, you may just need to generate the initial hashes.
2. Are you using `-pl` to target a child module from the root directory? If the parent module never executed `generate-hashes` and the child doesn't have any checked in, the child module won't find any hashes. This is normal local development behavior.
