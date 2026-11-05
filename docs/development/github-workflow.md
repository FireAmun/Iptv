# GitHub delivery and CI

Repository: [FireAmun/Iptv](https://github.com/FireAmun/Iptv). Base branch: `main`.

## Standing delivery workflow

The owner authorized this sequence for normal development without asking again at each push:

1. Work on a named branch and update `progress.md`.
2. Run relevant local checks and obtain code review.
3. Commit and push the work branch. Open a PR, or update the existing PR for that branch.
4. Wait for CI on the latest PR head and resolve failures. Missing, cancelled, pending, or failed checks do not count as passing.
5. Merge with the expected head SHA after checks pass. Squash merging is the normal project choice.
6. Confirm the merge, delete the remote/local work branch, and sync local `main` with a fast-forward-only pull.

Do not push code directly to `main` or force-push shared history. The initial empty `main` commit exists only to provide a PR base; all project files are introduced through the bootstrap PR.

When CI is the only remaining work, use a background `gh run watch <run-id> --repo FireAmun/Iptv --exit-status --interval 60` command and resume on completion. Keep its output in an ignored log. Do not spend model turns repeatedly checking unchanged status or dispatch an agent just to poll. After completion, inspect the latest jobs, actual XML and process logs. This records the owner's 5 November token-cost preference.

This is the agent/project delivery policy, not an unattended bot that merges arbitrary future pushes. GitHub Actions runs checks automatically; project agents open PRs, inspect those checks, and perform the authorized merge. Repository settings automatically remove merged head branches.

## What CI checks now

[The CI workflow](../../.github/workflows/ci.yml) runs on every PR update, pushes to `main`, and manual dispatch. Open/update the work branch's PR when pushing; the PR event checks that exact new head. This avoids duplicate device jobs for the branch push and its PR event. Unpublished branches can be tested using manual dispatch.

- Python unit tests for the repository link checker, including missing targets, invalid UTF-8, repository escape, encoded paths, and fenced examples.
- UTF-8 Markdown and inline local file links. It does not fetch external URLs, verify anchors, or parse reference-style links.
- PowerShell syntax for tracked development scripts without requiring local binary tools on the runner.
- Workflow validation using pinned actionlint 1.7.12.
- A final `CI` gate that fails when available checks fail or do not finish successfully.

CI requires the complete wrapper and runs `:core:model:test`, `:core:data:test`, `testDebugUnitTest`, `lintDebug`, `assembleDebug`, and `:app:assembleDebugAndroidTest` with Java 21 and SDK 36/Build Tools 36.0.0. It publishes the debug APK. A partial wrapper/settings setup fails CI rather than silently bypassing Android checks.

Device jobs run `:app:connectedDebugAndroidTest` on API 28 phone and API 36 Android TV emulators on disposable Linux runners. The final gate requires both matrix legs and the Android build to succeed. The complete registered instrumentation suite covers language/RTL, recreation, TV focus, source entry/cancellation, synthetic playback, cached catalogues, search/favourites and Continue Watching. Each device also runs the separate-process persistence probe, requiring distinct seed/restore PIDs and every source/progress verification marker. Inspect the actual latest-head XML and probe logs; the count grows as features are added. JVM checks cover model/parser/transport/ViewModel behavior, progress ordering and semantic colour contrast. These do not establish private-provider, codec, billing or physical-device compatibility. The old bootstrap Android skip was only repository coverage.

Actions are pinned to commit SHAs; workflow tokens have read-only repository access and checkout does not retain credentials. Gradle caches are read-only off `main`. No provider credentials or release signing material are required by this workflow.

The instrumented Compose journeys use a test-only `TestDiagnostics` rule. CI enables a 90-second per-test watchdog through `diagnosticTimeoutSeconds`; a timeout records enumerated cleanup phases and Java IDs/states/stack frames, then kills the instrumentation process so a partial suite cannot pass. It never prints arbitrary thread names, exception messages or source inputs. Logs are retained with device reports. The option is disabled by default for local/manual runs. A forced two-second local run was used to verify both failure and captured stacks.

## Local checks

```powershell
python -m unittest discover -s tests -v
python scripts/check_repository.py
.tools/actionlint/actionlint.exe
```

The actionlint executable is optional local tooling downloaded under ignored `.tools/`; CI installs the pinned Go module independently. Python checks use only the standard library. The local Markdown check examines Git-tracked and non-ignored untracked Markdown, excluding downloaded tools through `.gitignore`.

## Repository settings and current limitation

Automatic deletion of merged head branches is enabled. GitHub's API rejected branch protection with HTTP 403: this private repository requires GitHub Pro for that feature. Auto-merge also remains unavailable in repository settings. Consequently `main` is not server-protected by a required `CI` check; enforce the standing delivery workflow through the agent instructions and explicit CI inspection.

Do not bypass failed CI because server protection is unavailable. If the repository later gains supported branch protection, require PRs and the `CI` check for up-to-date branches, disable force pushes/deletion of `main`, and apply the rules to administrators. No plan upgrade or visibility change was made.
