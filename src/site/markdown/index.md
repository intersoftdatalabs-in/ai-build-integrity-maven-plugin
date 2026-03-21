# AI Build Integrity Maven Plugin

A Maven plugin that generates and verifies cryptographic hashes for AI instruction files, ensuring that **nothing changes AI instructions once the build has begun or once the artifact is shipped**.

## The Problem

Modern software projects increasingly embed AI agent instructions (e.g., `AGENTS.md`, `SKILLS.md`, prompt files) alongside source code. These instruction files directly control AI agent behavior during development, CI/CD, and production operations.

If an attacker, a rogue dependency, or an accidental change modifies these files after the build starts, the AI agents may execute unintended or malicious instructions.

## The Solution

This plugin creates a **tamper-evident seal** on all AI instruction files by:

1. **Generate phase** (`initialize`): Computing cryptographic hashes of all instruction files at the precise millisecond the build starts, writing them to a centralized ledger (`target/ai-integrity.sha256`).
2. **Verify phase** (`test`): Re-computing hashes and comparing them against the stored ledger. If any file has been modified, the build brutally fails.

This ensures comprehensive supply-chain integrity for AI instructions without relying on external SaaS tools or slowing down Maven.

## Engineering Performance

* **Blazing Fast**: Uses raw NIO `Files.walkFileTree` with static path pruning (e.g., `target`, `.git`, `node_modules` are completely bypassed) and a 64 KiB streaming buffer. It recursively seals massive multi-module projects in milliseconds.
* **Cross-OS Native**: Automatically sanitizes Windows/Linux line-endings (`\r\n` -> `\n`) in-memory, ensuring Mac and Windows developers generate identical cryptographic fingerprints.
* **Zero Pollution**: Uses a centralized ledger inside your `target/` directory instead of littering your source tree with sidecar files.
* **SIEM Auditable**: Optionally dumps JSON integrity maps (`ai-integrity-report.json`) for effortless Dev-Sec-Ops integration.

