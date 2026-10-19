package com.termux.terminal;

import java.util.Arrays;

/**
 * A row in a terminal, composed of a fixed number of cells.
 * <p>
 * The text in the row is stored in a char[] array, {@link #mText}, for quick access during rendering.
 */
public final class TerminalRow {

    private static final float SPARE_CAPACITY_FACTOR = 1.5f;

    /** The number of columns in this terminal row. */
    private final int mColumns;
    /** The text filling this terminal row. */
    public char[] mText;
    /** The number of java chars used in {@link #mText}. */
    private short mSpaceUsed;
    /** If this row has been line wrapped due to text output at the end of line. */
    boolean mLineWrap;
    /** If {@link #mLineWrap} was set by a resize reflowing the row, not by output reaching the end of the line. */
    boolean mLineWrapFromResize;
    /** The style bits of each cell in the row. See {@link TextStyle}. */
    final long[] mStyle;
    /** If this row might contain chars with width != 1, used for deactivating fast path */
    boolean mHasNonOneWidthOrSurrogateChars;

    /** Construct a blank row (containing only whitespace, ' ') with a specified style. */
    public TerminalRow(int columns, long style) {
        mColumns = columns;
        mText = new char[(int) (SPARE_CAPACITY_FACTOR * columns)];
        mStyle = new long[columns];
        clear(style);
    }

    /** NOTE: The sourceX2 is exclusive. */
    public void copyInterval(TerminalRow line, int sourceX1, int sourceX2, int destinationX) {
        mHasNonOneWidthOrSurrogateChars |= line.mHasNonOneWidthOrSurrogateChars;
        final int x1 = line.findStartOfColumn(sourceX1);
        final int x2 = line.findStartOfColumn(sourceX2);
        boolean startingFromSecondHalfOfWideChar = (sourceX1 > 0 && line.wideDisplayCharacterStartingAt(sourceX1 - 1));
        final char[] sourceChars = (this == line) ? Arrays.copyOf(line.mText, line.mText.length) : line.mText;
        // With wide or combining characters around, every setChar() rescans the row, which made each copied row
        // O(columns^2): scrolling inside left/right margins over a screen of CJK text took seconds. Work on one cell
        // per column instead and write the row back once; plain rows keep setChar()'s fast path.
        final String[] cells = mHasNonOneWidthOrSurrogateChars ? toCells() : null;
        int latestNonCombiningWidth = 0;
        for (int i = x1; i < x2; i++) {
            char sourceChar = sourceChars[i];
            int codePoint = Character.isHighSurrogate(sourceChar) ? Character.toCodePoint(sourceChar, sourceChars[++i]) : sourceChar;
            if (startingFromSecondHalfOfWideChar) {
                // Just treat copying second half of wide char as copying whitespace.
                codePoint = ' ';
                startingFromSecondHalfOfWideChar = false;
            }
            int w = WcWidth.width(codePoint);
            if (w > 0) {
                destinationX += latestNonCombiningWidth;
                sourceX1 += latestNonCombiningWidth;
                latestNonCombiningWidth = w;
            }
            if (cells != null) {
                setCellChar(cells, destinationX, codePoint, line.getStyle(sourceX1));
            } else {
                setChar(destinationX, codePoint, line.getStyle(sourceX1));
            }
        }
        if (cells != null) fromCells(cells);
    }

    /** setChar() for each column in [startColumn, endColumn), with the same result, in time linear in the row. */
    public void fillInterval(int startColumn, int endColumn, int codePoint, long style) {
        if (!mHasNonOneWidthOrSurrogateChars) {
            for (int column = startColumn; column < endColumn; column++) setChar(column, codePoint, style);
            return;
        }
        String[] cells = toCells();
        for (int column = startColumn; column < endColumn; column++) setCellChar(cells, column, codePoint, style);
        fromCells(cells);
    }

