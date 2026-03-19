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

