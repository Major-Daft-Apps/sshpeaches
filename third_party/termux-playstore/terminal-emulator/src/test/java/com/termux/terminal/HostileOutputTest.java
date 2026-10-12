package com.termux.terminal;

import java.nio.charset.StandardCharsets;
import java.util.Random;

/**
 * Output a server can send must never throw (over SSH that dropped the rest of the chunk, over Mosh it crashed the
 * app) or keep the main thread busy for seconds. Inputs here came from a fuzzing sweep.
 */
public class HostileOutputTest extends TerminalTestCase {

    private static final String WIDE_SCREEN = new String(new char[80 * 24 / 2]).replace("\0", "中");

    private void assertHandled(String... chunks) {
        withTerminalSized(80, 24);
        for (String chunk : chunks) enterString(chunk);
        mTerminal.getScreen().getSelectedText(0, -mTerminal.getScreen().getActiveTranscriptRows(), 79, 23);
    }

    private static long millis(Runnable runnable) {
        long start = System.nanoTime();
        runnable.run();
        return (System.nanoTime() - start) / 1_000_000;
    }

    public void testOscColorResetSkipsBadParameters() {
        assertHandled("\033]104;x\007");
        assertHandled("\033]104;999\007");
        assertHandled("\033]104;-1\007");
        assertHandled("\033]104;b287194b\007");
        // Valid numbers next to bad ones are still reset.
        withTerminalSized(80, 24);
        int defaultColor = mTerminal.mColors.mCurrentColors[3];
        enterString("\033]4;3;#ffffff\007");
        assertEquals(0xFFFFFFFF, mTerminal.mColors.mCurrentColors[3]);
        enterString("\033]104;x;3;999\007");
        assertEquals(defaultColor, mTerminal.mColors.mCurrentColors[3]);
    }

    public void testRectangularAttributesStayOnScreenInOriginMode() {
        assertHandled("\033[5;24r\033[?6h\033[1;1;24;80;1$r");
        assertHandled("\033[5;24r\033[?6h\033[1;1;24;80;1$t");
        assertHandled("\033[?69h\033[10;80s\033[?6h\033[1;1;24;80;1$r");
    }

    public void testWideCharacterRightOfRightMarginIsDropped() {
        assertHandled("\033[?69h\033[1;10s\033[1;80H中");
        assertHandled("\033[?69h\033[1;10s\033[1;79H\t中");
    }

    public void testHugeScrollAndRepeatCountsStayFast() {
        StringBuilder scrolls = new StringBuilder(WIDE_SCREEN + "\033[?69h\033[2;79s");
        for (int i = 0; i < 290; i++) scrolls.append("\033[9999S");
        long scrollMs = millis(() -> assertHandled(scrolls.toString()));
        assertTrue("CSI 9999 S with margins took " + scrollMs + "ms", scrollMs < 2000);

        StringBuilder repeats = new StringBuilder("中");
        for (int i = 0; i < 290; i++) repeats.append("\033[9999b");
        long repeatMs = millis(() -> assertHandled(repeats.toString()));
        assertTrue("CSI 9999 b took " + repeatMs + "ms", repeatMs < 5000);
    }

    public void testScrollUpWithMarginsMovesRegion() {
        withTerminalSized(5, 3);
        enterString("abcde\r\nfghij\r\nklmno\033[?69h\033[2;4s\033[1S");
        assertLinesAre("aghie", "flmnj", "k   o");
        enterString("\033[9999S");
        assertLinesAre("a   e", "f   j", "k   o");
    }

