# AI Build Integrity Maven Plugin

A Maven plugin that generates and verifies cryptographic hashes for AI instruction files, ensuring that **nothing changes AI instructions once the build has begun or once the artifact is shipped**.

## The Problem

Modern software projects increasingly embed AI agent instructions (e.g., `AGENTS.md`, `SKILL.md`, prompt files) alongside source code. These instruction files directly control AI agent behavior during development, CI/CD, and production. If an attacker or an accidental change modifies these files after the build starts, the AI agents may execute unintended or malicious instructions.

## The Solution

This plugin creates a **tamper-evident seal** on all AI instruction files by:

1. **Generate phase** (`initialize`): Computing SHA-256 hashes of all instruction files at the start of the build and writing companion `.sha256` sidecar files.
2. **Verify phase** (`test`): Re-computing hashes and comparing them against the stored values. If any file has been modified, the build fails immediately.

This ensures supply-chain integrity for AI instructions throughout the build lifecycle and in shipped artifacts.

## Coordinates

```xml
<groupId>com.intersoftdatalabs</groupId>
<artifactId>ai-build-integrity-maven-plugin</artifactId>
<version>1.0.0-SNAPSHOT</version>
```

## Quick Start

Add to your project's `pom.xml`:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>com.intersoftdatalabs</groupId>
            <artifactId>ai-build-integrity-maven-plugin</artifactId>
            <version>1.0.0-SNAPSHOT</version>
            <executions>
                <execution>
                    <id>generate-hashes</id>
                    <phase>initialize</phase>
                    <goals>
                        <goal>generate-hashes</goal>
                    </goals>
                </execution>
                <execution>
                    <id>verify-hashes</id>
                    <phase>test</phase>
                    <goals>
                        <goal>verify-hashes</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

## Goals

### `ai-build-integrity:generate-hashes`

Walks the project base directory and generates companion `.sha256` hash files for every file matching the include patterns.

* **Default phase:** `validate`
* **Default includes:** `**/*.md`
* **Default excludes:** `**/*.sha256`

### `ai-build-integrity:verify-hashes`

Finds all `.sha256` hash files, recomputes the hash of the corresponding source file, and **fails the build** if any mismatch is detected.

* **Default phase:** `test`

## Configuration Properties

| Property | Default | Description |
|---|---|---|
| `ai.integrity.algorithm.bits` | `256` | Hash algorithm bit width (256, 384, or 512) |
| `ai.integrity.includes` | `**/*.md` | Comma-separated glob patterns for files to hash |
| `ai.integrity.excludes` | `**/*.sha256` | Comma-separated glob patterns for files to exclude |
| `ai.integrity.baseDir` | `${project.basedir}` | Base directory to scan |
| `ai.integrity.outputExtension` | `.sha256` | Extension for companion hash files |
| `ai.integrity.skipExisting` | `false` | Skip generating hashes for files that already have a hash file |

## Example Output

After running `generate-hashes`, each matched file gets a companion hash file:

```
AGENTS.md
AGENTS.md.sha256
modules/skills/SKILL.md
modules/skills/SKILL.md.sha256
```

Each `.sha256` file contains the hash and filename in BSD-style format:

```
a1b2c3d4e5f6...  AGENTS.md
```

During `verify-hashes`, if `AGENTS.md` has been modified since hash generation:

```
[ERROR] HASH MISMATCH: AGENTS.md - file may have been tampered with!
[ERROR] Hash verification FAILED: 1 file(s) have been modified or tampered with!
```

The build fails with a `MojoExecutionException`.

## Behavior Details

* The `target/` directory is always skipped during scanning.
* Glob patterns like `**/*.md` match files at all directory depths, including the project root.
* The verify mojo is independent of the excludes configuration; it searches only for `.sha256` files.

## Requirements

* Maven 3.9+
* JDK 11+

## Building From Source

```bash
mvn clean install
```

## License

Copyright 2026 Intersoft Data Labs, LLC.

Licensed under the [Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0).

## Repository

[https://github.com/intersoftdatalabs-in/ai-build-integrity-maven-plugin](https://github.com/intersoftdatalabs-in/ai-build-integrity-maven-plugin)
