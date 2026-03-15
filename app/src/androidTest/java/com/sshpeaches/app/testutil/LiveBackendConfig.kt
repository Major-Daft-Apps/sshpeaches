package com.majordaftapps.sshpeaches.app.testutil

import androidx.test.platform.app.InstrumentationRegistry

object LiveBackendConfig {
    private val arguments = InstrumentationRegistry.getArguments()

    val host: String
        get() = arguments.getString("liveSshHost") ?: "10.0.2.2"

    val port: Int
        get() = arguments.getString("liveSshPort")?.toIntOrNull() ?: 56321

    val username: String
        get() = arguments.getString("liveSshUsername") ?: "tester"

    val password: String
        get() = arguments.getString("liveSshPassword") ?: "peaches-password"

    val keyUsername: String
        get() = arguments.getString("liveSshKeyUsername") ?: "tester-key"
}
