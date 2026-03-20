# Usage & Implementation

The AI Build Integrity Maven Plugin guarantees that your AI instructions (e.g. `AGENTS.md`) and critical files have not been modified since the build began or since the final artifact was shipped.

Below are the most common configurations for seamless enterprise integration.

## 1. Single-Module Project (Simplified)

For a standard Maven application, attaching the plugin is incredibly simple. It uses the `CENTRAL` ledger configuration to avoid depositing `.sha` sidecar files natively across your source structure.

```xml
<build>
    <plugins>
        <plugin>
            <groupId>com.intsof</groupId>
            <artifactId>ai-build-integrity-maven-plugin</artifactId>
            <version>0.9.0-SNAPSHOT</version>
            <configuration>
                <hashFileMode>CENTRAL</hashFileMode>
                <!-- Define your hashing intensity -->
                <hashBits>256</hashBits>

                <!-- Multi-line support makes large lists readable -->
                <includes>
                    src/main/resources/**/*.xml,
                    src/main/resources/**/*.properties,
                    **/ai-instructions.md
                </includes>

                <excludes>
                    **/target/**,
                    **/test-output/**
                </excludes>
            </configuration>
            <executions>
                <execution>
                    <id>generate</id>
                    <goals><goal>generate-hashes</goal></goals>
                </execution>
                <execution>
                    <id>verify</id>
                    <goals><goal>verify-hashes</goal></goals>
                </execution>
                <execution>
                    <id>clean</id>
                    <goals><goal>clean-hashes</goal></goals>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

---

## 2. Large Monorepos

In a Maven reactor build there is no lifecycle event that fires once after all modules have finished
building. The only reliable hook for mid-reactor integrity enforcement is each module's own lifecycle.

The correct architecture is therefore:

|       Goal        |                Scope                 |                                    When                                    |
|-------------------|--------------------------------------|----------------------------------------------------------------------------|
| `generate-hashes` | Root only (`executionRootOnly=true`) | `VALIDATE` — seals files before the build begins                           |
| `verify-hashes`   | **Every module**                     | `TEST` — re-verifies the sealed ledger just before each module is packaged |
| `clean-hashes`    | Root only (`executionRootOnly=true`) | `CLEAN` — removes the ledger once                                          |

Because `verify-hashes` must read the ledger from every child module, you must use `centralHashFile`
to point all modules at the single shared ledger written by the root. Without it, each child module
looks in its own `target/` directory, finds nothing, and silently skips.

Add the following to your **root parent POM's** `<build><pluginManagement>` and `<plugins>` sections:

```xml
<build>
    <pluginManagement>
        <plugins>
            <plugin>
                <groupId>com.intsof</groupId>
                <artifactId>ai-build-integrity-maven-plugin</artifactId>
                <version>0.9.0-SNAPSHOT</version>
                <configuration>
                    <hashFileMode>CENTRAL</hashFileMode>
                    <!-- Scan the entire repo from the root, not from each module's basedir -->
                    <baseDir>${maven.multiModuleProjectDirectory}</baseDir>
                    <!-- All modules share a single ledger in the root target/ directory -->
                    <centralHashFile>${maven.multiModuleProjectDirectory}/target/ai-integrity.sha256</centralHashFile>
                    <!-- All modules share a single audit report in the root target/ directory -->
                    <centralReportFile>${maven.multiModuleProjectDirectory}/target/ai-integrity-report.json</centralReportFile>
                    <includes>**/*.md,**/*.json</includes>
                </configuration>
                <executions>
                    <execution>
                        <id>generate</id>
                        <goals><goal>generate-hashes</goal></goals>
                        <!-- Seal all files exactly once, at the very start of the reactor build -->
                        <configuration>
                            <executionRootOnly>true</executionRootOnly>
                        </configuration>
                    </execution>
                    <execution>
                        <id>verify</id>
                        <!-- No executionRootOnly: fires in EVERY module before it is packaged -->
                        <goals><goal>verify-hashes</goal></goals>
                    </execution>
                    <execution>
                        <id>clean</id>
                        <goals><goal>clean-hashes</goal></goals>
                        <!-- Delete the shared ledger exactly once -->
                        <configuration>
                            <executionRootOnly>true</executionRootOnly>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </pluginManagement>
    <plugins>
        <!-- Activates the pluginManagement configuration above for the reactor -->
        <plugin>
            <groupId>com.intsof</groupId>
            <artifactId>ai-build-integrity-maven-plugin</artifactId>
        </plugin>
    </plugins>
