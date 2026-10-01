package com.majordaftapps.sshpeaches.app.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

internal enum class RemoteFileKind {
    FOLDER,
    LINK,
    IMAGE,
    VIDEO,
    AUDIO,
    PDF,
    DOCUMENT,
    SPREADSHEET,
    PRESENTATION,
    ARCHIVE,
    CODE,
    SCRIPT,
    TEXT,
    WEB,
    FONT,
    EXECUTABLE,
    KEY,
    DATABASE,
    CONFIG,
    GENERIC
}

internal data class RemoteFileTypeVisual(
    val kind: RemoteFileKind,
    val icon: ImageVector,
    val contentDescription: String
)

internal fun remoteFileTypeVisual(
    name: String,
    isDirectory: Boolean,
    isSymbolicLink: Boolean,
    isBrokenLink: Boolean,
    linkTargetIsDirectory: Boolean?
): RemoteFileTypeVisual {
    val kind = remoteFileKind(
        name = name,
        isDirectory = isDirectory,
        isSymbolicLink = isSymbolicLink,
        isBrokenLink = isBrokenLink,
        linkTargetIsDirectory = linkTargetIsDirectory
    )
    return RemoteFileTypeVisual(
        kind = kind,
        icon = remoteFileKindIcon(kind),
        contentDescription = remoteFileKindLabel(kind)
    )
}

internal fun remoteFileKind(
    name: String,
    isDirectory: Boolean,
    isSymbolicLink: Boolean,
    isBrokenLink: Boolean,
    linkTargetIsDirectory: Boolean?
): RemoteFileKind {
    if (isBrokenLink) return RemoteFileKind.LINK
    if (isDirectory || linkTargetIsDirectory == true) return RemoteFileKind.FOLDER
    val kind = kindForExtension(fileExtensionKey(name))
    if (isSymbolicLink && kind == RemoteFileKind.GENERIC) return RemoteFileKind.LINK
    return kind
}

internal fun fileExtensionKey(name: String): String {
    val base = name.substringAfterLast('/').lowercase()
    if (base.indexOf('.', startIndex = 1) < 0) return ""
    return when {
        base.endsWith(".tar.gz") || base.endsWith(".tgz") -> "tar.gz"
        base.endsWith(".tar.bz2") || base.endsWith(".tbz2") -> "tar.bz2"
        base.endsWith(".tar.xz") || base.endsWith(".txz") -> "tar.xz"
        else -> base.substringAfterLast('.')
    }
}

internal fun remoteFileKindIcon(kind: RemoteFileKind): ImageVector = when (kind) {
    RemoteFileKind.FOLDER -> Icons.Default.Folder
    RemoteFileKind.LINK -> Icons.Default.Link
    RemoteFileKind.IMAGE -> Icons.Default.Image
    RemoteFileKind.VIDEO -> Icons.Default.Movie
    RemoteFileKind.AUDIO -> Icons.Default.AudioFile
    RemoteFileKind.PDF -> Icons.AutoMirrored.Filled.Article
    RemoteFileKind.DOCUMENT -> Icons.AutoMirrored.Filled.TextSnippet
    RemoteFileKind.SPREADSHEET -> Icons.Default.TableChart
    RemoteFileKind.PRESENTATION -> Icons.Default.Slideshow
    RemoteFileKind.ARCHIVE -> Icons.Default.Archive
    RemoteFileKind.CODE -> Icons.Default.Code
    RemoteFileKind.SCRIPT -> Icons.Default.Terminal
    RemoteFileKind.TEXT -> Icons.AutoMirrored.Filled.TextSnippet
    RemoteFileKind.WEB -> Icons.Default.Language
    RemoteFileKind.FONT -> Icons.Default.FontDownload
    RemoteFileKind.EXECUTABLE -> Icons.Default.Apps
    RemoteFileKind.KEY -> Icons.Default.VpnKey
    RemoteFileKind.DATABASE -> Icons.Default.Storage
    RemoteFileKind.CONFIG -> Icons.Default.Settings
    RemoteFileKind.GENERIC -> Icons.AutoMirrored.Filled.InsertDriveFile
}

