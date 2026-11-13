# Emulator input-readiness compatibility

The workflow checks out the existing ReactiveCircus runner at immutable revision `a421e43855164a8197daf9d8d40fe71c6996bb0d`, then applies [this patch](emulator-input-readiness.patch) to its TypeScript and matching executable JavaScript. All other action code and dependencies remain at that revision. The publisher's Apache2 license remains in the checkout: [original license](https://github.com/ReactiveCircus/android-emulator-runner/blob/a421e43855164a8197daf9d8d40fe71c6996bb0d/LICENSE).

The extra read-only `service check input` predicate must succeed within the existing boot-poll attempt budget and job timeout before the original one-shot unlock. A failed probe continues ordinary boot polling; an unlock failure still stops the job. This follows the readiness problem discussed in [upstream issue489](https://github.com/ReactiveCircus/android-emulator-runner/issues/489) and [proposal495](https://github.com/ReactiveCircus/android-emulator-runner/pull/495), without adopting that fork's SDK-installer changes. Modified regions are identified in both files.

Before launching any guest, each device job runs five [bootstrap self-tests](../../tests/emulator-bootstrap.test.cjs) against the patched runtime. Commands, timers and SDK path are fake: missing service, transport error, timeout, unlock failure and pending boot are tested without an emulator. Full91-case device suites and persistence probes remain separate requirements.

Patch application is checked before execution and fails closed on incompatible source. Replace this compatibility layer only after reviewing a pinned upstream revision with equivalent readiness behavior and passing the same native gates.