</build>
```

> **Note:** If you only need a lightweight smoke-test and do not care about mid-reactor tampering
> (e.g. a CI pipeline with no third-party build steps), you may set `executionRootOnly=true` on
> the verify execution as well. This verifies once during the root module's `TEST` phase and skips
> all child modules entirely, but provides no protection after that point.

---

## 3. SIEM-Audited Setup (Security Hardened)

If you are an IT Security team rolling this out across a huge organization, you may encounter pushback from developers. To minimize friction, you can initially enable the plugin in "Soft-Fail" Auditing Mode while simultaneously emitting SIEM reports.

```xml
<verify-hashes-execution>
    <configuration>
        <!-- Swallow hard exceptions, only log heavily to avoid breaking CI pipelines during rollout -->
        <failOnError>false</failOnError>
        <!-- Emit a consolidated JSON Report of File Validations to ingest into Datadog/Splunk -->
        <generateAuditReport>true</generateAuditReport>
    </configuration>
</verify-hashes-execution>
```

---

## 4. Full Options Documentation

To leverage every configuration parameter exposed by the plugin engine, consult the `<configuration>` block below. These apply individually to the `generate-hashes`, `verify-hashes`, and `clean-hashes` goals.

```xml
<configuration>
    <!-- Use SHA-256 (default), SHA-384, or SHA-512 cryptographic digests -->
    <algorithmBits>256</algorithmBits>

    <!-- Defines which critical files to secure -->
    <includes>**/*.md,**/*.yml,**/*.json</includes>

    <!-- Prevents securing intermediate files or generated outputs internally -->
    <excludes>**/*.sha256</excludes>

    <!-- Force PRUNES tree traversal logic on these gigantic directories -->
    <skipDirs>target,.git,node_modules,.tmp</skipDirs>

    <!-- Prevents multi-module traversal repetition -->
    <executionRootOnly>false</executionRootOnly>

    <!-- Natively parses local .gitignore files (e.g., node_modules) safely -->
    <gitignoreAutoExclude>true</gitignoreAutoExclude>

    <!-- OVERWRITES gitignore exceptions (i.e. if you WANT to protect .env but it is gitignored) -->
    <forceIncludes>src/main/resources/.env*</forceIncludes>

    <!-- CENTRAL outputs to target/ai-integrity.sha256, SIDECAR outputs to local hidden files -->
    <hashFileMode>CENTRAL</hashFileMode>

    <!-- Hide sidecar files securely down at the OS level (chmod / attrib hidden) if using SIDECAR -->
    <hideHashFiles>true</hideHashFiles>

    <!-- Globally bypass the execution for local development agility -->
    <skip>false</skip>

    <!-- In-Memory normalizes Windows \r\n to Linux \n before hashing for exact OS fingerprints -->
    <normalizeLineEndings>false</normalizeLineEndings>

    <!-- (VERIFY ONLY) Bypass build failures and just log warnings -->
    <failOnError>true</failOnError>

    <!-- (VERIFY ONLY) Emits the Dev-Sec-Ops SIEM json payload mapping (default: true) -->
    <generateAuditReport>true</generateAuditReport>

    <!-- (VERIFY ONLY) Explicit path to the central audit report file -->
    <centralReportFile>${maven.multiModuleProjectDirectory}/target/ai-integrity-report.json</centralReportFile>
</configuration>
```

