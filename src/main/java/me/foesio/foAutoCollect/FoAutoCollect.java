package me.foesio.foAutoCollect;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import me.foesio.core.FoCoreContext;
import me.foesio.core.FoPluginCore;
import me.foesio.core.command.CommandVisibilityService;
import me.foesio.core.config.ConfigValueLists;
import me.foesio.core.config.ResourceFiles;
import me.foesio.core.dialog.DialogService;
import me.foesio.core.inventory.InventoryCloseSuppressor;
import me.foesio.core.inventory.InventoryDepositService;
import me.foesio.core.inventory.OverflowPolicy;
import me.foesio.core.message.FoMessageService;
import me.foesio.core.reload.FoReloadRegistry;
import me.foesio.core.sound.FoAdminSounds;
import me.foesio.core.sound.FoEditorSounds;
import me.foesio.core.sound.FoSound;
import me.foesio.core.sound.FoSoundMigrations;
import me.foesio.core.sound.FoSoundService;
import me.foesio.core.update.UpdateNoticeService;
import me.foesio.foAutoCollect.command.AutoCollectAdminCommand;
import me.foesio.foAutoCollect.command.AutoCollectCommand;
import me.foesio.foAutoCollect.editor.EditorManager;
import me.foesio.foAutoCollect.integration.FoDropsRewardBridge;
import me.foesio.foAutoCollect.listener.AutoCollectListener;
import me.foesio.foAutoCollect.storage.ToggleStore;
import org.bukkit.Material;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

public final class FoAutoCollect extends JavaPlugin {
    private static final String MODRINTH_PROJECT_ID = "foautocollect";
    private static final int BSTATS_PLUGIN_ID = 32375;

    private ToggleStore toggleStore;
    private FoCoreContext core;
    private InventoryDepositService inventoryDepositService;
    private CommandVisibilityService commandVisibilityService;
    private FoMessageService messages;
    private FoSoundService sounds;
    private FoEditorSounds editorSounds;
    private AutoCollectListener autoCollectListener;
    private boolean collectionListenerRegistered;
    private boolean defaultEnabled;
    private boolean forceEnabled;
    private boolean blockBreakEnabled;
    private boolean blockCollectDrops;
    private boolean blockCollectExperience;
    private boolean mobKillsEnabled;
    private boolean mobCollectDrops;
    private boolean mobCollectExperience;
    private boolean fishingEnabled;
    private boolean fishingCollectDrops;
    private boolean fishingCollectExperience;
    private boolean shearingEnabled;
    private boolean shearingCollectDrops;
    private boolean explosionsEnabled;
    private boolean explosionsCollectDrops;
    private int explosionMaxBlocksPerExplosion;
    private int explosionMaxTrackedExplosions;
    private boolean fullInventoryActionbar;
    private OverflowPolicy fullInventoryPolicy = OverflowPolicy.DROP_OVERFLOW;
    private Set<Material> disabledBlocks = Collections.emptySet();
    private Set<EntityType> disabledMobs = Collections.emptySet();
    private Set<String> blockEnabledWorlds = Collections.emptySet();
    private Set<String> blockDisabledWorlds = Collections.emptySet();
    private Set<String> mobEnabledWorlds = Collections.emptySet();
    private Set<String> mobDisabledWorlds = Collections.emptySet();
    private Set<String> fishingEnabledWorlds = Collections.emptySet();
    private Set<String> fishingDisabledWorlds = Collections.emptySet();
    private Set<String> shearingEnabledWorlds = Collections.emptySet();
    private Set<String> shearingDisabledWorlds = Collections.emptySet();
    private Set<String> explosionsEnabledWorlds = Collections.emptySet();
    private Set<String> explosionsDisabledWorlds = Collections.emptySet();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        getConfig().set("gui-titles", null);
        getConfig().set("gui-buttons", null);
        getConfig().set("update-checker", null);
        saveConfig();
        reloadMessages();
        core = FoPluginCore.create(this);
        inventoryDepositService = core.inventoryDeposits();
        core.warnIfNativeDialogsUnavailable();
        sounds = core.createSounds(soundMigrations());
        editorSounds = FoEditorSounds.create(sounds);
        reloadSettings();

        toggleStore = new ToggleStore(this);

