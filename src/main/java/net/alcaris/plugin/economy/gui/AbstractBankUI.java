package net.alcaris.plugin.economy.gui;

import net.alcaris.plugin.economy.AlcarisEconomy;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public abstract class AbstractBankUI implements InventoryHolder {

    protected final AlcarisEconomy plugin;
    protected final Inventory inventory;
    protected final AbstractBankUI previousUI;
    protected final Map<Integer, Runnable> actions = new HashMap<>();

    protected AbstractBankUI(AlcarisEconomy plugin, String title, int rows, AbstractBankUI previousUI) {
        this.plugin = plugin;
        this.inventory = Bukkit.createInventory(this, rows * 9, createTitle(title));
        this.previousUI = previousUI;
    }

    /**
     * Template method for creating GUI titles. Subclasses can override to provide custom styling.
     * Default implementation maintains backward compatibility with simple text titles.
     *
     * @param baseTitle The base title text
     * @return Component representing the GUI title
     */
    protected Component createTitle(String baseTitle) {
        return Component.text(baseTitle);  // Default implementation - backward compatibility
    }

    protected void setButton(int slot, ItemStack icon, Runnable action) {
        inventory.setItem(slot, icon);
        if (action != null) actions.put(slot, action);
        else actions.remove(slot);
    }

    protected void setButton(int slot, ItemStack icon) {
        setButton(slot, icon, null);
    }

    protected ItemStack makeFiller(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.empty());
        item.setItemMeta(meta);
        return item;
    }

    protected static Component c(String legacy) {
        return Component.empty()
                .decoration(TextDecoration.ITALIC, false)
                .append(LegacyComponentSerializer.legacyAmpersand().deserialize(legacy));
    }

    protected static ItemStack item(Material mat, String name) {
        ItemStack is = new ItemStack(mat);
        ItemMeta m = is.getItemMeta();
        m.displayName(c(name));
        is.setItemMeta(m);
        return is;
    }

    protected static ItemStack item(Material mat, String name, List<String> lore) {
        ItemStack is = item(mat, name);
        ItemMeta m = is.getItemMeta();
        m.lore(lore.stream().map(AbstractBankUI::c).collect(Collectors.toList()));
        is.setItemMeta(m);
        return is;
    }

    public void open(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(inventory));
    }

    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        Runnable action = actions.get(event.getRawSlot());
        if (action != null) action.run();
    }

    public void handleClose(InventoryCloseEvent event) {
        if (event.getReason() == InventoryCloseEvent.Reason.PLAYER && previousUI != null) {
            previousUI.open((Player) event.getPlayer());
        }
    }

    @Override
    public Inventory getInventory() { return inventory; }
}
