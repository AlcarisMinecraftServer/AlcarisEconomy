package net.alcaris.plugin.economy.gui;

import net.alcaris.plugin.economy.config.EconomyConfig;

public class BankNumpadHelper {

    private long currentInput = 0;
    private final long maxYen;

    public BankNumpadHelper(long maxYen) {
        this.maxYen = Math.max(0, maxYen);
    }

    public void digit(int n) {
        long next = currentInput * 10 + n;
        currentInput = Math.min(next, maxYen);
    }

    public void doubleZero() {
        long next = currentInput * 100;
        currentInput = Math.min(next, maxYen);
    }

    public void backspace() {
        currentInput = currentInput / 10;
    }

    public void clear() {
        currentInput = 0;
    }

    public void preset(long yen) {
        currentInput = Math.min(currentInput + yen, maxYen);
    }

    public void setAll() {
        currentInput = maxYen;
    }

    public long getInternal() {
        return currentInput * EconomyConfig.MULTIPLIER;
    }
}
