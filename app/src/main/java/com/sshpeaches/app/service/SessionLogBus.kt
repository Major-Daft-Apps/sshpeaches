@file:Suppress("DEPRECATION")

package com.majordaftapps.sshpeaches.app.service

import android.util.Log
import com.majordaftapps.sshpeaches.app.BuildConfig
import com.majordaftapps.sshpeaches.app.service.SessionLogBus.LogLevel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import net.schmizz.sshj.common.LoggerFactory
import org.slf4j.Logger
import org.slf4j.helpers.MarkerIgnoringBase
import org.slf4j.helpers.MessageFormatter

/**
 * Broadcasts verbose session logs (similar to `ssh -v`) so the UI can show them.
 */
object SessionLogBus {

    enum class LogLevel { DEBUG, INFO, WARN, ERROR }

    data class Entry(
        val hostId: String,
        val level: LogLevel,
        val message: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val _entries = MutableSharedFlow<Entry>(extraBufferCapacity = 200)
    val entries: SharedFlow<Entry> = _entries.asSharedFlow()

    fun emit(entry: Entry) {
        _entries.tryEmit(entry)
    }
}

class SessionLoggerFactory(
    private val hostId: String,
    private val delegate: LoggerFactory = LoggerFactory.DEFAULT
) : LoggerFactory {
    override fun getLogger(name: String): Logger =
        SessionLogger(hostId, delegate.getLogger(name))

    override fun getLogger(clazz: Class<*>?): Logger =
        getLogger(clazz?.name ?: "unknown")
}

private class SessionLogger(
    private val hostId: String,
    private val delegate: Logger
) : MarkerIgnoringBase() {
    companion object {
        private const val LOGCAT_TAG = "SSHPeachesSSH"
    }

    override fun getName(): String = delegate.name

    override fun isTraceEnabled(): Boolean = delegate.isTraceEnabled
    override fun trace(msg: String?) {
        delegate.trace(msg)
        emit(LogLevel.DEBUG, msg)
    }

    override fun trace(format: String?, arg: Any?) {
        delegate.trace(format, arg)
        emit(LogLevel.DEBUG, format, arrayOf(arg))
    }

    override fun trace(format: String?, arg1: Any?, arg2: Any?) {
        delegate.trace(format, arg1, arg2)
        emit(LogLevel.DEBUG, format, arrayOf(arg1, arg2))
    }

    override fun trace(format: String?, arguments: Array<out Any?>?) {
        delegate.trace(format, arguments)
        emit(LogLevel.DEBUG, format, arguments)
    }

    override fun trace(msg: String?, t: Throwable?) {
        delegate.trace(msg, t)
        emit(LogLevel.DEBUG, msg, throwable = t)
    }

    override fun isDebugEnabled(): Boolean = delegate.isDebugEnabled
    override fun debug(msg: String?) {
        delegate.debug(msg)
        emit(LogLevel.DEBUG, msg)
    }

    override fun debug(format: String?, arg: Any?) {
        delegate.debug(format, arg)
        emit(LogLevel.DEBUG, format, arrayOf(arg))
    }

    override fun debug(format: String?, arg1: Any?, arg2: Any?) {
        delegate.debug(format, arg1, arg2)
        emit(LogLevel.DEBUG, format, arrayOf(arg1, arg2))
    }

    override fun debug(format: String?, arguments: Array<out Any?>?) {
        delegate.debug(format, arguments)
        emit(LogLevel.DEBUG, format, arguments)
    }

    override fun debug(msg: String?, t: Throwable?) {
        delegate.debug(msg, t)
        emit(LogLevel.DEBUG, msg, throwable = t)
    }

    override fun isInfoEnabled(): Boolean = delegate.isInfoEnabled
    override fun info(msg: String?) {
        delegate.info(msg)
        emit(LogLevel.INFO, msg)
    }

    override fun info(format: String?, arg: Any?) {
        delegate.info(format, arg)
        emit(LogLevel.INFO, format, arrayOf(arg))
    }

    override fun info(format: String?, arg1: Any?, arg2: Any?) {
        delegate.info(format, arg1, arg2)
        emit(LogLevel.INFO, format, arrayOf(arg1, arg2))
    }

    override fun info(format: String?, arguments: Array<out Any?>?) {
        delegate.info(format, arguments)
        emit(LogLevel.INFO, format, arguments)
    }

    override fun info(msg: String?, t: Throwable?) {
        delegate.info(msg, t)
        emit(LogLevel.INFO, msg, throwable = t)
    }

    override fun isWarnEnabled(): Boolean = delegate.isWarnEnabled
    override fun warn(msg: String?) {
        delegate.warn(msg)
        emit(LogLevel.WARN, msg)
    }

    override fun warn(format: String?, arg: Any?) {
        delegate.warn(format, arg)
        emit(LogLevel.WARN, format, arrayOf(arg))
    }

    override fun warn(format: String?, arg1: Any?, arg2: Any?) {
        delegate.warn(format, arg1, arg2)
        emit(LogLevel.WARN, format, arrayOf(arg1, arg2))
    }

    override fun warn(format: String?, arguments: Array<out Any?>?) {
        delegate.warn(format, arguments)
        emit(LogLevel.WARN, format, arguments)
    }

    override fun warn(msg: String?, t: Throwable?) {
        delegate.warn(msg, t)
        emit(LogLevel.WARN, msg, throwable = t)
    }

    override fun isErrorEnabled(): Boolean = delegate.isErrorEnabled
    override fun error(msg: String?) {
        delegate.error(msg)
        emit(LogLevel.ERROR, msg)
    }

    override fun error(format: String?, arg: Any?) {
        delegate.error(format, arg)
        emit(LogLevel.ERROR, format, arrayOf(arg))
    }

    override fun error(format: String?, arg1: Any?, arg2: Any?) {
        delegate.error(format, arg1, arg2)
        emit(LogLevel.ERROR, format, arrayOf(arg1, arg2))
    }

    override fun error(format: String?, arguments: Array<out Any?>?) {
        delegate.error(format, arguments)
        emit(LogLevel.ERROR, format, arguments)
    }

    override fun error(msg: String?, t: Throwable?) {
        delegate.error(msg, t)
        emit(LogLevel.ERROR, msg, throwable = t)
    }

    private fun emit(
        level: LogLevel,
        message: String?,
        args: Array<out Any?>? = null,
        throwable: Throwable? = null
    ) {
        val text = when {
            message == null -> throwable?.message ?: return
            args == null -> message
            else -> MessageFormatter.arrayFormat(message, args).message
        }
        if (text.isNullOrBlank()) return
        if (BuildConfig.DEBUG) {
            val logcatMessage = "[$hostId] $text"
            when (level) {
                LogLevel.DEBUG -> Log.d(LOGCAT_TAG, logcatMessage, throwable)
                LogLevel.INFO -> Log.i(LOGCAT_TAG, logcatMessage, throwable)
                LogLevel.WARN -> Log.w(LOGCAT_TAG, logcatMessage, throwable)
                LogLevel.ERROR -> Log.e(LOGCAT_TAG, logcatMessage, throwable)
            }
        }
        SessionLogBus.emit(SessionLogBus.Entry(hostId, level, text))
    }
}
