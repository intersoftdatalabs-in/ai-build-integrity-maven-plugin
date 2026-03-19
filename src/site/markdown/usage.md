# Usage

Choose the lifecycle placement based on whether your build rewrites protected files such as `AGENTS.md`, `SKILL.md`, or other matched instruction files.

## If Your Build Does Not Rewrite Protected Files

Use this setup when your build only reads instruction files, or when tools like Spotless run in check-only mode and do not modify matched files.

### Single-Module Project

```xml
<build>
    <plugins>
        <plugin>
            <groupId>com.intsof</groupId>
            <artifactId>ai-build-integrity-maven-plugin</artifactId>
            <version>${project.version}</version>
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
                <execution>
                    <id>clean-hashes</id>
                    <phase>clean</phase>
                    <goals>
                        <goal>clean-hashes</goal>
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
<plugin>
    <groupId>com.intsof</groupId>
    <artifactId>ai-build-integrity-maven-plugin</artifactId>
    <version>${project.version}</version>
    <executions>
        <execution>
            <id>generate-hashes</id>
            <phase>initialize</phase>
            <goals>
                <goal>generate-hashes</goal>
            </goals>
            <configuration>
                <!-- Generates hashes for the entire repository ONLY at the root at T=0 -->
                <executionRootOnly>true</executionRootOnly>
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
                <executionRootOnly>false</executionRootOnly>
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
                <executionRootOnly>true</executionRootOnly>
            </configuration>
        </execution>
    </executions>
</plugin>
```

## If Your Build Uses Formatters or Other File-Mutating Plugins

Use this setup when a formatter or any other plugin rewrites files matched by `ai.integrity.includes`. The key rule is simple: run all mutating plugins first, then run `generate-hashes` against the final file bytes, then run `verify-hashes` later in the lifecycle.

Common examples of mutating plugins include Spotless `apply`, license-header plugins, templating steps, or custom generators that rewrite Markdown or prompt files.

```xml
<build>
    <plugins>
        <plugin>
            <groupId>com.diffplug.spotless</groupId>
            <artifactId>spotless-maven-plugin</artifactId>
            <version>2.46.1</version>
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
            <version>${project.version}</version>
            <executions>
                <execution>
                    <id>generate-hashes</id>
                    <phase>process-sources</phase>
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
                <execution>
                    <id>clean-hashes</id>
                    <phase>clean</phase>
                    <goals>
                        <goal>clean-hashes</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

## Configuration Properties

|             Property             |                Default                |                          Description                          |
|----------------------------------|---------------------------------------|---------------------------------------------------------------|
| `ai.integrity.algorithm.bits`    | `256`                                 | Hash algorithm bit width: `256`, `384`, or `512`              |
| `ai.integrity.includes`          | `**/*.md`                             | Comma-separated glob patterns for files to hash               |
| `ai.integrity.excludes`          | `**/*.sha256,**/*.sha384,**/*.sha512` | Comma-separated glob patterns for files to exclude            |
| `ai.integrity.baseDir`           | `${project.basedir}`                  | Base directory to scan                                        |
| `ai.integrity.outputExtension`   | `auto`                                | Sidecar file extension. `auto` derives from algorithmBits     |
| `ai.integrity.skipExisting`      | `false`                               | Skip generating hashes for files that already have a sidecar  |
| `ai.integrity.skipDirs`          | `target,.git,node_modules,.tmp`       | Comma-separated directory names to skip                       |
| `ai.integrity.executionRootOnly` | `false`                               | If true, the mojo only executes in the reactor's root project |

