package net.alcaris.plugin.economy.currency;

import net.alcaris.plugin.economy.config.EconomyConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

public class ChequeItem {

    private static NamespacedKey KEY_CHEQUE_ID;
    private static NamespacedKey KEY_CHEQUE_AMOUNT;
    private static int customModelData = 2001;

    private ChequeItem() {}

    public static void initialize(Plugin plugin) {
        KEY_CHEQUE_ID = new NamespacedKey(plugin, "cheque_id");
        KEY_CHEQUE_AMOUNT = new NamespacedKey(plugin, "cheque_amount");
    }

    public static void setCustomModelData(int cmd) {
        customModelData = cmd;
    }

    @SuppressWarnings("UnstableApiUsage")
    public static ItemStack create(long chequeId, long amount, String note, String issuerName) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(LegacyComponentSerializer.legacyAmpersand().deserialize("&b&l小切手"));

        List<Component> lore = new ArrayList<>();
        lore.add(LegacyComponentSerializer.legacyAmpersand().deserialize("&7発行者: " + issuerName));
        lore.add(LegacyComponentSerializer.legacyAmpersand().deserialize("&e額面: " + EconomyConfig.formatStatic(amount)));
        if (note != null && !note.isBlank()) {
            lore.add(LegacyComponentSerializer.legacyAmpersand().deserialize("&7メモ: " + note));
        }
        lore.add(LegacyComponentSerializer.legacyAmpersand().deserialize("&8[ 右クリックで換金 ]"));
        meta.lore(lore);

        if (customModelData != 0) {
            var cmd = meta.getCustomModelDataComponent();
            cmd.setFloats(List.of((float) customModelData));
            meta.setCustomModelDataComponent(cmd);
        }
        meta.addEnchant(Enchantment.FORTUNE, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(KEY_CHEQUE_ID, PersistentDataType.LONG, chequeId);
        pdc.set(KEY_CHEQUE_AMOUNT, PersistentDataType.LONG, amount);

        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack createUsed(long chequeId, long amount) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(LegacyComponentSerializer.legacyAmpersand().deserialize("&8小切手 &7[使用済み]"));
        List<Component> lore = new ArrayList<>();
        lore.add(LegacyComponentSerializer.legacyAmpersand().deserialize("&8この小切手は既に換金されています"));
        meta.lore(lore);
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(KEY_CHEQUE_ID, PersistentDataType.LONG, chequeId);
        pdc.set(KEY_CHEQUE_AMOUNT, PersistentDataType.LONG, amount);
        item.setItemMeta(meta);
        return item;
    }

    public static boolean hasChequeMarker(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(KEY_CHEQUE_ID, PersistentDataType.LONG);
    }

    public static long getChequeId(ItemStack item) {
        if (item == null) return -1L;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return -1L;
        Long id = meta.getPersistentDataContainer().get(KEY_CHEQUE_ID, PersistentDataType.LONG);
        return id != null ? id : -1L;
    }
}
