# TV test cleanup investigation

25 October 2026. Scope: instrumentation harness; production locale/playback behavior is unchanged by this fix.

Cloud TV attempts timed out after test locale restoration; phone tests and complete local TV runs had passed. Supported `swiftshader` was tried after renderer warnings, but did not resolve the stall. A separate attempt failed during emulator unlock before tests. None establishes an app playback failure or a GPU root cause.

[Diagnostic run 40140423491](https://github.com/FireAmun/Iptv/actions/runs/40140423491), head `7dd3fba96cfb3207a267c93baa70d137c95914a8`, captured a stall in CancellationFlowTest after `AFTER_LOCALE_RESET`, before the subsequent Compose idle wait returned. The same phase reproduced locally in WelcomeTest during Cinema development. Both dumps show main in `MessageQueue.nativePollOnce`, while the instrumentation thread waits through `Espresso.onIdle` and `AndroidComposeUiTestEnvironment.waitForNextChoreographerFrame`. The opt-in watchdog failed the run and its stacks were retained in device-report logcat.

Exact Compose ui-test 1.10.4 source posts a callback on an attached view and waits for a following frame; Espresso 3.7.0 schedules its idle work via the main handler. The observed race follows an unasserted locale-triggered activity relaunch during cleanup. The deeper framework/barrier mechanism is not established; graphics warnings alone do not explain it.

`RestoreLocalesRule` now wraps the Compose rule, so restoration runs after the scenario closes and Compose disposes. It restores the original locale, including after an assertion failure. API 33+ uses the target context's platform LocaleManager without requiring a live delegate; earlier devices use AppCompat. All explicit language switches, RTL assertions, both recreations, import, playback and lifecycle assertions remain. The diagnostic rule stays outermost to cover teardown failures.

Require complete latest-head cloud phone and TV results before merging. The watchdog is a failed-run diagnostic, not a test bypass; its forced two-second failure and captured stacks were verified locally.
