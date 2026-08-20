package com.termux.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalOutput;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public final class TerminalViewStandaloneInputTest {

    @Test
    public void copyTextMarksStandaloneTerminalSelectionSensitive() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            TerminalView view = new TerminalView(context, null);
            view.copyTextToClipboard("terminal-secret");

            ClipboardManager clipboard =
                (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            assertNotNull(clipboard);
            ClipData clipData = clipboard.getPrimaryClip();
            assertNotNull(clipData);
            assertEquals("terminal-secret", clipData.getItemAt(0).getText().toString());
            assertNotNull(clipData.getDescription().getExtras());
            assertTrue(
                clipData.getDescription().getExtras().getBoolean("android.content.extra.IS_SENSITIVE")
            );
            assertTrue(
                clipData.getDescription().getExtras().getBoolean(
                    "com.android.systemui.SUPPRESS_CLIPBOARD_OVERLAY"
                )
            );
        });
    }

    @Test
    public void commitTextRoutesCodePointToClientWithoutTerminalSession() {
        RecordingOutput output = new RecordingOutput();
        TerminalView view = new TerminalView(InstrumentationRegistry.getInstrumentation().getTargetContext(), null);
        RecordingClient client = new RecordingClient();
        view.setTerminalViewClient(client);
        view.attachEmulator(new TerminalEmulator(output, 80, 24, 1, 1, 100, new NoOpTerminalSessionClient()));

        InputConnection connection = view.onCreateInputConnection(new EditorInfo());
        assertTrue(connection.commitText("a", 1));

        assertEquals(1, client.codePoints.size());
        assertEquals((Integer) (int) 'a', client.codePoints.get(0));
        assertEquals(false, client.ctrlStates.get(0));
        assertNull(client.sessions.get(0));
        assertEquals(0, output.writeCount);
    }

    @Test
    public void commitTextRoutesEffectiveControlStateToClientWithoutTerminalSession() {
        RecordingOutput output = new RecordingOutput();
        TerminalView view = new TerminalView(InstrumentationRegistry.getInstrumentation().getTargetContext(), null);
        RecordingClient client = new RecordingClient();
        client.controlDown = true;
        view.setTerminalViewClient(client);
        view.attachEmulator(new TerminalEmulator(output, 80, 24, 1, 1, 100, new NoOpTerminalSessionClient()));

        InputConnection connection = view.onCreateInputConnection(new EditorInfo());
        assertTrue(connection.commitText("a", 1));

        assertEquals(1, client.codePoints.size());
        assertEquals((Integer) (int) 'a', client.codePoints.get(0));
        assertEquals(true, client.ctrlStates.get(0));
        assertNull(client.sessions.get(0));
        assertEquals(0, output.writeCount);
    }

    @Test
    public void commitTextReadsEffectiveAltStateBeforeNullSessionFallback() {
        RecordingOutput output = new RecordingOutput();
        TerminalView view = new TerminalView(InstrumentationRegistry.getInstrumentation().getTargetContext(), null);
        RecordingClient client = new RecordingClient();
        client.altDown = true;
        view.setTerminalViewClient(client);
        view.attachEmulator(new TerminalEmulator(output, 80, 24, 1, 1, 100, new NoOpTerminalSessionClient()));

        InputConnection connection = view.onCreateInputConnection(new EditorInfo());
        assertTrue(connection.commitText("a", 1));

        assertEquals(1, client.altReadCount);
        assertEquals(1, client.codePoints.size());
        assertNull(client.sessions.get(0));
        assertEquals(0, output.writeCount);
    }

    private static final class RecordingClient implements TerminalViewClient {
        final List<Integer> codePoints = new ArrayList<>();
        final List<Boolean> ctrlStates = new ArrayList<>();
        final List<TerminalSession> sessions = new ArrayList<>();
        boolean controlDown = false;
        boolean altDown = false;
        int altReadCount = 0;

        @Override
        public float onScale(float scale) {
            return scale;
        }

        @Override
        public void onSingleTapUp(MotionEvent e) {
        }

        @Override
        public boolean shouldBackButtonBeMappedToEscape() {
            return false;
        }

        @Override
        public boolean shouldEnforceCharBasedInput() {
            return false;
        }

        @Override
        public boolean isTerminalViewSelected() {
            return true;
        }

        @Override
        public void copyModeChanged(boolean copyMode) {
        }

        @Override
        public boolean onKeyDown(int keyCode, KeyEvent e, TerminalSession session) {
            return false;
        }

        @Override
        public boolean onKeyUp(int keyCode, KeyEvent e) {
            return false;
        }

        @Override
        public boolean onLongPress(MotionEvent event) {
            return false;
        }

        @Override
        public boolean readControlKey() {
            return controlDown;
        }

        @Override
        public boolean readAltKey() {
            altReadCount++;
            return altDown;
        }

        @Override
        public boolean readShiftKey() {
            return false;
        }

        @Override
        public boolean readFnKey() {
            return false;
        }

        @Override
        public boolean onCodePoint(int codePoint, boolean ctrlDown, TerminalSession session) {
            codePoints.add(codePoint);
            ctrlStates.add(ctrlDown);
            sessions.add(session);
            return true;
        }
    }

    private static final class RecordingOutput extends TerminalOutput {
        int writeCount = 0;

        @Override
        public void write(byte[] data, int offset, int count) {
            writeCount++;
        }

        @Override
        public void titleChanged(String oldTitle, String newTitle) {
        }

        @Override
        public void onCopyTextToClipboard(String text) {
        }

        @Override
        public void onPasteTextFromClipboard() {
        }

        @Override
        public void onBell() {
        }

        @Override
        public void onColorsChanged() {
        }
    }

    private static final class NoOpTerminalSessionClient implements TerminalSessionClient {
        @Override
        public void onTextChanged(TerminalSession changedSession) {
        }

        @Override
        public void onTitleChanged(TerminalSession changedSession) {
        }

        @Override
        public void onSessionFinished(TerminalSession finishedSession) {
        }

        @Override
        public void onCopyTextToClipboard(TerminalSession session, String text) {
        }

        @Override
        public void onPasteTextFromClipboard(TerminalSession session) {
        }

        @Override
        public void onBell(TerminalSession session) {
        }

        @Override
        public void onColorsChanged(TerminalSession session) {
        }

        @Override
        public void onTerminalCursorStateChange(boolean state) {
        }
    }
}
