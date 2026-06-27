<div align="center">
  <h1>🛡️ AI Build Integrity Maven Plugin</h1>
  <p><b>Zero-Trust Security for AI-Assisted Software Development</b></p>

[![Maven Central](https://img.shields.io/maven-central/v/com.intsof/ai-build-integrity-maven-plugin)](https://central.sonatype.com/artifact/com.intsof/ai-build-integrity-maven-plugin)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Build](https://github.com/intersoftdatalabs-in/ai-build-integrity-maven-plugin/actions/workflows/maven.yml/badge.svg)](https://github.com/intersoftdatalabs-in/ai-build-integrity-maven-plugin/actions/workflows/maven.yml)
[![Test Coverage](https://raw.githubusercontent.com/intersoftdatalabs-in/ai-build-integrity-maven-plugin/badges/jacoco.svg)](https://github.com/intersoftdatalabs-in/ai-build-integrity-maven-plugin/actions/workflows/maven.yml)
[![OpenSSF Best Practices](https://www.bestpractices.dev/projects/12230/badge)](https://www.bestpractices.dev/projects/12230)

</div>

---

If your repository contains `AGENTS.md`, `SKILLS.md`, or AI instruction files, those files natively control what AI agents are allowed to do with your codebase during development, CI/CD, and production operations.

**What happens if an attacker modifies those instructions during your build?**

The AI Build Integrity plugin solves this problem by applying a **cryptographic, tamper-evident seal** to your source code instructions the exact millisecond your build begins.

## 🚀 For Developers: Zero-Friction Integrity

We know you hate plugins that slow down your build or litter your workspace with garbage files.

- **Blazing Fast:** Written with raw NIO `Files.walkFileTree` and a 64KiB streaming buffer. It recursively seals a 500-module multi-module project in milliseconds.
- **Zero Pollution:** Uses a clean, centralized ledger inside your `target/` directory instead of vomiting `.sha` sidecar files all over your pristine source tree.
- **Broad Compatibility:** Requires only Maven 3.8+ and JDK 8+, making it accessible to virtually every existing Maven project without toolchain upgrades.
- **Cross-OS Native:** Automatically sanitizes Windows/Linux line-endings (`\r\n` -> `\n`) in-memory, ensuring Mac and Windows developers generate identical cryptographic fingerprints.
- **Opt-Out Any Time:** Need to quickly iterate locally? Just run `mvn install -Dai.integrity.skip=true` to skip protections seamlessly.

## 🔒 For Security Teams: Automated Compliance

Integrate Dev-Sec-Ops seamlessly without becoming a blocker for your engineering teams.

- **SIEM Ready:** The plugin automatically emits a JSON "Bill of Materials" Audit Report (`ai-integrity-report.json`) detailing the verified state of every single file in the artifact. Ingest this report natively into Splunk, DataDog, or your preferred SIEM.
- **Soft-Fail Rollouts:** Deploy the plugin globally to thousands of repositories in "Auditing Mode" (`failOnError=false`). You'll receive red-alert logs when tampering occurs, but the builds will safely continue until you are ready to enforce hard-blocking.
- **GitIgnore Aware:** Automatically respects downstream `.gitignore` rules, preventing accidental security breaches in temporary or ignored sub-directories.

---

## ⚡ Quick Start

Pick the configuration that matches your project structure.

---

<details open>
<summary><b>📦 Single-Module Project</b></summary>
<br>

Add the plugin to your `pom.xml`. The plugin seals your AI instruction files at `initialize`
and verifies them at `test`. A centralized ledger is written to `target/` — no sidecar files
in your source tree.

The plugin is available on Maven Central as of version 0.10.0.

```xml
<plugin>
    <groupId>com.intsof</groupId>
    <artifactId>ai-build-integrity-maven-plugin</artifactId>
    <version>0.10.0</version>
    <configuration>
        <!-- Centralized ledger: no sidecar files in your source tree -->
        <hashFileMode>CENTRAL</hashFileMode>
    </configuration>
    <executions>
        <execution>
            <goals>
                <goal>generate-hashes</goal>
                <goal>verify-hashes</goal>
                <goal>clean-hashes</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

</details>

---

<details>
<summary><b>🏗️ Multi-Module Project</b></summary>
<br>

Maven has no lifecycle event that fires once when the entire reactor finishes. To catch mid-build
tampering you must verify in **every module**. The correct architecture is:

- `generate-hashes` — seals all files once at `VALIDATE` on the **root only**
- `verify-hashes` — re-verifies in **every module** at `TEST` before packaging
- `clean-hashes` — deletes the ledger once on the **root only**

`centralHashFile` is required to point all child modules at the single shared ledger the root writes.

```xml
<build>
    <pluginManagement>
        <plugins>
            <plugin>
                <groupId>com.intsof</groupId>
                <artifactId>ai-build-integrity-maven-plugin</artifactId>
                <version>0.10.0</version>
                <configuration>
                    <hashFileMode>CENTRAL</hashFileMode>
                    <!-- Scan the entire repo, not just the current module's basedir -->
                    <baseDir>${maven.multiModuleProjectDirectory}</baseDir>
                    <!-- All modules share one ledger written to root target/ -->
                    <centralHashFile>${maven.multiModuleProjectDirectory}/target/ai-integrity.sha256</centralHashFile>
                    <!-- All modules share one audit report written to root target/ -->
                    <centralReportFile>${maven.multiModuleProjectDirectory}/target/ai-integrity-report.json</centralReportFile>
                </configuration>
                <executions>
                    <execution>
                        <id>generate</id>
                        <goals><goal>generate-hashes</goal></goals>
                        <!-- Seal once at reactor start -->
                        <configuration><executionRootOnly>true</executionRootOnly></configuration>
                    </execution>
                    <execution>
                        <id>verify</id>
                        <!-- No executionRootOnly — fires in every module before it is packaged -->
                        <goals><goal>verify-hashes</goal></goals>
                    </execution>
                    <execution>
                        <id>clean</id>
                        <goals><goal>clean-hashes</goal></goals>
                        <!-- Delete ledger once -->
                        <configuration><executionRootOnly>true</executionRootOnly></configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </pluginManagement>
    <plugins>
        <plugin>
            <groupId>com.intsof</groupId>
            <artifactId>ai-build-integrity-maven-plugin</artifactId>
        </plugin>
    </plugins>
</build>
```

</details>

---

## 📚 Documentation & Guides

The AI Build Integrity Plugin handles everything from massive Multi-Module Projects to complex `spotless:apply` interactions gracefully.

- **[Usage Guide](src/site/markdown/usage.md):** Detailed setup for Single-Module and Multi-Module Projects (Parent POMs).
- **English Language**: All documentation is provided in English, and we accept issues and pull requests in English.
- **[FAQ](src/site/markdown/faq.md):** Common questions regarding Git check-ins, formatting plugins, and best practices.
- **[Troubleshooting](src/site/markdown/troubleshooting.md):** Solutions for common errors, skipped subtrees, and execution ordering.

---

## 🤝 Community & Support

- 🐛 **Have an issue or found a bug?** [Open an issue on GitHub](https://github.com/intersoftdatalabs-in/ai-build-integrity-maven-plugin/issues)
- 💡 **Want to contribute?** We'd love your help! Check out our [Contributor Guide](CONTRIBUTING.md)
- 🛡️ **Found a security vulnerability?** Please read our [Security Policy](SECURITY.md) for responsible disclosure.

