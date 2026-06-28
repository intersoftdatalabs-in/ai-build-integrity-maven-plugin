# Project Roadmap

Our goal is to provide absolute build integrity for AI-first development teams. This roadmap outlines the planned features and improvements for the next 12 months.

## 🟢 Planned (Short Term)

### 📦 Shipped Artifacts Ledger 🚧 **IN PROGRESS**

Extend the hashing engine to generate an integrity ledger for the **final build artifacts** (JARs, WARs, ZIPs) in addition to source resources. This ensures that what is shipped to production matches exactly what was verified during the build.

**Implementation Status:** Core components implemented:
- `ArtifactDigestsGeneratorMojo` — generates digest files for build artifacts
- `ArtifactDigestsVerifyMojo` — verifies artifact digests
- `ArtifactDigestsCleanMojo` — removes generated digest files
- `ArtifactDigestsUtils` — streaming-only hash computation with path traversal protection

### 🔑 Sigstore Integration

Implement support for **Sigstore** to enable passwordless, OIDC-based signing of integrity reports. This aligns with modern supply-chain security best practices using ephemeral keys.

### 📊 Human-Readable Reports

Create a new Maven Site report that renders the `ai-integrity-report.json` as a beautiful, searchable table within the project's documentation.

---

## 🟡 Researching (Medium Term)

### 🛡️ Native SIEM Exporters

Direct out-of-the-box support for:
- **Splunk HEC**: Post audit reports directly to a Splunk HTTP Event Collector.
- **DataDog Logs**: Stream integrity events directly into DataDog for real-time alerting.

### ⚡ Performance Scaling (100k+ Files)

Further optimize the `GitIgnoreAwareFileVisitor` and streaming buffers to ensure minimal overhead even for the world's largest monorepos.

---

## 🔴 Future Vision (Long Term)

### 🤖 AI-Specific Integrity Checks

Develop specialized validators for LLM instruction files (`AGENTS.md`, `SKILL.md`) to detect non-deterministic or malicious prompts that may have been injected.

### 🌍 Universal Webhook Support

A generic webhook mechanism to notify external security orchestrators the moment a hash mismatch is detected during a build.
