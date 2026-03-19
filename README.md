# AI Build Integrity Maven Plugin

A Maven plugin that generates and verifies cryptographic hashes for AI instruction files, ensuring that **nothing changes AI instructions once the build has begun or once the artifact is shipped**.

## The Problem

Modern software projects increasingly embed AI agent instructions (e.g., `AGENTS.md`, `SKILL.md`, prompt files) alongside source code. These instruction files directly control AI agent behavior during development, CI/CD, and production. If an attacker or an accidental change modifies these files after the build starts, the AI agents may execute unintended or malicious instructions.

## The Solution

This plugin creates a **tamper-evident seal** on all AI instruction files by:

1. **Generate phase** (`initialize`): Computing cryptographic hashes of all instruction files at the start of the build and writing companion sidecar files (e.g. `.sha256`).
2. **Verify phase** (`test`): Re-computing hashes and comparing them against the stored values. If any file has been modified, the build fails immediately.

This ensures supply-chain integrity for AI instructions throughout the build lifecycle and in shipped artifacts.

## Design Goals

* **Fast and lightweight** — single-pass `Files.walkFileTree` with directory pruning, 64 KiB streaming hash buffer, lookup-table hex encoder. Zero external runtime dependencies beyond Maven's own API.
* **Configurable hash sizes** — SHA-256 (default), SHA-384, or SHA-512. The output extension adjusts automatically (`.sha256`, `.sha384`, `.sha512`).
* **Works everywhere** — single-module projects, large monorepos, multi-module reactor builds. Each module scans only its own `basedir`.
* **Configurable directory skipping** — `target`, `.git`, `node_modules`, `.tmp` skipped by default; fully configurable.

## Coordinates

```xml
<groupId>com.intsof</groupId>
<artifactId>ai-build-integrity-maven-plugin</artifactId>
<version>1.0.0-SNAPSHOT</version>
```

## Quick Start

### Single-Module Project

```xml
<build>
    <plugins>
        <plugin>
            <groupId>com.intsof</groupId>
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

### Monorepo (Parent POM)

Add to the parent POM's `<build><plugins>` section. Each child module will automatically generate and verify hashes within its own `${project.basedir}`:

```xml
<!-- In the parent pom.xml <build><plugins> section -->
<plugin>
    <groupId>com.intsof</groupId>
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
```

### Using SHA-512

```xml
<plugin>
    <groupId>com.intsof</groupId>
    <artifactId>ai-build-integrity-maven-plugin</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <configuration>
        <algorithmBits>512</algorithmBits>
    </configuration>
    <!-- executions as above -->
</plugin>
```

Or via command line: `-Dai.integrity.algorithm.bits=512`

## Goals

### `ai-build-integrity:generate-hashes`

Walks the project base directory and generates companion hash sidecar files for every file matching the include patterns.

* **Default phase:** `validate`
* **Default includes:** `**/*.md`
* **Default excludes:** `**/*.sha256,**/*.sha384,**/*.sha512`

### `ai-build-integrity:verify-hashes`

Finds all hash sidecar files, recomputes the hash of the corresponding source file, and **fails the build** if any mismatch is detected.

* **Default phase:** `test`

## Configuration Properties

| Property | Default | Description |
|---|---|---|
| `ai.integrity.algorithm.bits` | `256` | Hash algorithm bit width: `256`, `384`, or `512` |
| `ai.integrity.includes` | `**/*.md` | Comma-separated glob patterns for files to hash |
| `ai.integrity.excludes` | `**/*.sha256,**/*.sha384,**/*.sha512` | Comma-separated glob patterns for files to exclude |
| `ai.integrity.baseDir` | `${project.basedir}` | Base directory to scan |
| `ai.integrity.outputExtension` | `auto` | Sidecar file extension. `auto` derives from algorithmBits (e.g. `.sha256`) |
| `ai.integrity.skipExisting` | `false` | Skip generating hashes for files that already have a sidecar |
| `ai.integrity.skipDirs` | `target,.git,node_modules,.tmp` | Comma-separated directory names to skip during traversal |

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

## Performance

* **Directory traversal:** `Files.walkFileTree` with `SKIP_SUBTREE` pruning — one syscall per directory, no intermediate `List<Path>` allocation for the tree walk.
* **Hash computation:** 64 KiB streaming buffer minimizes syscalls while keeping heap pressure low.
* **Hex encoding:** Lookup-table encoder (~10x faster than `String.format("%02x")` per byte).
* **Monorepo behavior:** Each module scans only its own `basedir`, so a 200-module reactor does 200 small focused walks rather than one giant walk.

## Requirements

* Maven 3.6.3+
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
