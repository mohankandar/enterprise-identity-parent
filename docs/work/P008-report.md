# P008 report — Norton trust-store fix moved out of the repo

## Environment variable

`MAVEN_OPTS` is visible:

| Scope | Value |
|---|---|
| PowerShell (`$env:MAVEN_OPTS`) | `-Djavax.net.ssl.trustStoreType=Windows-ROOT` |
| Windows user variable (registry) | `-Djavax.net.ssl.trustStoreType=Windows-ROOT` |
| Windows machine variable | not set |
| Git Bash (`$MAVEN_OPTS`) | `-Djavax.net.ssl.trustStoreType=Windows-ROOT` |

## Changes

- `.mvn/jvm.config` was removed with `git rm`. The repo has no `.mvn` folder any more, so nothing in it is specific
  to Windows or to this machine.
- One paragraph was added to the Build section of `CLAUDE.md`: Norton 360 on the owner's laptop, the `MAVEN_OPTS`
  user variable, and "never add machine-specific settings to the repo".

## Online build

- `mvn -U clean verify` (online, without `.mvn/jvm.config`): **BUILD SUCCESS, 283 tests, 0 failures, 0 errors**
  (identity-core 73, identity-spring 15, notifications 65, notifications-testkit 101, notifications-spring 29). No
  PKIX errors.
- That build downloaded nothing, because every artifact was already in `~/.m2`. To show that `MAVEN_OPTS` alone
  fixes downloads, I fetched two artifacts that were not in `~/.m2`, with `mvn dependency:get`:
  - With `MAVEN_OPTS` cleared, `commons-csv:1.9.0` failed with `PKIX path building failed`.
  - With the user variable, `commons-csv:1.10.0` was downloaded from `repo.maven.apache.org`, and the build
    succeeded.

  Both jars are only in the local Maven cache, not in the repo.

## LexaVault and SDV

They need no `.mvn/jvm.config`. The user variable covers every Maven build on this laptop. The advice in P007 to
add the file to those repos no longer applies.
