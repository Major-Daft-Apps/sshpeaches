package com.sshpeaches.app.data.local

import android.content.Context
import android.util.Log
import android.os.SystemClock
import com.sshpeaches.app.data.model.HostConnection
import com.sshpeaches.app.data.model.Identity
import com.sshpeaches.app.data.model.PortForward
import com.sshpeaches.app.data.model.Snippet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val TAG = "CW/DataRepository"

class DataRepository private constructor(database: SshPeachesDatabase) {
    private val hostDao = database.hostDao()
    private val identityDao = database.identityDao()
    private val portForwardDao = database.portForwardDao()
    private val snippetDao = database.snippetDao()

    val hosts: Flow<List<HostConnection>> = hostDao.observeAll().map { list -> list.map { it.asModel() } }
    val identities: Flow<List<Identity>> = identityDao.observeAll().map { list -> list.map { it.asModel() } }
    val portForwards: Flow<List<PortForward>> = portForwardDao.observeAll().map { list -> list.map { it.asModel() } }
    val snippets: Flow<List<Snippet>> = snippetDao.observeAll().map { list -> list.map { it.asModel() } }

    suspend fun upsertHost(host: HostConnection) = performDbAction("upsertHost", "host=${host.name}") {
        hostDao.upsert(host.asEntity())
    }
    suspend fun deleteHost(host: HostConnection) = performDbAction("deleteHost", "host=${host.name}") {
        hostDao.delete(host.asEntity())
    }

    suspend fun upsertIdentity(identity: Identity) = performDbAction("upsertIdentity", "label=${identity.label}") {
        identityDao.upsert(identity.asEntity())
    }
    suspend fun deleteIdentity(identity: Identity) = performDbAction("deleteIdentity", "label=${identity.label}") {
        identityDao.delete(identity.asEntity())
    }

    suspend fun upsertForward(forward: PortForward) = performDbAction("upsertForward", "label=${forward.label}") {
        portForwardDao.upsert(forward.asEntity())
    }
    suspend fun deleteForward(forward: PortForward) = performDbAction("deleteForward", "label=${forward.label}") {
        portForwardDao.delete(forward.asEntity())
    }

    suspend fun upsertSnippet(snippet: Snippet) = performDbAction("upsertSnippet", "title=${snippet.title}") {
        snippetDao.upsert(snippet.asEntity())
    }
    suspend fun deleteSnippet(snippet: Snippet) = performDbAction("deleteSnippet", "title=${snippet.title}") {
        snippetDao.delete(snippet.asEntity())
    }

    private suspend fun <T> performDbAction(name: String, params: String, action: suspend () -> T): T {
        val t0 = SystemClock.elapsedRealtime()
        return try {
            val result = action()
            val ms = SystemClock.elapsedRealtime() - t0
            Log.i(TAG, "DB $name success $params ms=$ms")
            result
        } catch (e: Exception) {
            val ms = SystemClock.elapsedRealtime() - t0
            Log.e(TAG, "DB $name fail $params ms=$ms err=${e.javaClass.simpleName} msg=${e.message?.take(200)}", e)
            throw e
        }
    }

    companion object {
        @Volatile private var instance: DataRepository? = null
        fun get(context: Context): DataRepository =
            instance ?: synchronized(this) {
                instance ?: DataRepository(SshPeachesDatabase.get(context)).also { instance = it }
            }
    }
}