        EditorManager editorManager = new EditorManager(this);
        FoReloadRegistry reloadRegistry = FoReloadRegistry.create()
            .addConfig(this)
            .add("sounds", sounds::reload)
            .add("messages", this::reloadMessages)
            .add("settings", this::reloadSettings);
        autoCollectListener = new AutoCollectListener(this);
        getServer().getPluginManager().registerEvents(editorManager, this);
        refreshCollectionListeners();
        commandVisibilityService = CommandVisibilityService.builder(this)
            .hideWithout("foautocollect.use", "foautocollect", "autocollect")
            .hideWithout("foautocollect.admin", "foautocollectadmin", "autocollectadmin")
            .register();
        FoDropsRewardBridge foDropsRewardBridge = new FoDropsRewardBridge(this, autoCollectListener);
        foDropsRewardBridge.register();
        FoAdminSounds adminSounds = FoAdminSounds.create(sounds);
        UpdateNoticeService updates = core.createUpdateNotices(messages, MODRINTH_PROJECT_ID, adminSounds).start();

        AutoCollectCommand userCommand = new AutoCollectCommand(this);
        if (getCommand("foautocollect") != null) {
            getCommand("foautocollect").setExecutor(userCommand);
            getCommand("foautocollect").setTabCompleter(userCommand);
        } else {
            getLogger().severe("Command 'foautocollect' is missing from plugin.yml.");
        }

        AutoCollectAdminCommand.register(this, editorManager, messages, reloadRegistry, updates, adminSounds);

