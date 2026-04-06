package com.termux.terminal;

import android.view.KeyEvent;

import java.nio.charset.StandardCharsets;

public class VtCompatibilityTest extends TerminalTestCase {

	private void enterChunks(String... chunks) {
		for (String chunk : chunks) {
			byte[] bytes = chunk.getBytes(StandardCharsets.UTF_8);
			mTerminal.append(bytes, bytes.length);
			assertInvariants();
		}
	}

	private static String repeat(char ch, int count) {
		StringBuilder builder = new StringBuilder(count);
		for (int i = 0; i < count; i++) builder.append(ch);
		return builder.toString();
	}

	private static String cup(int row, int col) {
		return "\033[" + row + ";" + col + "H";
	}

	private String collectMarginCharacters(int startRow, int endRow, int column) {
		StringBuilder builder = new StringBuilder();
		for (int row = startRow; row <= endRow; row++) {
			TerminalRow line = mTerminal.getScreen().allocateFullLineIfNecessary(
				mTerminal.getScreen().externalToInternalRow(row)
			);
			char ch = line.mText[column];
			if (ch != ' ') builder.append(ch);
		}
		return builder.toString();
	}

	private String dumpScreen() {
		StringBuilder builder = new StringBuilder();
		for (int row = 0; row < mTerminal.mRows; row++) {
			TerminalRow line = mTerminal.getScreen().allocateFullLineIfNecessary(
				mTerminal.getScreen().externalToInternalRow(row)
			);
			builder.append(row).append(':').append(new String(line.mText, 0, line.getSpaceUsed())).append('\n');
		}
		return builder.toString();
	}

	private void assertLineStartsWith(int row, String expectedPrefix) {
		TerminalRow line = mTerminal.getScreen().allocateFullLineIfNecessary(
			mTerminal.getScreen().externalToInternalRow(row)
		);
		String actual = new String(line.mText, 0, line.getSpaceUsed());
		assertTrue("row " + row + " expected prefix '" + expectedPrefix + "' but was '" + actual + "'",
			actual.startsWith(expectedPrefix));
	}

	public void testApplicationCursorModeUsesSs3ForArrowsAndHomeEnd() {
		assertEquals("\033OC", KeyHandler.getCode(KeyEvent.KEYCODE_DPAD_RIGHT, 0, true, false));
		assertEquals("\033OD", KeyHandler.getCode(KeyEvent.KEYCODE_DPAD_LEFT, 0, true, false));
		assertEquals("\033OA", KeyHandler.getCode(KeyEvent.KEYCODE_DPAD_UP, 0, true, false));
		assertEquals("\033OB", KeyHandler.getCode(KeyEvent.KEYCODE_DPAD_DOWN, 0, true, false));
		assertEquals("\033OH", KeyHandler.getCode(KeyEvent.KEYCODE_MOVE_HOME, 0, true, false));
		assertEquals("\033OF", KeyHandler.getCode(KeyEvent.KEYCODE_MOVE_END, 0, true, false));
	}

	public void testApplicationCursorModeStillDiffersFromFunctionKeys() {
		String appRight = KeyHandler.getCode(KeyEvent.KEYCODE_DPAD_RIGHT, 0, true, false);
		String appLeft = KeyHandler.getCode(KeyEvent.KEYCODE_DPAD_LEFT, 0, true, false);
		String f1 = KeyHandler.getCode(KeyEvent.KEYCODE_F1, 0, false, false);
		String f2 = KeyHandler.getCode(KeyEvent.KEYCODE_F2, 0, false, false);
		String f3 = KeyHandler.getCode(KeyEvent.KEYCODE_F3, 0, false, false);
		String f4 = KeyHandler.getCode(KeyEvent.KEYCODE_F4, 0, false, false);

		assertFalse(appRight.equals(f1));
		assertFalse(appRight.equals(f2));
		assertFalse(appRight.equals(f3));
		assertFalse(appRight.equals(f4));
		assertFalse(appLeft.equals(f1));
		assertFalse(appLeft.equals(f2));
		assertFalse(appLeft.equals(f3));
		assertFalse(appLeft.equals(f4));
	}

