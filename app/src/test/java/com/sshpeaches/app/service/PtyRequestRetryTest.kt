package com.majordaftapps.sshpeaches.app.service

import java.util.concurrent.TimeoutException
import net.schmizz.sshj.connection.ConnectionException
import net.schmizz.sshj.transport.TransportException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 0.14.1 on a slow link: the pty request's answer came after sshj stopped waiting, the app sent a second request,
 * and OpenSSH dropped the connection with "Protocol error: you already have a pty."
 */
class PtyRequestRetryTest {
    @Test
    fun aServerThatRefusedIsAskedAgainWithThePlainRequest() {
        // What sshj delivers for SSH_MSG_CHANNEL_FAILURE.
        assertTrue(mayAskForPtyAgain(ConnectionException("Request failed"), sessionOpen = true))
    }

    @Test
    fun aRequestThatTimedOutIsNeverSentTwice() {
        // What sshj's Promise throws when the answer doesn't come in time.
        val timedOut = ConnectionException.chainer.chain(TimeoutException("Timeout expired: 20000 MILLISECONDS"))
        assertFalse(mayAskForPtyAgain(timedOut, sessionOpen = true))
        assertFalse(mayAskForPtyAgain(ConnectionException.chainer.chain(InterruptedException()), sessionOpen = true))
    }

    @Test
    fun nothingIsSentOnASessionOrConnectionThatIsGone() {
        assertFalse(mayAskForPtyAgain(ConnectionException("Request failed"), sessionOpen = false))
        assertFalse(mayAskForPtyAgain(TransportException("Broken transport; encountered EOF"), sessionOpen = true))
        assertFalse(mayAskForPtyAgain(IllegalStateException("Not connected"), sessionOpen = true))
    }
}
