# Global Tier Tagger

<img src="branding/icon-512.png" width="96" height="96" alt="Global Tier Tagger">

A client-side Fabric mod by **raincakecat** for viewing Minecraft PvP tiers
from 17 tier lists in one place.

## Download

Get the JAR for your Minecraft version from [Releases](https://github.com/raincakecat/global-tier-tagger/releases).
Install matching versions of **Fabric Loader** and **Fabric API**, then put
the JAR in your `mods` folder. Replace older copies; do not install both.

Supports **1.21.11, 26.1.2, 26.2, and 26.3**. No server installation is needed.

## Features

- Show up to three tiers above player names and your primary tier in tab.
- Choose tier lists, game modes, and game-mode icons.
- Browse full player profiles, grouped by tier list or game mode.
- Check restrictions locally, including an on-demand online-player scan.
- Find provider status, community Discords, and support in the menu.

Untested modes stay hidden. Results are cached, so new tiers may take time
to appear. Only supported premium Minecraft identities are used.

## Controls

**I** opens settings. **P** opens the nearest loaded player's profile.
Both keys can be changed in Minecraft Controls.

- `/tiertagger` opens settings.
- `/check <username>` checks restrictions in local chat.
- `/tiertagger check <username>` is the alternative restriction command.

## Tier lists

McTiers, PvP Tiers, SubTiers, Lucnoxity, tiertests.com, MCPVP.com, PvPHQ,
Nova Tiers, Prestige Tiers, Lunar Tiers, Nexus Tiers, JP Tiers, TierMC,
Crack Tiers, Global Tierlist, PrimeTiers, and Collapse Tiers.

PrimeTiers uses premium username records; username changes or reuse can affect its ranks.
Collapse Tiers displays ranks only for profiles with a linked Minecraft identity.

## Source and support

[Build instructions](docs/building.md) · [Changelog](CHANGELOG.md) ·
[Support Discord](https://discord.gg/j3QxZ7xYR4)

Global Tier Tagger is an independent project, not an official mod of the
listed providers.

## License

**All rights reserved.** Do not redistribute or reuse the code, JARs,
assets, or modified versions without written permission from raincakecat.
Official compiled releases may be installed for personal gameplay.
See [LICENSE](LICENSE) and [third-party notices](THIRD-PARTY-NOTICES.md)
for the full terms, including GitHub's platform permissions.
