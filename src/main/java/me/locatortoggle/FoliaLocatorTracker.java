package me.locatortoggle;

import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Свой локатор для Folia.
 *
 * В Folia ванильный ServerWaypointManager отключён (trackWaypoint/addPlayer/updatePlayer — пустые),
 * поэтому сервер никому не отправляет точки и у всех видна только полоса опыта.
 * Этот класс делает ту же работу сам, безопасно для многопоточности Folia:
 *  - каждый игрок раз в несколько тиков в СВОЁМ потоке записывает свой снимок (позиция, виден ли он, иконка);
 *  - затем в том же потоке читает неизменяемые снимки остальных и отправляет себе пакеты локатора.
 * Чужие сущности напрямую из другого потока не трогаются.
 */
final class FoliaLocatorTracker implements Listener {

    private static final long PERIOD_TICKS = 2L;

    /** Неизменяемый снимок игрока. Пишет только поток самого игрока, читают все. */
    private record Snapshot(UUID id, UUID world, double x, double y, double z,
                            boolean transmitting, double transmitRange, Object icon) { }

    /** Что уже отправлено получателю про конкретного игрока. */
    private record Sent(boolean exact, int a, int b, int c) { }

    private final Plugin plugin;
    private final LocatorManager manager;
    private final WaypointPackets packets;
    private final Map<UUID, Snapshot> snapshots = new ConcurrentHashMap<>();
    /** Для каждого получателя — его отправленные точки. Меняется только в потоке получателя. */
    private final Map<UUID, Map<UUID, Sent>> sentByReceiver = new ConcurrentHashMap<>();
    private volatile boolean errorLogged;

    FoliaLocatorTracker(Plugin plugin, LocatorManager manager, WaypointPackets packets) {
        this.plugin = plugin;
        this.manager = manager;
        this.packets = packets;
    }

    void start(Player player) {
        UUID id = player.getUniqueId();
        sentByReceiver.put(id, new HashMap<>());
        player.getScheduler().runAtFixedRate(plugin, task -> tick(player), () -> forget(id), 1L, PERIOD_TICKS);
    }

    private void forget(UUID id) {
        snapshots.remove(id);
        sentByReceiver.remove(id);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        start(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        forget(event.getPlayer().getUniqueId());
    }

    // После смены мира или возрождения клиент мог очистить свои точки — отправим всё заново.
    @EventHandler(priority = EventPriority.MONITOR)
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        resend(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        resend(event.getPlayer());
    }

    private void resend(Player player) {
        Map<UUID, Sent> sent = sentByReceiver.get(player.getUniqueId());
        if (sent != null) {
            for (UUID other : sent.keySet()) {
                safe(() -> packets.sendRemove(player, other));
            }
            sent.clear();
        }
    }

    @SuppressWarnings("removal")
    private void tick(Player me) {
        if (!me.isOnline()) {
            return;
        }
        UUID myId = me.getUniqueId();
        Map<UUID, Sent> sent = sentByReceiver.get(myId);
        if (sent == null) {
            return;
        }

        Location loc = me.getLocation();
        Boolean rule = me.getWorld().getGameRuleValue(GameRule.LOCATOR_BAR);
        boolean locatorOn = manager.isEnabled(me) && !Boolean.FALSE.equals(rule);
        // Наблюдатель (spectator) не виден на локаторе и сам его не видит.
        // Когда он выходит из наблюдателя, точки вернутся на следующем тике (если локатор включён).
        boolean spectator = me.getGameMode() == GameMode.SPECTATOR;

        // 1. Обновляем свой снимок (в своём потоке).
        boolean transmitting = false;
        Object icon = null;
        try {
            transmitting = locatorOn && !spectator && packets.isTransmitting(me);
            icon = transmitting ? packets.icon(me) : null;
        } catch (Throwable t) {
            logOnce(t);
        }
        snapshots.put(myId, new Snapshot(myId, me.getWorld().getUID(), loc.getX(), loc.getY(), loc.getZ(),
                transmitting, attribute(me, Attribute.WAYPOINT_TRANSMIT_RANGE), icon));

        // 2. Решаем, кого показать себе.
        Set<UUID> visible = new HashSet<>();
        if (locatorOn && !spectator) {
            double receiveRange = attribute(me, Attribute.WAYPOINT_RECEIVE_RANGE);
            double exactRange = Math.max(2, me.getViewDistance()) * 16.0;
            UUID myWorld = me.getWorld().getUID();

            for (Snapshot other : snapshots.values()) {
                if (other.id().equals(myId) || !other.transmitting() || other.icon() == null
                        || !other.world().equals(myWorld)) {
                    continue;
                }
                double dx = other.x() - loc.getX(), dy = other.y() - loc.getY(), dz = other.z() - loc.getZ();
                double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (dist > other.transmitRange() || dist > receiveRange) {
                    continue;
                }
                visible.add(other.id());

                // Рядом — точная позиция (со стрелками вверх/вниз), далеко — только чанк, как в ванили.
                boolean exact = dist <= exactRange;
                Sent now = exact
                        ? new Sent(true, floor(other.x()), floor(other.y()), floor(other.z()))
                        : new Sent(false, floor(other.x()) >> 4, 0, floor(other.z()) >> 4);
                Sent before = sent.get(other.id());
                if (now.equals(before)) {
                    continue;
                }
                boolean add = before == null || before.exact() != exact;
                if (before != null && add) {
                    safe(() -> packets.sendRemove(me, other.id()));
                }
                boolean addFinal = add;
                safe(() -> {
                    if (exact) {
                        packets.sendPosition(me, other.id(), other.icon(), now.a(), now.b(), now.c(), addFinal);
                    } else {
                        packets.sendChunk(me, other.id(), other.icon(), now.a(), now.c(), addFinal);
                    }
                });
                sent.put(other.id(), now);
            }
        }

        // 3. Убираем тех, кого больше показывать не нужно.
        for (Iterator<UUID> it = sent.keySet().iterator(); it.hasNext(); ) {
            UUID other = it.next();
            if (!visible.contains(other)) {
                safe(() -> packets.sendRemove(me, other));
                it.remove();
            }
        }
    }

    private static int floor(double v) {
        return (int) Math.floor(v);
    }

    private static double attribute(Player player, Attribute attribute) {
        AttributeInstance instance = player.getAttribute(attribute);
        return instance == null ? 0 : instance.getValue();
    }

    private interface PacketAction {
        void run() throws Throwable;
    }

    private void safe(PacketAction action) {
        try {
            action.run();
        } catch (Throwable t) {
            logOnce(t);
        }
    }

    private void logOnce(Throwable t) {
        if (!errorLogged) {
            errorLogged = true;
            plugin.getLogger().log(Level.SEVERE, "Ошибка при отправке точек локатора", t);
        }
    }
}
