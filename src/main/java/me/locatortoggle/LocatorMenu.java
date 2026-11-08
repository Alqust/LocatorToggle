package me.locatortoggle;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** Меню из одного ряда с кнопкой включения/выключения локатора. */
public final class LocatorMenu implements InventoryHolder {

    public static final int TOGGLE_SLOT = 4;
    public static final int CLOSE_SLOT = 8;

    private final Inventory inventory;
    private final LocatorManager manager;

    public LocatorMenu(LocatorManager manager, Player viewer) {
        this.manager = manager;
        this.inventory = Bukkit.createInventory(this, 9,
                Component.text("Локатор игроков", NamedTextColor.DARK_GRAY));
        render(viewer);
    }

    public void render(Player viewer) {
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "), List.of());
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }

        boolean enabled = manager.isEnabled(viewer);
        Component name = enabled
                ? Component.text("Локатор: ВКЛЮЧЁН", NamedTextColor.GREEN, TextDecoration.BOLD)
                : Component.text("Локатор: ВЫКЛЮЧЕН", NamedTextColor.RED, TextDecoration.BOLD);
        List<Component> lore = enabled
                ? List.of(
                        line("Вы видите игроков с включённым локатором,", NamedTextColor.GRAY),
                        line("и они видят вас.", NamedTextColor.GRAY),
                        Component.empty(),
                        line("Нажмите, чтобы выключить", NamedTextColor.YELLOW))
                : List.of(
                        line("Вас никто не видит на локаторе,", NamedTextColor.GRAY),
                        line("и вы не видите других.", NamedTextColor.GRAY),
                        Component.empty(),
                        line("Нажмите, чтобы включить", NamedTextColor.YELLOW));

        inventory.setItem(TOGGLE_SLOT, item(enabled ? Material.LIME_DYE : Material.GRAY_DYE, name, lore));
        inventory.setItem(CLOSE_SLOT, item(Material.BARRIER,
                Component.text("Закрыть", NamedTextColor.RED), List.of()));
    }

    private static Component line(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    private static ItemStack item(Material material, Component name, List<Component> lore) {
        ItemStack stack = new ItemStack(material);
        stack.editMeta(meta -> {
            meta.displayName(name.decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
        });
        return stack;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
