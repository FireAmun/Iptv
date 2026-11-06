# Local AVD launching is disabled by the owner's 6 November 2026 host policy.
# The old phone/review AVDs were removed; this command must not recreate them.
$ErrorActionPreference = 'Stop'
throw @"
Local Android emulator launching is disabled by AGENTS.md (6 November 2026).
Use the existing GitHub Actions phone/TV matrix for device tests.
Do not restore or recreate the removed AVDs. Future local testing requires
explicit owner authorization for one fresh disposable AVD, a current usable
WHPX check, and stopped/disabled legacy drivers. Never use AEHD/GVM or HAXM.
"@