	public void testTermcapApplicationCursorModeUsesSs3ForArrowsAndHomeEnd() {
		assertEquals("\033OC", KeyHandler.getCodeFromTermcap("kr", true, false));
		assertEquals("\033OD", KeyHandler.getCodeFromTermcap("kl", true, false));
		assertEquals("\033OA", KeyHandler.getCodeFromTermcap("ku", true, false));
		assertEquals("\033OB", KeyHandler.getCodeFromTermcap("kd", true, false));
		assertEquals("\033OH", KeyHandler.getCodeFromTermcap("kh", true, false));
		assertEquals("\033OF", KeyHandler.getCodeFromTermcap("@7", true, false));
	}

	public void testDecSet1EnablesAndDisablesApplicationCursorMode() {
		withTerminalSized(5, 5);
		assertFalse(mTerminal.isCursorKeysApplicationMode());

		enterString("\033[?1h");
		assertTrue(mTerminal.isCursorKeysApplicationMode());

		enterString("\033[?1l");
		assertFalse(mTerminal.isCursorKeysApplicationMode());

		enterString("\033[?1h");
		assertTrue(mTerminal.isCursorKeysApplicationMode());
		mTerminal.reset();
		assertFalse(mTerminal.isCursorKeysApplicationMode());
	}

	public void testFragmentedDecSet1Sequence() {
		withTerminalSized(5, 5);
		enterChunks("\033", "[?", "1", "h");
		assertTrue(mTerminal.isCursorKeysApplicationMode());

		enterChunks("\033", "[?", "1", "l");
		assertFalse(mTerminal.isCursorKeysApplicationMode());
	}

	public void testFragmentedDecSetSaveRestoreForCursorMode() {
		withTerminalSized(5, 5);

		enterChunks("\033", "[?", "1", "h");
		assertTrue(mTerminal.isCursorKeysApplicationMode());

		enterChunks("\033", "[?", "1", "s");
		enterChunks("\033", "[?", "1", "l");
		assertFalse(mTerminal.isCursorKeysApplicationMode());

		enterChunks("\033", "[?", "1", "r");
		assertTrue(mTerminal.isCursorKeysApplicationMode());
	}

	public void testFragmentedDeviceStatusReport() {
		withTerminalSized(5, 5);
		enterChunks("\033", "[", "5", "n");
		assertEquals("\033[0n", mOutput.getOutputAndClear());

		enterChunks("\033", "[", "6", "n");
		assertEquals("\033[1;1R", mOutput.getOutputAndClear());
	}

	public void testFragmentedExtendedCursorStatusReport() {
		withTerminalSized(5, 5);
		enterString("AB\r\nC");
		assertCursorAt(1, 1);

		enterChunks("\033", "[?", "6", "n");
		assertEquals("\033[?2;2;1R", mOutput.getOutputAndClear());
	}

	public void testFragmentedScrollRegionWithOriginMode() {
		withTerminalSized(3, 4).enterString("111222333444");

		enterChunks("\033", "[?", "6", "h", "\033", "[", "2", "r");
		assertCursorAt(1, 0);

		enterChunks("\r", "\n", "\r", "\n", "\r", "\n", "ABC");
		assertLinesAre("111", "333", "444", "ABC");
	}

	public void testFragmentedCursorPositioning() {
		withTerminalSized(4, 4);
		enterChunks("\033", "[", "3", ";", "2", "H", "X");
		assertLinesAre("    ", "    ", " X  ", "    ");
		assertCursorAt(2, 2);
	}

	public void testDecanmEntersAndExitsVt52Mode() {
		withTerminalSized(5, 5);
		assertFalse(mTerminal.isVt52Mode());

		enterString("\033[?2l");
		assertTrue(mTerminal.isVt52Mode());

		enterString("\033<");
		assertFalse(mTerminal.isVt52Mode());

		enterString("\033[?2l");
		assertTrue(mTerminal.isVt52Mode());
	}

	public void testVt52DirectCursorAddressing() {
		withTerminalSized(4, 4);
		enterString("\033[?2l");
		enterString("\033Y#$X");
		assertLinesAre("    ", "    ", "    ", "   X");
		assertCursorAt(3, 3);
	}

