# Usage

Choose the lifecycle placement based on whether your build rewrites protected files such as `AGENTS.md`, `SKILL.md`, or other matched instruction files.

## Minimal Config

The most common enterprise setup uses `CENTRAL` ledger mode to eliminate sidecar pollution across the source directory, relying entirely on the plugin's secure defaults.

### Single-Module Project

```xml
<build>
    <plugins>
        <plugin>
            <groupId>com.intsof</groupId>
            <artifactId>ai-build-integrity-maven-plugin</artifactId>
            <version>${project.version}</version>
            <configuration>
                <hashFileMode>CENTRAL</hashFileMode>
            </configuration>
            <executions>
                <execution>
                    <id>generate</id>
                    <goals><goal>generate-hashes</goal></goals>
                </execution>
                <execution>
                    <id>verify</id>
                    <goals><goal>verify-hashes</goal></goals>
                </execution>
                <execution>
                    <id>clean</id>
                    <goals><goal>clean-hashes</goal></goals>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

### Monorepo (Parent POM)

Add to the parent POM's `<build><plugins>` section. The parent project will secure the entire repository at T=0 natively.

```xml
<build>
    <plugins>
        <plugin>
            <groupId>com.intsof</groupId>
            <artifactId>ai-build-integrity-maven-plugin</artifactId>
            <version>${project.version}</version>
            <configuration>
                <hashFileMode>CENTRAL</hashFileMode>
            </configuration>
            <executions>
                <execution>
                    <id>generate</id>
                    <goals><goal>generate-hashes</goal></goals>
                    <configuration>
                        <!-- Generates hashes for the entire repository ONLY at the root at T=0 -->
                        <executionRootOnly>true</executionRootOnly>
                    </configuration>
                </execution>
                <execution>
                    <id>verify</id>
                    <goals><goal>verify-hashes</goal></goals>
                    <!-- Verify automatically runs in every child module -->
                </execution>
                <execution>
                    <id>clean</id>
                    <goals><goal>clean-hashes</goal></goals>
                    <configuration>
                        <!-- Cleans the entire repository's hashes only once at the root -->
                        <executionRootOnly>true</executionRootOnly>
                    </configuration>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

---

## Full Options

## If Your Build Does Not Rewrite Protected Files

Use this setup when your build only reads instruction files, or when tools like Spotless run in check-only mode and do not modify matched files.

### Single-Module Project (Full Options)

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
                    <configuration>
                        <algorithmBits>256</algorithmBits>
                        <includes>**/*.md</includes>
                        <excludes>**/*.sha256,**/*.sha384,**/*.sha512</excludes>
                        <skipDirs>target,.git,node_modules,.tmp</skipDirs>
                        <executionRootOnly>false</executionRootOnly>
                        <gitignoreAutoExclude>false</gitignoreAutoExclude>
                        <forceIncludes></forceIncludes>
                        <hideHashFiles>true</hideHashFiles>
                        <hashFileMode>SIDECAR</hashFileMode>
                        <skip>false</skip>
                        <normalizeLineEndings>false</normalizeLineEndings>
                    </configuration>
                </execution>
                <execution>
                    <id>verify-hashes</id>
                    <phase>test</phase>
                    <goals>
                        <goal>verify-hashes</goal>
                    </goals>
                    <configuration>
                        <algorithmBits>256</algorithmBits>
                        <includes>**/*.md</includes>
                        <excludes>**/*.sha256,**/*.sha384,**/*.sha512</excludes>
                        <skipDirs>target,.git,node_modules,.tmp</skipDirs>
                        <executionRootOnly>false</executionRootOnly>
                        <gitignoreAutoExclude>false</gitignoreAutoExclude>
                        <forceIncludes></forceIncludes>
                        <hideHashFiles>true</hideHashFiles>
                        <hashFileMode>SIDECAR</hashFileMode>
                        <skip>false</skip>
                        <normalizeLineEndings>false</normalizeLineEndings>
                        <failOnError>true</failOnError>
                        <generateAuditReport>false</generateAuditReport>
                    </configuration>
                </execution>
                <execution>
                    <id>clean-hashes</id>
                    <phase>clean</phase>
                    <goals>
                        <goal>clean-hashes</goal>
                    </goals>
                    <configuration>
                        <algorithmBits>256</algorithmBits>
                        <includes>**/*.md</includes>
                        <excludes>**/*.sha256,**/*.sha384,**/*.sha512</excludes>
                        <skipDirs>target,.git,node_modules,.tmp</skipDirs>
                        <executionRootOnly>false</executionRootOnly>
                        <gitignoreAutoExclude>false</gitignoreAutoExclude>
                        <forceIncludes></forceIncludes>
                        <hideHashFiles>true</hideHashFiles>
                        <hashFileMode>SIDECAR</hashFileMode>
                        <skip>false</skip>
                    </configuration>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

