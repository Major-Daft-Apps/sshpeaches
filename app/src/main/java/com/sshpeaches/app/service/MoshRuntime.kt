package com.majordaftapps.sshpeaches.app.service

import android.content.Context
import android.os.Build
import java.io.File
import java.io.IOException
import java.security.MessageDigest

internal object MoshRuntime {

    private const val ASSET_ROOT = "mosh"
    private const val RUNTIME_MARKER_VERSION = 2

    data class Prepared(
        val abi: String,
        val rootDir: File,
        val clientBinary: File,
        val libDir: File,
        val terminfoDir: File
    )

    @Synchronized
    fun prepare(context: Context): Prepared {
        val abi = resolveSupportedAbi()
            ?: throw IllegalStateException("No supported ABI found for bundled mosh runtime.")
        val rootDir = File(context.filesDir, "mosh-runtime/$abi")
        val marker = File(rootDir, ".ready-v$RUNTIME_MARKER_VERSION")
        val expectedMarkerValue = buildMarkerValue(context, abi)
        val currentMarkerValue = marker.takeIf { it.exists() }?.readText()
        if (currentMarkerValue != expectedMarkerValue) {
            rootDir.deleteRecursively()
            if (!rootDir.mkdirs() && !rootDir.exists()) {
                throw IOException("Failed to create mosh runtime directory: ${rootDir.absolutePath}")
            }
            copyAssetTree(context, "$ASSET_ROOT/$abi", rootDir)
            val client = File(rootDir, "bin/mosh-client")
            if (!client.setExecutable(true, true)) {
                client.setExecutable(true, false)
            }
            marker.writeText(expectedMarkerValue)
        }
        val clientBinary = File(rootDir, "bin/mosh-client")
        val libDir = File(rootDir, "lib")
        val terminfoDir = File(rootDir, "share/terminfo")
        if (!clientBinary.exists()) {
            throw IOException("Bundled mosh client is missing for ABI $abi.")
        }
        return Prepared(
            abi = abi,
            rootDir = rootDir,
            clientBinary = clientBinary,
            libDir = libDir,
            terminfoDir = terminfoDir
        )
    }

    private fun resolveSupportedAbi(): String? {
        val supported = Build.SUPPORTED_ABIS ?: emptyArray()
        return supported.firstOrNull { it == "arm64-v8a" || it == "x86_64" }
    }

    private fun buildMarkerValue(context: Context, abi: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update("marker-version:$RUNTIME_MARKER_VERSION\n".toByteArray())
        updateAssetDigest(context, digest, "$ASSET_ROOT/$abi")
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun updateAssetDigest(context: Context, digest: MessageDigest, assetPath: String) {
        val children = (context.assets.list(assetPath) ?: emptyArray()).sorted()
        if (children.isEmpty()) {
            digest.update("file:$assetPath\n".toByteArray())
            context.assets.open(assetPath).use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return
        }
        digest.update("dir:$assetPath\n".toByteArray())
        children.forEach { child ->
            updateAssetDigest(context, digest, "$assetPath/$child")
        }
    }

    private fun copyAssetTree(context: Context, assetPath: String, target: File) {
        val children = context.assets.list(assetPath) ?: emptyArray()
        if (children.isEmpty()) {
            target.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input ->
                target.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            return
        }
        if (!target.exists()) {
            target.mkdirs()
        }
        children.forEach { child ->
            copyAssetTree(
                context = context,
                assetPath = "$assetPath/$child",
                target = File(target, child)
            )
        }
    }
}
