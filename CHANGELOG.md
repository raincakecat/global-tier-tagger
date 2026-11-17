# Changelog

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