### Monorepo (Parent POM - Full Options)

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
                <algorithmBits>256</algorithmBits>
                <includes>**/*.md</includes>
                <excludes>**/*.sha256,**/*.sha384,**/*.sha512</excludes>
                <skipDirs>target,.git,node_modules,.tmp</skipDirs>
                <executionRootOnly>true</executionRootOnly>
                <gitignoreAutoExclude>false</gitignoreAutoExclude>
                <forceIncludes></forceIncludes>
                <hideHashFiles>true</hideHashFiles>
                <hashFileMode>SIDECAR</hashFileMode>
                <skip>false</skip>
                <normalizeLineEndings>false</normalizeLineEndings>
                <failOnError>true</failOnError>
                <generateAuditReport>false</generateAuditReport>
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
                <algorithmBits>256</algorithmBits>
                <includes>**/*.md</includes>
                <excludes>**/*.sha256,**/*.sha384,**/*.sha512</excludes>
                <skipDirs>target,.git,node_modules,.tmp</skipDirs>
                <executionRootOnly>false</executionRootOnly>
                <gitignoreAutoExclude>false</gitignoreAutoExclude>
                <forceIncludes></forceIncludes>
                <hideHashFiles>true</hideHashFiles>
                <hashFileMode>SIDECAR</hashFileMode>
                <skip>false</skip>
                <normalizeLineEndings>false</normalizeLineEndings>
                <failOnError>true</failOnError>
                <generateAuditReport>false</generateAuditReport>
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
                <algorithmBits>256</algorithmBits>
                <includes>**/*.md</includes>
                <excludes>**/*.sha256,**/*.sha384,**/*.sha512</excludes>
                <skipDirs>target,.git,node_modules,.tmp</skipDirs>
                <executionRootOnly>true</executionRootOnly>
                <gitignoreAutoExclude>false</gitignoreAutoExclude>
                <forceIncludes></forceIncludes>
                <hideHashFiles>true</hideHashFiles>
                <hashFileMode>SIDECAR</hashFileMode>
                <skip>false</skip>
                <normalizeLineEndings>false</normalizeLineEndings>
                <failOnError>true</failOnError>
                <generateAuditReport>false</generateAuditReport>
            </configuration>
        </execution>
    </executions>
