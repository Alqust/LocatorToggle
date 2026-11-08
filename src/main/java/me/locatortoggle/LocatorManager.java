package me.locatortoggle;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Хранит выбор игрока и применяет его через ванильные атрибуты локатора.
 *
 * Как это работает:
 *  - waypoint_transmit_range — на каком расстоянии игрока видят другие на своём локаторе;
 *  - waypoint_receive_range  — на каком расстоянии игрок сам видит других.
 *
 * Если у игрока локатор выключен, оба атрибута обнуляются модификатором ×0:
 *  - его никто не видит (transmit = 0), даже если у других локатор включён;
 *  - он сам никого не видит, и вместо локатора у него обычная полоса опыта (receive = 0).
 * Если включён — модификатор снимается, и работают ванильные значения.
 * В итоге двое игроков видят друг друга только тогда, когда локатор включён у обоих.
 *
 * Все методы, которые трогают игрока, должны вызываться в потоке этого игрока (Folia).
 */
public final class LocatorManager {

    private final NamespacedKey stateKey;
    private final NamespacedKey modifierKey;
    private final boolean defaultEnabled;

    public LocatorManager(Plugin plugin, boolean defaultEnabled) {
        this.stateKey = new NamespacedKey(plugin, "enabled");
        this.modifierKey = new NamespacedKey(plugin, "locator_off");
        this.defaultEnabled = defaultEnabled;
    }

    /** Включён ли локатор у игрока. Значение хранится в данных игрока (PDC) и переживает перезаходы и рестарты. */
    public boolean isEnabled(Player player) {
        Boolean stored = player.getPersistentDataContainer().get(stateKey, PersistentDataType.BOOLEAN);
        return stored == null ? defaultEnabled : stored;
    }

    public void setEnabled(Player player, boolean enabled) {
        player.getPersistentDataContainer().set(stateKey, PersistentDataType.BOOLEAN, enabled);
        apply(player);
    }

    public boolean toggle(Player player) {
        boolean newState = !isEnabled(player);
        setEnabled(player, newState);
        return newState;
    }

    /** Приводит атрибуты игрока в соответствие с его выбором. Можно вызывать сколько угодно раз. */
    public void apply(Player player) {
        boolean hide = !isEnabled(player);
        applyTo(player.getAttribute(Attribute.WAYPOINT_TRANSMIT_RANGE), hide);
        applyTo(player.getAttribute(Attribute.WAYPOINT_RECEIVE_RANGE), hide);
    }

    /** Снимает модификаторы плагина (при выключении плагина). */
    public void clear(Player player) {
        applyTo(player.getAttribute(Attribute.WAYPOINT_TRANSMIT_RANGE), false);
        applyTo(player.getAttribute(Attribute.WAYPOINT_RECEIVE_RANGE), false);
    }

    private void applyTo(AttributeInstance instance, boolean hide) {
        if (instance == null) {
            return;
        }
        instance.removeModifier(modifierKey);
        if (hide) {
            // MULTIPLY_SCALAR_1 с -1: итог = значение × (1 + (-1)) = 0
            instance.addTransientModifier(new AttributeModifier(
                    modifierKey, -1.0, AttributeModifier.Operation.MULTIPLY_SCALAR_1, EquipmentSlotGroup.ANY));
        }
    }
}
