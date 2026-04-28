# Post-MVP Forge Backlog

Lightweight intake for non-MVP-blocking findings discovered during testing.
Source-of-truth for freeze/defer policy: `documentation/decisions/FreezeNowDeferSafely.md`.

## Intake Rules
- Keep each item to <= 10 lines.
- One item = one clear problem.
- No deep design here; link out if needed.
- Review only at checkpoint/revisit triggers.

## Items

### [PM-001] Remove matched player from queue
- **Status:** Proposed
- **Found in:** local Forge testing
- **Problem:** After a player successfully finds a match, the player should be removed from the active queue state.
- **Why it matters:** reliability and UX
- **MVP impact:** Not blocking (defer-safe)
- **Proposed direction:** On successful match allocation, finalize queue lifecycle by immediately clearing the player's queue ticket/state.
- **Revisit trigger:** First cloud push readiness review
- **Estimate:** S
- **Owner:** Unowned
- **Links:** `documentation/decisions/FreezeNowDeferSafely.md`
