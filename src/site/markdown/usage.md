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

Monorepos possess extreme complexity, as they can sometimes contain hundreds of localized modules. The plugin intelligently scales into long-running reactor builds.

Add the following to your Parent POM's `<build><plugins>` section.

**How it works seamlessly:** The parent securely traverses and generates hashes at the very start of the build (T=0). As Maven builds each of the 500 child modules over hours, the child modules locally execute quick hash verifications. If a rogue process modifies an instruction file halfway through the pipeline, the build brutally fails.

```xml
<build>
    <plugins>
        <plugin>
            <groupId>com.intsof</groupId>
            <artifactId>ai-build-integrity-maven-plugin</artifactId>
            <version>0.9.0-SNAPSHOT</version>
            <configuration>
                <hashFileMode>CENTRAL</hashFileMode>
                <!-- Include your proprietary rulebook files -->
                <includes>**/*.md,**/*.json</includes>
            </configuration>
            <executions>
                <execution>
                    <id>generate</id>
                    <goals><goal>generate-hashes</goal></goals>
                    <configuration>
                        <!-- Hash ONLY once at the root directory level across the whole repo -->
                        <executionRootOnly>true</executionRootOnly>
                    </configuration>
                </execution>
                <execution>
                    <id>verify</id>
                    <goals><goal>verify-hashes</goal></goals>
                    <!-- Missing executionRootOnly means it safely verifies on localized child modules -->
                </execution>
                <execution>
                    <id>clean</id>
                    <goals><goal>clean-hashes</goal></goals>
                    <configuration>
                        <!-- Deletes the central target/ ledger only once when root is cleaned -->
                        <executionRootOnly>true</executionRootOnly>
                    </configuration>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

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
    <forceIncludes>
        <forceInclude>src/main/resources/.env*</forceInclude>
    </forceIncludes>

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

    <!-- (VERIFY ONLY) Emits the Dev-Sec-Ops SIEM json payload mapping -->
    <generateAuditReport>false</generateAuditReport>
</configuration>
```

