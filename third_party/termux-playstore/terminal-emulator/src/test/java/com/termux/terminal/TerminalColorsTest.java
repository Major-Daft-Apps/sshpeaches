package com.termux.terminal;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TerminalColorsTest {

    @Test
    public void sessionColorScheme_survivesFullAndIndexedResets() {
        TerminalColors colors = new TerminalColors();
        int[] ansiColors = new int[16];
        for (int index = 0; index < ansiColors.length; index++) {
            ansiColors[index] = 0xFF100000 | index;
        }

        colors.setColorScheme(ansiColors, 0xFF112233, 0xFF445566, 0xFF778899);
        colors.mCurrentColors[0] = 0xFFFFFFFF;
        colors.mCurrentColors[TextStyle.COLOR_INDEX_FOREGROUND] = 0xFFFFFFFF;

        colors.reset(0);
        assertEquals(ansiColors[0], colors.mCurrentColors[0]);

        colors.reset();
        assertEquals(ansiColors[15], colors.mCurrentColors[15]);
        assertEquals(0xFF112233, colors.mCurrentColors[TextStyle.COLOR_INDEX_FOREGROUND]);
        assertEquals(0xFF445566, colors.mCurrentColors[TextStyle.COLOR_INDEX_BACKGROUND]);
        assertEquals(0xFF778899, colors.mCurrentColors[TextStyle.COLOR_INDEX_CURSOR]);
    }
}
