# Troubleshooting

If the `ai-build-integrity-maven-plugin` fails your build, it means the plugin has successfully detected a condition where an AI instruction file was modified, tampered with, or appears to be missing.

This page covers the most common failures developers encounter and how to resolve them.

## Common Errors

### `HASH MISMATCH: [file] - file may have been tampered with!`

**Why it happened:**
The cryptographic hash of the file listed in the error output no longer matches the previously generated signature in the central ledger (or `.sha256` sidecar).

**How to fix it:**
1. **Did you legitimately edit the file?**
If you intentionally modified the file (e.g., updating a system prompt in `AGENTS.md`), the old hash seal is now instantly invalid. You must re-sign the file by generating a new hash:

```bash
# From the project root, resign all files:
mvn validate
```

2. **Did a formatting tool rewrite the file?**
   If a plugin like `spotless:apply` rewrote the Markdown layout, injected a license header, or altered line-endings mid-build, the hash will change and verification will fail. Ensure your formatters are configured to run *before* `generate-hashes` in the Maven lifecycle (see [Usage](./usage.html)).

3. **Was the file actually tampered with mid-build?**
   If you did not intentionally edit the file and no formatter ran, congratulations—the plugin just stopped a potential Time-of-Check-to-Time-of-Use (TOCTOU) pipeline tampering attack! This means an AI agent, rogue script, or attacker modified the file *after* the build started but *before* it was verified.

---

### `Source file missing for hash ...`

**Why it happened:**
An orphaned signature exists in the ledger, but the original source file (e.g. `SKILL.md`) was deleted or renamed by a developer mid-build.

**How to fix it:**
If you legitimately deleted the source file, simply clean your build directory to drop the old ledger:

```bash
mvn clean
```

---

### `Central hash file not found` / `No hash files found` (Warning)

**Why it happened:**
The `verify-hashes` goal executed, but it couldn't find the `target/ai-integrity.sha256` ledger. This is usually just a warning, and the build proceeds successfully.

**How to fix it:**
1. Did you forget to run `generate-hashes`? If building locally skipping the `initialize` phase, you may just need to generate the initial hashes.
2. Are you using `-pl` to target a child module from the root directory? If the parent module never executed `generate-hashes`, the child module won't find the global ledger. This is expected local development behavior.
