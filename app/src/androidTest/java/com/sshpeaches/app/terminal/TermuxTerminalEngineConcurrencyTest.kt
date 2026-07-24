package com.majordaftapps.sshpeaches.app.terminal

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.majordaftapps.sshpeaches.app.ui.terminal.TermuxTerminalEngine
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TermuxTerminalEngineConcurrencyTest {

    @Test
    fun rapidResizeWhileOutputArrives_keepsEmulatorUsable() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val engine = TermuxTerminalEngine(onWriteToRemote = {})
        val output = buildString {
            repeat(64) { index ->
                append("\u001B[38;5;")
                append(16 + index % 200)
                append("m tmux-pane-")
                append(index)
                append(" abcdefghijklmnopqrstuvwxyz 0123456789\u001B[0m\r\n")
            }
        }.encodeToByteArray()

        runBlocking {
            repeat(48) {
                engine.appendIncoming(output)
            }
        }

        val failure = AtomicReference<Throwable?>()
        val writerReady = CountDownLatch(1)
        val start = CountDownLatch(1)
        val writer = thread(start = false, isDaemon = true, name = "terminal-output-stress") {
            writerReady.countDown()
            start.await()
            try {
                runBlocking {
                    repeat(600) {
                        engine.appendIncoming(output)
                    }
                }
            } catch (error: Throwable) {
                failure.compareAndSet(null, error)
            }
        }

        writer.start()
        assertTrue("Output writer did not start", writerReady.await(5, TimeUnit.SECONDS))
        start.countDown()

        repeat(400) { index ->
            if (failure.get() != null) return@repeat
            try {
                instrumentation.runOnMainSync {
                    val wide = index % 2 == 0
                    engine.resize(
                        columns = if (wide) 160 else 32,
                        rows = if (wide) 52 else 10,
                        cellWidthPx = 8,
                        cellHeightPx = 16
                    )
                }
            } catch (error: Throwable) {
                failure.compareAndSet(null, error)
            }
        }

        writer.join(TimeUnit.SECONDS.toMillis(30))
        assertFalse("Output writer deadlocked during resize", writer.isAlive)
        failure.get()?.let { throw AssertionError("Concurrent output and resize failed", it) }

        instrumentation.runOnMainSync {
            engine.resize(80, 24, 8, 16)
        }
        assertTrue(engine.renderText().contains("tmux-pane-"))
    }
}
