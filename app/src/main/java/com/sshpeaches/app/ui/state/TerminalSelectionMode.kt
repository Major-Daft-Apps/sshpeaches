package com.majordaftapps.sshpeaches.app.ui.state

enum class TerminalSelectionMode(val label: String, val description: String) {
    NATURAL(
        label = "Natural",
        description = "Unwrap visually wrapped lines when copying."
    ),
    BLOCK(
        label = "Block",
        description = "Keep wrapped rows as separate lines."
    )
}