    /** The text of each column; null marks the second half of a wide character. */
    private String[] toCells() {
        String[] cells = new String[mColumns];
        int column = 0;
        int index = 0;
        while (index < mSpaceUsed && column < mColumns) {
            int start = index;
            int baseWidth = 0;
            // A column is its base character plus the zero-width characters after it.
            while (index < mSpaceUsed) {
                int codePoint = Character.codePointAt(mText, index, mSpaceUsed);
                int width = WcWidth.width(codePoint);
                if (width > 0) {
                    if (baseWidth > 0) break;
                    baseWidth = width;
                }
                index += Character.charCount(codePoint);
            }
            cells[column] = new String(mText, start, index - start);
            if (baseWidth == 2 && column + 1 < mColumns) cells[++column] = null;
            column++;
        }
        for (; column < mColumns; column++) cells[column] = " ";
        return cells;
    }

    private void fromCells(String[] cells) {
        int length = 0;
        for (String cell : cells) if (cell != null) length += cell.length();
        char[] text = (length <= mText.length) ? mText : new char[Math.max(length, mText.length + mColumns)];
        int position = 0;
        for (String cell : cells) {
            if (cell == null) continue;
            cell.getChars(0, cell.length(), text, position);
            position += cell.length();
        }
        mText = text;
        mSpaceUsed = (short) length;
    }

    private static int baseWidth(String cell) {
        return WcWidth.width(cell.codePointAt(0));
    }

    private boolean isWideCellStart(String[] cells, int column) {
        return column < mColumns && cells[column] != null && baseWidth(cells[column]) == 2;
    }

    /** setChar() on a cell array, with the same results, in constant time per call. */
    private void setCellChar(String[] cells, int columnToSet, int codePoint, long style) {
        if (columnToSet < 0 || columnToSet >= mStyle.length)
            throw new IllegalArgumentException("TerminalRow.setChar(): columnToSet=" + columnToSet + ", codePoint=" + codePoint + ", style=" + style);

        mStyle[columnToSet] = style;
        final int newCodePointDisplayWidth = WcWidth.width(codePoint);
        if (codePoint >= Character.MIN_SUPPLEMENTARY_CODE_POINT || newCodePointDisplayWidth != 1) {
            mHasNonOneWidthOrSurrogateChars = true;
        }
        final String newText = new String(Character.toChars(codePoint));

        if (newCodePointDisplayWidth <= 0) {
            // Combining characters join the existing contents of the column (the wide character on its second half).
            if (cells[columnToSet] == null) columnToSet--;
            String old = cells[columnToSet];
            if (old.length() + newText.length() > 15) return; // Protect against too many combining characters.
            cells[columnToSet] = old + newText;
            return;
        }

        // Overwriting the second half of a wide character starting at the previous column:
        if (cells[columnToSet] == null) setCellChar(cells, columnToSet - 1, ' ', style);
        // Overwriting the first half of a wide character starting at the next column:
        if (newCodePointDisplayWidth == 2 && isWideCellStart(cells, columnToSet + 1)) setCellChar(cells, columnToSet + 1, ' ', style);

        final int oldCodePointDisplayWidth = baseWidth(cells[columnToSet]);
        cells[columnToSet] = newText;
        if (oldCodePointDisplayWidth == 2 && newCodePointDisplayWidth == 1) {
            // Replace second half of wide char with a space.
            cells[columnToSet + 1] = " ";
        } else if (oldCodePointDisplayWidth == 1 && newCodePointDisplayWidth == 2) {
            if (columnToSet == mColumns - 1) {
                throw new IllegalArgumentException("Cannot put wide character in last column");
            } else if (columnToSet < mColumns - 2) {
                // The next column becomes the second half; setChar() only removes its base character, so any
                // combining characters it had stay, now after this one.
                String next = cells[columnToSet + 1];
                cells[columnToSet] = newText + next.substring(Character.charCount(next.codePointAt(0)));
            }
            cells[columnToSet + 1] = null;
        }
    }

    public int getSpaceUsed() {
        return mSpaceUsed;
    }

