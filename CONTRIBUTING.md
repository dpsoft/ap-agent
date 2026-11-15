# Contributing

Issues and pull requests are welcome. Please run the full build before opening a pull request.

## Building and testing

```shell
mvn verify -Dgpg.skip=true          # unit tests, smoke tests with -javaagent and the compatibility tests
scripts/bench-startup.sh            # agent startup time and loaded classes
mvn -Passets verify -Dgpg.skip=true # regenerate the images in docs/assets
```

* **Unit tests** (`src/test/java`) cover the parsing and the endpoint, against the real profiler in the test JVM.
* **Smoke tests** (`*IT`, run by failsafe after `package`) attach the shaded jar to a separate JVM with `-javaagent`.
* **Compatibility tests** (`CompatibilityIT`) download the last release from Maven Central and compare every response
  with the current build; intended differences are declared in the test, and `target/compatibility-report.md` lists
  them all.
* **Coverage**: JaCoCo measures the unit tests (`target/site/jacoco`) and, through an agent attached to the smoke
  test JVMs, the shaded jar (`scripts/coverage-it.sh` → `target/site/jacoco-it`). CI uploads both to Codecov
  (repository secret `CODECOV_TOKEN`), which merges them.
* **Documentation examples** (`src/test/java/io/github/dpsoft/ap/examples`) are compiled and run, and
  `ReadmeExamplesTest` checks that the docs show exactly that code: change both together.

The project targets Java 11 (`--release 11`); CI builds on JDK 11, 17, 21 and 25, and on Linux arm64.

## Checking the live links of the docs

The docs link to the interactive HTML profiles through htmlpreview.github.io, which only works once the files are on
`main`. `CheckLiveLinks` opens every one of them in headless Chrome and checks that the profile really renders:

```shell
mvn -q -Passets dependency:build-classpath -Dmdep.outputFile=target/pw.cp -Dmdep.includeScope=test
java -cp "$(cat target/pw.cp)" scripts/assets/CheckLiveLinks.java                         # after merging
java -cp "$(cat target/pw.cp)" scripts/assets/CheckLiveLinks.java integration/modernize   # before, on a branch
```

## Releasing

1. Set the release version in `pom.xml` (and in the docs, which show it in the download links).
2. `mvn clean deploy` publishes the signed artifacts to Maven Central (needs the Central credentials and the GPG key).
3. Run `CheckLiveLinks` against `main` (see above).
4. Create a GitHub release with the tag `v<version>`: the `Release` workflow builds it again and attaches
   `ap-agent-<version>.jar` and `ap-agent.jar` (for the `releases/latest/download/ap-agent.jar` link).
5. Bump `ap-agent.baseline.version` in `pom.xml` to the new release and empty `EXPECTED_CHANGES` in
   `CompatibilityIT`: the next comparison starts from the new release, and every later difference must be declared.
