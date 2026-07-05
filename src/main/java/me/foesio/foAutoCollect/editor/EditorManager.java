package me.foesio.foAutoCollect.editor;

import me.foesio.core.dialog.DialogButton;
import me.foesio.core.dialog.TextDialogRequest;
import me.foesio.core.editor.EditorDialogInputs;
import me.foesio.core.editor.EditorItemFactory;
import me.foesio.core.gui.GuiButtonConfig;
import me.foesio.core.material.MaterialChooserClick;
import me.foesio.core.material.MaterialChooserHolder;
import me.foesio.core.material.MaterialChooserMenus;
import me.foesio.core.material.MaterialChooserMode;
import me.foesio.core.material.MaterialChooserRequest;
import me.foesio.core.material.MaterialSelections;
import me.foesio.core.material.MaterialTypes;
import me.foesio.core.mob.MobChooserClick;
import me.foesio.core.mob.MobChooserHolder;
import me.foesio.core.mob.MobChooserMenus;
import me.foesio.core.mob.MobChooserMode;
import me.foesio.core.mob.MobChooserRequest;
import me.foesio.core.mob.MobSelections;
import me.foesio.core.mob.MobTypes;
import me.foesio.core.selector.TriStateSelectionClick;
import me.foesio.core.selector.TriStateSelectionHolder;
import me.foesio.core.selector.TriStateSelectionMenus;
import me.foesio.core.selector.TriStateSelectionRequest;
import me.foesio.core.selector.TriStateSelectionState;
import me.foesio.core.selector.TriStateSelections;
import me.foesio.core.selector.WorldSelectionEntries;
import me.foesio.foAutoCollect.FoAutoCollect;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.IllegalPluginAccessException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class EditorManager implements Listener {
    private static final GuiButtonConfig GUI_BUTTONS = GuiButtonConfig.defaults();

    private final FoAutoCollect plugin;
    private final ItemStack backgroundFiller;
    private final Map<UUID, SectionType> disabledPickerSections = new ConcurrentHashMap<>();
    private final Map<UUID, SectionType> worldPickerSections = new ConcurrentHashMap<>();

    public EditorManager(FoAutoCollect plugin) {
        this.plugin = plugin;
        this.backgroundFiller = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
    }

    public void openMainMenu(Player player) {
        EditorMenu menu = new EditorMenu(MenuType.MAIN, null);
        Inventory inventory = Bukkit.createInventory(menu, 54, plugin.getGuiTitle("main", "&8ꜰᴏᴀᴜᴛᴏᴄᴏʟʟᴇᴄᴛ ᴇᴅɪᴛᴏʀ"));
        menu.setInventory(inventory);

        inventory.setItem(10, createToggleItem(
            Material.NETHER_STAR,
            "Force Everyone",
            plugin.isForceEnabled(),
            "{white}When enabled, every player",
            "{white}is treated as auto collect ON.",
            "{white}Personal toggles are ignored."
        ));
        inventory.setItem(12, createItem(
            Material.GRASS_BLOCK,
            "{theme}Block Breaks",
            "{white}Status: " + formatState(plugin.getConfig().getBoolean("auto-collect.block-break.enabled", true)),
            "{white}Drops: " + formatState(plugin.isBlockDropCollectionEnabled()),
            "{white}XP: " + formatState(plugin.isBlockExperienceCollectionEnabled()),
            "{white}Disabled blocks: {theme}" + plugin.getDisabledBlocks().size(),
            "",
            "{white}Click to edit block rules."
        ));
        inventory.setItem(14, createItem(
            Material.ZOMBIE_HEAD,
            "{theme}Mob Kills",
            "{white}Status: " + formatState(plugin.getConfig().getBoolean("auto-collect.mob-kills.enabled", true)),
            "{white}Drops: " + formatState(plugin.isMobDropCollectionEnabled()),
            "{white}XP: " + formatState(plugin.isMobExperienceCollectionEnabled()),
            "{white}Disabled mobs: {theme}" + plugin.getDisabledMobs().size(),
            "",
            "{white}Click to edit mob rules."
        ));
        inventory.setItem(16, createItem(
            Material.ENDER_CHEST,
            "{theme}FoDrops Integration",
            "{white}Installed: " + formatState(plugin.isFoDropsInstalled()),
            "{white}Collects compatible drops",
            "{white}inside safe Bukkit events.",
            "{white}Unsafe spawned item",
            "{white}guessing is disabled."
        ));
        inventory.setItem(19, createItem(
            Material.FISHING_ROD,
            "{theme}Fishing",
            "{white}Status: " + formatState(plugin.getConfig().getBoolean("auto-collect.fishing.enabled", true)),
            "{white}Drops: " + formatState(plugin.isFishingDropCollectionEnabled()),
            "{white}XP: " + formatState(plugin.isFishingExperienceCollectionEnabled()),
            "",
            "{white}Click to edit fishing."
        ));
        inventory.setItem(21, createItem(
            Material.SHEARS,
            "{theme}Shearing",
            "{white}Status: " + formatState(plugin.getConfig().getBoolean("auto-collect.shearing.enabled", true)),
            "{white}Drops: " + formatState(plugin.isShearingDropCollectionEnabled()),
            "",
            "{white}Click to edit shearing."
        ));
        inventory.setItem(23, createItem(
            Material.TNT,
            "{theme}Explosions",
            "{white}Status: " + formatState(plugin.getConfig().getBoolean("auto-collect.explosions.enabled", true)),
            "{white}FoDrops rewards: " + formatState(plugin.isExplosionDropCollectionEnabled()),
            "",
            "{white}Click to edit explosion rewards."
        ));
        inventory.setItem(28, createItem(
            Material.HOPPER,
            "{theme}Full Inventory Mode",
            "{white}Current: {theme}" + plugin.getFullInventoryPolicy().displayName(),
            "",
            "{white}Drop Overflow: leftovers drop.",
            "{white}Block Collection: leave drops",
            "{white}if everything cannot fit.",
            "{white}Click to cycle."
        ));
        inventory.setItem(30, createToggleItem(
            Material.OAK_SIGN,
            "Full Inventory Actionbar",
            plugin.isFullInventoryActionbarEnabled(),
            "{white}Shows a short actionbar",
            "{white}when inventory blocks or",
            "{white}overflows collection."
        ));
        inventory.setItem(32, createToggleItem(
            Material.NOTE_BLOCK,
            "Full Inventory Sound",
            plugin.isFullInventorySoundEnabled(),
            "{white}Plays a warning sound",
            "{white}when inventory blocks or",
            "{white}overflows collection."
        ));
        fillBackground(inventory);
        player.openInventory(inventory);
    }

    public void openSectionMenu(Player player, SectionType section) {
        EditorMenu menu = new EditorMenu(MenuType.SECTION, section);
        Inventory inventory = Bukkit.createInventory(menu, 54, plugin.getGuiTitle(section.guiTitleKey, section.fallbackTitle));
        menu.setInventory(inventory);

        inventory.setItem(11, createToggleItem(
            section.mainIcon,
            section.displayName + " Enabled",
            plugin.getConfig().getBoolean(section.basePath + ".enabled", true),
            "{white}Controls this full collection",
            "{white}category for all players."
        ));
        inventory.setItem(13, createToggleItem(
            Material.CHEST,
            "Collect Drops",
            plugin.getConfig().getBoolean(section.basePath + ".collect-drops", true),
            "{white}If ON, matching drops go",
            "{white}straight into inventory."
        ));
        inventory.setItem(15, createToggleItem(
            Material.EXPERIENCE_BOTTLE,
            "Collect Experience",
            plugin.getConfig().getBoolean(section.basePath + ".collect-experience", true),
            "{white}If ON, matching XP goes",
            "{white}straight into the XP bar."
        ));
        if (!section.hasExperience) {
            inventory.setItem(15, createItem(
                Material.GRAY_DYE,
                "{muted}No Experience Setting",
                "{white}This event does not expose",
                "{white}XP collection."
            ));
        }
        if (section.hasDisabledBrowser()) {
            inventory.setItem(31, createItem(
                section.listIcon,
                "{theme}" + section.disabledDisplayName,
                "{white}Entries: {theme}" + getDisabledList(section).size(),
                "",
                "{white}Click to open browser."
            ));
        }
        inventory.setItem(worldsSlot(section), EditorItemFactory.worlds(
            getWorldList(section, WorldListType.ENABLED).size(),
            getWorldList(section, WorldListType.DISABLED).size(),
            "Enabled"
        ));
        inventory.setItem(49, GUI_BUTTONS.back());

        fillBackground(inventory);
        player.openInventory(inventory);
    }

    public void openPicker(Player player, SectionType section, int page) {
        openPicker(player, section, page, "");
    }

    public void openPicker(Player player, SectionType section, int page, String filter) {
        disabledPickerSections.put(player.getUniqueId(), section);
        if (section == SectionType.BLOCK_BREAK) {
            MaterialChooserMenus.open(player, MaterialChooserRequest.builder()
                .title(plugin.getGuiTitle("picker", "&8ʙʀᴏᴡꜱᴇʀ"))
                .mode(MaterialChooserMode.DISABLED_TOGGLE)
                .page(page)
                .filter(filter)
                .buttons(GUI_BUTTONS)
                .availableMaterials(MaterialTypes.allBlocks())
                .selectedMaterials(MaterialSelections.fromKeys(getDisabledList(section)))
                .build());
            return;
        }

        MobChooserMenus.open(player, MobChooserRequest.builder()
            .title(plugin.getGuiTitle("picker", "&8ʙʀᴏᴡꜱᴇʀ"))
            .mode(MobChooserMode.DISABLED_TOGGLE)
            .page(page)
            .filter(filter)
            .buttons(GUI_BUTTONS)
            .availableTypes(MobTypes.allLiving())
            .selectedTypes(MobSelections.fromKeys(getDisabledList(section)))
            .build());
    }

    public void openWorldPicker(Player player, SectionType section, int page) {
        openWorldPicker(player, section, page, "");
    }

    public void openWorldPicker(Player player, SectionType section, int page, String filter) {
        worldPickerSections.put(player.getUniqueId(), section);
        List<String> enabled = getWorldList(section, WorldListType.ENABLED);
        List<String> disabled = getWorldList(section, WorldListType.DISABLED);
        List<String> configured = new ArrayList<>();
        configured.addAll(enabled);
        configured.addAll(disabled);

        TriStateSelectionMenus.open(player, TriStateSelectionRequest.builder()
            .worldSelection()
            .entries(WorldSelectionEntries.loadedAndConfigured(plugin, configured))
            .states(TriStateSelections.fromEnabledDisabled(enabled, disabled))
            .cycleOrder(List.of(
                TriStateSelectionState.NEUTRAL,
                TriStateSelectionState.ENABLED,
                TriStateSelectionState.DISABLED
            ))
            .enabledLabel("Enabled")
            .disabledLabel("Disabled")
            .neutralLabel("Use Default")
            .page(page)
            .filter(filter)
            .build());
    }

    @EventHandler
    public void onEditorClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof MaterialChooserHolder materialHolder) {
            handleMaterialChooserClick(player, event, materialHolder);
            return;
        }
        if (holder instanceof MobChooserHolder mobHolder) {
            handleMobChooserClick(player, event, mobHolder);
            return;
        }
        if (holder instanceof TriStateSelectionHolder selectionHolder) {
            handleWorldSelectionClick(player, event, selectionHolder);
            return;
        }
        if (!(holder instanceof EditorMenu menu)) {
            return;
        }

        event.setCancelled(true);
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getView().getTopInventory())) {
            return;
        }

        if (!player.hasPermission("foautocollect.admin")) {
            player.closeInventory();
            plugin.sendMessage(player, "no-permission");
            return;
        }

        int slot = event.getSlot();
        if (menu.type == MenuType.MAIN) {
            handleMainClick(player, slot);
            return;
        }
        if (menu.type == MenuType.SECTION) {
            handleSectionClick(player, menu.section, slot);
            return;
        }
    }

    @EventHandler
    public void onEditorDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!(holder instanceof EditorMenu
            || holder instanceof MaterialChooserHolder
            || holder instanceof MobChooserHolder
            || holder instanceof TriStateSelectionHolder)) {
            return;
        }

        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < event.getView().getTopInventory().getSize()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onEditorClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (plugin.getInventoryCloseSuppressor().consumeSuppressedClose(player)) {
            return;
        }

        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof MaterialChooserHolder || holder instanceof MobChooserHolder) {
            cleanupDisabledPickerLater(player);
            return;
        }
        if (holder instanceof TriStateSelectionHolder) {
            cleanupWorldPickerLater(player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        disabledPickerSections.remove(event.getPlayer().getUniqueId());
        worldPickerSections.remove(event.getPlayer().getUniqueId());
    }

    private void handleMainClick(Player player, int slot) {
        if (slot == 10) {
            toggle("force-enabled");
            plugin.sendMessage(player, "editor-saved", "{setting}", "force enabled");
            openMainMenu(player);
            return;
        }
        if (slot == 12) {
            openSectionMenu(player, SectionType.BLOCK_BREAK);
            return;
        }
        if (slot == 14) {
            openSectionMenu(player, SectionType.MOB_KILLS);
            return;
        }
        if (slot == 19) {
            openSectionMenu(player, SectionType.FISHING);
            return;
        }
        if (slot == 21) {
            openSectionMenu(player, SectionType.SHEARING);
            return;
        }
        if (slot == 23) {
            openSectionMenu(player, SectionType.EXPLOSIONS);
            return;
        }
        if (slot == 28) {
            plugin.getConfig().set("full-inventory.mode", plugin.getFullInventoryPolicy().next().name());
            saveSettings();
            plugin.sendMessage(player, "editor-saved", "{setting}", "full inventory mode");
            openMainMenu(player);
            return;
        }
        if (slot == 30) {
            toggle("full-inventory.actionbar");
            plugin.sendMessage(player, "editor-saved", "{setting}", "full inventory actionbar");
            openMainMenu(player);
            return;
        }
        if (slot == 32) {
            toggle("full-inventory.sound");
            plugin.sendMessage(player, "editor-saved", "{setting}", "full inventory sound");
            openMainMenu(player);
            return;
        }
    }

    private void handleSectionClick(Player player, SectionType section, int slot) {
        if (slot == 11) {
            toggle(section.basePath + ".enabled");
            plugin.sendMessage(player, "editor-saved", "{setting}", section.displayName + " enabled");
            openSectionMenu(player, section);
            return;
        }
        if (slot == 13) {
            toggle(section.basePath + ".collect-drops");
            plugin.sendMessage(player, "editor-saved", "{setting}", section.displayName + " drops");
            openSectionMenu(player, section);
            return;
        }
        if (slot == 15) {
            if (!section.hasExperience) {
                return;
            }
            toggle(section.basePath + ".collect-experience");
            plugin.sendMessage(player, "editor-saved", "{setting}", section.displayName + " XP");
            openSectionMenu(player, section);
            return;
        }
        if (slot == 31) {
            if (section.hasDisabledBrowser()) {
                openPicker(player, section, 0);
            }
            return;
        }
        if (slot == worldsSlot(section)) {
            openWorldPicker(player, section, 0);
            return;
        }
        if (slot == 49) {
            openMainMenu(player);
        }
    }

    private void handleMaterialChooserClick(Player player, InventoryClickEvent event, MaterialChooserHolder holder) {
        event.setCancelled(true);
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getView().getTopInventory())) {
            return;
        }
        if (!ensureAdmin(player)) {
            return;
        }

        SectionType section = disabledPickerSections.get(player.getUniqueId());
        if (section == null) {
            player.closeInventory();
            return;
        }

        MaterialChooserClick click = MaterialChooserMenus.handleClick(event.getRawSlot(), holder);
        switch (click.action()) {
            case PREVIOUS_PAGE, NEXT_PAGE, CLEAR_SEARCH -> MaterialChooserMenus.open(player, click.nextRequest());
            case SEARCH -> startSearchPrompt(player, section, holder.request().filter());
            case BACK -> openSectionMenu(player, section);
            case TOGGLE -> {
                Set<Material> next = MaterialSelections.toggled(holder.request().selectedMaterials(), click.material());
                plugin.getConfig().set(section.disabledPath(), MaterialSelections.toKeys(next));
                saveSettings();
                plugin.sendMessage(
                    player,
                    next.contains(click.material()) ? "editor-added" : "editor-removed",
                    "{value}",
                    MaterialTypes.displayName(click.material())
                );
                MaterialChooserMenus.open(player, holder.request().withSelectedMaterials(next));
            }
            default -> {
            }
        }
    }

    private void handleMobChooserClick(Player player, InventoryClickEvent event, MobChooserHolder holder) {
        event.setCancelled(true);
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getView().getTopInventory())) {
            return;
        }
        if (!ensureAdmin(player)) {
            return;
        }

        SectionType section = disabledPickerSections.get(player.getUniqueId());
        if (section == null) {
            player.closeInventory();
            return;
        }

        MobChooserClick click = MobChooserMenus.handleClick(event.getRawSlot(), holder);
        switch (click.action()) {
            case PREVIOUS_PAGE, NEXT_PAGE, CLEAR_SEARCH -> MobChooserMenus.open(player, click.nextRequest());
            case SEARCH -> startSearchPrompt(player, section, holder.request().filter());
            case BACK -> openSectionMenu(player, section);
            case TOGGLE -> {
                Set<EntityType> next = MobSelections.toggled(holder.request().selectedTypes(), click.entityType());
                plugin.getConfig().set(section.disabledPath(), MobSelections.toKeys(next));
                saveSettings();
                plugin.sendMessage(
                    player,
                    next.contains(click.entityType()) ? "editor-added" : "editor-removed",
                    "{value}",
                    MobTypes.displayName(click.entityType())
                );
                MobChooserMenus.open(player, holder.request().withSelectedTypes(next));
            }
            default -> {
            }
        }
    }

    private void handleWorldSelectionClick(Player player, InventoryClickEvent event, TriStateSelectionHolder holder) {
        event.setCancelled(true);
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getView().getTopInventory())) {
            return;
        }
        if (!ensureAdmin(player)) {
            return;
        }

        SectionType section = worldPickerSections.get(player.getUniqueId());
        if (section == null) {
            player.closeInventory();
            return;
        }

        TriStateSelectionClick click = TriStateSelectionMenus.handleClick(event.getRawSlot(), holder);
        switch (click.action()) {
            case PREVIOUS_PAGE, NEXT_PAGE, CLEAR_SEARCH -> TriStateSelectionMenus.open(player, click.nextRequest());
            case SEARCH -> startWorldSearchPrompt(player, section, holder.request().filter());
            case BACK -> openSectionMenu(player, section);
            case TOGGLE -> {
                saveWorldSelection(section, click.nextRequest());
                plugin.sendMessage(player, "editor-saved", "{setting}", section.displayName + " worlds");
                TriStateSelectionMenus.open(player, click.nextRequest());
            }
            default -> {
            }
        }
    }

    private boolean ensureAdmin(Player player) {
        if (player.hasPermission("foautocollect.admin")) {
            return true;
        }
        player.closeInventory();
        plugin.sendMessage(player, "no-permission");
        return false;
    }

    private void cleanupDisabledPickerLater(Player player) {
        cleanupPickerLater(player, false);
    }

    private void cleanupWorldPickerLater(Player player) {
        cleanupPickerLater(player, true);
    }

    private void cleanupPickerLater(Player player, boolean worldPicker) {
        UUID playerId = player.getUniqueId();
        if (!plugin.isEnabled()) {
            removePickerState(playerId, worldPicker);
            return;
        }

        try {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!player.isOnline() || !hasOpenPicker(player, worldPicker)) {
                    removePickerState(playerId, worldPicker);
                }
            });
        } catch (IllegalPluginAccessException ignored) {
            removePickerState(playerId, worldPicker);
        }
    }

    private boolean hasOpenPicker(Player player, boolean worldPicker) {
        InventoryHolder holder = player.getOpenInventory().getTopInventory().getHolder();
        if (worldPicker) {
            return holder instanceof TriStateSelectionHolder;
        }
        return holder instanceof MaterialChooserHolder || holder instanceof MobChooserHolder;
    }

    private void removePickerState(UUID playerId, boolean worldPicker) {
        if (worldPicker) {
            worldPickerSections.remove(playerId);
            return;
        }
        disabledPickerSections.remove(playerId);
    }

    private void startSearchPrompt(Player player, SectionType section, String filter) {
        TextDialogRequest prompt = searchPrompt(section.singularTitle + "s");
        EditorDialogInputs.openTextFromInventory(
            plugin,
            plugin.getInventoryCloseSuppressor(),
            plugin.getDialogService(),
            player,
            prompt,
            input -> openPicker(player, section, 0, input.trim()),
            () -> {
                plugin.sendMessage(player, "prompt-cancelled");
                openPicker(player, section, 0, filter);
            }
        );
    }

    private void startWorldSearchPrompt(Player player, SectionType section, String filter) {
        TextDialogRequest prompt = searchPrompt("Worlds");
        EditorDialogInputs.openTextFromInventory(
            plugin,
            plugin.getInventoryCloseSuppressor(),
            plugin.getDialogService(),
            player,
            prompt,
            input -> openWorldPicker(player, section, 0, input.trim()),
            () -> {
                plugin.sendMessage(player, "prompt-cancelled");
                openWorldPicker(player, section, 0, filter);
            }
        );
    }

    private TextDialogRequest searchPrompt(String typePlural) {
        return new TextDialogRequest(
            "Search",
            List.of(plugin.format("{white}Type a search term to filter {type_plural}.", "{type_plural}", typePlural)),
            plugin.format("{white}Search term"),
            "",
            "",
            DialogButton.search(),
            DialogButton.cancel(),
            320,
            320,
            64,
            true,
            false,
            false
        );
    }

    private void saveWorldSelection(SectionType section, TriStateSelectionRequest request) {
        plugin.getConfig().set(section.worldPath(WorldListType.ENABLED), lowerKeys(TriStateSelections.enabledKeys(request)));
        plugin.getConfig().set(section.worldPath(WorldListType.DISABLED), lowerKeys(TriStateSelections.disabledKeys(request)));
        saveSettings();
    }

    private int worldsSlot(SectionType section) {
        return section.hasDisabledBrowser() ? 33 : 31;
    }

    private void toggle(String path) {
        boolean current = plugin.getConfig().getBoolean(path, false);
        plugin.getConfig().set(path, !current);
        saveSettings();
    }

    private void saveSettings() {
        plugin.saveConfig();
        plugin.reloadSettings();
    }

    private List<String> getDisabledList(SectionType section) {
        List<String> values = new ArrayList<>();
        for (String raw : plugin.getConfig().getStringList(section.disabledPath())) {
            String normalized = normalizeKey(raw);
            if (!normalized.isBlank() && !values.contains(normalized)) {
                values.add(normalized);
            }
        }
        values.sort(Comparator.naturalOrder());
        return values;
    }

    private List<String> getWorldList(SectionType section, WorldListType worldListType) {
        Set<String> source = switch (section) {
            case BLOCK_BREAK -> worldListType == WorldListType.ENABLED ? plugin.getBlockEnabledWorlds() : plugin.getBlockDisabledWorlds();
            case MOB_KILLS -> worldListType == WorldListType.ENABLED ? plugin.getMobEnabledWorlds() : plugin.getMobDisabledWorlds();
            case FISHING -> worldListType == WorldListType.ENABLED ? plugin.getFishingEnabledWorlds() : plugin.getFishingDisabledWorlds();
            case SHEARING -> worldListType == WorldListType.ENABLED ? plugin.getShearingEnabledWorlds() : plugin.getShearingDisabledWorlds();
            case EXPLOSIONS -> worldListType == WorldListType.ENABLED ? plugin.getExplosionsEnabledWorlds() : plugin.getExplosionsDisabledWorlds();
        };

        List<String> values = new ArrayList<>(source);
        values.sort(Comparator.naturalOrder());
        return values;
    }

    private List<String> lowerKeys(List<String> values) {
        return values.stream()
            .map(value -> value.toLowerCase(Locale.ROOT))
            .sorted()
            .toList();
    }

    private ItemStack createToggleItem(Material material, String label, boolean enabled, String... lore) {
        String state = enabled ? "{good}" + label + ": ON" : "{bad}" + label + ": OFF";
        List<String> lines = new ArrayList<>();
        lines.add("{white}Current: " + formatState(enabled));
        lines.add("");
        lines.addAll(List.of(lore));
        lines.add("");
        lines.add("{white}Click to toggle.");
        return createItem(enabled ? Material.LIME_DYE : Material.RED_DYE, state, lines.toArray(new String[0]));
    }

    private ItemStack createItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(plugin.format(name));
            List<String> renderedLore = new ArrayList<>();
            for (String line : lore) {
                renderedLore.add(plugin.format(line));
            }
            meta.setLore(renderedLore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void fillBackground(Inventory inventory) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (inventory.getItem(slot) == null) {
                inventory.setItem(slot, backgroundFiller);
            }
        }
    }

    private String formatState(boolean enabled) {
        return enabled ? "{good}Enabled" : "{bad}Disabled";
    }

    private String normalizeKey(String value) {
        return MaterialTypes.normalizeKey(value);
    }

    public enum SectionType {
        BLOCK_BREAK(
            "Block Breaks",
            "Block",
            "Disabled Blocks",
            "auto-collect.block-break",
            "block-break",
            "&8ʙʟᴏᴄᴋ ʙʀᴇᴀᴋꜱ",
            "disabled-blocks",
            Material.GRASS_BLOCK,
            Material.COBBLESTONE,
            true
        ),
        MOB_KILLS(
            "Mob Kills",
            "Mob",
            "Disabled Mobs",
            "auto-collect.mob-kills",
            "mob-kills",
            "&8ᴍᴏʙ ᴋɪʟʟꜱ",
            "disabled-mobs",
            Material.ZOMBIE_HEAD,
            Material.CREEPER_HEAD,
            true
        ),
        FISHING(
            "Fishing",
            "Item",
            "",
            "auto-collect.fishing",
            "fishing",
            "&8ꜰɪꜱʜɪɴɢ",
            null,
            Material.FISHING_ROD,
            Material.COD,
            true
        ),
        SHEARING(
            "Shearing",
            "Drop",
            "",
            "auto-collect.shearing",
            "shearing",
            "&8ꜱʜᴇᴀʀɪɴɢ",
            null,
            Material.SHEARS,
            Material.WHITE_WOOL,
            false
        ),
        EXPLOSIONS(
            "Explosions",
            "Reward",
            "",
            "auto-collect.explosions",
            "explosions",
            "&8ᴇxᴘʟᴏꜱɪᴏɴꜱ",
            null,
            Material.TNT,
            Material.GUNPOWDER,
            false
        );

        private final String displayName;
        private final String singularTitle;
        private final String disabledDisplayName;
        private final String basePath;
        private final String guiTitleKey;
        private final String fallbackTitle;
        private final String disabledGuiTitleKey;
        private final Material mainIcon;
        private final Material listIcon;
        private final boolean hasExperience;

        SectionType(
            String displayName,
            String singularTitle,
            String disabledDisplayName,
            String basePath,
            String guiTitleKey,
            String fallbackTitle,
            String disabledGuiTitleKey,
            Material mainIcon,
            Material listIcon,
            boolean hasExperience
        ) {
            this.displayName = displayName;
            this.singularTitle = singularTitle;
            this.disabledDisplayName = disabledDisplayName;
            this.basePath = basePath;
            this.guiTitleKey = guiTitleKey;
            this.fallbackTitle = fallbackTitle;
            this.disabledGuiTitleKey = disabledGuiTitleKey;
            this.mainIcon = mainIcon;
            this.listIcon = listIcon;
            this.hasExperience = hasExperience;
        }

        private String disabledPath() {
            return basePath + "." + disabledGuiTitleKey;
        }

        private boolean hasDisabledBrowser() {
            return disabledGuiTitleKey != null;
        }

        private String worldPath(WorldListType worldListType) {
            return basePath + "." + worldListType.configKey;
        }
    }

    private enum MenuType {
        MAIN,
        SECTION
    }

    private enum WorldListType {
        ENABLED("enabled-worlds"),
        DISABLED("disabled-worlds");

        private final String configKey;

        WorldListType(String configKey) {
            this.configKey = configKey;
        }
    }

    private static class EditorMenu implements InventoryHolder {
        private final MenuType type;
        private final SectionType section;
        private Inventory inventory;

        private EditorMenu(MenuType type, SectionType section) {
            this.type = type;
            this.section = section;
        }

        private void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