	public void testFragmentedVt52DirectCursorAddressing() {
		withTerminalSized(4, 4);
		enterString("\033[?2l");
		enterChunks("\033", "Y", "\"", "!");
		enterString("Z");
		assertLinesAre("    ", "    ", " Z  ", "    ");
		assertCursorAt(2, 2);
	}

	public void testVt52CursorMovementAndHome() {
		withTerminalSized(4, 4);
		enterString("\033[?2lAB");
		enterString("\033B\033CX");
		assertLinesAre("AB  ", "   X", "    ", "    ");
		assertCursorAt(1, 3);

		enterString("\033D\033AY");
		assertLinesAre("ABY ", "   X", "    ", "    ");
		assertCursorAt(0, 3);

		enterString("\033HZ");
		assertLinesAre("ZBY ", "   X", "    ", "    ");
		assertCursorAt(0, 1);
	}

	public void testVt52EraseAndReverseLineFeed() {
		withTerminalSized(4, 4);
		enterString("1111222233334444");
		enterString("\033[?2l");
		enterString("\033Y\"\"");
		enterString("\033K");
		assertLinesAre("1111", "2222", "33  ", "4444");

		enterString("\033H\033I");
		assertLinesAre("    ", "1111", "2222", "33  ");
		assertCursorAt(0, 0);

		enterString("\033Y!\"\033J");
		assertLinesAre("    ", "11  ", "    ", "    ");
		assertCursorAt(1, 2);
	}

	public void testVt52IdentifyAndKeypadMode() {
		withTerminalSized(4, 4);
		enterString("\033[?2l");
		assertTrue(mTerminal.isVt52Mode());
		assertFalse(mTerminal.isKeypadApplicationMode());

		enterString("\033=");
		assertTrue(mTerminal.isKeypadApplicationMode());

		assertEnteringStringGivesResponse("\033Z", "\033/Z");

		enterString("\033>");
		assertFalse(mTerminal.isKeypadApplicationMode());
	}

	public void testVttestMovementBorderTopAndBottomRowsMatch() {
		final int width = 80;
		final int height = 24;
		final int innerL = (width - 60) / 2;
		final int innerR = 61 + innerL;

		withTerminalSized(width, height);

		enterString("\033#8");
		enterString("\033[9;" + innerL + "H\033[1J");
		enterString("\033[18;60H\033[0J\033[1K");
		enterString("\033[9;" + innerR + "H\033[0K");
		for (int row = 10; row <= 16; row++) {
			enterString("\033[" + row + ";" + innerL + "H\033[1K");
			enterString("\033[" + row + ";" + innerR + "H\033[0K");
		}
		enterString("\033[17;30H\033[2K");
		for (int col = 1; col <= width; col++) {
			enterString("\033[" + height + ";" + col + "f*");
			enterString("\033[1;" + col + "f*");
		}
		enterString("\033[2;2H");
		for (int row = 2; row <= height - 1; row++) {
			enterString("+\033[1D\033D");
		}
		enterString("\033[" + (height - 1) + ";" + (width - 1) + "H");
		for (int row = height - 1; row >= 2; row--) {
			enterString("+\033[1D\033M");
		}
		enterString("\033[2;1H");
		for (int row = 2; row <= height - 1; row++) {
			enterString("*");
			enterString("\033[" + row + ";" + width + "H*");
			enterString("\033[10D");
			if (row < 10) {
				enterString("\033E");
			} else {
				enterString("\r\n");
			}
		}
		enterString("\033[2;10H\033[42D\033[2C");
		for (int col = 3; col <= width - 2; col++) {
			enterString("+\033[0C\033[2D\033[1C");
		}
		enterString("\033[" + (height - 1) + ";" + (innerR - 1) + "H\033[42C\033[2D");
		for (int col = width - 2; col >= 3; col--) {
			enterString("+\033[1D\033[1C\033[0D\b");
		}

		String expected = "*" + repeat('+', width - 2) + "*";
		assertLineIs(1, expected);
		assertLineIs(height - 2, expected);
	}

	public void testVttestScreenWrapAroundTopRowsMatch() {
		final int width = 80;
		withTerminalSized(width, 24);

		enterString("\033[H");
		enterString("\033[?7h");
		enterString(repeat('*', width * 2));
		enterString("\033[?7l");
		enterString("\033[3;1H");
		enterString(repeat('*', width * 2));

		String expected = repeat('*', width);
		assertLineIs(0, expected);
		assertLineIs(1, expected);
		assertLineIs(2, expected);
	}

