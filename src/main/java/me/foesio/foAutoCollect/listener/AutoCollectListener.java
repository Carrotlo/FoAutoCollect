package me.foesio.foAutoCollect.listener;

import me.foesio.core.inventory.InventoryDepositResult;
import me.foesio.core.inventory.InventoryDepositResultMode;
import me.foesio.core.inventory.InventoryDepositService;
import me.foesio.core.inventory.OverflowPolicy;
import me.foesio.foAutoCollect.FoAutoCollect;
import me.foesio.foAutoCollect.event.AutoCollectItemsCollectedEvent;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.TNTPrimeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitTask;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

public class AutoCollectListener implements Listener {
    private static final long BLOCK_DROP_BATCH_DELAY_TICKS = 4L;
    private static final long COLLECTED_EVENT_LISTENER_CACHE_MILLIS = 1_000L;

    private final FoAutoCollect plugin;
    private final InventoryDepositService inventoryDeposits;
    private final Map<UUID, BlockDropBatch> pendingBlockDropBatches = new HashMap<>();
    private final Map<UUID, ShearContext> recentShears = new HashMap<>();
    private final List<ExplosionSource> recentExplosionSources = new ArrayList<>();
    private final List<ExplosionCollectContext> recentExplosionCollections = new ArrayList<>();
    private long collectedEventListenerCacheExpiresAt;
    private boolean collectedEventListenersPresent;

    public AutoCollectListener(FoAutoCollect plugin) {
        this.plugin = plugin;
        this.inventoryDeposits = plugin.getInventoryDepositService();
    }

    public void clearRuntimeState() {
        flushPendingBlockDropBatches();
        recentShears.clear();
        recentExplosionSources.clear();
        recentExplosionCollections.clear();
    }

