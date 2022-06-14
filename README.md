# Tools and Integrations tests for Nessie

This integrations tests project for Nessie leverages Gradle included builds for referenced
projects, where possible. This means, that IDEs (at least IntelliJ) allows you to work on all
three code bases (integrations tests, Nessie and Iceberg).

**IMPORTANT** Setup the required included builds as described below **before** you open this project
in your IDE!!

## For developers

The recommended way to "link" this project to "latest Nessie" and "latest Iceberg" is to put those
into the [`included-builds/`](included-builds) directory.

All "linked projects" (Nessie, Iceberg) must include the code changes (patches) that are necessary
to make the code bases work together. Think: Nessie requires code changes on top of the `main`
branch to let Nessie's Spark extensions work with the latest version of Iceberg. For this reason,
we maintain "integrations branches" with the necessary changes.

### Git worktree

The easiest way to implement this locally is to use [Git worktree](https://git-scm.com/docs/git-worktree).

1. Clone this repository and save the path in `NESSIE_INTEGRATION_TESTS`
   ```shell
   git clone https://github.com/projectnessie/nessie-integration-tests
   NESSIE_INTEGRATION_TESTS=$(realpath nessie-integration-tests)
   ```
2. Go to your local Nessie clone and create a Git worktree in the [`included-builds/`](included-builds)
   directory.
   ```shell
   cd PATH_TO_YOUR_LOCAL_NESSIE_CLONE
   git branch -b integ-bump/iceberg origin/integ-bump/iceberg
   git worktree add ${NESSIE_INTEGRATION_TESTS}/included-builds/nessie integ-bump/iceberg
   ```
3. Go to your local Iceberg clone and create a Git worktree in the [`included-builds/`](included-builds)
   directory.
   ```shell
   cd PATH_TO_YOUR_LOCAL_ICEBERG_CLONE
   git branch -b master-nessie origin/master
   git worktree add ${NESSIE_INTEGRATION_TESTS}/included-builds/iceberg master-nessie
   ```
   Note: the above example uses a Git worktree with a branch "detached" from the
   origin's `master` branch. As long as there are no code changes necessary, it might be way more
   convenient to just create a symbolic link to your local Iceberg clone containing the already
   checked out `master` branch.

### Symbolic links

As an alternative, you can also create symbolic links called `nessie` and `iceberg` to your local
clones/worktrees with the "right" code. Example:
```shell
ln -s INSERT_PATH_TO_YOUR_LOCAL_NESSIE_CLONE included-builds/nessie
ln -s INSERT_PATH_TO_YOUR_LOCAL_ICEBERG_CLONE included-builds/iceberg
```

### Checking if everything works

Canary build:
```bash
./gradlew :nessie:clients:client:jar :iceberg:iceberg-nessie:jar :iceberg:iceberg-core:jar
```

Run Iceberg/Nessie tests:
```bash
./gradlew :iceberg:iceberg-nessie:test
```

Run Nessie Spark 3.2 Extensions tests:
```bash
./gradlew :nessie:clients:spark-32-extensions:intTest
```

Run the actual integrations tests:
```bash
./gradlew intTest
```

## Included Maven projects

This project can also use Maven projects, currently Presto, which has support for Nessie.

While Gradle supports included builds and supports substitution of dependencies, Maven builds can
only rely on local Maven repositories. This means, that before any Maven based project can be
tested, Nessie and Iceberg need to be built and published to the local Maven repository.

This project has, in theory, everything that's needed to publish snnapshot artifacts to your local
Maven repo directly by just running `./gradlew publisLocal`. Sadly doesn't work yet.

## In CI

CI builds are triggered using GitHub actions. See [main.yml](.github/workflows/main.yml).

CI will fetch the latest commit from any project's main/master branch and apply the necessary
changes by merging the configured "patch branch".