    /** Note that the column may end of second half of wide character. */
    public int findStartOfColumn(int column) {
        if (column == mColumns) return getSpaceUsed();

        int currentColumn = 0;
        int currentCharIndex = 0;
        while (true) { // 0<2 1 < 2
            int newCharIndex = currentCharIndex;
            char c = mText[newCharIndex++]; // cci=1, cci=2
            boolean isHigh = Character.isHighSurrogate(c);
            int codePoint = isHigh ? Character.toCodePoint(c, mText[newCharIndex++]) : c;
            int wcwidth = WcWidth.width(codePoint); // 1, 2
            if (wcwidth > 0) {
                currentColumn += wcwidth;
                if (currentColumn == column) {
                    while (newCharIndex < mSpaceUsed) {
                        // Skip combining chars.
                        if (Character.isHighSurrogate(mText[newCharIndex])) {
                            if (WcWidth.width(Character.toCodePoint(mText[newCharIndex], mText[newCharIndex + 1])) <= 0) {
                                newCharIndex += 2;
                            } else {
                                break;
                            }
                        } else if (WcWidth.width(mText[newCharIndex]) <= 0) {
                            newCharIndex++;
                        } else {
                            break;
                        }
                    }
                    return newCharIndex;
                } else if (currentColumn > column) {
                    // Wide column going past end.
                    return currentCharIndex;
                }
            }
            currentCharIndex = newCharIndex;
        }
    }

    private boolean wideDisplayCharacterStartingAt(int column) {
        for (int currentCharIndex = 0, currentColumn = 0; currentCharIndex < mSpaceUsed; ) {
            char c = mText[currentCharIndex++];
            int codePoint = Character.isHighSurrogate(c) ? Character.toCodePoint(c, mText[currentCharIndex++]) : c;
            int wcwidth = WcWidth.width(codePoint);
            if (wcwidth > 0) {
                if (currentColumn == column && wcwidth == 2) return true;
                currentColumn += wcwidth;
                if (currentColumn > column) return false;
            }
        }
        return false;
    }

    public void clear(long style) {
        Arrays.fill(mText, ' ');
        Arrays.fill(mStyle, style);
        mSpaceUsed = (short) mColumns;
        mHasNonOneWidthOrSurrogateChars = false;
    }