internal fun remoteFileKindLabel(kind: RemoteFileKind): String = when (kind) {
    RemoteFileKind.FOLDER -> "Folder"
    RemoteFileKind.LINK -> "Link"
    RemoteFileKind.IMAGE -> "Image"
    RemoteFileKind.VIDEO -> "Video"
    RemoteFileKind.AUDIO -> "Audio"
    RemoteFileKind.PDF -> "PDF"
    RemoteFileKind.DOCUMENT -> "Document"
    RemoteFileKind.SPREADSHEET -> "Spreadsheet"
    RemoteFileKind.PRESENTATION -> "Presentation"
    RemoteFileKind.ARCHIVE -> "Archive"
    RemoteFileKind.CODE -> "Code"
    RemoteFileKind.SCRIPT -> "Script"
    RemoteFileKind.TEXT -> "Text"
    RemoteFileKind.WEB -> "Web"
    RemoteFileKind.FONT -> "Font"
    RemoteFileKind.EXECUTABLE -> "App"
    RemoteFileKind.KEY -> "Key"
    RemoteFileKind.DATABASE -> "Database"
    RemoteFileKind.CONFIG -> "Config"
    RemoteFileKind.GENERIC -> "File"
}

internal fun remoteFileKindTint(kind: RemoteFileKind, isBrokenLink: Boolean, fallback: Color, primary: Color, error: Color): Color {
    if (isBrokenLink) return error
    return when (kind) {
        RemoteFileKind.FOLDER -> primary
        RemoteFileKind.LINK -> primary
        RemoteFileKind.IMAGE -> Color(0xFF4DB6AC)
        RemoteFileKind.VIDEO -> Color(0xFF9575CD)
        RemoteFileKind.AUDIO -> Color(0xFFF06292)
        RemoteFileKind.PDF -> Color(0xFFEF5350)
        RemoteFileKind.DOCUMENT -> Color(0xFF64B5F6)
        RemoteFileKind.SPREADSHEET -> Color(0xFF81C784)
        RemoteFileKind.PRESENTATION -> Color(0xFFFFB74D)
        RemoteFileKind.ARCHIVE -> Color(0xFFFFA726)
        RemoteFileKind.CODE -> Color(0xFF7986CB)
        RemoteFileKind.SCRIPT -> Color(0xFF4DB6AC)
        RemoteFileKind.TEXT -> fallback
        RemoteFileKind.WEB -> Color(0xFF4FC3F7)
        RemoteFileKind.FONT -> Color(0xFFA1887F)
        RemoteFileKind.EXECUTABLE -> Color(0xFF90A4AE)
        RemoteFileKind.KEY -> Color(0xFFFFD54F)
        RemoteFileKind.DATABASE -> Color(0xFF4DD0E1)
        RemoteFileKind.CONFIG -> fallback
        RemoteFileKind.GENERIC -> fallback
    }
}

private fun kindForExtension(extension: String): RemoteFileKind = when (extension) {
    "png", "jpg", "jpeg", "gif", "webp", "svg", "bmp", "ico", "heic", "heif",
    "tif", "tiff", "avif", "raw", "psd" -> RemoteFileKind.IMAGE
    "mp4", "mkv", "mov", "webm", "avi", "m4v", "wmv", "flv", "mpeg", "mpg" ->
        RemoteFileKind.VIDEO
    "mp3", "wav", "flac", "aac", "ogg", "m4a", "wma", "opus", "aiff" ->
        RemoteFileKind.AUDIO
    "pdf" -> RemoteFileKind.PDF
    "doc", "docx", "odt", "rtf", "pages" -> RemoteFileKind.DOCUMENT
    "xls", "xlsx", "csv", "ods", "tsv", "numbers" -> RemoteFileKind.SPREADSHEET
    "ppt", "pptx", "odp" -> RemoteFileKind.PRESENTATION
    "zip", "tar", "gz", "bz2", "xz", "7z", "rar", "iso", "tar.gz", "tgz",
    "tar.bz2", "tbz2", "tar.xz", "txz" -> RemoteFileKind.ARCHIVE
    "kt", "kts", "java", "py", "js", "mjs", "cjs", "ts", "jsx", "tsx", "c", "cc",
    "cpp", "h", "hpp", "rs", "go", "rb", "php", "swift", "cs", "json", "xml",
    "yaml", "yml", "gradle", "cmake", "sql" -> RemoteFileKind.CODE
    "sh", "bash", "zsh", "fish", "ps1", "bat", "cmd" -> RemoteFileKind.SCRIPT
    "txt", "md", "markdown", "log", "rst" -> RemoteFileKind.TEXT
    "html", "htm", "css", "scss", "sass" -> RemoteFileKind.WEB
    "ttf", "otf", "woff", "woff2", "eot" -> RemoteFileKind.FONT
    "exe", "app", "bin", "so", "dylib", "dll", "apk", "aab", "deb", "rpm" ->
        RemoteFileKind.EXECUTABLE
    "pem", "pub", "p12", "pfx", "crt", "cer", "asc", "key" -> RemoteFileKind.KEY
    "db", "sqlite", "sqlite3" -> RemoteFileKind.DATABASE
    "conf", "config", "ini", "toml", "env", "properties", "cfg" -> RemoteFileKind.CONFIG
    else -> RemoteFileKind.GENERIC
}
