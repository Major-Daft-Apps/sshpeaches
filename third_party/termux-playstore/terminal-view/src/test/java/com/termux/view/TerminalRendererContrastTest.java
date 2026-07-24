package com.termux.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TerminalRendererContrastTest {

    @Test
    public void blackForegroundOnBlackBackground_isMadeReadable() {
        int adjusted = TerminalRenderer.ensureReadableForeground(0xFF000000, 0xFF000000);

        assertNotEquals(0xFF000000, adjusted);
        assertTrue(relativeLuminance(adjusted) >= 0.175);
    }

    @Test
    public void whiteForegroundOnWhiteBackground_isMadeReadable() {
        int adjusted = TerminalRenderer.ensureReadableForeground(0xFFFFFFFF, 0xFFFFFFFF);

        assertNotEquals(0xFFFFFFFF, adjusted);
        assertTrue(relativeLuminance(adjusted) <= 0.1835);
    }

    @Test
    public void readableColor_isNotChanged() {
        int foreground = 0xFF66BB6A;

        assertEquals(foreground, TerminalRenderer.ensureReadableForeground(foreground, 0xFF000000));
    }

    private static double relativeLuminance(int color) {
        return 0.2126 * linear(color >> 16 & 0xFF)
            + 0.7152 * linear(color >> 8 & 0xFF)
            + 0.0722 * linear(color & 0xFF);
    }

    private static double linear(int channel) {
        double value = channel / 255.0;
        return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }
}
