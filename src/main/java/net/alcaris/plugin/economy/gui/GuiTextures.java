package net.alcaris.plugin.economy.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public class GuiTextures {

    public static final String BANK_GUI_TEXT         = "\uF80A\uF822\uF809\uF802\uE260\uF80C\uF80B\uF808\uF80B\uF824\uF82A\uF829\uF822";
    public static final String BANK_DEPOSIT_GUI_TEXT = "\uF80A\uF822\uF809\uF802\uE261\uF80C\uF80B\uF808\uF80B\uF824\uF82A\uF829\uF822";

    public static Component createBankTitle(String titleText) {
        return createTitle(BANK_GUI_TEXT, titleText);
    }

    public static Component createDepositTitle(String titleText) {
        return createTitle(BANK_DEPOSIT_GUI_TEXT, titleText);
    }

    private static Component createTitle(String guiText, String titleText) {
        TextComponent prefixPart = Component.text(guiText).color(NamedTextColor.WHITE);
        TextComponent titlePart = Component.text(titleText)
            .color(NamedTextColor.DARK_GRAY)
            .decoration(TextDecoration.BOLD, true);

        return Component.empty()
            .append(prefixPart)
            .append(titlePart);
    }
}