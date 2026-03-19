# Contributing to AI Build Integrity Maven Plugin

Thank you for your interest in contributing! We welcome contributions of all kinds, including bug fixes, feature requests, and documentation improvements.

## Code of Conduct

All contributors are expected to follow our standard Code of Conduct (be kind, be helpful, and be respectful).

## How to Contribute

### Reporting Issues

1. Check the [Issue Tracker](https://github.com/intersoftdatalabs-in/ai-build-integrity-maven-plugin/issues) to see if the issue has already been reported.
2. If it hasn't, open a new issue. Provide as much detail as possible, including your environment, steps to reproduce, and expected vs. actual behavior.

### Making Changes

1. Fork the repository and create a new branch from `main`.
2. Make your changes. Ensure you include unit tests for any new functionality.
3. Ensure your code follows the project's style guide by running:

   ```bash
   mvn spotless:apply
   ```

4. Run all tests to ensure no regressions:

   ```bash
   mvn clean test
   ```

5. Commit your changes with descriptive commit messages.
6. Push your branch to your fork and open a Pull Request.

### Pull Request Process

1. Provide a clear description of the problem being solved or the feature being added.
2. Link to the relevant issue if one exists.
3. A maintainer will review your pull request and may request changes.
4. Once approved, your pull request will be merged into the `main` branch.

## Development Environment

- **Java**: JDK 11 or newer is required.
- **Maven**: 3.9.14 or newer is required.
- **Formatting**: We use the [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html). This is enforced by the `spotless-maven-plugin`.

## License

By contributing, you agree that your contributions will be licensed under the project's [Apache License, Version 2.0](LICENSE).