    // https://github.com/steven676/Android-Terminal-Emulator/commit/9a47042620bec87617f0b4f5d50568535668fe26
    public void setChar(int columnToSet, int codePoint, long style) {
        if (columnToSet  < 0 || columnToSet >= mStyle.length)
            throw new IllegalArgumentException("TerminalRow.setChar(): columnToSet=" + columnToSet + ", codePoint=" + codePoint + ", style=" + style);

        mStyle[columnToSet] = style;

        final int newCodePointDisplayWidth = WcWidth.width(codePoint);

        // Fast path when we don't have any chars with width != 1
        if (!mHasNonOneWidthOrSurrogateChars) {
            if (codePoint >= Character.MIN_SUPPLEMENTARY_CODE_POINT || newCodePointDisplayWidth != 1) {
                mHasNonOneWidthOrSurrogateChars = true;
            } else {
                mText[columnToSet] = (char) codePoint;
                return;
            }
        }

        final boolean newIsCombining = newCodePointDisplayWidth <= 0;

        boolean wasExtraColForWideChar = (columnToSet > 0) && wideDisplayCharacterStartingAt(columnToSet - 1);

        if (newIsCombining) {
            // When standing at second half of wide character and inserting combining:
            if (wasExtraColForWideChar) columnToSet--;
        } else {
            // Check if we are overwriting the second half of a wide character starting at the previous column:
            if (wasExtraColForWideChar) setChar(columnToSet - 1, ' ', style);
            // Check if we are overwriting the first half of a wide character starting at the next column:
            boolean overwritingWideCharInNextColumn = newCodePointDisplayWidth == 2 && wideDisplayCharacterStartingAt(columnToSet + 1);
            if (overwritingWideCharInNextColumn) setChar(columnToSet + 1, ' ', style);
        }

        char[] text = mText;
        final int oldStartOfColumnIndex = findStartOfColumn(columnToSet);
        final int oldCodePointDisplayWidth = WcWidth.width(text, oldStartOfColumnIndex);

        // Get the number of elements in the mText array this column uses now
        int oldCharactersUsedForColumn;
        if (columnToSet + oldCodePointDisplayWidth < mColumns) {
            int oldEndOfColumnIndex = findStartOfColumn(columnToSet + oldCodePointDisplayWidth);
            oldCharactersUsedForColumn = oldEndOfColumnIndex - oldStartOfColumnIndex;
        } else {
            // Last character.
            oldCharactersUsedForColumn = mSpaceUsed - oldStartOfColumnIndex;
        }

        // Find how many chars this column will need
        int newCharactersUsedForColumn = Character.charCount(codePoint);
        if (newIsCombining) {
            // Combining characters are added to the contents of the column instead of overwriting them, so that they
            // modify the existing contents.
            // FIXME: Unassigned characters also get width=0.
            if (newCharactersUsedForColumn + oldCharactersUsedForColumn > 15) {
                // Protect against too many combining characters.
                return;
            }
            newCharactersUsedForColumn += oldCharactersUsedForColumn;
        }

        int oldNextColumnIndex = oldStartOfColumnIndex + oldCharactersUsedForColumn;
        int newNextColumnIndex = oldStartOfColumnIndex + newCharactersUsedForColumn;

        final int javaCharDifference = newCharactersUsedForColumn - oldCharactersUsedForColumn;
        if (javaCharDifference > 0) {
            // Shift the rest of the line right.
            int oldCharactersAfterColumn = mSpaceUsed - oldNextColumnIndex;
            if (mSpaceUsed + javaCharDifference > text.length) {
                // We need to grow the array
                char[] newText = new char[text.length + mColumns];
                System.arraycopy(text, 0, newText, 0, oldStartOfColumnIndex + oldCharactersUsedForColumn);
                System.arraycopy(text, oldNextColumnIndex, newText, newNextColumnIndex, oldCharactersAfterColumn);
                mText = text = newText;
            } else {
                System.arraycopy(text, oldNextColumnIndex, text, newNextColumnIndex, oldCharactersAfterColumn);
            }
        } else if (javaCharDifference < 0) {
            // Shift the rest of the line left.
            System.arraycopy(text, oldNextColumnIndex, text, newNextColumnIndex, mSpaceUsed - oldNextColumnIndex);
        }
        mSpaceUsed += javaCharDifference;

        // Store char. A combining character is stored at the end of the existing contents so that it modifies them:
        //noinspection ResultOfMethodCallIgnored - since we already now how many java chars is used.
        Character.toChars(codePoint, text, oldStartOfColumnIndex + (newIsCombining ? oldCharactersUsedForColumn : 0));

        if (oldCodePointDisplayWidth == 2 && newCodePointDisplayWidth == 1) {
            // Replace second half of wide char with a space. Which mean that we actually add a ' ' java character.
            if (mSpaceUsed + 1 > text.length) {
                char[] newText = new char[text.length + mColumns];
                System.arraycopy(text, 0, newText, 0, newNextColumnIndex);
                System.arraycopy(text, newNextColumnIndex, newText, newNextColumnIndex + 1, mSpaceUsed - newNextColumnIndex);
                mText = text = newText;
            } else {
                System.arraycopy(text, newNextColumnIndex, text, newNextColumnIndex + 1, mSpaceUsed - newNextColumnIndex);
            }
            text[newNextColumnIndex] = ' ';

            ++mSpaceUsed;
        } else if (oldCodePointDisplayWidth == 1 && newCodePointDisplayWidth == 2) {
            if (columnToSet == mColumns - 1) {
                throw new IllegalArgumentException("Cannot put wide character in last column");
            } else if (columnToSet == mColumns - 2) {
                // Truncate the line to the second part of this wide char:
                mSpaceUsed = (short) newNextColumnIndex;
            } else {
                // Overwrite the contents of the next column, which mean we actually remove java characters. Due to the
                // check at the beginning of this method we know that we are not overwriting a wide char.
                int newNextNextColumnIndex = newNextColumnIndex + (Character.isHighSurrogate(mText[newNextColumnIndex]) ? 2 : 1);
                int nextLen = newNextNextColumnIndex - newNextColumnIndex;

                // Shift the array leftwards.
                System.arraycopy(text, newNextNextColumnIndex, text, newNextColumnIndex, mSpaceUsed - newNextNextColumnIndex);
                mSpaceUsed -= nextLen;
            }
        }
    }

    boolean isBlank() {
        for (int charIndex = 0, charLen = getSpaceUsed(); charIndex < charLen; charIndex++)
            if (mText[charIndex] != ' ') return false;
        return true;
    }

    public long getStyle(int column) {
        return mStyle[column];
    }

}