        registerPlaceholderHook();
        startMetrics();
    }

    @Override
    public void onDisable() {
        if (autoCollectListener != null) {
            autoCollectListener.clearRuntimeState();
        }
        if (collectionListenerRegistered && autoCollectListener != null) {
            HandlerList.unregisterAll(autoCollectListener);
            collectionListenerRegistered = false;
        }
        if (commandVisibilityService != null) {
            commandVisibilityService.close();
        }
        if (core != null) {
            core.close();
        }
    }

    public void reloadSettings() {
        defaultEnabled = getConfig().getBoolean("default-enabled", false);
        forceEnabled = getConfig().getBoolean("force-enabled", false);
        blockBreakEnabled = getConfig().getBoolean("auto-collect.block-break.enabled", true);
        blockCollectDrops = getConfig().getBoolean("auto-collect.block-break.collect-drops", true);
        blockCollectExperience = getConfig().getBoolean("auto-collect.block-break.collect-experience", true);
        mobKillsEnabled = getConfig().getBoolean("auto-collect.mob-kills.enabled", true);
        mobCollectDrops = getConfig().getBoolean("auto-collect.mob-kills.collect-drops", true);
        mobCollectExperience = getConfig().getBoolean("auto-collect.mob-kills.collect-experience", true);
        fishingEnabled = getConfig().getBoolean("auto-collect.fishing.enabled", true);
        fishingCollectDrops = getConfig().getBoolean("auto-collect.fishing.collect-drops", true);
        fishingCollectExperience = getConfig().getBoolean("auto-collect.fishing.collect-experience", true);
        shearingEnabled = getConfig().getBoolean("auto-collect.shearing.enabled", true);
        shearingCollectDrops = getConfig().getBoolean("auto-collect.shearing.collect-drops", true);
        explosionsEnabled = getConfig().getBoolean("auto-collect.explosions.enabled", true);
        explosionsCollectDrops = getConfig().getBoolean("auto-collect.explosions.collect-drops", true);
        explosionMaxBlocksPerExplosion = positiveConfigInt("auto-collect.explosions.max-blocks-per-explosion", 512);
        explosionMaxTrackedExplosions = positiveConfigInt("auto-collect.explosions.max-tracked-explosions", 64);
        fullInventoryPolicy = OverflowPolicy.fromConfig(getConfig().getString("full-inventory.mode"));
        fullInventoryActionbar = getConfig().getBoolean("full-inventory.actionbar", true);
        disabledBlocks = normalizeMaterialSet("auto-collect.block-break.disabled-blocks");
        disabledMobs = normalizeMobSet("auto-collect.mob-kills.disabled-mobs");
        blockEnabledWorlds = normalizeWorldList("auto-collect.block-break.enabled-worlds");
        blockDisabledWorlds = normalizeWorldList("auto-collect.block-break.disabled-worlds");
        mobEnabledWorlds = normalizeWorldList("auto-collect.mob-kills.enabled-worlds");
        mobDisabledWorlds = normalizeWorldList("auto-collect.mob-kills.disabled-worlds");
        fishingEnabledWorlds = normalizeWorldList("auto-collect.fishing.enabled-worlds");
        fishingDisabledWorlds = normalizeWorldList("auto-collect.fishing.disabled-worlds");
        shearingEnabledWorlds = normalizeWorldList("auto-collect.shearing.enabled-worlds");
        shearingDisabledWorlds = normalizeWorldList("auto-collect.shearing.disabled-worlds");
        explosionsEnabledWorlds = normalizeWorldList("auto-collect.explosions.enabled-worlds");
        explosionsDisabledWorlds = normalizeWorldList("auto-collect.explosions.disabled-worlds");
        refreshCollectionListeners();
    }

    public ToggleStore getToggleStore() {
        return toggleStore;
    }

    public boolean isAutoCollectActive(Player player) {
        if (isForceEnabled()) {
            return true;
        }
        if (!toggleStore.hasOverrides()) {
            return defaultEnabled;
        }
        return toggleStore.isEnabled(player.getUniqueId());
    }

    public boolean isDefaultEnabled() {
        return defaultEnabled;
    }

    public boolean isForceEnabled() {
        return forceEnabled;
    }

    public void setForceEnabled(boolean enabled) {
        getConfig().set("force-enabled", enabled);
        saveConfig();
        reloadSettings();
    }

    public void refreshCollectionListeners() {
        if (autoCollectListener == null) {
            return;
        }

        boolean shouldRegister = canAnyPlayerAutoCollect();
        if (shouldRegister && !collectionListenerRegistered) {
            getServer().getPluginManager().registerEvents(autoCollectListener, this);
            collectionListenerRegistered = true;
            return;
        }
        if (!shouldRegister && collectionListenerRegistered) {
            HandlerList.unregisterAll(autoCollectListener);
            autoCollectListener.clearRuntimeState();
            collectionListenerRegistered = false;
        }
    }

    private boolean canAnyPlayerAutoCollect() {
        if (forceEnabled || defaultEnabled) {
            return true;
        }
        return toggleStore != null && toggleStore.hasEnabledOverrides();
    }

    public boolean isBlockBreakEnabled(Material material, World world) {
        return blockBreakEnabled
            && material != null
            && !disabledBlocks.contains(material)
            && isWorldAllowed(world, blockEnabledWorlds, blockDisabledWorlds);
    }

    public boolean isBlockDropCollectionEnabled() {
        return blockCollectDrops;
    }

    public boolean isBlockExperienceCollectionEnabled() {
        return blockCollectExperience;
    }

    public boolean isMobKillEnabled(EntityType entityType, World world) {
        return mobKillsEnabled
            && entityType != null
            && !disabledMobs.contains(entityType)
            && isWorldAllowed(world, mobEnabledWorlds, mobDisabledWorlds);
    }

    public boolean isMobDropCollectionEnabled() {
        return mobCollectDrops;
    }

    public boolean isMobExperienceCollectionEnabled() {
        return mobCollectExperience;
    }

    public boolean isFishingEnabled(World world) {
        return fishingEnabled && isWorldAllowed(world, fishingEnabledWorlds, fishingDisabledWorlds);
    }

    public boolean isFishingDropCollectionEnabled() {
        return fishingCollectDrops;
    }

    public boolean isFishingExperienceCollectionEnabled() {
        return fishingCollectExperience;
    }

    public boolean isShearingEnabled(World world) {
        return shearingEnabled && isWorldAllowed(world, shearingEnabledWorlds, shearingDisabledWorlds);
    }

    public boolean isShearingDropCollectionEnabled() {
        return shearingCollectDrops;
    }

    public boolean isExplosionsEnabled(World world) {
        return explosionsEnabled && isWorldAllowed(world, explosionsEnabledWorlds, explosionsDisabledWorlds);
    }

    public boolean isExplosionDropCollectionEnabled() {
        return explosionsCollectDrops;
    }

    public int getExplosionMaxBlocksPerExplosion() {
        return explosionMaxBlocksPerExplosion;
    }

    public int getExplosionMaxTrackedExplosions() {
        return explosionMaxTrackedExplosions;
    }

    public Set<Material> getDisabledBlocks() {
        return disabledBlocks;
    }

    public Set<EntityType> getDisabledMobs() {
        return disabledMobs;
    }

    public Set<String> getBlockEnabledWorlds() {
        return blockEnabledWorlds;
    }

    public Set<String> getBlockDisabledWorlds() {
        return blockDisabledWorlds;
    }

    public Set<String> getMobEnabledWorlds() {
        return mobEnabledWorlds;
    }

    public Set<String> getMobDisabledWorlds() {
        return mobDisabledWorlds;
    }

    public Set<String> getFishingEnabledWorlds() {
        return fishingEnabledWorlds;
    }

    public Set<String> getFishingDisabledWorlds() {
        return fishingDisabledWorlds;
    }

    public Set<String> getShearingEnabledWorlds() {
        return shearingEnabledWorlds;
    }

    public Set<String> getShearingDisabledWorlds() {
        return shearingDisabledWorlds;
    }

    public Set<String> getExplosionsEnabledWorlds() {
        return explosionsEnabledWorlds;
    }

    public Set<String> getExplosionsDisabledWorlds() {
        return explosionsDisabledWorlds;
    }

    public OverflowPolicy getFullInventoryPolicy() {
        return fullInventoryPolicy;
    }

    public boolean isFullInventoryActionbarEnabled() {
        return fullInventoryActionbar;
    }

    public FoSoundService getSounds() {
        return sounds;
    }

    public FoMessageService getMessages() {
        return messages;
    }

    public FoEditorSounds getEditorSounds() {
        return editorSounds;
    }

    public boolean isFoDropsInstalled() {
        return getServer().getPluginManager().isPluginEnabled("FoDrops");
    }

    private FoSoundMigrations soundMigrations() {
        return FoSoundMigrations.create()
            .moveFromConfig("full-inventory.sound", "full-inventory.enabled")
            .remove("admin.update-checking")
            .replaceIfMatches(
                "auto-collect.enabled",
                sound("ENTITY_PLAYER_LEVELUP", SoundCategory.PLAYERS, 0.45f, 1.0f),
                sound("BLOCK_COPPER_BULB_TURN_ON", SoundCategory.PLAYERS, 0.85f, 1.0f)
            )
            .replaceIfMatches(
                "auto-collect.enabled",
                sound("BLOCK_AMETHYST_BLOCK_CHIME", SoundCategory.PLAYERS, 0.65f, 1.15f),
                sound("BLOCK_COPPER_BULB_TURN_ON", SoundCategory.PLAYERS, 0.85f, 1.0f)
            )
            .replaceIfMatches(
                "auto-collect.enabled",
                sound("BLOCK_COPPER_BULB_TURN_ON", SoundCategory.PLAYERS, 0.85f, 1.0f),
                sound("BLOCK_AMETHYST_BLOCK_RESONATE", SoundCategory.PLAYERS, 2.0f, 1.5f)
            )
            .replaceIfMatches(
                "auto-collect.disabled",
                sound("BLOCK_NOTE_BLOCK_BASS", SoundCategory.PLAYERS, 0.35f, 0.8f),
                sound("BLOCK_CRAFTER_FAIL", SoundCategory.PLAYERS, 0.9f, 0.9f)
            )
            .replaceIfMatches(
                "auto-collect.disabled",
                sound("BLOCK_AMETHYST_BLOCK_RESONATE", SoundCategory.PLAYERS, 0.7f, 0.9f),
                sound("BLOCK_CRAFTER_FAIL", SoundCategory.PLAYERS, 0.9f, 0.9f)
            )
            .replaceIfMatches(
                "auto-collect.disabled",
                sound("BLOCK_CRAFTER_FAIL", SoundCategory.PLAYERS, 0.9f, 0.9f),
                sound("BLOCK_CRAFTER_FAIL", SoundCategory.PLAYERS, 2.0f, 2.0f)
            )
            .replaceIfMatches(
                "auto-collect.pickup",
                sound("ENTITY_ITEM_PICKUP", SoundCategory.PLAYERS, 0.2f, 1.0f),
                sound("ENTITY_ALLAY_ITEM_TAKEN", SoundCategory.PLAYERS, 0.4f, 1.0f)
            )
            .replaceIfMatches(
                "auto-collect.pickup",
                sound("ENTITY_ITEM_PICKUP", SoundCategory.PLAYERS, 0.35f, 1.0f),
                sound("ENTITY_ALLAY_ITEM_TAKEN", SoundCategory.PLAYERS, 0.4f, 1.0f)
            )
            .replaceIfMatches(
                "auto-collect.pickup",
                sound("ENTITY_ALLAY_ITEM_TAKEN", SoundCategory.PLAYERS, 0.4f, 1.0f),
                sound("ENTITY_ITEM_PICKUP", SoundCategory.PLAYERS, 0.2f, 1.0f)
            )
            .replaceIfMatches(
                "editor.open",
                sound("BLOCK_CHEST_OPEN", SoundCategory.MASTER, 0.45f, 1.0f),
                sound("BLOCK_VAULT_OPEN_SHUTTER", SoundCategory.MASTER, 0.55f, 1.0f)
            )
            .replaceIfMatches(
                "editor.open",
                sound("BLOCK_AMETHYST_BLOCK_CHIME", SoundCategory.MASTER, 0.65f, 1.0f),
                sound("BLOCK_VAULT_OPEN_SHUTTER", SoundCategory.MASTER, 0.55f, 1.0f)
            )
            .replaceIfMatches(
                "editor.back",
                sound("UI_BUTTON_CLICK", SoundCategory.MASTER, 0.35f, 0.85f),
                sound("BLOCK_VAULT_CLOSE_SHUTTER", SoundCategory.MASTER, 0.55f, 0.95f)
            )
            .replaceIfMatches(
                "editor.back",
                sound("BLOCK_AMETHYST_BLOCK_RESONATE", SoundCategory.MASTER, 0.6f, 0.85f),
                sound("BLOCK_VAULT_CLOSE_SHUTTER", SoundCategory.MASTER, 0.55f, 0.95f)
            )
            .replaceIfMatches(
                "editor.page-next",
                sound("ITEM_BOOK_PAGE_TURN", SoundCategory.MASTER, 0.35f, 1.1f),
                sound("BLOCK_BAMBOO_WOOD_BUTTON_CLICK_ON", SoundCategory.MASTER, 0.55f, 1.1f)
            )
            .replaceIfMatches(
                "editor.page-next",
                sound("BLOCK_AMETHYST_BLOCK_CHIME", SoundCategory.MASTER, 0.5f, 1.2f),
                sound("BLOCK_BAMBOO_WOOD_BUTTON_CLICK_ON", SoundCategory.MASTER, 0.55f, 1.1f)
            )
            .replaceIfMatches(
                "editor.page-previous",
                sound("ITEM_BOOK_PAGE_TURN", SoundCategory.MASTER, 0.35f, 0.9f),
                sound("BLOCK_CHERRY_WOOD_BUTTON_CLICK_OFF", SoundCategory.MASTER, 0.55f, 0.95f)
            )
            .replaceIfMatches(
                "editor.page-previous",
                sound("BLOCK_AMETHYST_BLOCK_CHIME", SoundCategory.MASTER, 0.5f, 0.85f),
                sound("BLOCK_CHERRY_WOOD_BUTTON_CLICK_OFF", SoundCategory.MASTER, 0.55f, 0.95f)
            )
            .replaceIfMatches(
                "editor.search",
                sound("UI_BUTTON_CLICK", SoundCategory.MASTER, 0.25f, 1.2f),
                sound("BLOCK_CRAFTER_CRAFT", SoundCategory.MASTER, 0.55f, 1.0f)
            )
            .replaceIfMatches(
                "editor.search",
                sound("BLOCK_AMETHYST_CLUSTER_HIT", SoundCategory.MASTER, 0.55f, 1.15f),
                sound("BLOCK_CRAFTER_CRAFT", SoundCategory.MASTER, 0.55f, 1.0f)
            )
            .replaceIfMatches(
                "editor.clear-search",
                sound("UI_BUTTON_CLICK", SoundCategory.MASTER, 0.25f, 0.75f),
                sound("BLOCK_COPPER_BULB_TURN_OFF", SoundCategory.MASTER, 0.55f, 1.0f)
            )
            .replaceIfMatches(
                "editor.clear-search",
                sound("BLOCK_AMETHYST_CLUSTER_HIT", SoundCategory.MASTER, 0.55f, 0.85f),
                sound("BLOCK_COPPER_BULB_TURN_OFF", SoundCategory.MASTER, 0.55f, 1.0f)
            )
            .replaceIfMatches(
                "editor.toggle-on",
                sound("BLOCK_NOTE_BLOCK_PLING", SoundCategory.MASTER, 0.4f, 1.35f),
                sound("BLOCK_BEACON_ACTIVATE", SoundCategory.MASTER, 0.8f, 1.2f)
            )
            .replaceIfMatches(
                "editor.toggle-on",
                sound("ENTITY_ALLAY_ITEM_GIVEN", SoundCategory.MASTER, 0.65f, 1.15f),
                sound("BLOCK_BAMBOO_WOOD_BUTTON_CLICK_ON", SoundCategory.MASTER, 0.8f, 1.1f)
            )
            .replaceIfMatches(
                "editor.toggle-on",
                sound("BLOCK_BEACON_ACTIVATE", SoundCategory.MASTER, 0.8f, 1.2f),
                sound("BLOCK_COPPER_BULB_TURN_ON", SoundCategory.MASTER, 0.8f, 1.2f)
            )
            .replaceIfMatches(
                "editor.toggle-on",
                sound("BLOCK_BAMBOO_WOOD_BUTTON_CLICK_ON", SoundCategory.MASTER, 0.8f, 1.1f),
                sound("BLOCK_CRAFTER_CRAFT", SoundCategory.MASTER, 0.8f, 1.3f)
            )
            .replaceIfMatches(
                "editor.toggle-on",
                sound("BLOCK_COPPER_BULB_TURN_ON", SoundCategory.MASTER, 0.8f, 1.2f),
                sound("BLOCK_CRAFTER_CRAFT", SoundCategory.MASTER, 0.8f, 1.3f)
            )
            .replaceIfMatches(
                "editor.toggle-off",
                sound("BLOCK_NOTE_BLOCK_BASS", SoundCategory.MASTER, 0.35f, 0.85f),
                sound("BLOCK_COPPER_BULB_TURN_OFF", SoundCategory.MASTER, 0.8f, 0.8f)
            )
            .replaceIfMatches(
                "editor.toggle-off",
                sound("BLOCK_AMETHYST_BLOCK_RESONATE", SoundCategory.MASTER, 0.6f, 0.8f),
                sound("BLOCK_COPPER_BULB_TURN_OFF", SoundCategory.MASTER, 0.8f, 0.8f)
            )
            .replaceIfMatches(
                "editor.toggle-off",
                sound("BLOCK_COPPER_BULB_TURN_OFF", SoundCategory.MASTER, 0.65f, 0.9f),
                sound("BLOCK_COPPER_BULB_TURN_OFF", SoundCategory.MASTER, 0.8f, 0.8f)
            )
            .replaceIfMatches(
                "editor.toggle-off",
                sound("BLOCK_BEACON_DEACTIVATE", SoundCategory.MASTER, 0.8f, 1.0f),
                sound("BLOCK_COPPER_BULB_TURN_OFF", SoundCategory.MASTER, 0.8f, 0.8f)
            )
            .replaceIfMatches(
                "editor.toggle-off",
                sound("BLOCK_BAMBOO_WOOD_BUTTON_CLICK_OFF", SoundCategory.MASTER, 0.8f, 0.95f),
                sound("BLOCK_VAULT_REJECT_REWARDED_PLAYER", SoundCategory.MASTER, 0.8f, 0.8f)
            )
            .replaceIfMatches(
                "editor.toggle-off",
                sound("BLOCK_COPPER_BULB_TURN_OFF", SoundCategory.MASTER, 0.8f, 0.8f),
                sound("BLOCK_VAULT_REJECT_REWARDED_PLAYER", SoundCategory.MASTER, 0.8f, 0.8f)
            )
            .replaceIfMatches(
                "editor.toggle-on",
                sound("BLOCK_CRAFTER_CRAFT", SoundCategory.MASTER, 0.8f, 1.3f),
                sound("BLOCK_VAULT_INSERT_ITEM", SoundCategory.MASTER, 1.0f, 1.5f)
            )
            .replaceIfMatches(
                "editor.toggle-off",
                sound("BLOCK_VAULT_REJECT_REWARDED_PLAYER", SoundCategory.MASTER, 0.8f, 0.8f),
                sound("BLOCK_VAULT_INSERT_ITEM_FAIL", SoundCategory.MASTER, 1.0f, 1.5f)
            )
            .replaceIfMatches(
                "editor.add",
                sound("ENTITY_ITEM_PICKUP", SoundCategory.MASTER, 0.35f, 1.15f),
                sound("BLOCK_VAULT_INSERT_ITEM", SoundCategory.MASTER, 0.55f, 1.1f)
            )
            .replaceIfMatches(
                "editor.add",
                sound("ENTITY_ITEM_PICKUP", SoundCategory.MASTER, 0.4f, 1.15f),
                sound("BLOCK_VAULT_INSERT_ITEM", SoundCategory.MASTER, 0.55f, 1.1f)
            )
            .replaceIfMatches(
                "editor.delete",
                sound("ENTITY_ITEM_BREAK", SoundCategory.MASTER, 0.4f, 0.9f),
                sound("BLOCK_VAULT_EJECT_ITEM", SoundCategory.MASTER, 0.6f, 0.9f)
            )
            .replaceIfMatches(
                "editor.delete",
                sound("ENTITY_ITEM_BREAK", SoundCategory.MASTER, 0.5f, 0.9f),
                sound("BLOCK_VAULT_EJECT_ITEM", SoundCategory.MASTER, 0.6f, 0.9f)
            )
            .replaceIfMatches(
                "editor.cycle",
                sound("UI_BUTTON_CLICK", SoundCategory.MASTER, 0.3f, 1.05f),
                sound("BLOCK_COPPER_BULB_TURN_ON", SoundCategory.MASTER, 0.9f, 0.5f)
            )
            .replaceIfMatches(
                "editor.cycle",
                sound("BLOCK_AMETHYST_CLUSTER_HIT", SoundCategory.MASTER, 0.5f, 1.0f),
                sound("BLOCK_COPPER_BULB_TURN_ON", SoundCategory.MASTER, 0.9f, 0.5f)
            )
            .replaceIfMatches(
                "editor.cycle",
                sound("BLOCK_COPPER_GRATE_HIT", SoundCategory.MASTER, 0.5f, 1.0f),
                sound("BLOCK_COPPER_BULB_TURN_ON", SoundCategory.MASTER, 0.9f, 0.5f)
            )
            .replaceIfMatches(
                "editor.cycle",
                sound("BLOCK_COPPER_BULB_TURN_ON", SoundCategory.MASTER, 0.9f, 0.5f),
                sound("BLOCK_COPPER_BULB_TURN_ON", SoundCategory.MASTER, 1.0f, 0.5f)
            )
            .replaceIfMatches(
                "editor.save",
                sound("ENTITY_EXPERIENCE_ORB_PICKUP", SoundCategory.MASTER, 0.3f, 1.15f),
                sound("BLOCK_VAULT_ACTIVATE", SoundCategory.MASTER, 0.65f, 1.0f)
            )
            .replaceIfMatches(
                "editor.save",
                sound("ENTITY_PLAYER_LEVELUP", SoundCategory.MASTER, 0.6f, 1.1f),
                sound("BLOCK_VAULT_ACTIVATE", SoundCategory.MASTER, 0.65f, 1.0f)
            )
            .replaceIfMatches(
                "editor.error",
                sound("ENTITY_VILLAGER_NO", SoundCategory.MASTER, 0.35f, 1.0f),
                sound("BLOCK_VAULT_INSERT_ITEM_FAIL", SoundCategory.MASTER, 0.75f, 1.0f)
            )
            .replaceIfMatches(
                "editor.error",
                sound("ENTITY_VILLAGER_NO", SoundCategory.MASTER, 0.6f, 1.0f),
                sound("BLOCK_VAULT_INSERT_ITEM_FAIL", SoundCategory.MASTER, 0.75f, 1.0f)
            )
            .replaceIfMatches(
                "full-inventory",
                sound("BLOCK_NOTE_BLOCK_BASS", SoundCategory.MASTER, 0.75f, 0.75f),
                sound("BLOCK_VAULT_REJECT_REWARDED_PLAYER", SoundCategory.MASTER, 0.8f, 0.9f)
            )
            .replaceIfMatches(
                "full-inventory",
                sound("ENTITY_ALLAY_HURT", SoundCategory.MASTER, 0.7f, 0.9f),
                sound("BLOCK_VAULT_REJECT_REWARDED_PLAYER", SoundCategory.MASTER, 0.8f, 0.9f)
            )
            .replaceIfMatches(
                "admin.update-latest",
                sound("ENTITY_ALLAY_ITEM_GIVEN", SoundCategory.MASTER, 0.7f, 1.05f),
                sound("BLOCK_TRIAL_SPAWNER_EJECT_ITEM", SoundCategory.MASTER, 0.8f, 1.0f)
            )
            .replaceIfMatches(
                "admin.update-available",
                sound("BLOCK_VAULT_ACTIVATE", SoundCategory.MASTER, 0.75f, 1.1f),
                sound("BLOCK_TRIAL_SPAWNER_OPEN_SHUTTER", SoundCategory.MASTER, 0.7f, 0.9f)
            )
            .replaceIfMatches(
                "admin.update-error",
                sound("BLOCK_VAULT_INSERT_ITEM_FAIL", SoundCategory.MASTER, 0.85f, 0.9f),
                sound("BLOCK_TRIAL_SPAWNER_DETECT_PLAYER", SoundCategory.MASTER, 0.8f, 1.0f)
            )
            .replaceIfMatches(
                "admin.reload",
                sound("BLOCK_COPPER_CHEST_OPEN", SoundCategory.MASTER, 0.65f, 1.0f),
                sound("BLOCK_BEACON_ACTIVATE", SoundCategory.MASTER, 0.8f, 1.2f)
            )
            .replaceIfMatches(
                "admin.reload-error",
                sound("BLOCK_CRAFTER_FAIL", SoundCategory.MASTER, 0.85f, 0.8f),
                sound("BLOCK_TRIAL_SPAWNER_OMINOUS_ACTIVATE", SoundCategory.MASTER, 0.8f, 0.9f)
            )
            .build();
    }

    private FoSound sound(String key, SoundCategory category, float volume, float pitch) {
        return new FoSound(key, category, volume, pitch, true);
    }

    public DialogService getDialogService() {
        return core.dialogService();
    }

    public InventoryCloseSuppressor getInventoryCloseSuppressor() {
        return core.inventoryCloseSuppressor();
    }

    public InventoryDepositService getInventoryDepositService() {
        return inventoryDepositService;
    }

    public String getPlaceholderState(Player player) {
        return isAutoCollectActive(player) ? "enabled" : "disabled";
    }

    public void sendMessage(CommandSender sender, String key, String... placeholders) {
        messages.send(sender, "messages." + key, "{prefix} {bad}Missing message: " + key, placeholders(placeholders));
    }

    public String getMessage(String key, String... placeholders) {
        return messages.render("messages." + key, "{prefix} {bad}Missing message: " + key, placeholders(placeholders));
    }

    public String getGuiTitle(String key, String fallback, String... placeholders) {
        return format(fallback, placeholders);
    }

    public String format(String template, String... placeholders) {
        return messages.renderTemplate(template, placeholders(placeholders));
    }

    private void reloadMessages() {
        ResourceFiles.saveDefault(this, "messages.yml");
        if (messages == null) {
            messages = FoMessageService.load(this);
        } else {
            messages.reload();
        }
        boolean messagePrefixWasSet = messages.config().isSet("tokens.prefix");
        applyBundledMessageDefaults();
        migrateLegacyConfigPrefix(messagePrefixWasSet);
        messages.save();
    }

    private void applyBundledMessageDefaults() {
        try (InputStream stream = getResource("messages.yml")) {
            if (stream != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)
                );
                copyMissingDefaults(messages.config(), defaults);
            }
        } catch (Exception exception) {
            getLogger().warning("Could not update missing message defaults: " + exception.getMessage());
        }
    }

    private void copyMissingDefaults(FileConfiguration config, YamlConfiguration defaults) {
        for (String key : defaults.getKeys(true)) {
            if (!defaults.isConfigurationSection(key) && !config.isSet(key)) {
                config.set(key, defaults.get(key));
            }
        }
    }

    private void migrateLegacyConfigPrefix(boolean messagePrefixWasSet) {
        if (!getConfig().isSet("prefix")) {
            return;
        }

        String legacyPrefix = getConfig().getString("prefix");
        if (!messagePrefixWasSet && legacyPrefix != null && !legacyPrefix.isBlank()) {
            messages.config().set("tokens.prefix", legacyPrefix);
        }
        getConfig().set("prefix", null);
        saveConfig();
    }

    private Set<Material> normalizeMaterialSet(String path) {
        return enumSet(ConfigValueLists.materials(this, path, Material::isBlock, "block material"), Material.class);
    }

    private Set<EntityType> normalizeMobSet(String path) {
        return enumSet(ConfigValueLists.mobs(this, path), EntityType.class);
    }

    private <E extends Enum<E>> Set<E> enumSet(Set<E> values, Class<E> enumType) {
        if (values == null || values.isEmpty()) {
            return Collections.unmodifiableSet(EnumSet.noneOf(enumType));
        }
        return Collections.unmodifiableSet(EnumSet.copyOf(values));
    }

    private Set<String> normalizeWorldList(String path) {
        return ConfigValueLists.lowerStrings(getConfig(), path);
    }

    private int positiveConfigInt(String path, int fallback) {
        int value = getConfig().getInt(path, fallback);
        return value > 0 ? value : fallback;
    }

    private boolean isWorldAllowed(World world, Set<String> enabledWorlds, Set<String> disabledWorlds) {
        if (world == null) {
            return true;
        }
        if (enabledWorlds.isEmpty() && disabledWorlds.isEmpty()) {
            return true;
        }

        String worldName = world.getName().toLowerCase(Locale.ROOT);
        if (disabledWorlds.contains(worldName)) {
            return false;
        }
        return enabledWorlds.isEmpty() || enabledWorlds.contains(worldName);
    }

    private void registerPlaceholderHook() {
        core.placeholders("foautocollect")
            .player("state", this::getPlaceholderState)
            .registerIfAvailable();
    }

    private void startMetrics() {
        if (BSTATS_PLUGIN_ID <= 0) {
            return;
        }

        core.metrics(BSTATS_PLUGIN_ID)
            .togglePie("default_enabled", this::isDefaultEnabled)
            .togglePie("force_enabled", this::isForceEnabled)
            .togglePie("block_break_enabled", () -> blockBreakEnabled)
            .togglePie("mob_kills_enabled", () -> mobKillsEnabled)
            .togglePie("fishing_enabled", () -> fishingEnabled)
            .togglePie("shearing_enabled", () -> shearingEnabled)
            .togglePie("explosions_enabled", () -> explosionsEnabled)
            .simplePie("full_inventory_mode", () -> fullInventoryPolicy.name().toLowerCase(Locale.ROOT));
    }

    private Map<String, String> placeholders(String... placeholders) {
        Map<String, String> values = new HashMap<>();
        if (placeholders == null) {
            return values;
        }
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            values.put(cleanPlaceholderKey(placeholders[i]), replaceMessageTokens(placeholders[i + 1]));
        }
        return values;
    }

    private String cleanPlaceholderKey(String key) {
        String cleaned = Objects.toString(key, "");
        if (cleaned.startsWith("{") && cleaned.endsWith("}") && cleaned.length() > 2) {
            return cleaned.substring(1, cleaned.length() - 1);
        }
        if (cleaned.startsWith("%") && cleaned.endsWith("%") && cleaned.length() > 2) {
            return cleaned.substring(1, cleaned.length() - 1);
        }
        return cleaned;
    }

    private String replaceMessageTokens(String text) {
        String rendered = Objects.toString(text, "");
        for (Map.Entry<String, String> entry : messages.tokenValues().entrySet()) {
            rendered = rendered.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return rendered;
    }

}