	public void testVttestScreenWrapAroundTopRowsMatchAfterDeccolmResetTo80Columns() {
		withTerminalSized(132, 24);

		enterString("\033[?3l");
		assertEquals(80, mTerminal.mColumns);

		enterString("\033[H");
		enterString("\033[?7h");
		enterString(repeat('*', 160));
		enterString("\033[?7l");
		enterString("\033[3;1H");
		enterString(repeat('*', 160));

		String expected = repeat('*', 80);
		assertLineIs(0, expected);
		assertLineIs(1, expected);
		assertLineIs(2, expected);
	}

	public void testVttestMovement132PassKeepsIntroOutsideScrollRegion() {
		final int width = 132;
		final int region = 18;
		final char[] onLeft = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
		final char[] onRight = "abcdefghijklmnopqrstuvwxyz".toCharArray();

		withTerminalSized(width, 24);
		enterString("Test of autowrap, mixing control and print characters.\r\n");
		enterString("The left/right margins should have letters in order:\r\n");
		enterString("\033[3;" + (region + 3) + "r");
		enterString("\033[?6h");

		for (int i = 0; i < onLeft.length; ++i) {
			switch (i % 4) {
				case 0:
					enterString(cup(region + 1, 1) + onLeft[i]);
					enterString(cup(region + 1, width) + onRight[i]);
					enterString("\n");
					break;
				case 1:
					enterString(cup(region, width) + onRight[i - 1] + onLeft[i]);
					enterString(cup(region + 1, width) + onLeft[i] + "\b" + " " + onRight[i]);
					enterString("\n");
					break;
				case 2:
					enterString(cup(region + 1, width) + onLeft[i] + "\b\b\t\t" + onRight[i]);
					enterString(cup(region + 1, 2) + "\b" + onLeft[i] + "\n");
					break;
				default:
					enterString(cup(region + 1, width) + "\n");
					enterString(cup(region, 1) + onLeft[i]);
					enterString(cup(region, width) + onRight[i]);
					break;
			}
		}

		assertLineStartsWith(0, "Test of autowrap, mixing control and print characters.");
		assertLineStartsWith(1, "The left/right margins should have letters in order:");
	}

	// TODO: Re-enable as a regression test once the autowrap/control-character behavior
	// matches vttest exactly. The current logic bug is still under investigation.
	public void reproVttestMovementAutowrapWithControlsKeepsMarginsInOrder() {
		final int width = 80;
		final int height = 24;
		final int region = height - 6;
		final char[] onLeft = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
		final char[] onRight = "abcdefghijklmnopqrstuvwxyz".toCharArray();

		withTerminalSized(width, height);
		enterString("\033[3;" + (region + 3) + "r");
		enterString("\033[?6h");

		for (int i = 0; i < onLeft.length; i++) {
			switch (i % 4) {
				case 0:
					enterString(cup(region + 1, 1) + onLeft[i]);
					enterString(cup(region + 1, width) + onRight[i]);
					enterString("\n");
					break;
				case 1:
					enterString(cup(region, width) + onRight[i - 1] + onLeft[i]);
					enterString(cup(region + 1, width) + onLeft[i] + "\b" + " " + onRight[i]);
					enterString("\n");
					break;
				case 2:
					enterString(cup(region + 1, width) + onLeft[i] + "\b\b\t\t" + onRight[i]);
					enterString(cup(region + 1, 2) + "\b" + onLeft[i] + "\n");
					break;
				default:
					enterString(cup(region + 1, width) + "\n");
					enterString(cup(region, 1) + onLeft[i]);
					enterString(cup(region, width) + onRight[i]);
					break;
			}
		}

		String left = collectMarginCharacters(0, height - 1, 0);
		String right = collectMarginCharacters(0, height - 1, width - 1);
		assertEquals(dumpScreen(), "ABCDEFGHIJKLMNOPQRSTUVWXYZ", left);
		assertEquals(dumpScreen(), "abcdefghijklmnopqrstuvwxyz", right);
	}
}
