package com.majordaftapps.sshpeaches.app.service

internal val PROTECTED_REMOTE_SYSTEM_ROOTS: Set<String> = setOf(
    "/",
    "/bin",
    "/boot",
    "/dev",
    "/etc",
    "/home",
    "/lib",
    "/lib64",
    "/lost+found",
    "/media",
    "/mnt",
    "/opt",
    "/proc",
    "/root",
    "/run",
    "/sbin",
    "/srv",
    "/sys",
    "/tmp",
    "/usr",
    "/var"
)

internal enum class RemoteDeleteMode {
    NON_RECURSIVE,
    RECURSIVE
}

internal fun remoteDeleteMode(operation: String): RemoteDeleteMode? = when (operation.trim().lowercase()) {
    "delete_file", "delete_non_recursive", "rm" -> RemoteDeleteMode.NON_RECURSIVE
    "delete", "delete_recursive", "rm_recursive" -> RemoteDeleteMode.RECURSIVE
    else -> null
}

internal fun preserveRemoteTransferPath(path: String): String =
    path.takeUnless { it.isBlank() }.orEmpty()

internal fun validateRemotePathMutation(
    operation: String,
    sourcePath: String,
    destinationPath: String? = null
) {
    val normalizedOperation = operation.trim().lowercase()
    val protectsSource = remoteDeleteMode(normalizedOperation) != null ||
        normalizedOperation == "move" ||
        normalizedOperation == "rename"
    val protectsDestination = normalizedOperation == "move" || normalizedOperation == "rename"

    if (protectsSource) {
        requireRemotePathIsNotProtected(sourcePath, role = "source")
    }
    if (protectsDestination) {
        requireRemotePathIsNotProtected(
            destinationPath.orEmpty(),
            role = "destination"
        )
    }
}

internal fun normalizeRemotePathLexically(path: String): String {
    val trimmed = path.trim()
    if (trimmed.isEmpty()) return ""
    val isAbsolute = trimmed.startsWith("/")
    val components = ArrayDeque<String>()
    trimmed.split('/').forEach { component ->
        when {
            component.isEmpty() || component == "." -> Unit
            component == ".." && components.isNotEmpty() && components.last() != ".." ->
                components.removeLast()
            component == ".." && !isAbsolute -> components.addLast(component)
            component != ".." -> components.addLast(component)
        }
    }
    val normalized = components.joinToString("/")
    return when {
        isAbsolute && normalized.isEmpty() -> "/"
        isAbsolute -> "/$normalized"
        normalized.isEmpty() -> "."
        else -> normalized
    }
}

private fun requireRemotePathIsNotProtected(path: String, role: String) {
    val normalized = normalizeRemotePathLexically(path)
    require(normalized !in PROTECTED_REMOTE_SYSTEM_ROOTS) {
        "Refusing to modify protected remote $role path: $normalized"
    }
}
