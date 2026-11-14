# Global Tier Tagger 1.0.1 reliability audit

## Changes

- Filter unsupported/non-premium UUID formats before ranking requests.
- Keep HTTP 400/422 rejections and malformed or mismatched profiles isolated
  to that player. These failures are not cached as unranked results.
- Keep provider-wide pauses for service failures and rate limits. Preserve
  advertised Retry-After pauses across disconnects and manual Retry.
- Pace requests after both success and failure, with at least one second
  between starts; increase spacing for lower advertised limits.
- Keep queues bounded, merge pending duplicates, and prioritize the profile
  the user is inspecting, even when background requests fill the queue.
- Report the current player's queued, checking, cooldown, failed, and complete
  outcomes independently of other online players' requests.
- Retry deferred foreground lookups while their profile remains open.
- Bound complete response bodies and timeouts; cancel obsolete requests
  and prevent responses from a previous connection overwriting new results.
- Preserve successful Nexus mode results on a partial endpoint failure.
- Keep good Lunar batch profiles when another player is missing or invalid,
  and respect the server's advertised cache interval.
- Validate TierTests rank-owner UUIDs and reject McTiers error objects instead of caching them as unranked.
- Keep premium UUID matching, PvPHQ's one-game minimum, supplied icon sets,
  provider ordering, rank formats, and the existing UI layout.

The ordinary five-minute profile caches, tiertests.com's four-hour cache,
Nova's hourly snapshot, and Crack Tiers' fifteen-minute snapshot remain.
Lunar still normally refreshes after its advertised 60 seconds.

## Live adapter checks

Each of the 14 Java adapters completed a public read-only lookup during the
2026-11-14 audit. Ranked samples were checked for McTiers, PvP Tiers,
SubTiers, Lucnoxity, tiertests.com, Prestige Tiers, Lunar Tiers, Nexus Tiers,
JP Tiers and TierMC. MCPVP.com's parser also accepted a ranked sample;
its transport test accepted a valid unranked profile. PvPHQ's sampled
profile had no eligible played modes, so none were displayed. Nova and
Crack Tiers' UUID snapshots parsed successfully; Crack Tiers only includes
premium-marked records with a supported UUID.

No private keys, production player databases, response dumps, local paths,
or research conversations are included in this repository.

## Regression and compatibility scope

52 common tests cover identity validation, rejected and missing profiles,
UUID mismatches, caching, duplicate merging, rate-limit pauses, request
spacing, foreground priority, full queues, disconnect generations, partial
Nexus results, partial Lunar batches, response bounds, and restriction
cancellation/history behavior.

Client tests check the menu's actual per-profile progress and error labels,
as well as the existing keybind, tag, preview, scrolling, and restriction
flows. The compatibility manifest records three Fabric API releases for
each supported Minecraft version.

External service outages, upstream data changes, and every possible
third-party mod/server combination cannot be ruled out. Errors remain
visible and incomplete responses are not described as clean results.
