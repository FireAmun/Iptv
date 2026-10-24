# Project handoff

- At the start of work in this project, read [progress.md](progress.md). Use its next-session handoff and open-item list to resume work.
- Read the linked product brief and architecture sections relevant to the task. Check actual workspace state rather than treating tracker entries as proof.
- Follow the user's latest instructions and recorded decisions; later explicit authorization supersedes older planning-stage status. Do not repeat already-resolved questions.
- Update `progress.md` after meaningful work and before handing off: current phase, delivered artifacts, verification evidence, open decisions/blockers, and next task.
- Keep secrets and private provider/source data out of project documentation, logs, and telemetry.
- The owner explicitly re-authorized local hands-on app testing on 24 October 2026. Use one emulator at a time alongside CI; do not change host firewall rules, virtualization drivers or reboot the laptop. Preserve the earlier unexpected-shutdown incident and its unresolved cause in environment notes.

## GitHub delivery workflow

- Remote: `https://github.com/FireAmun/Iptv.git`; base branch: `main`.
- The user authorized the normal delivery cycle: create a work branch, commit, push, open/update its PR, inspect CI, merge after it passes, and delete the merged remote/local work branch. Do not ask for this authorization again for ordinary project changes.
- Do not push code directly to `main`, force-push shared history, bypass checks, or merge while checks are missing, pending, cancelled, or failing.
- Run local checks appropriate to the change and obtain code review before opening/merging the PR. Inspect CI for the latest head revision; bind the merge to that exact head SHA so a later push cannot slip in unchecked.
- After merging, confirm the merge, sync local `main` with a fast-forward-only pull, and remove the merged work branch. Reuse an open PR for additional pushes on the same branch.
- CI checks repository tooling/docs/workflows, model/parser/loader/import-state JVM tests, Android build/lint, and phone/TV instrumentation. Require actual latest-head device results; the earlier bootstrap Android skip was not app or playback evidence.
- If GitHub plan/permissions prevent server-side branch protection, record the limitation in `progress.md` and still follow this workflow. The initial empty baseline commit is the sole bootstrap exception; all project files go through a PR.
