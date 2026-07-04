package me.foesio.foAutoCollect.integration;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import me.foesio.core.material.MaterialTypes;
import me.foesio.core.mob.MobTypes;
import me.foesio.foAutoCollect.FoAutoCollect;
import me.foesio.foAutoCollect.listener.AutoCollectListener;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.EventExecutor;

public class FoDropsRewardBridge implements Listener {
    private static final String EVENT_CLASS_NAME = "me.foesio.foDrops.api.FoDropRewardEvent";

    private final FoAutoCollect plugin;
    private final AutoCollectListener collector;
    private final Map<String, Material> materialCache = new HashMap<>();
    private final Map<String, EntityType> entityTypeCache = new HashMap<>();
    private Method getPlayer;
    private Method getItemStack;
    private Method getLocation;
    private Method getEventType;
    private Method getTargetTypeKey;
    private Method setCancelled;
    private Method setHandled;

    public FoDropsRewardBridge(FoAutoCollect plugin, AutoCollectListener collector) {
        this.plugin = plugin;
        this.collector = collector;
    }

    @SuppressWarnings("unchecked")
    public void register() {
        try {
            Class<?> rawEventClass = Class.forName(EVENT_CLASS_NAME);
            if (!Event.class.isAssignableFrom(rawEventClass)) {
                return;
            }

            getPlayer = rawEventClass.getMethod("getPlayer");
            getItemStack = rawEventClass.getMethod("getItemStack");
            getLocation = rawEventClass.getMethod("getLocation");
            getEventType = rawEventClass.getMethod("getEventType");
            getTargetTypeKey = rawEventClass.getMethod("getTargetTypeKey");
            setCancelled = rawEventClass.getMethod("setCancelled", boolean.class);
            try {
                setHandled = rawEventClass.getMethod("setHandled", boolean.class);
            } catch (NoSuchMethodException ignored) {
                setHandled = null;
            }

            Class<? extends Event> eventClass = (Class<? extends Event>) rawEventClass;
            EventExecutor executor = (listener, event) -> handleRewardEvent(event);
            plugin.getServer().getPluginManager().registerEvent(eventClass, this, EventPriority.HIGHEST, executor, plugin, true);
            plugin.getLogger().info("Hooked FoDrops reward event integration.");
        } catch (ClassNotFoundException ignored) {
            return;
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().warning("FoDrops reward event integration is unavailable: " + exception.getMessage());
        }
    }

    private void handleRewardEvent(Event event) {
        try {
            Player player = (Player) getPlayer.invoke(event);
            if (player == null || !player.isOnline() || !plugin.isAutoCollectActive(player)) {
                return;
            }

            ItemStack itemStack = (ItemStack) getItemStack.invoke(event);
            if (itemStack == null || itemStack.getType() == Material.AIR || itemStack.getAmount() <= 0) {
                return;
            }

            Location location = (Location) getLocation.invoke(event);
            String eventType = normalizeEvent(getEventType.invoke(event));
            if (!shouldCollectReward(eventType, event, location)) {
                return;
            }

            if (collector.collectExternalReward(player, itemStack, location)) {
                setCancelled.invoke(event, true);
                markHandled(event);
            }
        } catch (ReflectiveOperationException | ClassCastException exception) {
            plugin.getLogger().warning("Failed to handle FoDrops reward event: " + exception.getMessage());
        }
    }

    private void markHandled(Event event) {
        if (setHandled == null) {
            return;
        }

        try {
            setHandled.invoke(event, true);
        } catch (ReflectiveOperationException exception) {
            setHandled = null;
            plugin.getLogger().warning("FoDrops setHandled hook failed; continuing with event cancellation only: "
                + exception.getMessage());
        }
    }

    private boolean shouldCollectReward(String eventType, Event event, Location location) throws ReflectiveOperationException {
        World world = location != null ? location.getWorld() : null;

        if (eventType.equals("BLOCK_BREAK") || eventType.equals("BLOCK_DROP_ITEM")) {
            if (!plugin.isBlockDropCollectionEnabled()) {
                return false;
            }
            Material material = parseMaterial(normalizeKey(getTargetTypeKey.invoke(event)));
            return material != null && plugin.isBlockBreakEnabled(material, world);
        }

        if (eventType.equals("MOB_KILL")) {
            if (!plugin.isMobDropCollectionEnabled()) {
                return false;
            }
            EntityType entityType = parseEntityType(normalizeKey(getTargetTypeKey.invoke(event)));
            return entityType != null && plugin.isMobKillEnabled(entityType, world);
        }

        if (eventType.equals("PLAYER_FISH")) {
            return plugin.isFishingEnabled(world) && plugin.isFishingDropCollectionEnabled();
        }

        if (eventType.equals("PLAYER_SHEAR_ENTITY")) {
            return plugin.isShearingEnabled(world) && plugin.isShearingDropCollectionEnabled();
        }

        if (eventType.equals("BLOCK_EXPLOSION") || eventType.equals("ENTITY_EXPLOSION")) {
            return plugin.isExplosionsEnabled(world) && plugin.isExplosionDropCollectionEnabled();
        }

        return false;
    }

    private Material parseMaterial(String key) {
        Material cached = materialCache.get(key);
        if (cached != null) {
            return cached;
        }
        Material material = MaterialTypes.match(key);
        if (material == null || !material.isBlock()) {
            return null;
        }
        materialCache.put(key, material);
        return material;
    }

    private EntityType parseEntityType(String key) {
        EntityType cached = entityTypeCache.get(key);
        if (cached != null) {
            return cached;
        }
        EntityType entityType = MobTypes.match(key);
        if (entityType == null) {
            return null;
        }
        entityTypeCache.put(key, entityType);
        return entityType;
    }

    private String normalizeEvent(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Enum<?> enumValue) {
            return enumValue.name();
        }
        return value.toString().trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeKey(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Enum<?> enumValue) {
            return enumValue.name();
        }
        return MaterialTypes.normalizeKey(value.toString());
    }
}
