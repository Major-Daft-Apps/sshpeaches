package com.termux.terminal;

import java.util.Arrays;
import java.util.Random;

import junit.framework.TestCase;

/** copyInterval() must give exactly what the original cell-by-cell setChar() copy gave, only faster. */
public class TerminalRowCopyIntervalTest extends TestCase {

    private static final int[] CODE_POINTS = {'a', 'b', ' ', 'x', 0x4e2d, 0x6587, 0x301, 0x308, 0x1F600, 0x1F44D, 0xFE0F, 0x200B, 0x0E33, 0x10348};

    /** The copy as it was before the cell path: one setChar() per source character. */
    private static void referenceCopyInterval(TerminalRow destination, TerminalRow line, int sourceX1, int sourceX2, int destinationX) {
        destination.mHasNonOneWidthOrSurrogateChars |= line.mHasNonOneWidthOrSurrogateChars;
        final int x1 = line.findStartOfColumn(sourceX1);
        final int x2 = line.findStartOfColumn(sourceX2);
        boolean startingFromSecondHalfOfWideChar = (sourceX1 > 0 && wideStartingAt(line, sourceX1 - 1));
        final char[] sourceChars = (destination == line) ? Arrays.copyOf(line.mText, line.mText.length) : line.mText;
        int latestNonCombiningWidth = 0;
        for (int i = x1; i < x2; i++) {
            char sourceChar = sourceChars[i];
            int codePoint = Character.isHighSurrogate(sourceChar) ? Character.toCodePoint(sourceChar, sourceChars[++i]) : sourceChar;
            if (startingFromSecondHalfOfWideChar) {
                codePoint = ' ';
                startingFromSecondHalfOfWideChar = false;
            }
            int w = WcWidth.width(codePoint);
            if (w > 0) {
                destinationX += latestNonCombiningWidth;
                sourceX1 += latestNonCombiningWidth;
                latestNonCombiningWidth = w;
            }
            destination.setChar(destinationX, codePoint, line.getStyle(sourceX1));
        }
    }

    private static boolean wideStartingAt(TerminalRow row, int column) {
        for (int index = 0, current = 0; index < row.getSpaceUsed(); ) {
            char c = row.mText[index++];
            int codePoint = Character.isHighSurrogate(c) ? Character.toCodePoint(c, row.mText[index++]) : c;
            int width = WcWidth.width(codePoint);
            if (width > 0) {
                if (current == column && width == 2) return true;
                current += width;
                if (current > column) return false;
            }
        }
        return false;
    }

    /** A row built by writing random characters the way the emulator does (never a wide char in the last column). */
    private static TerminalRow randomRow(long seed, int columns) {
        Random random = new Random(seed);
        TerminalRow row = new TerminalRow(columns, 0);
        for (int step = random.nextInt(columns * 3); step > 0; step--) {
            int codePoint = CODE_POINTS[random.nextInt(CODE_POINTS.length)];
            int width = WcWidth.width(codePoint);
            int column = random.nextInt(columns);
            if (width <= 0 && column == 0) continue;
            if (width == 2 && column >= columns - 1) continue;
            row.setChar(column, codePoint, random.nextInt(1000));
        }
        return row;
    }

    private static String describe(TerminalRow row) {
        return new String(row.mText, 0, row.getSpaceUsed()) + " styles=" + Arrays.toString(row.mStyle) + " flag=" + row.mHasNonOneWidthOrSurrogateChars;
    }

    public void testMatchesTheCellByCellCopy() {
        int compared = 0;
        for (int seed = 0; seed < 20_000; seed++) {
            Random random = new Random(seed * 31L + 7);
            int columns = 2 + random.nextInt(14);
            boolean selfCopy = random.nextInt(3) == 0;
            int width = 1 + random.nextInt(columns);
            int sourceX1 = random.nextInt(columns - width + 1);
            int destinationX = random.nextInt(columns - width + 1);

            TerminalRow expectedDestination = randomRow(seed, columns);
            TerminalRow actualDestination = randomRow(seed, columns);
            TerminalRow expectedSource = selfCopy ? expectedDestination : randomRow(seed + 1_000_000, columns);
            TerminalRow actualSource = selfCopy ? actualDestination : randomRow(seed + 1_000_000, columns);

            RuntimeException expectedError = null;
            try {
                referenceCopyInterval(expectedDestination, expectedSource, sourceX1, sourceX1 + width, destinationX);
            } catch (RuntimeException e) {
                expectedError = e;
            }
            if (expectedError != null) continue; // The old copy left a half-written row; nothing to match.
            actualDestination.copyInterval(actualSource, sourceX1, sourceX1 + width, destinationX);
            String where = "seed " + seed + " columns=" + columns + " self=" + selfCopy + " src=" + sourceX1 + "+" + width + " dst=" + destinationX;
            assertEquals(where, describe(expectedDestination), describe(actualDestination));
            compared++;
        }
        assertTrue("only " + compared + " copies compared", compared > 15_000);
    }

    public void testFillMatchesSetCharForEachColumn() {
        for (int seed = 0; seed < 20_000; seed++) {
            Random random = new Random(seed * 17L + 3);
            int columns = 2 + random.nextInt(14);
            int start = random.nextInt(columns);
            int end = start + 1 + random.nextInt(columns - start);
            int codePoint = random.nextInt(4) == 0 ? CODE_POINTS[random.nextInt(CODE_POINTS.length)] : ' ';
            if (WcWidth.width(codePoint) == 2) continue; // Filling with wide characters is not something the emulator does.
            long style = random.nextInt(1000);
            TerminalRow expected = randomRow(seed, columns);
            TerminalRow actual = randomRow(seed, columns);
            for (int column = start; column < end; column++) expected.setChar(column, codePoint, style);
            actual.fillInterval(start, end, codePoint, style);
            assertEquals("seed " + seed + " columns=" + columns + " fill " + start + ".." + end + " with " + codePoint,
                describe(expected), describe(actual));
        }
    }

    public void testScrollingInsideMarginsOverWideTextIsFast() {
        TerminalEmulator emulator = new TerminalEmulator(new TerminalTestCase.MockTerminalOutput(), 80, 24, 13, 15, 48, null);
        StringBuilder input = new StringBuilder(new String(new char[80 * 24 / 2]).replace("\0", "中"));
        input.append("\033[?69h\033[2;79s\033[24;2H");
        for (int i = 0; i < 2048; i++) input.append('\n');
        byte[] bytes = input.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        long start = System.nanoTime();
        emulator.append(bytes, bytes.length);
        long ms = (System.nanoTime() - start) / 1_000_000;
        assertTrue("2048 line feeds inside margins took " + ms + "ms", ms < 1500);
    }
}
