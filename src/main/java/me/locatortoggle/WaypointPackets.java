package me.locatortoggle;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.UUID;

/**
 * Доступ к внутренностям сервера через рефлексию, чтобы плагин собирался только с paper-api.
 * Начиная с 26.x сервер не обфусцирован, поэтому имена классов и методов совпадают с ванильными.
 */
final class WaypointPackets {

    private final MethodHandle getHandle;
    private final MethodHandle connection;
    private final MethodHandle send;
    private final MethodHandle isTransmitting;
    private final MethodHandle waypointIcon;
    private final MethodHandle cloneAndAssignStyle;
    private final MethodHandle newVec3i;
    private final MethodHandle newChunkPos;
    private final MethodHandle addPosition;
    private final MethodHandle updatePosition;
    private final MethodHandle addChunk;
    private final MethodHandle updateChunk;
    private final MethodHandle remove;

    WaypointPackets() throws ReflectiveOperationException {
        ClassLoader cl = Bukkit.getServer().getClass().getClassLoader();
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();

        Class<?> craftPlayer = Class.forName("org.bukkit.craftbukkit.entity.CraftPlayer", false, cl);
        Class<?> serverPlayer = Class.forName("net.minecraft.server.level.ServerPlayer", false, cl);
        Class<?> livingEntity = Class.forName("net.minecraft.world.entity.LivingEntity", false, cl);
        Class<?> listener = Class.forName("net.minecraft.server.network.ServerGamePacketListenerImpl", false, cl);
        Class<?> packet = Class.forName("net.minecraft.network.protocol.Packet", false, cl);
        Class<?> waypointPacket = Class.forName("net.minecraft.network.protocol.game.ClientboundTrackedWaypointPacket", false, cl);
        Class<?> icon = Class.forName("net.minecraft.world.waypoints.Waypoint$Icon", false, cl);
        Class<?> vec3i = Class.forName("net.minecraft.core.Vec3i", false, cl);
        Class<?> chunkPos = Class.forName("net.minecraft.world.level.ChunkPos", false, cl);

        getHandle = lookup.findVirtual(craftPlayer, "getHandle", MethodType.methodType(serverPlayer));
        connection = lookup.findGetter(serverPlayer, "connection", listener);
        send = lookup.findVirtual(listener, "send", MethodType.methodType(void.class, packet));
        isTransmitting = lookup.findVirtual(livingEntity, "isTransmittingWaypoint", MethodType.methodType(boolean.class));
        waypointIcon = lookup.findVirtual(livingEntity, "waypointIcon", MethodType.methodType(icon));
        cloneAndAssignStyle = lookup.findVirtual(icon, "cloneAndAssignStyle", MethodType.methodType(icon, livingEntity));
        newVec3i = lookup.findConstructor(vec3i, MethodType.methodType(void.class, int.class, int.class, int.class));
        newChunkPos = lookup.findConstructor(chunkPos, MethodType.methodType(void.class, int.class, int.class));
        addPosition = lookup.findStatic(waypointPacket, "addWaypointPosition", MethodType.methodType(waypointPacket, UUID.class, icon, vec3i));
        updatePosition = lookup.findStatic(waypointPacket, "updateWaypointPosition", MethodType.methodType(waypointPacket, UUID.class, icon, vec3i));
        addChunk = lookup.findStatic(waypointPacket, "addWaypointChunk", MethodType.methodType(waypointPacket, UUID.class, icon, chunkPos));
        updateChunk = lookup.findStatic(waypointPacket, "updateWaypointChunk", MethodType.methodType(waypointPacket, UUID.class, icon, chunkPos));
        remove = lookup.findStatic(waypointPacket, "removeWaypoint", MethodType.methodType(waypointPacket, UUID.class));
    }

    /** Ванильная проверка «передаёт ли игрок точку»: учитывает присед, невидимость, тыкву/голову и атрибут дальности. */
    boolean isTransmitting(Player player) throws Throwable {
        return (boolean) isTransmitting.invoke(getHandle.invoke(player));
    }

    /** Иконка игрока на локаторе (стиль и цвет), как её строит ванильный сервер. */
    Object icon(Player player) throws Throwable {
        Object handle = getHandle.invoke(player);
        return cloneAndAssignStyle.invoke(waypointIcon.invoke(handle), handle);
    }

    void sendPosition(Player receiver, UUID id, Object icon, int x, int y, int z, boolean add) throws Throwable {
        Object pos = newVec3i.invoke(x, y, z);
        send(receiver, add ? addPosition.invoke(id, icon, pos) : updatePosition.invoke(id, icon, pos));
    }

    void sendChunk(Player receiver, UUID id, Object icon, int chunkX, int chunkZ, boolean add) throws Throwable {
        Object pos = newChunkPos.invoke(chunkX, chunkZ);
        send(receiver, add ? addChunk.invoke(id, icon, pos) : updateChunk.invoke(id, icon, pos));
    }

    void sendRemove(Player receiver, UUID id) throws Throwable {
        send(receiver, remove.invoke(id));
    }

    private void send(Player receiver, Object packet) throws Throwable {
        Object conn = connection.invoke(getHandle.invoke(receiver));
        if (conn != null) {
            send.invoke(conn, packet);
        }
    }
}
