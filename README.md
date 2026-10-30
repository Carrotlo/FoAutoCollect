# FoAutoCollect

[![Modrinth](https://img.shields.io/badge/Download-Modrinth-1bd96a?logo=modrinth&logoColor=white)](https://modrinth.com/plugin/foautocollect) [![Versions](https://img.shields.io/badge/Supported_versions-Modrinth-1bd96a)](https://modrinth.com/plugin/foautocollect/versions) [![Discord](https://img.shields.io/badge/Support-Discord-5865f2?logo=discord&logoColor=white)](https://discord.gg/kEF77QCfu7)

Automatic item and experience collection for player-owned rewards.

*Developed by Carrotio.*

## Features

- Collects eligible item drops and experience for the player who earned them.
- Supports block breaks, mob kills, fishing, shearing, explosions, and FoDrops rewards.
- Lets players toggle collection and view their current state.
- Provides configurable full-inventory handling and forced/default modes.
- Fires an event after item batches are collected for other plugins to observe.

## Download and dependencies

Download the latest compatible version from [Modrinth](https://modrinth.com/plugin/foautocollect). Check the [versions page](https://modrinth.com/plugin/foautocollect/versions) for supported Minecraft versions and server platforms.

- **Required:** None
- **Optional integrations:** `FoDrops`, `PlaceholderAPI`

## Support

For help or issue reports, join the [Carrotio Discord server](https://discord.gg/kEF77QCfu7).

<details>
<summary><strong>Commands and permissions</strong></summary>

### Commands

- `/foautocollect [status|on|off]` — Toggle auto collect for yourself. Aliases: `/autocollect`.
- `/foautocollectadmin <version|reload|editor|set|force>` — Admin tools for FoAutoCollect. Aliases: `/autocollectadmin`.

### Permissions

- `foautocollect.use` — Allows toggling personal auto collect. Default: `true`.
- `foautocollect.admin` — Allows access to FoAutoCollect admin commands and editor. Default: `op`.

</details>

<details>
<summary><strong>PlaceholderAPI</strong></summary>

Requires PlaceholderAPI.

- `%foautocollect_state%` — Player's current auto-collect state.

</details>

<details>
<summary><strong>Configuration</strong></summary>

- `config.yml` — Main behavior and feature settings.
- `messages.yml` — Player-facing messages and interface text.
- `sounds.yml` — Configurable interaction sounds.

Main config areas: `force-enabled`, `default-enabled`, `native-dialogs`, `auto-collect`, `full-inventory`.

</details>

<details>
<summary><strong>Developer API</strong></summary>

Compile against the matching jar (`provided` in Maven, `compileOnly` in Gradle); install it locally if needed. Declare `FoAutoCollect` in your plugin metadata (`softdepend` for an optional integration).

```xml
<dependency>
  <groupId>me.foesio</groupId>
  <artifactId>FoAutoCollect</artifactId>
  <version>2.1</version>
  <scope>provided</scope>
</dependency>
```

```java
import me.foesio.foAutoCollect.api.AutoCollectService;
import me.foesio.foAutoCollect.event.AutoCollectExperienceCollectedEvent;

AutoCollectService api = Bukkit.getServicesManager().load(AutoCollectService.class);
if (api != null) api.isActive(player);

@EventHandler
public void onAutoCollectExperienceCollectedEvent(AutoCollectExperienceCollectedEvent event) {
    Bukkit.getLogger().info(event.getClass().getSimpleName());
}
```

**API methods** (overloads grouped):

- `AutoCollectService`: `isActive`, `getSessionPreference`, `requestPreference`, `offerReward`.

**Other API types:** `CollectionSource`, `PreferenceChangeResult`, `RewardOfferResult`.

**Events:** `AutoCollectExperienceCollectedEvent`, `AutoCollectItemsCollectedEvent`, `AutoCollectPreCollectEvent`, `AutoCollectPreferenceChangedEvent`.

[Browse API source](src/main/java/me/foesio/foAutoCollect/api) for signatures and event details.

</details>
