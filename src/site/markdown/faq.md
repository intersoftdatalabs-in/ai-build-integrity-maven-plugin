# Frequently Asked Questions

The AI Build Integrity Maven Plugin answers questions across both security posture constraints and multi-OS developer productivity problems.

### Should I check the `.sha` hash files into Git?

**It depends entirely on your `hashFileMode` configuration and workflow constraints.**

- If you use **`<hashFileMode>CENTRAL</hashFileMode>` (Recommended)**, the plugin compiles all hashes down into the `target/ai-integrity.sha256` output. **Do not check this into Git**, because the `target/` directory is ephemeral and is already typically ignored. The plugin natively generates this on-the-fly when builds start downstream.
- If you use **`<hashFileMode>SIDECAR</hashFileMode>`**, the plugin generates hidden files scattered perfectly alongside your source files (e.g. `.SKILL.md.sha256`).
  - **Yes, check them into Git** if you want to mathematically verify that the repository was not tampered with on the developer's client OS *before* Maven started.
  - **No, do not check them into Git** if you only care about protecting the integrity of the build pipeline *during and after* the Maven run. Git constantly changes byte representation dynamically (via OS line-endings or attribute resets), which will invalidate client-side hashes if they differ from the originating machine.

If you don't track sidecars in Git, add the following to your root `.gitignore`:

```
# Ignore local sidecar files
.*.sha256
.*.sha384
.*.sha512
```

### Git line endings (`\r\n` vs `\n`) are randomly failing the validation locally! What do I do?

Git performs intense native modifications to bytes simply by pulling code across Windows vs Mac architectures due to line-endings differences. The cryptographic hashing system sees this as tampering!

To solve this, add `<normalizeLineEndings>true</normalizeLineEndings>` to both the `generate-hashes` and `verify-hashes` configuration blocks. This will tell the plugin to natively load the code as a UTF-8 String, sanitize all Windows `\r\n` to Linux `\n`, convert it back to bytes, and hash *that payload*. This guarantees that your hashes will always match cross-os!

### My Spotless/Prettier Auto-Formatter plugin is failing the build!

Because the plugin hashes the exact bytes on disk, **proper Maven execution ordering is vital.**

If you are using an auto-formatter like `spotless:apply` that runs on `process-sources` and routinely rewrites protected text bytes dynamically during the build pipeline, the plugin validation run during the `test` phase will correctly identify the modification and shut down the build!

You **must** move your auto-formatter plugin higher up in the execution order sequence in your `pom.xml`, or trigger the formatter gracefully before `ai-build-integrity` runs.

### How do I protect files (`.env`) that are already inside `.gitignore`?

By default, enabling `<gitignoreAutoExclude>true</gitignoreAutoExclude>` stops the plugin from hashing files that Git already ignores, which natively sweeps up massive directories like `node_modules`.

However, some configuration files are strictly Git-ignored but are fiercely critical to build protection on CI runners (like a proprietary `.env` or `secret.yaml`). You can force the scanner to inspect these dynamically by injecting an inclusion overlay:

```xml
<configuration>
    <gitignoreAutoExclude>true</gitignoreAutoExclude>
    <forceIncludes>src/main/resources/.env*</forceIncludes>
</configuration>
```

