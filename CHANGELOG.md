# Changelog

## 1.0.3

- Reduced background refresh traffic while keeping everyone in tab eligible for tier lookup.
- Kept cached ranks and no-rank results across reconnects and server switches.
- Collected fuller batches and stopped pending requests when disconnected or a tier list is deselected.
- Added longer delays after repeated service failures and local request/download counters in provider support reports.
- Updated PrimeTiers to its current API address.

Nearby players refresh after at least five minutes; distant tab players refresh after at least fifteen minutes. Provider-specific longer cache times still apply. Opening a profile can refresh it sooner when its normal cache expires. New tiers may take longer to appear in background tab results.

Replace your old JAR with the **1.0.3** file matching your Minecraft version. Install only one Global Tier Tagger JAR.

Supports **1.21.11, 26.1.2, 26.2, and 26.3** with Fabric Loader and Fabric API.

[Support Discord](https://discord.gg/j3QxZ7xYR4)


## 1.0.2

- Added Global Tierlist, PrimeTiers, and Collapse Tiers.
- Made the new lists available in tag selection, full player profiles, Status, and Info.
- Added cached group lookups for PrimeTiers and Collapse Tiers.

PrimeTiers uses premium username records, so renamed or reused usernames may affect its results. Collapse Tiers requires a linked Minecraft identity before ranks appear.

Replace the old JAR with the 1.0.2 file matching your Minecraft version.

## 1.0.1

- Fixed unsupported player UUIDs causing rejected API requests and blocking other players' lookups.
- Improved request queues, retry handling, and response validation across the tier-list integrations.
- Prioritized opened player profiles and made queued, retrying, and partial-result messages more accurate.
- Preserved successful results when only part of a lookup fails.
- Improved restriction-checker timeout and cancellation handling.

Replace the old JAR with the 1.0.1 file matching your Minecraft version.
