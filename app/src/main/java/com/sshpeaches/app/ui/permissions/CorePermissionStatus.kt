package com.majordaftapps.sshpeaches.app.ui.permissions

data class CorePermissionStatus(
    val id: String,
    val title: String,
    val description: String,
    val granted: Boolean,
    val remediation: CorePermissionRemediation = CorePermissionRemediation.REQUEST
)

enum class CorePermissionRemediation {
    REQUEST,
    SETTINGS
}