</plugin>
```

#### Standalone Module Builds

When building individual modules within a Monorepo, the plugin natively secures the developer workflow:

1. **Running from a child directory** (`cd child-b && mvn test`): Because Maven is invoked from the child directory, it officially becomes the execution root for that session. `generate-hashes` securely runs locally at T=0.
2. **Targeting via Project List** (`mvn test -pl child-b`): The root parent is the execution root but omitted from the build. `generate-hashes` is safely bypassed. Because no fresh hashes are generated for the omitted root execution, the child module simply verifies whatever `.sha` files currently exist on your local disk from a previous global generation. To explicitly establish a fresh tamper-seal during a targeted build, use `mvn ai-build-integrity:generate-hashes -pl child-b`.

## Securing Entire Applications (Mixed & Frontend Projects)

To seal all source code, resources, scripts, and front-end application files, simply expand the `<includes>` tag with comma-separated glob patterns.

Because the plugin's `skipDirs` configuration natively ignores `node_modules`, `target`, and `.git` by default, the file scanner will effortlessly navigate heavy front-end repositories containing hundreds of thousands of dependencies without sacrificing any performance. You can also set `<gitignoreAutoExclude>true</gitignoreAutoExclude>` to natively read your repository's `.gitignore` files and automatically exclude local development caches or IDE folders like `.idea/`. If there are critical hidden files (like `.env`) that you intentionally ignore in Git but still want to secure, you can strictly override this logic using `<forceIncludes>**/.env</forceIncludes>`.

```xml
<configuration>
    <!-- Secure BOTH the Java Backend and the TS/React JS Frontend -->
    <algorithmBits>256</algorithmBits>
    <includes>
        **/*.java,**/*.xml,**/*.properties,**/*.yaml,**/*.sh,
        **/*.ts,**/*.tsx,**/*.js,**/*.jsx,**/*.json,
        **/*.css,**/*.scss,**/*.html,**/*.md
    </includes>
    <excludes>**/*.sha256,**/*.sha384,**/*.sha512</excludes>
    <skipDirs>target,.git,node_modules,.tmp</skipDirs>
    <executionRootOnly>true</executionRootOnly>
    <gitignoreAutoExclude>false</gitignoreAutoExclude>
    <forceIncludes></forceIncludes>
    <hideHashFiles>true</hideHashFiles>
    <hashFileMode>SIDECAR</hashFileMode>
    <skip>false</skip>
    <normalizeLineEndings>false</normalizeLineEndings>
    <failOnError>true</failOnError>
    <generateAuditReport>false</generateAuditReport>
</configuration>
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
                    <configuration>
                        <algorithmBits>256</algorithmBits>
                        <includes>**/*.md</includes>
                        <excludes>**/*.sha256,**/*.sha384,**/*.sha512</excludes>
                        <skipDirs>target,.git,node_modules,.tmp</skipDirs>
                        <executionRootOnly>false</executionRootOnly>
                        <gitignoreAutoExclude>false</gitignoreAutoExclude>
                        <forceIncludes></forceIncludes>
                        <hideHashFiles>true</hideHashFiles>
                        <hashFileMode>SIDECAR</hashFileMode>
                        <skip>false</skip>
                        <normalizeLineEndings>false</normalizeLineEndings>
                        <failOnError>true</failOnError>
                        <generateAuditReport>false</generateAuditReport>
                    </configuration>
                </execution>
                <execution>
                    <id>verify-hashes</id>
                    <phase>test</phase>
                    <goals>
                        <goal>verify-hashes</goal>
                    </goals>
                    <configuration>
                        <algorithmBits>256</algorithmBits>
                        <includes>**/*.md</includes>
                        <excludes>**/*.sha256,**/*.sha384,**/*.sha512</excludes>
                        <skipDirs>target,.git,node_modules,.tmp</skipDirs>
                        <executionRootOnly>false</executionRootOnly>
                        <gitignoreAutoExclude>false</gitignoreAutoExclude>
                        <forceIncludes></forceIncludes>
                        <hideHashFiles>true</hideHashFiles>
                        <hashFileMode>SIDECAR</hashFileMode>
                        <skip>false</skip>
                        <normalizeLineEndings>false</normalizeLineEndings>
                        <failOnError>true</failOnError>
                        <generateAuditReport>false</generateAuditReport>
                    </configuration>
                </execution>
                <execution>
                    <id>clean-hashes</id>
                    <phase>clean</phase>
                    <goals>
                        <goal>clean-hashes</goal>
                    </goals>
                    <configuration>
                        <algorithmBits>256</algorithmBits>
                        <includes>**/*.md</includes>
                        <excludes>**/*.sha256,**/*.sha384,**/*.sha512</excludes>
                        <skipDirs>target,.git,node_modules,.tmp</skipDirs>
                        <executionRootOnly>false</executionRootOnly>
                        <gitignoreAutoExclude>false</gitignoreAutoExclude>
                        <forceIncludes></forceIncludes>
                        <hideHashFiles>true</hideHashFiles>
                        <hashFileMode>SIDECAR</hashFileMode>
                        <skip>false</skip>
                        <normalizeLineEndings>false</normalizeLineEndings>
                    </configuration>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

## Configuration Properties

|              Property               |                Default                |                                Description                                |
|-------------------------------------|---------------------------------------|---------------------------------------------------------------------------|
| `ai.integrity.algorithm.bits`       | `256`                                 | Hash algorithm bit width: `256`, `384`, or `512`                          |
| `ai.integrity.includes`             | `**/*.md`                             | Comma-separated glob patterns for files to hash                           |
| `ai.integrity.excludes`             | `**/*.sha256,**/*.sha384,**/*.sha512` | Comma-separated glob patterns for files to exclude                        |
| `ai.integrity.baseDir`              | `${project.basedir}`                  | Base directory to scan                                                    |
| `ai.integrity.outputExtension`      | `auto`                                | Sidecar file extension. `auto` derives from algorithmBits                 |
| `ai.integrity.skipExisting`         | `false`                               | Skip generating hashes for files that already have a sidecar              |
| `ai.integrity.skipDirs`             | `target,.git,node_modules,.tmp`       | Comma-separated directory names to skip                                   |
| `ai.integrity.executionRootOnly`    | `false`                               | If true, the mojo only executes in the reactor's root project             |
| `ai.integrity.gitignoreAutoExclude` | `false`                               | If true, parses local `.gitignore` files to auto-skip paths               |
| `ai.integrity.forceIncludes`        | `""`                                  | Comma-separated glob patterns to strictly include, bypassing `.gitignore` |
| `ai.integrity.hideHashFiles`        | `true`                                | If false, does not hide the generated hash sidecar files cross-platform   |

