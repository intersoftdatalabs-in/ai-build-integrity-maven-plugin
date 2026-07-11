# Reactor-Scoped Hash Generation (Mini Spec)

**Date:** 2026-07-10  
**Project:** ai-build-integrity-maven-plugin  
**Status:** Approved for implementation  
**Semver:** MINOR (backwards-compatible default improvement)

## 1. Problem

The recommended multi-module configuration is:

|       Setting       |                 Value                  |
|---------------------|----------------------------------------|
| `baseDir`           | `${maven.multiModuleProjectDirectory}` |
| `executionRootOnly` | `true` on `generate-hashes`            |
| `hashFileMode`      | `CENTRAL`                              |

`HashGeneratorMojo` always walks configured `baseDir` and, in CENTRAL mode, **overwrites** the ledger from that full scan. A partial build (e.g. `mvn -pl :module-62 package`, or building from a child module directory) therefore re-walks and rehashes the entire multi-module tree (e.g. 62 modules) before the selected module builds.

Existing related features do **not** solve this:

- `skipExisting` — SIDECAR only; does not apply to CENTRAL.
- `resumeFromModule` / `-rf` — resume-only; does not handle `-pl` or child-directory builds.

## 2. Goal

**Default (option A — module-scoped seal):** seal only what the current Maven reactor is building.

| Reactor |                                           Behavior                                            |
|---------|-----------------------------------------------------------------------------------------------|
| Full    | Walk configured `baseDir` once and rewrite the central ledger (unchanged).                    |
| Partial | Walk only **seal roots** derived from `session.getProjects()`; merge into the central ledger. |

No new required configuration for the common multi-module POM samples. Optional force-full escape hatch.

## 3. Approach

**Approach 1 — Walk seal roots from the current reactor** (chosen).

Rejected alternatives:

- Full walk + filter (still pays full tree walk).
- Config-only per-module `baseDir` (breaks “seal once at root” model).

## 4. Partial-reactor detection

Treat the build as **partial** when any of the following hold:

1. `session.getAllProjects()` is non-null/non-empty and  
   `session.getProjects().size() < session.getAllProjects().size()`, or
2. The request has an explicit non-empty `-pl` selection **and** the reactor is a proper subset of `getAllProjects()` when all-projects is available, or
3. Configured `baseDir` is a strict **ancestor** of every selected project basedir, and no selected project’s basedir equals `baseDir` (typical `cd module-62 && mvn …` with multi-module `baseDir`).

Otherwise treat as **full**.

Log example:

```text
Partial reactor detected (1 of 62 projects); sealing 1 module root(s).
```

## 5. Seal roots

From basedirs of `session.getProjects()` (absolute, normalized):

- Include only basedirs that are equal to or under configured `baseDir`.
- A basedir is a **seal root** if no *other* selected basedir is a proper child of it.

Effects:

- `-pl :module-62` → seal root = module-62 only.
- `-pl :module-62 -am` including aggregator → seal roots = deepest selected modules only (aggregator basedir is **not** walked as a whole tree).
- Full reactor → ignore seal-root reduction; walk configured `baseDir` (covers root-level files outside child modules).

## 6. CENTRAL ledger merge

Today generation overwrites the central file. A partial seal must not drop other modules’ entries.

On **partial** generate:

1. If the central ledger exists, load it.
2. Drop entries whose relative path (resolved against `baseDir`) lies under any seal root.
3. Append newly computed hashes for files found under seal roots (may be empty).
4. Write the merged ledger.

If no ledger exists, write a partial ledger (only sealed paths).

On **full** generate: keep overwrite-from-full-scan (authoritative full seal).

Even when no files match includes under seal roots, still perform merge step 2–4 so stale entries under those roots are removed.

## 7. SIDECAR mode

- Partial: create/update sidecars only under seal roots; leave other modules’ sidecars untouched.
- Full: unchanged.

## 8. Configuration

|          Property           |   Type   | Default |                                                                Purpose                                                                 |
|-----------------------------|----------|---------|----------------------------------------------------------------------------------------------------------------------------------------|
| `ai.integrity.reactorScope` | `String` | `AUTO`  | `AUTO` = full vs partial as above; `FULL` = always walk `baseDir`; `REACTOR` = always use seal-root walks from `session.getProjects()` |

Invalid values → fail the build with a clear error (or treat as AUTO with a warning; **prefer fail** for explicit misconfiguration).

## 9. Verify / clean (non-goals for this change)

|              Goal               |                                  Behavior                                  |
|---------------------------------|----------------------------------------------------------------------------|
| `verify-hashes` CENTRAL         | Unchanged (ledger-driven). May still verify preserved non-reactor entries. |
| `verify-hashes` SIDECAR         | Optional follow-up: scope walk to seal roots.                              |
| `clean-hashes`                  | Out of scope.                                                              |
| Artifact digest mojos           | Out of scope.                                                              |
| Reuse-if-present without rehash | Out of scope (option B).                                                   |

## 10. Interaction with existing features

- **`executionRootOnly`:** unchanged.
- **`resumeFromModule` / `-rf`:** existing skip/regen rules remain; seal roots still apply when the reactor is partial.
- **`skipExisting`:** SIDECAR only; orthogonal.

## 11. Tests

1. Full reactor + CENTRAL → full ledger rewrite (existing behavior).
2. Partial reactor (subset of modules) → only seal-root paths hashed; other modules’ files not visited for hashing.
3. Partial + existing full ledger → merge preserves other modules; refreshes selected paths.
4. Partial + no ledger → partial ledger only.
5. Aggregator + leaf selected (`-am` style) → seal root is leaf only.
6. `reactorScope=FULL` forces full walk on an otherwise partial reactor.
7. SIDECAR partial does not alter out-of-scope sidecars.
8. Empty match under seal roots still drops stale CENTRAL entries under those roots.

## 12. Documentation

- `usage.md` / README multi-module section: document AUTO partial sealing.
- `troubleshooting.md`: update `-pl` note.
- `reference.md`: new property.
- `CHANGELOG.md` + version bump (MINOR).

## 13. Implementation notes

- Prefer a small pure helper (e.g. `ReactorSealScope`) for detection, seal-root reduction, and ledger merge so unit tests do not require full Mojo wiring for every case.
- Relative paths in the central ledger remain relative to configured `baseDir` (forward slashes).
- Java 8 source/target compatibility required.

