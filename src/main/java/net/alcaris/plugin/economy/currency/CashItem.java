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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.entity.Player;

public class CashItem {

    private static NamespacedKey KEY_CASH;
    private static NamespacedKey KEY_AMOUNT;
    private static NamespacedKey KEY_CHECKSUM;

    private CashItem() {}

    public static void initialize(Plugin plugin) {
        KEY_CASH = new NamespacedKey(plugin, "economy_cash");
        KEY_AMOUNT = new NamespacedKey(plugin, "economy_amount");
        KEY_CHECKSUM = new NamespacedKey(plugin, "economy_checksum");
    }

    public static ItemStack create(EconomyConfig.Denomination denom, String serverKey) {
        ItemStack item = new ItemStack(denom.material());
        ItemMeta meta = item.getItemMeta();

        meta.displayName(LegacyComponentSerializer.legacyAmpersand().deserialize(denom.displayName()));

        List<Component> loreComponents = new ArrayList<>();
        for (String line : denom.lore()) {
            loreComponents.add(LegacyComponentSerializer.legacyAmpersand().deserialize(line));
        }
        meta.lore(loreComponents);

        if (denom.customModelData() != 0) {
            meta.setCustomModelData(denom.customModelData());
        }

        if (denom.glint()) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(KEY_CASH, PersistentDataType.BOOLEAN, true);
        pdc.set(KEY_AMOUNT, PersistentDataType.INTEGER, denom.amount());
        pdc.set(KEY_CHECKSUM, PersistentDataType.STRING, generateChecksum(denom.amount(), serverKey));

        item.setItemMeta(meta);
        return item;
    }

    public static List<ItemStack> makeChange(long totalInternal, List<EconomyConfig.Denomination> denomsDesc, String serverKey) {
        List<ItemStack> result = new ArrayList<>();
        long remaining = totalInternal;

        for (EconomyConfig.Denomination denom : denomsDesc) {
            long denomInternal = (long) denom.amount() * EconomyConfig.MULTIPLIER;
            long count = remaining / denomInternal;
            remaining %= denomInternal;

            while (count > 0) {
                int stackSize = (int) Math.min(count, 64);
                ItemStack stack = create(denom, serverKey);
                stack.setAmount(stackSize);
                result.add(stack);
                count -= stackSize;
            }
        }
        return result;
    }

    public static boolean isValid(ItemStack item, String serverKey, Set<Integer> validAmounts) {
        if (item == null || item.getType() == Material.AIR) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        if (!pdc.has(KEY_CASH, PersistentDataType.BOOLEAN)) return false;

        Integer amount = pdc.get(KEY_AMOUNT, PersistentDataType.INTEGER);
        if (amount == null || !validAmounts.contains(amount)) return false;

        String checksum = pdc.get(KEY_CHECKSUM, PersistentDataType.STRING);
        if (checksum == null) return false;

        return checksum.equals(generateChecksum(amount, serverKey));
    }

    public static boolean hasCashMarker(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(KEY_CASH, PersistentDataType.BOOLEAN);
    }

    public static int getAmount(ItemStack item) {
        if (item == null) return 0;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return 0;
        Integer amount = meta.getPersistentDataContainer().get(KEY_AMOUNT, PersistentDataType.INTEGER);
        return amount != null ? amount : 0;
    }

    public static Set<Integer> buildValidAmounts(List<EconomyConfig.Denomination> denoms) {
        Set<Integer> amounts = new HashSet<>();
        for (EconomyConfig.Denomination d : denoms) {
            amounts.add(d.amount());
        }
        return amounts;
    }

    public static long countInventoryCash(Player player, EconomyConfig config) {
        long total = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null) continue;
            ItemMeta meta = item.getItemMeta();
            if (meta == null) continue;
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            if (pdc.getOrDefault(KEY_CASH, PersistentDataType.BOOLEAN, false)) {
                Integer amt = pdc.get(KEY_AMOUNT, PersistentDataType.INTEGER);
                if (amt != null) total += (long) amt * item.getAmount() * EconomyConfig.MULTIPLIER;
            }
        }
        return total;
    }

    public static String generateChecksum(int amount, String serverKey) {
        String input = "economy_cash:amount=" + amount + ":key=" + serverKey;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
