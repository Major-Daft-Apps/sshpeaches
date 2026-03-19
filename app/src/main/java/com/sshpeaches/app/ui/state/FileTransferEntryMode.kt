package com.majordaftapps.sshpeaches.app.ui.state

import com.majordaftapps.sshpeaches.app.data.model.ConnectionMode

enum class FileTransferEntryMode {
    DOWNLOAD,
    UPLOAD
}

fun ConnectionMode.userFacingLabel(
    fileTransferEntryMode: FileTransferEntryMode? = null
): String = when (this) {
    ConnectionMode.SSH -> "Terminal session"
    ConnectionMode.SFTP -> "File browser"
    ConnectionMode.SCP -> when (fileTransferEntryMode) {
        FileTransferEntryMode.UPLOAD -> "File upload"
        FileTransferEntryMode.DOWNLOAD -> "File download"
        null -> "File transfer"
    }
}