    public void testWordSelectionOnHugeWrappedLineIsFast() {
        mTerminal = new TerminalEmulator(mOutput, 120, 40, INITIAL_CELL_WIDTH_PIXELS, INITIAL_CELL_HEIGHT_PIXELS, 5000, null);
        byte[] chunk = new String(new char[120 * 50]).replace("\0", "a").getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < 101; i++) mTerminal.append(chunk, chunk.length);
        TerminalBuffer screen = mTerminal.getScreen();
        long[] bounds = new long[4];
        long ms = millis(() -> {
            int[] b = screen.getWordBoundsAtLocation(5, 0);
            for (int i = 0; i < 4; i++) bounds[i] = b[i];
        });
        assertTrue("word bounds took " + ms + "ms", ms < 2000);
        assertEquals(-screen.getActiveTranscriptRows(), bounds[1]);
    }

    public void testWordBoundsStopAtUnwrappedLines() {
        withTerminalSized(5, 4);
        enterString("ab\r\ncdefghij\r\nk");
        assertLinesAre("ab   ", "cdefg", "hij  ", "k    ");
        int[] bounds = mTerminal.getScreen().getWordBoundsAtLocation(1, 2);
        assertEquals(0, bounds[0]);
        assertEquals(1, bounds[1]);
        assertEquals(2, bounds[2]);
        assertEquals(2, bounds[3]);
    }

    private static final String[] FINALS = {"@", "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "P", "S", "T",
        "X", "Z", "`", "a", "b", "c", "d", "e", "f", "g", "h", "l", "m", "n", "q", "r", "s", "t", "u", "x", " q", "\"q", "!p",
        "$r", "$t", "$v", "$x", "$z", "${", "$p", "'}", "'~", "*x"};

    /** Seeded random escape sequences at sizes the app uses (it never goes below 2x2). */
    public void testRandomSequencesDoNotThrow() {
        for (int seed = 0; seed < 3000; seed++) {
            Random r = new Random(seed);
            withTerminalSized(new int[]{2, 3, 10, 80, 133}[r.nextInt(5)], new int[]{2, 3, 5, 24, 50}[r.nextInt(5)]);
            StringBuilder input = new StringBuilder();
            try {
                for (int step = 5 + r.nextInt(60); step > 0; step--) {
                    if (r.nextInt(25) == 0) {
                        mTerminal.resize(2 + r.nextInt(140), 2 + r.nextInt(60), INITIAL_CELL_WIDTH_PIXELS, INITIAL_CELL_HEIGHT_PIXELS);
                        continue;
                    }
                    String sequence = randomSequence(r);
                    input.append(sequence);
                    byte[] bytes = sequence.chars().anyMatch(c -> c > 255)
                        ? sequence.getBytes(StandardCharsets.UTF_8) : sequence.getBytes(StandardCharsets.ISO_8859_1);
                    mTerminal.append(bytes, bytes.length);
                }
                TerminalBuffer screen = mTerminal.getScreen();
                screen.getSelectedText(0, -screen.getActiveTranscriptRows(), mTerminal.mColumns - 1, mTerminal.mRows - 1);
            } catch (RuntimeException e) {
                throw new AssertionError("seed " + seed + " input " + input, e);
            }
        }
    }

    private static String randomSequence(Random r) {
        switch (r.nextInt(12)) {
            case 0: {
                StringBuilder b = new StringBuilder();
                for (int i = 1 + r.nextInt(20); i > 0; i--) b.append((char) r.nextInt(256));
                return b.toString();
            }
            case 1: return "\033]" + r.nextInt(120) + ";" + randomText(r) + (r.nextBoolean() ? "\007" : "\033\\");
            case 2: return "\033P" + randomText(r) + "\033\\";
            case 3: return "\033_" + randomText(r) + "\033\\";
            case 4: return new String(Character.toChars(new int[]{0x4e2d, 0x301, 0x1F600, 0x200B, 0xFE0F, 0x0E33, 'x', 0x10FFFF}[r.nextInt(8)]));
            case 5: return "\033" + "()*+#%78DEHMNZc=><".charAt(r.nextInt(18)) + (char) ('0' + r.nextInt(10));
            case 6: return String.valueOf("\t\r\n\b\u000b\u000c\u000e\u000f".charAt(r.nextInt(8)));
            default: {
                StringBuilder b = new StringBuilder("\033[");
                if (r.nextInt(4) == 0) b.append("?>=<".charAt(r.nextInt(4)));
                int count = r.nextInt(r.nextInt(10) == 0 ? 40 : 6);
                for (int i = 0; i < count; i++) {
                    if (i > 0) b.append(r.nextInt(8) == 0 ? ":" : ";");
                    b.append(new String[]{"0", "9999", "2147483648", "", Integer.toString(r.nextInt(30)), Integer.toString(r.nextInt(300))}[r.nextInt(6)]);
                }
                return b.append(FINALS[r.nextInt(FINALS.length)]).toString();
            }
        }
    }

    private static String randomText(Random r) {
        StringBuilder b = new StringBuilder();
        for (int i = r.nextInt(12); i > 0; i--)
            b.append(r.nextBoolean() ? Integer.toString(r.nextInt(300) - 10) : String.valueOf(";?:x#rgb:ff/00/".charAt(r.nextInt(15))));
        return b.toString();
    }
}
