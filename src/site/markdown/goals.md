# Goals

## `ai-build-integrity:generate-hashes`

Walks the project base directory and generates companion hash sidecar files for every file matching the include patterns.

* **Default phase:** `validate`
* **Default includes:** `**/*.md`
* **Default excludes:** `**/*.sha256,**/*.sha384,**/*.sha512`

## `ai-build-integrity:verify-hashes`

Finds all hash sidecar files, recomputes the hash of the corresponding source file, and **fails the build** if any mismatch is detected.

* **Default phase:** `test`

