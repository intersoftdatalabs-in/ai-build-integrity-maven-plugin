# AI Build Integrity Maven Plugin

[![Maven Central](https://img.shields.io/maven-central/v/com.intsof/ai-build-integrity-maven-plugin)](https://central.sonatype.com/artifact/com.intsof/ai-build-integrity-maven-plugin)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

A Maven plugin that generates and verifies cryptographic hashes for AI instruction files, ensuring that **nothing changes AI instructions once the build has begun or once the artifact is shipped**.

## The Problem

Modern software projects increasingly embed AI agent instructions (e.g., `AGENTS.md`, `SKILL.md`, prompt files) alongside source code. These instruction files directly control AI agent behavior during development, CI/CD, and production. If an attacker or an accidental change modifies these files after the build starts, the AI agents may execute unintended or malicious instructions.

## The Solution

This plugin creates a **tamper-evident seal** on all AI instruction files, and any other source or resource files that you want to protect, by:

1. **Generate phase** (`initialize`): Computing cryptographic hashes of all instruction files at the start of the build and writing companion sidecar files (e.g. `.sha256`).
2. **Verify phase** (`test`): Re-computing hashes and comparing them against the stored values. If any file has been modified, the build fails immediately.

This ensures supply-chain integrity for AI instructions throughout the build lifecycle and in shipped artifacts.

## Design Goals

- **Fast and lightweight** — single-pass `Files.walkFileTree` with directory pruning, 64 KiB streaming hash buffer, lookup-table hex encoder. Zero external runtime dependencies beyond Maven's own API.
- **Configurable hash sizes** — SHA-256 (default), SHA-384, or SHA-512. The output extension adjusts automatically (`.sha256`, `.sha384`, `.sha512`).
- **Works everywhere** — single-module projects, large monorepos, multi-module reactor builds. Each module scans only its own `basedir`.
- **Configurable directory skipping** — `target`, `.git`, `node_modules`, `.tmp` skipped by default; fully configurable.

## Coordinates

```xml
<groupId>com.intsof</groupId>
<artifactId>ai-build-integrity-maven-plugin</artifactId>
<version>0.1.4-SNAPSHOT</version>
```

## Quick Start

Choose the lifecycle placement based on whether your build rewrites protected files such as `AGENTS.md`, `SKILL.md`, or other matched instruction files.

### If Your Build Does Not Rewrite Protected Files

Use this setup when your build only reads instruction files, or when tools like Spotless run in check-only mode and do not modify matched files.

### Single-Module Project

```xml
<build>
    <plugins>
        <plugin>
            <groupId>com.intsof</groupId>
            <artifactId>ai-build-integrity-maven-plugin</artifactId>
            <version>0.9.0-SNAPSHOT</version>
            <executions>
                <execution>
                    <id>generate-hashes</id>
                    <phase>initialize</phase>
                    <goals>
                        <goal>generate-hashes</goal>
                    </goals>
                    <configuration>
                        <algorithmBits>256</algorithmBits>
                        <includes>**/*.md</includes>
                        <excludes>**/*.sha256,**/*.sha384,**/*.sha512</excludes>
                        <skipDirs>target,.git,node_modules,.tmp</skipDirs>
                        <executionRootOnly>false</executionRootOnly>
                        <gitignoreAutoExclude>false</gitignoreAutoExclude>
                        <forceIncludes></forceIncludes>
                        <hideHashFiles>true</hideHashFiles>
                        <hashFileMode>SIDECAR</hashFileMode>
                        <skip>false</skip>
                        <normalizeLineEndings>false</normalizeLineEndings>
                    </configuration>
                </execution>
                <execution>
                    <id>verify-hashes</id>
                    <phase>test</phase>
                    <goals>
                        <goal>verify-hashes</goal>
                    </goals>
                    <configuration>
                        <algorithmBits>256</algorithmBits>
                        <includes>**/*.md</includes>
                        <excludes>**/*.sha256,**/*.sha384,**/*.sha512</excludes>
                        <skipDirs>target,.git,node_modules,.tmp</skipDirs>
                        <executionRootOnly>false</executionRootOnly>
                        <gitignoreAutoExclude>false</gitignoreAutoExclude>
                        <forceIncludes></forceIncludes>
                        <hideHashFiles>true</hideHashFiles>
                        <hashFileMode>SIDECAR</hashFileMode>
                        <skip>false</skip>
                        <normalizeLineEndings>false</normalizeLineEndings>
                        <failOnError>true</failOnError>
                        <generateAuditReport>false</generateAuditReport>
                    </configuration>
                </execution>
                <execution>
                    <id>clean-hashes</id>
                    <phase>clean</phase>
                    <goals>
                        <goal>clean-hashes</goal>
                    </goals>
                    <configuration>
                        <algorithmBits>256</algorithmBits>
                        <includes>**/*.md</includes>
                        <excludes>**/*.sha256,**/*.sha384,**/*.sha512</excludes>
                        <skipDirs>target,.git,node_modules,.tmp</skipDirs>
                        <executionRootOnly>false</executionRootOnly>
                        <gitignoreAutoExclude>false</gitignoreAutoExclude>
                        <forceIncludes></forceIncludes>
                        <hideHashFiles>true</hideHashFiles>
                        <hashFileMode>SIDECAR</hashFileMode>
                        <skip>false</skip>
                    </configuration>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

### Monorepo (Parent POM)

Add to the parent POM's `<build><plugins>` section. The parent project will secure the entire repository at T=0 by generating the hashes across all modules. As the long-running reactor build progresses, the child modules will continually verify those hashes to ensure files were not tampered with mid-build:

```xml
<!-- In the parent pom.xml <build><plugins> section -->
<plugin>
    <groupId>com.intsof</groupId>
    <artifactId>ai-build-integrity-maven-plugin</artifactId>
    <version>0.9.0-SNAPSHOT</version>
    <executions>
        <execution>
            <id>generate-hashes</id>
            <phase>initialize</phase>
            <goals>
                <goal>generate-hashes</goal>
            </goals>
            <configuration>
                <!-- Generates hashes for the entire repository ONLY at the root at T=0 -->
                <algorithmBits>256</algorithmBits>
                <includes>**/*.md</includes>
                <excludes>**/*.sha256,**/*.sha384,**/*.sha512</excludes>
                <skipDirs>target,.git,node_modules,.tmp</skipDirs>
                <executionRootOnly>true</executionRootOnly>
                <gitignoreAutoExclude>false</gitignoreAutoExclude>
                <forceIncludes></forceIncludes>
                <hideHashFiles>true</hideHashFiles>
                <hashFileMode>SIDECAR</hashFileMode>
                <skip>false</skip>
                <normalizeLineEndings>false</normalizeLineEndings>
                <failOnError>true</failOnError>
                <generateAuditReport>false</generateAuditReport>
            </configuration>
        </execution>
        <execution>
            <id>verify-hashes</id>
            <phase>test</phase>
            <goals>
                <goal>verify-hashes</goal>
            </goals>
            <configuration>
                <!-- Runs locally in every child module to continually ensure hashes didn't change -->
                <algorithmBits>256</algorithmBits>
                <includes>**/*.md</includes>
                <excludes>**/*.sha256,**/*.sha384,**/*.sha512</excludes>
                <skipDirs>target,.git,node_modules,.tmp</skipDirs>
                <executionRootOnly>false</executionRootOnly>
                <gitignoreAutoExclude>false</gitignoreAutoExclude>
                <forceIncludes></forceIncludes>
                <hideHashFiles>true</hideHashFiles>
                <hashFileMode>SIDECAR</hashFileMode>
                <skip>false</skip>
                <normalizeLineEndings>false</normalizeLineEndings>
                <failOnError>true</failOnError>
                <generateAuditReport>false</generateAuditReport>
            </configuration>
        </execution>
        <execution>
            <id>clean-hashes</id>
            <phase>clean</phase>
            <goals>
                <goal>clean-hashes</goal>
            </goals>
            <configuration>
                <!-- Cleans the ENTIRE repository hashes only once at the root -->
                <algorithmBits>256</algorithmBits>
                <includes>**/*.md</includes>
                <excludes>**/*.sha256,**/*.sha384,**/*.sha512</excludes>
                <skipDirs>target,.git,node_modules,.tmp</skipDirs>
                <executionRootOnly>true</executionRootOnly>
                <gitignoreAutoExclude>false</gitignoreAutoExclude>
                <forceIncludes></forceIncludes>
                <hideHashFiles>true</hideHashFiles>
                <hashFileMode>SIDECAR</hashFileMode>
                <skip>false</skip>
                <normalizeLineEndings>false</normalizeLineEndings>
                <failOnError>true</failOnError>
                <generateAuditReport>false</generateAuditReport>
            </configuration>
        </execution>
    </executions>
</plugin>
```

#### Standalone Module Builds

When building individual modules within a Monorepo, the plugin natively secures the developer workflow:

1. **Running from a child directory** (`cd child-b && mvn test`): Because Maven is invoked from the child directory, it officially becomes the execution root for that session. `generate-hashes` securely runs locally at T=0.
2. **Targeting via Project List** (`mvn test -pl child-b`): The root parent is the execution root but omitted from the build. `generate-hashes` is safely bypassed. Because no fresh hashes are generated for the omitted root execution, the child module simply verifies whatever `.sha` files currently exist on your local disk from a previous global generation. To explicitly establish a fresh tamper-seal during a targeted build, use `mvn ai-build-integrity:generate-hashes -pl child-b`.

### Securing Entire Applications (Mixed & Frontend Projects)

To seal all source code, resources, scripts, and front-end application files, simply expand the `<includes>` tag with comma-separated glob patterns.

Because the plugin's `skipDirs` configuration natively ignores `node_modules`, `target`, and `.git` by default, the file scanner will effortlessly navigate heavy front-end repositories containing hundreds of thousands of dependencies without sacrificing any performance. You can also set `<gitignoreAutoExclude>true</gitignoreAutoExclude>` to natively read your repository's `.gitignore` files and automatically exclude local development caches or IDE folders like `.idea/`. If there are critical hidden files (like `.env`) that you intentionally ignore in Git but still want to secure, you can strictly override this logic using `<forceIncludes>**/.env</forceIncludes>`.

```xml
<configuration>
    <!-- Secure BOTH the Java Backend and the TS/React JS Frontend -->
    <algorithmBits>256</algorithmBits>
    <includes>
        **/*.java,**/*.xml,**/*.properties,**/*.yaml,**/*.sh,
        **/*.ts,**/*.tsx,**/*.js,**/*.jsx,**/*.json,
        **/*.css,**/*.scss,**/*.html,**/*.md
    </includes>
    <excludes>**/*.sha256,**/*.sha384,**/*.sha512</excludes>
    <skipDirs>target,.git,node_modules,.tmp</skipDirs>
    <executionRootOnly>true</executionRootOnly>
    <gitignoreAutoExclude>false</gitignoreAutoExclude>
    <forceIncludes></forceIncludes>
    <hideHashFiles>true</hideHashFiles>
    <hashFileMode>SIDECAR</hashFileMode>
    <skip>false</skip>
    <normalizeLineEndings>false</normalizeLineEndings>
    <failOnError>true</failOnError>
    <generateAuditReport>false</generateAuditReport>
</configuration>
```

### If Your Build Uses Formatters or Other File-Mutating Plugins

Use this setup when a formatter or any other plugin rewrites files matched by `ai.integrity.includes`. The key rule is simple: run all mutating plugins first, then run `generate-hashes` against the final file bytes, then run `verify-hashes` later in the lifecycle.

Common examples of mutating plugins include Spotless `apply`, license-header plugins, templating steps, or custom generators that rewrite Markdown or prompt files.

```xml
<build>
    <plugins>
        <plugin>
            <groupId>com.diffplug.spotless</groupId>
            <artifactId>spotless-maven-plugin</artifactId>
            <version>2.43.0</version>
            <executions>
                <execution>
                    <id>format-instructions</id>
                    <phase>process-sources</phase>
                    <goals>
                        <goal>apply</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>

        <plugin>
            <groupId>com.intsof</groupId>
            <artifactId>ai-build-integrity-maven-plugin</artifactId>
            <version>0.9.0-SNAPSHOT</version>
            <executions>
                <execution>
                    <id>generate-hashes</id>
                    <phase>process-sources</phase>
                    <goals>
                        <goal>generate-hashes</goal>
                    </goals>
                    <configuration>
                        <algorithmBits>256</algorithmBits>
                        <includes>**/*.md</includes>
                        <excludes>**/*.sha256,**/*.sha384,**/*.sha512</excludes>
                        <skipDirs>target,.git,node_modules,.tmp</skipDirs>
                        <executionRootOnly>false</executionRootOnly>
                        <gitignoreAutoExclude>false</gitignoreAutoExclude>
                        <forceIncludes></forceIncludes>
                        <hideHashFiles>true</hideHashFiles>
                        <hashFileMode>SIDECAR</hashFileMode>
                        <skip>false</skip>
                        <normalizeLineEndings>false</normalizeLineEndings>
                        <failOnError>true</failOnError>
                        <generateAuditReport>false</generateAuditReport>
                    </configuration>
                </execution>
                <execution>
                    <id>verify-hashes</id>
                    <phase>test</phase>
                    <goals>
                        <goal>verify-hashes</goal>
                    </goals>
                    <configuration>
                        <algorithmBits>256</algorithmBits>
                        <includes>**/*.md</includes>
                        <excludes>**/*.sha256,**/*.sha384,**/*.sha512</excludes>
                        <skipDirs>target,.git,node_modules,.tmp</skipDirs>
                        <executionRootOnly>false</executionRootOnly>
                        <gitignoreAutoExclude>false</gitignoreAutoExclude>
                        <forceIncludes></forceIncludes>
                        <hideHashFiles>true</hideHashFiles>
                        <hashFileMode>SIDECAR</hashFileMode>
                        <skip>false</skip>
                        <normalizeLineEndings>false</normalizeLineEndings>
                        <failOnError>true</failOnError>
                        <generateAuditReport>false</generateAuditReport>
                    </configuration>
                </execution>
                <execution>
                    <id>clean-hashes</id>
                    <phase>clean</phase>
                    <goals>
                        <goal>clean-hashes</goal>
                    </goals>
                    <configuration>
                        <algorithmBits>256</algorithmBits>
                        <includes>**/*.md</includes>
                        <excludes>**/*.sha256,**/*.sha384,**/*.sha512</excludes>
                        <skipDirs>target,.git,node_modules,.tmp</skipDirs>
                        <executionRootOnly>false</executionRootOnly>
                        <gitignoreAutoExclude>false</gitignoreAutoExclude>
                        <forceIncludes></forceIncludes>
                        <hideHashFiles>true</hideHashFiles>
                        <hashFileMode>SIDECAR</hashFileMode>
                        <skip>false</skip>
                        <normalizeLineEndings>false</normalizeLineEndings>
                    </configuration>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

Important ordering notes:

- If a formatter rewrites protected files, declare that plugin before `ai-build-integrity-maven-plugin` when both are bound to the same phase.
- If you are unsure about plugin ordering in the same phase, move `generate-hashes` to a later phase after all file mutation is complete.
- `spotless:check` is safe with the earlier setup because it validates formatting without rewriting files.

### Using SHA-512

```xml
<plugin>
    <groupId>com.intsof</groupId>
    <artifactId>ai-build-integrity-maven-plugin</artifactId>
    <version>0.9.0-SNAPSHOT</version>
    <configuration>
        <algorithmBits>512</algorithmBits>
    </configuration>
    <!-- executions as above -->
</plugin>
```

Or via command line: `-Dai.integrity.algorithm.bits=512`

## Lifecycle Guidance

This plugin secures files by natively hashing the exact file bytes on disk. Because the plugin does not normalize line endings or whitespace (to guarantee absolute byte-for-byte integrity), proper execution ordering is vital.

**The Golden Rule: Generate at T=0, Verify at T=N**
You should always bind `generate-hashes` to an early phase (like `initialize`) and `verify-hashes` to a later phase (like `test`).

Why? Because the overriding goal of this plugin is to protect files _during the automated build and packaging process_. By generating the hashes freshly at the start of every local or CI/CD build, you establish a secure tamper-seal precisely when Maven takes control of the repository. If any background AI agent, malicious dependency, or rogue formatting plugin modifies your critical instructions while tests are running, the plugin will catch the modified footprint and brutally fail the build.

### Dealing with Formatters

- If your build uses an auto-formatter (like `spotless:apply`) that routinely modifies protected bytes, you **must** ensure the formatter executes _before_ `generate-hashes` locks down the footprint.
- If validation suddenly fails during a local build, examine whether a later execution step (like `process-resources`) is unknowingly rewriting your instructions after they were mathematically synthesized.

## Goals

### `ai-build-integrity:generate-hashes`

Walks the project base directory and generates companion hash sidecar files for every file matching the include patterns.

- **Default phase:** `validate`
- **Default includes:** `**/*.md`
- **Default excludes:** `**/*.sha256,**/*.sha384,**/*.sha512`

### `ai-build-integrity:verify-hashes`

Finds all hash sidecar files, recomputes the hash of the corresponding source file, and **fails the build** if any mismatch is detected.

- **Default phase:** `test`

### `ai-build-integrity:clean-hashes`

Walks the project base directory and automatically removes all generated hash sidecar files matching the configured output extension.

- **Default phase:** `clean`

## Configuration Properties

|              Property               |                Default                |                                Description                                 |
|-------------------------------------|---------------------------------------|----------------------------------------------------------------------------|
| `ai.integrity.algorithm.bits`       | `256`                                 | Hash algorithm bit width: `256`, `384`, or `512`                           |
| `ai.integrity.includes`             | `**/*.md`                             | Comma-separated glob patterns for files to hash                            |
| `ai.integrity.excludes`             | `**/*.sha256,**/*.sha384,**/*.sha512` | Comma-separated glob patterns for files to exclude                         |
| `ai.integrity.baseDir`              | `${project.basedir}`                  | Base directory to scan                                                     |
| `ai.integrity.outputExtension`      | `auto`                                | Sidecar file extension. `auto` derives from algorithmBits (e.g. `.sha256`) |
| `ai.integrity.skipExisting`         | `false`                               | Skip generating hashes for files that already have a sidecar               |
| `ai.integrity.skipDirs`             | `target,.git,node_modules,.tmp`       | Comma-separated directory names to skip during traversal                   |
| `ai.integrity.executionRootOnly`    | `false`                               | If true, the mojo only executes in the reactor's execution root project    |
| `ai.integrity.gitignoreAutoExclude` | `false`                               | If true, parses local `.gitignore` files to auto-skip paths                |
| `ai.integrity.forceIncludes`        | `""`                                  | Comma-separated glob patterns to strictly include, bypassing `.gitignore`  |
| `ai.integrity.hideHashFiles`        | `true`                                | If false, does not hide the generated hash sidecar files cross-platform    |

## Example Output

After running `generate-hashes` with default SHA-256:

```
AGENTS.md
AGENTS.md.sha256
modules/skills/SKILL.md
modules/skills/SKILL.md.sha256
```

With `algorithmBits=512`:

```
AGENTS.md
AGENTS.md.sha512
```

Each sidecar file contains the hash and filename in BSD-style format:

```
a1b2c3d4e5f6...  AGENTS.md
```

During `verify-hashes`, if `AGENTS.md` has been modified since hash generation:

```
[ERROR] HASH MISMATCH: AGENTS.md - file may have been tampered with!
[ERROR] Hash verification FAILED: 1 file(s) have been modified or tampered with!
```

The build fails with a `MojoExecutionException`.

## Troubleshooting

If the plugin fails your build with `Hash verification FAILED`, it means an instruction file was altered.

**As a developer, how do I fix this?**
If you intentionally edited the file (e.g. updating a prompt), you must re-sign the file so the hash seal matches your new changes. Run the following command locally and commit the updated `.sha256` file alongside your `.md` file to Git:

```bash
mvn ai-build-integrity:generate-hashes
```

_(If you are developing inside a Monorepo, you can target just your module by running `mvn ai-build-integrity:generate-hashes -pl your-module`)._

For more detailed guides on other errors, see the [Troubleshooting Documentation](https://intersoftdatalabs-in.github.io/ai-build-integrity-maven-plugin/troubleshooting.html) on the plugin site.

## Performance

- **Directory traversal:** `Files.walkFileTree` with `SKIP_SUBTREE` pruning — one syscall per directory, no intermediate `List<Path>` allocation for the tree walk.
- **Hash computation:** 64 KiB streaming buffer minimizes syscalls while keeping heap pressure low.
- **Hex encoding:** Lookup-table encoder (~10x faster than `String.format("%02x")` per byte).
- **Monorepo behavior:** In a secure Monorepo setup, the parent module performs one incredibly fast, global `Files.walkFileTree` at T=0 to generate all hashes securely. Then, each of the 200 child modules performs small focused walks during their own `test` phases to continuously verify local integrity.

## FAQ

### Am I supposed to check all of these hash files into Git?

**No.** You should exclusively rely on your `.gitignore` to keep them thoroughly untracked across your collaborative workflows.

**Why?**
Git's `core.autocrlf` and `.gitattributes` configuration will notoriously alter the actual byte content of your text files when seamlessly checking them out on disparate operating systems (mutating Linux `\n` to Windows `\r\n`).

Because our plugin's tamper-seal is absolute and refuses to normalize whitespace artificially, generating a `.sha` hash on a Linux developer's machine and committing it to Git guarantees that it will immediately and categorically fail validation on every Windows machine checking out that branch!

By entirely ignoring the `.sha` extensions in source control, you compel the Maven build to synthesize the hashes purely on the fly leveraging the exact OS-specific line endings physically present on the local checkout. This completely avoids cross-platform collisions while robustly securing the footprint of the build.

```gitignore
# Ignore AI-Build-Integrity Hash Sidecars
*.sha256
*.sha384
*.sha512

# Note: The plugin natively discovers your ignored `.sha` sidecars
# so they still verify correctly, even if gitignoreAutoExclude=true!
```

## Requirements

- Maven 3.9.x+
- JDK 11+

## Building From Source

```bash
mvn clean install
```

## License

Copyright 2026 Intersoft Data Labs, LLC.

Licensed under the [Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0).

## Repository

[https://github.com/intersoftdatalabs-in/ai-build-integrity-maven-plugin](https://github.com/intersoftdatalabs-in/ai-build-integrity-maven-plugin)
