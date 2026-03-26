package net.alcaris.plugin.economy.loan;

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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class LoanNoteItem {

    private static NamespacedKey KEY_LOAN_ID;
    private static int customModelData = 3001;

    private LoanNoteItem() {}

    public static void initialize(Plugin plugin) {
        KEY_LOAN_ID = new NamespacedKey(plugin, "loan_id");
    }

    public static void setCustomModelData(int cmd) {
        customModelData = cmd;
    }

    @SuppressWarnings("UnstableApiUsage")
    public static ItemStack create(long loanId, String borrowerName, long principal,
                                   long repayAmount, long dueAt, boolean hasCollateral, long remaining) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(LegacyComponentSerializer.legacyAmpersand().deserialize("&c&l借用証"));

        String dateStr = new SimpleDateFormat("yyyy-MM-dd").format(new Date(dueAt));
        List<Component> lore = new ArrayList<>();
        lore.add(LegacyComponentSerializer.legacyAmpersand().deserialize("&7債務者: " + borrowerName));
        lore.add(LegacyComponentSerializer.legacyAmpersand().deserialize("&e貸付額: " + EconomyConfig.formatStatic(principal)));
        lore.add(LegacyComponentSerializer.legacyAmpersand().deserialize("&e返済額: " + EconomyConfig.formatStatic(repayAmount)));
        lore.add(LegacyComponentSerializer.legacyAmpersand().deserialize("&7期限: " + dateStr));
        lore.add(LegacyComponentSerializer.legacyAmpersand().deserialize("&7担保: " + (hasCollateral ? "あり" : "なし")));
        lore.add(LegacyComponentSerializer.legacyAmpersand().deserialize("&7残債: " + EconomyConfig.formatStatic(remaining)));
        lore.add(LegacyComponentSerializer.legacyAmpersand().deserialize("&8[ 右クリックで返済回収 ]"));
        meta.lore(lore);

        if (customModelData != 0) {
            var cmd = meta.getCustomModelDataComponent();
            cmd.setFloats(List.of((float) customModelData));
            meta.setCustomModelDataComponent(cmd);
        }
        meta.addEnchant(Enchantment.FORTUNE, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

        meta.getPersistentDataContainer().set(KEY_LOAN_ID, PersistentDataType.LONG, loanId);
        item.setItemMeta(meta);
        return item;
    }

    public static boolean hasLoanMarker(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(KEY_LOAN_ID, PersistentDataType.LONG);
    }

    public static long getLoanId(ItemStack item) {
        if (item == null) return -1L;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return -1L;
        Long id = meta.getPersistentDataContainer().get(KEY_LOAN_ID, PersistentDataType.LONG);
        return id != null ? id : -1L;
    }
}
