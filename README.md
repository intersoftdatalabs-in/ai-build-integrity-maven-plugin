<div align="center">
  <h1>🛡️ AI Build Integrity Maven Plugin</h1>
  <p><b>Zero-Trust Security for AI-Assisted Software Development</b></p>

[![Maven Central](https://img.shields.io/maven-central/v/com.intsof/ai-build-integrity-maven-plugin)](https://central.sonatype.com/artifact/com.intsof/ai-build-integrity-maven-plugin)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

</div>

---

If your repository contains `AGENTS.md`, `SKILLS.md`, or AI instruction files, those files natively control what AI agents are allowed to do with your codebase during development, CI/CD, and production operations.

**What happens if an attacker modifies those instructions during your build?**

The AI Build Integrity plugin solves this problem by applying a **cryptographic, tamper-evident seal** to your source code instructions the exact millisecond your build begins.

## 🚀 For Developers: Zero-Friction Integrity

We know you hate plugins that slow down your build or litter your workspace with garbage files.
- **Blazing Fast:** Written with raw NIO `Files.walkFileTree` and a 64KiB streaming buffer. It recursively seals a 500-module monorepo in milliseconds.
- **Zero Pollution:** Uses a clean, centralized ledger inside your `target/` directory instead of vomiting `.sha` sidecar files all over your pristine source tree.
- **Cross-OS Native:** Automatically sanitizes Windows/Linux line-endings (`\r\n` -> `\n`) in-memory, ensuring Mac and Windows developers generate identical cryptographic fingerprints.
- **Opt-Out Any Time:** Need to quickly iterate locally? Just run `mvn install -Dai.integrity.skip=true` to skip protections seamlessly.

## 🔒 For Security Teams: Automated Compliance

Integrate Dev-Sec-Ops seamlessly without becoming a blocker for your engineering teams.
- **SIEM Ready:** The plugin automatically emits a JSON "Bill of Materials" Audit Report (`ai-integrity-report.json`) detailing the verified state of every single file in the artifact. Ingest this report natively into Splunk, DataDog, or your preferred SIEM.
- **Soft-Fail Rollouts:** Deploy the plugin globally to thousands of repositories in "Auditing Mode" (`failOnError=false`). You'll receive red-alert logs when tampering occurs, but the builds will safely continue until you are ready to enforce hard-blocking.
- **GitIgnore Aware:** Automatically respects downstream `.gitignore` rules, preventing accidental security breaches in temporary or ignored sub-directories.

---

## ⚡ Quick Start

For a typical enterprise repository, simply attach the plugin without complex XML configurations. The plugin uses secure default timings to lockdown your repo at `initialize` and verify hashes at `test`.

```xml
<plugin>
    <groupId>com.intsof</groupId>
    <artifactId>ai-build-integrity-maven-plugin</artifactId>
    <version>0.9.0-SNAPSHOT</version>
    <configuration>
        <!-- Use a centralized ledger to avoid source-tree pollution -->
        <hashFileMode>CENTRAL</hashFileMode>
    </configuration>
    <executions>
        <execution>
            <goals>
                <!-- Lock the repo down at T=0 -->
                <goal>generate-hashes</goal>
                <!-- Verify integrity just before packaging -->
                <goal>verify-hashes</goal>
                <!-- Safely delete the ledger when cleaning -->
                <goal>clean-hashes</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

---

## 📚 Documentation & Guides

The AI Build Integrity Plugin handles everything from massive Monorepos to complex `spotless:apply` interactions gracefully.

- **[Usage Guide](src/site/markdown/usage.md):** Detailed setup for Single-Module and Monorepos (Parent POMs).
- **[FAQ](src/site/markdown/faq.md):** Common questions regarding Git check-ins, formatting plugins, and best practices.
- **[Troubleshooting](src/site/markdown/troubleshooting.md):** Solutions for common errors, skipped subtrees, and execution ordering.

---

## 🤝 Community & Support

- 🐛 **Have an issue or found a bug?** [Open an issue on GitHub](https://github.com/intersoftdatalabs-in/ai-build-integrity-maven-plugin/issues)
- 💡 **Want to contribute?** We'd love your help! Check out our [Contributor Guide](CONTRIBUTING.md)
- 🛡️ **Found a security vulnerability?** Please read our [Security Policy](SECURITY.md) for responsible disclosure.

