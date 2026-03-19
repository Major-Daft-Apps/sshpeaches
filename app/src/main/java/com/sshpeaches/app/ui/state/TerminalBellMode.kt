package com.majordaftapps.sshpeaches.app.ui.state

enum class TerminalBellMode(val label: String, val description: String) {
    DISABLED(
        label = "Disabled",
        description = "Ignore terminal bell events."
    ),
    VIBRATE_DEVICE(
        label = "Vibrate device",
        description = "Vibrate the device when the terminal sends a bell."
    ),
    SHOW_NOTIFICATION(
        label = "Show notification",
        description = "Show a notification when the terminal sends a bell."
    )
}
