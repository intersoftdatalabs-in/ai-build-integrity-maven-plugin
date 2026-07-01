---
name: Bug Report
about: Report a bug in the ai-build-integrity-maven-plugin
title: '[Bug] Cross-platform path comparison fails on Windows'
labels: bug, windows, security
---

## Description

The `ArtifactDigestsUtils.validateArtifactPath()` method uses hardcoded forward slash (`/`) separators when comparing paths:

```java
realPath.toString().startsWith(canonicalBuildDirStr + "/")
```

On Windows, `Path.toString()` returns backslash-separated paths (e.g., `C:\project\target`), so appending `/` produces a path like `C:\project\target/` which never matches.

## Affected Code

`src/main/java/com/intsof/ai/build/integrity/ArtifactDigestsUtils.java`:
- Line 154: `startsWith(canonicalBuildDirStr + "/")`
- Line 178: `startsWith(canonicalBuildDirStr + "/")`
- Line 203: `startsWith(canonicalBuildDirStr + "/")`

## Impact

Path traversal validation fails on Windows, potentially allowing artifacts outside the build directory to be processed.

## Fix

Replace string-based path comparison with `Path.startsWith()` which is platform-aware:

```java
realPath.startsWith(canonicalBuildDir)
```

This uses `java.nio.file.Path`'s native cross-platform comparison logic.

## Severity

- **Security**: Medium (path traversal validation bypass on Windows)
- **Compatibility**: High (plugin does not function correctly on Windows)

## Reproduction

Run tests on Windows - path traversal validation tests will fail due to incorrect path comparison.
