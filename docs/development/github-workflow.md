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

This is the agent/project delivery policy, not an unattended bot that merges arbitrary future pushes. GitHub Actions runs checks automatically; project agents open PRs, inspect those checks, and perform the authorized merge. Repository settings automatically remove merged head branches.

## What CI checks now

[The CI workflow](../../.github/workflows/ci.yml) runs on pushes, pull requests, and manual dispatch:

- Python unit tests for the repository link checker, including missing targets, invalid UTF-8, repository escape, encoded paths, and fenced examples.
- UTF-8 Markdown and inline local file links. It does not fetch external URLs, verify anchors, or parse reference-style links.
- PowerShell syntax for tracked development scripts without requiring local binary tools on the runner.
- Workflow validation using pinned actionlint 1.7.12.
- A final `CI` gate that fails when available checks fail or do not finish successfully.

Once Gradle settings/wrapper are added, CI requires a complete wrapper and runs `testDebugUnitTest`, `lintDebug`, and `assembleDebug` with Java 21 and Android SDK 36/Build Tools 36.0.0. Change these versions alongside the application's eventual pinned toolchain when necessary. A partial wrapper/settings setup fails CI rather than silently bypassing Android checks.

Until the Gradle app exists, the Android job is explicitly skipped and the run summary identifies bootstrap coverage. This does not prove playback, UI behavior, Android compilation, billing, or physical-device compatibility. Instrumentation tests will be added with the app's device-test plan.

Actions are pinned to commit SHAs; workflow tokens have read-only repository access and checkout does not retain credentials. Gradle caches are read-only off `main`. No provider credentials or release signing material are required by this workflow.

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