    public boolean collectExternalReward(Player player, ItemStack itemStack, Location location) {
        if (player == null || itemStack == null || itemStack.getType() == Material.AIR) {
            return false;
        }

        InventoryDepositResult result = depositItem(player, itemStack, location);
        if (!acceptDeposit(player, result)) {
            return false;
        }
        callCollectedEvent(player, result);
        return true;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerFish(PlayerFishEvent event) {
        Player player = event.getPlayer();
        if (!plugin.isAutoCollectActive(player) || !plugin.isFishingEnabled(player.getWorld())) {
            return;
        }

        if (plugin.isFishingExperienceCollectionEnabled()) {
            int experience = event.getExpToDrop();
            if (experience > 0) {
                event.setExpToDrop(0);
                player.giveExp(experience);
            }
        }

        if (!plugin.isFishingDropCollectionEnabled() || event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }

        if (!(event.getCaught() instanceof Item item)) {
            return;
        }
        ItemStack stack = item.getItemStack();
        if (stack == null || stack.getType() == Material.AIR) {
            return;
        }

        InventoryDepositResult result = depositItem(player, stack, item.getLocation());
        if (!acceptDeposit(player, result)) {
            return;
        }

        item.remove();
        callCollectedEvent(player, result);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void trackShearing(PlayerShearEntityEvent event) {
        Player player = event.getPlayer();
        if (!plugin.isAutoCollectActive(player)
            || !plugin.isShearingEnabled(event.getEntity().getWorld())
            || !plugin.isShearingDropCollectionEnabled()) {
            return;
        }

        recentShears.put(event.getEntity().getUniqueId(), new ShearContext(player.getUniqueId(), System.currentTimeMillis() + 2_000L));
        cleanupShears();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDropItem(EntityDropItemEvent event) {
        cleanupShears();
        ShearContext context = recentShears.get(event.getEntity().getUniqueId());
        if (context == null) {
            return;
        }

        Player player = plugin.getServer().getPlayer(context.playerId());
        if (player == null
            || !player.isOnline()
            || !plugin.isAutoCollectActive(player)
            || !plugin.isShearingEnabled(event.getEntity().getWorld())
            || !plugin.isShearingDropCollectionEnabled()) {
            return;
        }

        ItemStack stack = event.getItemDrop().getItemStack();
        if (stack == null || stack.getType() == Material.AIR) {
            return;
        }

        InventoryDepositResult result = depositItem(player, stack, event.getItemDrop().getLocation());
        if (!acceptDeposit(player, result)) {
            return;
        }

        event.setCancelled(true);
        event.getItemDrop().remove();
        callCollectedEvent(player, result);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onTntPrime(TNTPrimeEvent event) {
        Player player = resolveExplosionSourcePlayer(event.getPrimingEntity(), centered(event.getBlock().getLocation()));
        if (player == null && event.getPrimingBlock() != null) {
            player = findRecentExplosionSource(centered(event.getPrimingBlock().getLocation()));
        }
        if (player == null) {
            return;
        }

        rememberExplosionSource(player, centered(event.getBlock().getLocation()), 30_000L);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent event) {
        Player player = event.getPlayer();
        if (player != null) {
            rememberExplosionSource(player, centered(event.getBlock().getLocation()), 10_000L);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onExplosiveInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
            || event.getAction() != Action.RIGHT_CLICK_BLOCK
            || event.getClickedBlock() == null
            || !isPlayerTriggeredExplosive(event.getClickedBlock().getType())) {
            return;
        }

        rememberExplosionSource(event.getPlayer(), centered(event.getClickedBlock().getLocation()), 10_000L);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        Player player = resolveExplosionSourcePlayer(event.getEntity(), event.getLocation());
        handleExplosionDrops(player, event.getLocation(), event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        Player player = findRecentExplosionSource(centered(event.getBlock().getLocation()));
        handleExplosionDrops(player, centered(event.getBlock().getLocation()), event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExplosionItemSpawn(ItemSpawnEvent event) {
        cleanupExplosions();
        ExplosionCollectContext context = findExplosionCollection(event.getLocation());
        if (context == null) {
            return;
        }

        Player player = plugin.getServer().getPlayer(context.playerId());
        if (player == null
            || !player.isOnline()
            || !plugin.isAutoCollectActive(player)
            || !plugin.isExplosionsEnabled(event.getLocation().getWorld())
            || !plugin.isExplosionDropCollectionEnabled()) {
            return;
        }

        ItemStack stack = event.getEntity().getItemStack();
        if (stack == null || stack.getType() == Material.AIR) {
            return;
        }

        InventoryDepositResult result = depositItem(player, stack, event.getLocation());
        if (!acceptDeposit(player, result)) {
            return;
        }

        event.setCancelled(true);
        callCollectedEvent(player, result);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!plugin.isBlockExperienceCollectionEnabled()) {
            return;
        }

        int experience = event.getExpToDrop();
        if (experience <= 0) {
            return;
        }

        Player player = event.getPlayer();
        Material blockType = event.getBlock().getType();
        if (!plugin.isAutoCollectActive(player) || !plugin.isBlockBreakEnabled(blockType, event.getBlock().getWorld())) {
            return;
        }

        event.setExpToDrop(0);
        player.giveExp(experience);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockDropItem(BlockDropItemEvent event) {
        List<Item> drops = event.getItems();
        if (drops.isEmpty()) {
            return;
        }

        BlockState blockState = event.getBlockState();
        Player player = event.getPlayer();
        Material blockType = blockState.getType();
        if (!shouldCollectBlockDrops(player, blockType, blockState.getWorld())) {
            return;
        }

        List<ItemStack> stacks = collectItemStacks(drops);
        if (stacks.isEmpty()) {
            return;
        }

        if (shouldDepositBlockDropsImmediately()) {
            InventoryDepositResult result = depositBlockDrops(player, stacks, blockState);
            if (!acceptDeposit(player, result)) {
                return;
            }
            callCollectedEvent(player, result);
        } else {
            enqueueBlockDrops(player, stacks, blockState);
        }

        removeDropEntities(drops);
        drops.clear();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDeath(EntityDeathEvent event) {
        Player player = event.getEntity().getKiller();
        if (player == null) {
            return;
        }
        if (!plugin.isAutoCollectActive(player) || !plugin.isMobKillEnabled(event.getEntityType(), event.getEntity().getWorld())) {
            return;
        }

        if (plugin.isMobExperienceCollectionEnabled()) {
            int experience = event.getDroppedExp();
            if (experience > 0) {
                event.setDroppedExp(0);
                player.giveExp(experience);
            }
        }

        if (plugin.isMobDropCollectionEnabled() && !event.getDrops().isEmpty()) {
            List<ItemStack> stacks = new ArrayList<>(event.getDrops());
            InventoryDepositResult result = depositItems(player, stacks, event.getEntity().getLocation());
            if (!acceptDeposit(player, result)) {
                return;
            }

            event.getDrops().clear();
            callCollectedEvent(player, result);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        flushPendingBlockDrops(event.getPlayer());
        inventoryDeposits.clearFullInventoryFeedbackCooldown(event.getPlayer());
        recentShears.values().removeIf(context -> context.playerId().equals(event.getPlayer().getUniqueId()));
        recentExplosionSources.removeIf(context -> context.playerId().equals(event.getPlayer().getUniqueId()));
        recentExplosionCollections.removeIf(context -> context.playerId().equals(event.getPlayer().getUniqueId()));
    }

    private void cleanupShears() {
        long now = System.currentTimeMillis();
        recentShears.values().removeIf(context -> context.expiresAt() < now);
    }

    private void handleExplosionDrops(Player player, Location location, List<Block> blocks) {
        cleanupExplosions();
        if (player == null
            || !plugin.isAutoCollectActive(player)
            || !plugin.isExplosionsEnabled(location != null ? location.getWorld() : null)
            || !plugin.isExplosionDropCollectionEnabled()
            || blocks.isEmpty()) {
            return;
        }
        if (blocks.size() > plugin.getExplosionMaxBlocksPerExplosion()) {
            return;
        }

        Set<BlockKey> affectedBlocks = new HashSet<>();
        for (Block block : blocks) {
            affectedBlocks.add(BlockKey.from(block.getLocation()));
        }
        trimOldestForNextExplosionEntry(recentExplosionCollections);
        recentExplosionCollections.add(new ExplosionCollectContext(
            player.getUniqueId(),
            affectedBlocks,
            System.currentTimeMillis() + 3_000L
        ));
    }

    private void rememberExplosionSource(Player player, Location location, long ttlMillis) {
        if (player == null || location == null || location.getWorld() == null) {
            return;
        }

        cleanupExplosions();
        trimOldestForNextExplosionEntry(recentExplosionSources);
        recentExplosionSources.add(new ExplosionSource(
            player.getUniqueId(),
            location.clone(),
            System.currentTimeMillis() + ttlMillis
        ));
    }

    private Player resolveExplosionSourcePlayer(Entity entity, Location location) {
        if (entity instanceof Player player) {
            return player;
        }
        if (entity instanceof TNTPrimed tnt && tnt.getSource() != null) {
            Player player = resolveExplosionSourcePlayer(tnt.getSource(), tnt.getLocation());
            if (player != null) {
                rememberExplosionSource(player, tnt.getLocation(), 10_000L);
                return player;
            }
        }
        if (entity instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) {
                rememberExplosionSource(player, projectile.getLocation(), 10_000L);
                return player;
            }
            if (shooter instanceof Entity shooterEntity) {
                Player player = resolveExplosionSourcePlayer(shooterEntity, projectile.getLocation());
                if (player != null) {
                    return player;
                }
            }
        }
        return findRecentExplosionSource(location);
    }

    private Player findRecentExplosionSource(Location location) {
        cleanupExplosions();
        ExplosionSource source = findNearestExplosionSource(location);
        return source == null ? null : plugin.getServer().getPlayer(source.playerId());
    }

    private ExplosionSource findNearestExplosionSource(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }

        ExplosionSource nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (ExplosionSource source : recentExplosionSources) {
            if (!source.location().getWorld().equals(location.getWorld())) {
                continue;
            }

            double distance = source.location().distanceSquared(location);
            if (distance <= 144.0D && distance < nearestDistance) {
                nearest = source;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private ExplosionCollectContext findExplosionCollection(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }

        int blockX = location.getBlockX();
        int blockY = location.getBlockY();
        int blockZ = location.getBlockZ();
        String worldName = location.getWorld().getName();
        for (ExplosionCollectContext context : recentExplosionCollections) {
            for (int x = blockX - 1; x <= blockX + 1; x++) {
                for (int y = blockY - 1; y <= blockY + 1; y++) {
                    for (int z = blockZ - 1; z <= blockZ + 1; z++) {
                        if (context.affectedBlocks().contains(new BlockKey(worldName, x, y, z))) {
                            return context;
                        }
                    }
                }
            }
        }
        return null;
    }

    private void cleanupExplosions() {
        long now = System.currentTimeMillis();
        recentExplosionSources.removeIf(context -> context.expiresAt() < now);
        recentExplosionCollections.removeIf(context -> context.expiresAt() < now);
    }

    private void trimOldestForNextExplosionEntry(List<?> contexts) {
        int maxTracked = plugin.getExplosionMaxTrackedExplosions();
        while (contexts.size() >= maxTracked && !contexts.isEmpty()) {
            contexts.remove(0);
        }
    }

    private boolean isPlayerTriggeredExplosive(Material material) {
        String name = material.name();
        return name.endsWith("_BED") || name.equals("RESPAWN_ANCHOR");
    }

    private boolean shouldCollectBlockDrops(Player player, Material blockType, World world) {
        return plugin.isBlockDropCollectionEnabled()
            && plugin.isBlockBreakEnabled(blockType, world)
            && plugin.isAutoCollectActive(player);
    }

    private boolean shouldDepositBlockDropsImmediately() {
        return overflowPolicy() == OverflowPolicy.BLOCK_COLLECTION || hasCollectedEventListeners();
    }

    private List<ItemStack> collectItemStacks(Collection<Item> items) {
        List<ItemStack> stacks = new ArrayList<>(items.size());
        for (Item item : items) {
            if (item == null) {
                continue;
            }

            ItemStack stack = item.getItemStack();
            if (isDepositable(stack)) {
                stacks.add(stack);
            }
        }
        return stacks;
    }

    private void removeDropEntities(Collection<Item> items) {
        for (Item item : items) {
            if (item != null) {
                item.remove();
            }
        }
    }

    private InventoryDepositResult depositItem(Player player, ItemStack stack, Location overflowLocation) {
        if (!hasCollectedEventListeners()) {
            return depositItemFast(player, stack, () -> overflowLocation);
        }
        return inventoryDeposits.deposit(
            player,
            stack,
            overflowLocation,
            overflowPolicy(),
            InventoryDepositResultMode.WITH_ACCEPTED_ITEMS
        );
    }

    private InventoryDepositResult depositItems(Player player, Collection<ItemStack> stacks, Location overflowLocation) {
        if (!hasCollectedEventListeners()) {
            return depositItemsFast(player, stacks, () -> overflowLocation);
        }
        return inventoryDeposits.deposit(
            player,
            stacks,
            overflowLocation,
            overflowPolicy(),
            InventoryDepositResultMode.WITH_ACCEPTED_ITEMS
        );
    }

    private InventoryDepositResult depositBlockDrops(Player player, Collection<ItemStack> stacks, BlockState blockState) {
        if (!hasCollectedEventListeners()) {
            return depositItemsFast(player, stacks, () -> centered(blockState.getLocation()));
        }
        return inventoryDeposits.deposit(
            player,
            stacks,
            centered(blockState.getLocation()),
            overflowPolicy(),
            InventoryDepositResultMode.WITH_ACCEPTED_ITEMS
        );
    }

    private boolean acceptDeposit(Player player, InventoryDepositResult result) {
        if (inventoryDeposits.shouldSendFullInventoryFeedback(player, result)) {
            showInventoryFullFeedback(player);
        }
        return !result.blocked();
    }

    private void callCollectedEvent(Player player, InventoryDepositResult result) {
        if (hasCollectedEventListeners() && !result.acceptedItems().isEmpty()) {
            plugin.getServer().getPluginManager().callEvent(
                new AutoCollectItemsCollectedEvent(player, result.acceptedItems(), result.location())
            );
        }
    }

    private OverflowPolicy overflowPolicy() {
        return plugin.getFullInventoryPolicy();
    }

    private boolean hasCollectedEventListeners() {
        long now = System.currentTimeMillis();
        if (now < collectedEventListenerCacheExpiresAt) {
            return collectedEventListenersPresent;
        }

        collectedEventListenersPresent = AutoCollectItemsCollectedEvent.getHandlerList().getRegisteredListeners().length > 0;
        collectedEventListenerCacheExpiresAt = now + COLLECTED_EVENT_LISTENER_CACHE_MILLIS;
        return collectedEventListenersPresent;
    }

    private InventoryDepositResult depositItemFast(Player player, ItemStack stack, Supplier<Location> overflowLocation) {
        if (player == null || !isDepositable(stack)) {
            return InventoryDepositResult.empty(null);
        }
        if (overflowPolicy() == OverflowPolicy.BLOCK_COLLECTION && !canFitAll(player, stack)) {
            return InventoryDepositResult.blocked(List.of(), null);
        }

        List<ItemStack> overflow = depositFast(player, List.of(stack));
        if (!overflow.isEmpty()) {
            dropOverflow(resolveDropLocation(player, overflowLocation), overflow);
            sendInventoryFullFeedback(player);
        }
        return InventoryDepositResult.empty(null);
    }

    private void enqueueBlockDrops(Player player, Collection<ItemStack> stacks, BlockState blockState) {
        UUID playerId = player.getUniqueId();
        DropLocation location = DropLocation.centered(blockState);
        BlockDropBatch batch = pendingBlockDropBatches.get(playerId);
        if (batch == null) {
            batch = new BlockDropBatch(location);
            batch.task(scheduleBlockDropFlush(playerId));
            pendingBlockDropBatches.put(playerId, batch);
        } else {
            batch.location(location);
        }

        batch.add(stacks);
    }

    private BukkitTask scheduleBlockDropFlush(UUID playerId) {
        return plugin.getServer().getScheduler().runTaskLater(
            plugin,
            () -> flushPendingBlockDrops(playerId),
            BLOCK_DROP_BATCH_DELAY_TICKS
        );
    }

    private void flushPendingBlockDropBatches() {
        List<UUID> playerIds = new ArrayList<>(pendingBlockDropBatches.keySet());
        for (UUID playerId : playerIds) {
            flushPendingBlockDrops(playerId);
        }
    }

    private void flushPendingBlockDrops(Player player) {
        flushPendingBlockDrops(player.getUniqueId(), player);
    }

    private void flushPendingBlockDrops(UUID playerId) {
        flushPendingBlockDrops(playerId, plugin.getServer().getPlayer(playerId));
    }

    private void flushPendingBlockDrops(UUID playerId, Player player) {
        BlockDropBatch batch = pendingBlockDropBatches.remove(playerId);
        if (batch == null) {
            return;
        }
        batch.cancelTask();

        Location location = batch.location();
        if (player == null || !player.isOnline()) {
            dropOverflow(location, batch.stacks());
            return;
        }

        InventoryDepositResult result = depositItems(player, batch.stacks(), location);
        if (!acceptDeposit(player, result)) {
            dropOverflow(location, batch.stacks());
            return;
        }
        callCollectedEvent(player, result);
    }

    private InventoryDepositResult depositItemsFast(Player player, Collection<ItemStack> stacks, Supplier<Location> overflowLocation) {
        if (player == null || stacks == null || stacks.isEmpty()) {
            return InventoryDepositResult.empty(null);
        }

        List<ItemStack> compacted = compactStacks(stacks);
        if (compacted.isEmpty()) {
            return InventoryDepositResult.empty(null);
        }
        if (overflowPolicy() == OverflowPolicy.BLOCK_COLLECTION && !canFitAll(player, compacted)) {
            return InventoryDepositResult.blocked(List.of(), null);
        }

        List<ItemStack> overflow = depositFast(player, compacted);
        if (!overflow.isEmpty()) {
            dropOverflow(resolveDropLocation(player, overflowLocation), overflow);
            sendInventoryFullFeedback(player);
        }
        return InventoryDepositResult.empty(null);
    }

    private List<ItemStack> compactStacks(Collection<ItemStack> stacks) {
        List<ItemStack> compacted = new ArrayList<>();
        Map<Material, ItemStack> simpleOpenStacks = new EnumMap<>(Material.class);
        for (ItemStack stack : stacks) {
            if (!isDepositable(stack)) {
                continue;
            }

            if (isSimpleStack(stack)) {
                compactSimpleStack(stack, compacted, simpleOpenStacks);
                continue;
            }

            int remaining = stack.getAmount();
            for (ItemStack existing : compacted) {
                if (remaining <= 0) {
                    break;
                }
                if (!canStackTogether(existing, stack)) {
                    continue;
                }

                int room = Math.max(0, existing.getMaxStackSize() - existing.getAmount());
                if (room <= 0) {
                    continue;
                }

                int move = Math.min(remaining, room);
                existing.setAmount(existing.getAmount() + move);
                remaining -= move;
            }

            int maxStackSize = Math.max(1, stack.getMaxStackSize());
            while (remaining > 0) {
                ItemStack compactedStack = stack.clone();
                int move = Math.min(remaining, maxStackSize);
                compactedStack.setAmount(move);
                compacted.add(compactedStack);
                remaining -= move;
            }
        }
        return compacted;
    }

    private void compactSimpleStack(ItemStack stack, List<ItemStack> compacted, Map<Material, ItemStack> simpleOpenStacks) {
        Material type = stack.getType();
        int remaining = stack.getAmount();
        int maxStackSize = Math.max(1, stack.getMaxStackSize());

        ItemStack openStack = simpleOpenStacks.get(type);
        if (openStack != null) {
            int room = Math.max(0, maxStackSize - openStack.getAmount());
            if (room > 0) {
                int move = Math.min(remaining, room);
                openStack.setAmount(openStack.getAmount() + move);
                remaining -= move;
            }
            if (openStack.getAmount() >= maxStackSize) {
                simpleOpenStacks.remove(type);
            }
        }

        while (remaining > 0) {
            ItemStack compactedStack = stack.clone();
            int move = Math.min(remaining, maxStackSize);
            compactedStack.setAmount(move);
            compacted.add(compactedStack);
            remaining -= move;

            if (move < maxStackSize) {
                simpleOpenStacks.put(type, compactedStack);
            } else {
                simpleOpenStacks.remove(type);
            }
        }
    }

    private List<ItemStack> depositFast(Player player, Collection<ItemStack> stacks) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] storage = inventory.getStorageContents();
        boolean[] changedSlots = new boolean[storage.length];
        List<ItemStack> overflow = null;

        for (ItemStack stack : stacks) {
            if (!isDepositable(stack)) {
                continue;
            }

            int remaining = insertIntoStorage(stack, storage, changedSlots);
            if (remaining > 0) {
                if (overflow == null) {
                    overflow = new ArrayList<>();
                }
                ItemStack leftover = stack.clone();
                leftover.setAmount(remaining);
                overflow.add(leftover);
            }
        }

        for (int i = 0; i < changedSlots.length; i++) {
            if (changedSlots[i]) {
                inventory.setItem(i, storage[i]);
            }
        }
        return overflow == null ? List.of() : overflow;
    }

    private int insertIntoStorage(ItemStack stack, ItemStack[] storage, boolean[] changedSlots) {
        int remaining = stack.getAmount();

        for (int i = 0; i < storage.length && remaining > 0; i++) {
            ItemStack existing = storage[i];
            if (existing == null || existing.getType().isAir() || !canStackTogether(existing, stack)) {
                continue;
            }

            int maxStackSize = Math.max(1, existing.getMaxStackSize());
            int room = Math.max(0, maxStackSize - existing.getAmount());
            if (room <= 0) {
                continue;
            }

            int move = Math.min(remaining, room);
            ItemStack updated = existing.clone();
            updated.setAmount(existing.getAmount() + move);
            storage[i] = updated;
            changedSlots[i] = true;
            remaining -= move;
        }

        int maxStackSize = Math.max(1, stack.getMaxStackSize());
        for (int i = 0; i < storage.length && remaining > 0; i++) {
            ItemStack existing = storage[i];
            if (existing != null && !existing.getType().isAir()) {
                continue;
            }

            int move = Math.min(remaining, maxStackSize);
            ItemStack inserted = stack.clone();
            inserted.setAmount(move);
            storage[i] = inserted;
            changedSlots[i] = true;
            remaining -= move;
        }

        return remaining;
    }

    private boolean canFitAll(Player player, ItemStack stack) {
        if (player == null) {
            return false;
        }

        InventorySimulation simulation = simulateInventory(player);
        return canFitStack(stack, simulation.stacks(), simulation.amounts());
    }

    private boolean canFitAll(Player player, Collection<ItemStack> stacks) {
        if (player == null) {
            return false;
        }

        InventorySimulation simulation = simulateInventory(player);

        for (ItemStack stack : stacks) {
            if (!canFitStack(stack, simulation.stacks(), simulation.amounts())) {
                return false;
            }
        }
        return true;
    }

    private InventorySimulation simulateInventory(Player player) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        ItemStack[] simulated = new ItemStack[contents.length];
        int[] amounts = new int[contents.length];
        for (int i = 0; i < contents.length; i++) {
            simulated[i] = contents[i];
            amounts[i] = contents[i] == null ? 0 : contents[i].getAmount();
        }
        return new InventorySimulation(simulated, amounts);
    }

    private boolean canFitStack(ItemStack stack, ItemStack[] simulated, int[] simulatedAmounts) {
        if (!isDepositable(stack)) {
            return true;
        }

        int remaining = stack.getAmount();
        int maxStackSize = Math.max(1, stack.getMaxStackSize());

        for (int i = 0; i < simulated.length && remaining > 0; i++) {
            ItemStack existing = simulated[i];
            if (existing == null || existing.getType().isAir() || !canStackTogether(existing, stack)) {
                continue;
            }
            int move = Math.min(remaining, Math.max(0, existing.getMaxStackSize() - simulatedAmounts[i]));
            simulatedAmounts[i] += move;
            remaining -= move;
        }

        for (int i = 0; i < simulated.length && remaining > 0; i++) {
            ItemStack existing = simulated[i];
            if (existing != null && !existing.getType().isAir()) {
                continue;
            }
            int move = Math.min(remaining, maxStackSize);
            ItemStack copy = stack.clone();
            copy.setAmount(move);
            simulated[i] = copy;
            simulatedAmounts[i] = move;
            remaining -= move;
        }

        return remaining <= 0;
    }

    private Location resolveDropLocation(Player player, Supplier<Location> overflowLocation) {
        Location location = overflowLocation == null ? null : overflowLocation.get();
        if (location == null) {
            location = player.getLocation();
        }
        return location == null ? null : location.clone();
    }

    private void dropOverflow(Location location, Collection<ItemStack> leftovers) {
        if (location == null || location.getWorld() == null) {
            return;
        }

        for (ItemStack leftover : leftovers) {
            if (isDepositable(leftover)) {
                location.getWorld().dropItemNaturally(location, leftover.clone());
            }
        }
    }

    private void sendInventoryFullFeedback(Player player) {
        if (inventoryDeposits.shouldSendFullInventoryFeedback(player)) {
            showInventoryFullFeedback(player);
        }
    }

    private boolean isDepositable(ItemStack stack) {
        return stack != null && !stack.getType().isAir() && stack.getAmount() > 0;
    }

    private boolean canStackTogether(ItemStack existing, ItemStack stack) {
        if (existing.getType() != stack.getType()) {
            return false;
        }
        if (isSimpleStack(existing) && isSimpleStack(stack)) {
            return true;
        }
        return existing.isSimilar(stack);
    }

    private boolean isSimpleStack(ItemStack stack) {
        return stack != null && !stack.hasItemMeta();
    }

    private void showInventoryFullFeedback(Player player) {
        if (plugin.isFullInventoryActionbarEnabled()) {
            player.spigot().sendMessage(
                ChatMessageType.ACTION_BAR,
                TextComponent.fromLegacyText(plugin.getMessage("inventory-full-actionbar"))
            );
        }
        if (plugin.isFullInventorySoundEnabled()) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.75F, 0.75F);
        }
    }

    private Location centered(Location location) {
        return location.clone().add(0.5D, 0.5D, 0.5D);
    }

    private record ShearContext(UUID playerId, long expiresAt) {
    }

    private record ExplosionSource(UUID playerId, Location location, long expiresAt) {
    }

    private record ExplosionCollectContext(UUID playerId, Set<BlockKey> affectedBlocks, long expiresAt) {
    }

    private record InventorySimulation(ItemStack[] stacks, int[] amounts) {
    }

    private record DropLocation(World world, double x, double y, double z) {
        private static DropLocation centered(BlockState blockState) {
            return new DropLocation(
                blockState.getWorld(),
                blockState.getX() + 0.5D,
                blockState.getY() + 0.5D,
                blockState.getZ() + 0.5D
            );
        }

        private Location toLocation() {
            return world == null ? null : new Location(world, x, y, z);
        }
    }

    private static final class BlockDropBatch {
        private final List<ItemStack> stacks = new ArrayList<>();
        private DropLocation location;
        private BukkitTask task;

        private BlockDropBatch(DropLocation location) {
            location(location);
        }

        private void add(Collection<ItemStack> source) {
            for (ItemStack stack : source) {
                if (isDepositableStatic(stack)) {
                    stacks.add(stack.clone());
                }
            }
        }

        private List<ItemStack> stacks() {
            return stacks;
        }

        private Location location() {
            return location == null ? null : location.toLocation();
        }

        private void location(DropLocation location) {
            this.location = location;
        }

        private void task(BukkitTask task) {
            this.task = task;
        }

        private void cancelTask() {
            if (task != null) {
                try {
                    task.cancel();
                } catch (IllegalStateException ignored) {
                    // Already running or already completed.
                }
                task = null;
            }
        }

        private static boolean isDepositableStatic(ItemStack stack) {
            return stack != null && !stack.getType().isAir() && stack.getAmount() > 0;
        }
    }

    private record BlockKey(String worldName, int x, int y, int z) {
        private static BlockKey from(Location location) {
            return new BlockKey(
                location.getWorld().getName(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ()
            );
        }
    }
}
