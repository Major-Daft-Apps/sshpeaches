package com.majordaftapps.sshpeaches.app.diagnostics

import com.google.firebase.appcheck.FirebaseAppCheck
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object AppCheckTokenProvider {
    suspend fun getToken(): String? =
        suspendCancellableCoroutine { cont ->
            FirebaseAppCheck.getInstance()
                .getAppCheckToken(false)
                .addOnSuccessListener { result ->
                    cont.resume(result.token)
                }
                .addOnFailureListener {
                    cont.resume(null)
                }
        }
}
