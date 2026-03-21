# Contributing to AI Build Integrity Maven Plugin

Thank you for your interest in contributing! We welcome contributions of all kinds from both developers and security engineers. Whether you are adding new SIEM reporting capabilities, optimizing the hashing engine, or just fixing a typo, your help makes the AI supply-chain more secure for everyone.

## Code of Conduct

All contributors are expected to follow our standard Code of Conduct (be kind, be helpful, and be respectful).

## How to Contribute

### Reporting Issues

1. **Language**: We provide documentation in English and accept bug reports and comments in English.
2. Check the [Issue Tracker](https://github.com/intersoftdatalabs-in/ai-build-integrity-maven-plugin/issues) to see if the issue has already been reported.
3. If it hasn't, open a new issue. Provide as much detail as possible, including your environment, your `hashFileMode` configuration, and steps to reproduce.

### Making Changes

1. Fork the repository and create a new branch from `main`.
2. Make your changes. Ensure you include unit tests for any new functionality.
3. Ensure your code follows the project's style guide by running:

   ```bash
   mvn spotless:apply
   ```
4. Run all tests to ensure no regressions and verify there are no new compiler or Javadoc warnings:

   ```bash
   mvn clean verify
   ```
5. Update any relevant documentation (e.g., `README.md`, usage guides, or Javadocs).
6. Commit your changes with descriptive commit messages.
7. Push your branch to your fork and open a Pull Request.

### Pull Request Process

1. Provide a clear description of the problem being solved or the feature being added.
2. Complete the checklist in our [Pull Request Template](.github/PULL_REQUEST_TEMPLATE.md).
3. Link to the relevant issue if one exists.
4. A maintainer will review your pull request and may request changes.
5. Once approved, your pull request will be merged into the `main` branch.

## Development Environment

- **Java**: JDK 11 or newer is required.
- **Maven**: 3.9.x or newer is required.
- **Formatting**: We use the [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html). This is enforced by the `spotless-maven-plugin`.

## Releasing to Maven Central

This section documents the full release process for maintainers.

### Prerequisites

Before your first release, ensure you have:

1. **GPG key** — a GPG key pair for signing artifacts. Your public key must be published to a
   key server (e.g., `keys.openpgp.org` or `keyserver.ubuntu.com`).
2. **Central Portal credentials** — a `<server>` entry in your `~/.m2/settings.xml` with
   `<id>central</id>` and your Central Portal username/token.
3. **GitHub push access** — permission to push tags and commits to `main`.

Example `settings.xml` server entry:

```xml
<server>
    <id>central</id>
    <username>YOUR_CENTRAL_USERNAME</username>
    <password>YOUR_CENTRAL_TOKEN</password>
</server>
```

### Step 1 — Prepare the Release Version

Update the POM version from `SNAPSHOT` to the release version:

```bash
mvn versions:set -DnewVersion=1.0.2
```

Update the version references in `README.md` code examples to match the new
release version (replace `LATEST` with the actual version, e.g., `1.0.2`).

Commit the version change:

```bash
git add pom.xml README.md
git commit -m "Release 1.0.2"
```

### Step 2 — Build and Test Locally

Run a full build with signing enabled to verify everything works before publishing:

```bash
mvn clean verify -Dsign=true
```

This will:

- Compile and run all unit tests
- Generate sources and javadoc JARs
- Sign all artifacts with GPG
- Run Spotless formatting checks

Fix any issues before proceeding.

### Step 3 — Deploy to Central (Staging)

Deploy the signed artifacts to the Central Portal staging area:

```bash
mvn clean deploy -Dsign=true
```

Because `autoPublish` is set to `false`, this uploads the bundle to the Central Portal
but does **not** publish it automatically.

### Step 4 — Verify and Publish on Central Portal

1. Go to [central.sonatype.com](https://central.sonatype.com) and log in.
2. Navigate to your deployment/staging area.
3. Review the staged bundle — verify the artifact contents, POM metadata, signatures, and
   javadoc/sources JARs are all present.
4. Click **Publish** to release the artifact to Maven Central.

> **Note:** Once published, a release **cannot be undone**. Central artifacts are permanent.

### Step 5 — Tag and Prepare Next Development Iteration

After successful publishing, tag the release and bump to the next SNAPSHOT:

```bash
git tag v1.0.2
git push origin main --tags

mvn versions:set -DnewVersion=1.0.3-SNAPSHOT
```

Revert the `README.md` version references back to `LATEST`:

```bash
git add pom.xml README.md
git commit -m "Prepare next development iteration 1.0.3-SNAPSHOT"
git push origin main
```

### Using the Maven Release Plugin (Alternative)

Instead of manual version management, you can use the Maven Release Plugin:

```bash
mvn release:prepare -Dsign=true
mvn release:perform -Dsign=true
```

This will automatically:

- Strip `-SNAPSHOT` from the version
- Commit and tag the release
- Deploy to Central
- Bump to the next SNAPSHOT version

Remember to still verify and manually publish on the Central Portal since `autoPublish` is
`false`.

## Maintenance Policy

This project is actively maintained. We ensure that:
- Dependencies are updated weekly via **Dependabot**.
- Security vulnerabilities are prioritized and addressed immediately.
- The project follows a semantic versioning release cycle for new features and bug fixes.
- All incoming issues and pull requests are reviewed by maintainers in a timely manner.

## License

By contributing, you agree that your contributions will be licensed under the project's [Apache License, Version 2.0](LICENSE).
