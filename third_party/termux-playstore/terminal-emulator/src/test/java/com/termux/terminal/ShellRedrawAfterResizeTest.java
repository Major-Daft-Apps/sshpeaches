package com.termux.terminal;

import java.util.Base64;

/**
 * A wider font (or rotation) reflows the screen, then the shell redraws its prompt and command line relative to the
 * rows it laid them out on. These replay real bash 5 and zsh 5.9 output captured in a 40-column pty, widen the screen
 * to 60 columns, and replay what the shell sent after SIGWINCH.
 */
public class ShellRedrawAfterResizeTest extends TerminalTestCase {

    private void replay(String beforeResize, String afterResize) {
        withTerminalSized(40, 12);
        byte[] before = Base64.getDecoder().decode(beforeResize);
        mTerminal.append(before, before.length);
        resize(60, 12);
        byte[] after = Base64.getDecoder().decode(afterResize);
        mTerminal.append(after, after.length);
    }

    private String row(int row) {
        return mTerminal.getScreen().getSelectedText(0, row, mTerminal.mColumns, row);
    }

    /** "echo hello", then a command typed past the edge (it wraps at 40 columns but fits in 60). */
    public void testBashRedrawsTheTypedCommandOnItsOwnRow() {
        replay("G1s/MjAwNGh1c2VyQGhvc3Q6fiQgZWNobyBoZWxsbw0KG1s/MjAwNGwNaGVsbG8NChtbPzIwMDRodXNlckBob3N0On4kIGVjaG8gdGhlIHF1aWNrIGJyb3duIGZveCBqdW1wcyBvdmVy", "DRtbSw0bW0EbW0sNdXNlckBob3N0On4kIGVjaG8gdGhlIHF1aWNrIGJyb3duIGZveCBqdW1wcyBvdmVy");
        assertEquals("user@host:~$ echo hello", row(0));
        assertEquals("hello", row(1)); // was overwritten: the prompt went one row too high
        assertEquals("user@host:~$ echo the quick brown fox jumps over", row(2));
        assertCursorAt(2, 48);
    }

    public void testZshRedrawsTheTypedCommandOnItsOwnRow() {
        replay("G1sxbRtbN20lG1syN20bWzFtG1swbSAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgIA0gDQ0bWzBtG1syN20bWzI0bRtbSnVzZXJAaG9zdDp+JSAbW0sbWz8yMDA0aGUIZWNobyBoZWxsbxtbPzIwMDRsDQ0KaGVsbG8NChtbMW0bWzdtJRtbMjdtG1sxbRtbMG0gICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICANIA0NG1swbRtbMjdtG1syNG0bW0p1c2VyQGhvc3Q6fiUgG1tLG1s/MjAwNGhlCGVjaG8gdGhlIHF1aWNrIGJyb3duIGZveCBqdSANG1tLbQ1tcHMgb3Zlcg==", "G1tBDQ0bWzBtG1syN20bWzI0bRtbSnVzZXJAaG9zdDp+JSBlY2hvIHRoZSBxdWljayBicm93biBmb3gganVtcHMgb3Zlcg0NChtbSxtbQRtbNDhD");
        assertEquals("hello", row(1));
        assertEquals("user@host:~% echo the quick brown fox jumps over", row(2));
        assertCursorAt(2, 48);
    }

    /** "printf abc": zsh marks the missing newline with % and pads the row (PROMPT_SP), which wraps it. */
    public void testZshPromptIsNotJoinedToTheRowAboveIt() {
        replay("G1sxbRtbN20lG1syN20bWzFtG1swbSAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgIA0gDQ0bWzBtG1syN20bWzI0bRtbSnVzZXJAaG9zdDp+JSAbW0sbWz8yMDA0aGUIZWNobyBoZWxsbxtbPzIwMDRsDQ0KaGVsbG8NChtbMW0bWzdtJRtbMjdtG1sxbRtbMG0gICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICANIA0NG1swbRtbMjdtG1syNG0bW0p1c2VyQGhvc3Q6fiUgG1tLG1s/MjAwNGhwCHByaW50ZiBhYmMbWz8yMDA0bA0NCmFiYxtbMW0bWzdtJRtbMjdtG1sxbRtbMG0gICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICANIA0NG1swbRtbMjdtG1syNG0bW0p1c2VyQGhvc3Q6fiUgG1tLG1s/MjAwNGg=", "DQ0bWzBtG1syN20bWzI0bRtbSnVzZXJAaG9zdDp+JSA=");
        assertEquals("user@host:~% printf abc", row(2));
        assertEquals("abc%", row(3)); // was "abc%" + spaces + the prompt, then the prompt alone
        assertEquals("user@host:~%", row(4));
        assertCursorAt(4, 13);
    }
}
