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

Compile against the matching FoAutoCollect jar as `provided` (Maven) or `compileOnly` (Gradle). Do not shade FoAutoCollect into a consumer. There is no published API artifact coordinate promised by this project; use the matching plugin jar. For example, install a local copy with `mvn install:install-file -Dfile=/path/to/FoAutoCollect-2.1.jar -DgroupId=me.foesio -DartifactId=FoAutoCollect -Dversion=2.1 -Dpackaging=jar`, then use the following dependency:

```xml
<dependency>
  <groupId>me.foesio</groupId>
  <artifactId>FoAutoCollect</artifactId>
  <version>2.1</version>
  <scope>provided</scope>
</dependency>
```

For a Bukkit `plugin.yml` consumer, add `softdepend: [FoAutoCollect]` (or `depend` if required). Put direct FoAutoCollect class references in an integration class loaded only when FoAutoCollect is enabled.

```java
if (Bukkit.getPluginManager().isPluginEnabled("FoAutoCollect")) {
    RegisteredServiceProvider<AutoCollectService> registration =
        Bukkit.getServicesManager().getRegistration(AutoCollectService.class);
    if (registration != null) {
        AutoCollectService collect = registration.getProvider();
        if (collect.isActive(player)) {
            RewardOfferResult result = collect.offerReward(player, reward, dropLocation);
            if (!result.handled()) {
                // Your plugin still owns and must deliver the reward.
            }
        }
    }
}
```

Import `me.foesio.foAutoCollect.api.*`; Bukkit provides `Bukkit` and `RegisteredServiceProvider`. Get the service after FoAutoCollect enables, and reacquire it after a disable/re-enable. The service is unregistered on disable. Call its methods on the Bukkit primary thread; off-thread calls throw. The normal collection listeners run in their source Bukkit event or scheduled-task context. Folia/region-thread operation has not been validated.

### Player state

- `isActive(player)` returns effective state: `force-enabled` wins, then a session preference, then `default-enabled`.
- `getSessionPreference(uuid)` returns an empty `Optional` when the player has not set an override this session. Preferences are not persisted across restarts.
- `requestPreference(player, enabled)` is for a player-style toggle. It returns `FORCED` without changing state while force mode is active, `UNCHANGED` for an existing equal preference, or `CHANGED`. It requires an online player and throws if FoAutoCollect is disabled. It does not check the caller's permissions; consumers must enforce their own UI/command permissions. Admin commands may set the session preference during force mode.
- `AutoCollectPreferenceChangedEvent` fires **after** a stored preference changes through player commands, admin commands, or the service. It is non-cancellable and includes old/new effective state. A new explicit preference equal to the default still counts as a preference change. Config reload, force-mode changes, and startup loading do not fire this event; query `isActive` for current effective state.

### Items

- `offerReward(player, item, location)` offers an item already owned by the caller. It checks active state and calls `AutoCollectPreCollectEvent` before deposit. The input item and location are not mutated. `HANDLED` means FoAutoCollect deposited accepted units and dropped overflow at the supplied location (or the player's location when null); **do not deliver the reward again**. `DECLINED`, `CANCELLED`, and `BLOCKED` leave the full reward with the caller. `BLOCKED` applies to `BLOCK_COLLECTION` when the whole item cannot fit. `acceptedAmount` and `overflowAmount` are exact only for `HANDLED` and zero otherwise.
- `AutoCollectPreCollectEvent` fires before FoAutoCollect routes an eligible item or item batch, after source eligibility checks. It has cloned, read-only item snapshots, a cloned location, and a `CollectionSource`. It is cancellable. For immediate sources, cancellation leaves the original Bukkit drop or reward with its caller. It does not cancel XP collection. Active pre-collection listeners make block-drop deposit immediate. If a listener registers while a four-tick block batch is pending, it also sees that batch before deposit; cancellation then drops the queued items at the batch location because the original drop event has ended.
- `AutoCollectItemsCollectedEvent` fires after accepted items enter the inventory. It is non-cancellable and reports **only accepted items**, plus source and cloned location. In `DROP_OVERFLOW`, overflow is already dropped when the event fires. The existing three-argument constructor remains and sets source to `UNKNOWN`; new dispatches use a concrete source. A cancelled pre-collection request or a blocked deposit does not fire it.
- `CollectionSource` values are `BLOCK_BREAK`, `MOB_KILL`, `FISHING`, `SHEARING`, `EXPLOSION`, `FO_DROPS`, `EXTERNAL`, and `UNKNOWN`. `FO_DROPS` means the FoDrops reward bridge handled the item; its underlying activity is available from FoDrops' own event.

### Experience

- `AutoCollectExperienceCollectedEvent` fires after FoAutoCollect zeroes the source event's XP drop and gives positive XP directly to the player. It includes player, amount, source, and cloned location. It is non-cancellable. It reports block-break, mob-kill, and fishing XP; it does not report item collection or XP awarded independently by other plugins.

All events are dispatched on the thread handling their source and mark `isAsynchronous()` according to whether that is Bukkit's primary thread. Do not retain a `Player` for later off-thread access; schedule any later Bukkit world/player work on its appropriate server thread.

### Event listener example

Inside a Bukkit `Listener` registered by your plugin (import this event class, `org.bukkit.event.EventHandler`, and `org.bukkit.Bukkit` where used):

```java
@EventHandler
public void onAutoCollectPreCollect(AutoCollectPreCollectEvent event) {
    Bukkit.getLogger().info("Collecting from " + event.getSource());
}
```

### Complete public API method index

Signatures use short type names; import Fo types from this plugin's `api` package and Bukkit types from Bukkit.

#### `AutoCollectService`

```java
boolean isActive(Player);
Optional<Boolean> getSessionPreference(UUID);
PreferenceChangeResult requestPreference(Player, boolean);
RewardOfferResult offerReward(Player, ItemStack, Location);
```

**Other public contract types:** `CollectionSource`, `PreferenceChangeResult`, `RewardOfferResult`.

### Complete Bukkit event index

- `AutoCollectExperienceCollectedEvent`.
- `AutoCollectItemsCollectedEvent`.
- `AutoCollectPreCollectEvent`.
- `AutoCollectPreferenceChangedEvent`.

</details>
