package com.sshpeaches.app.service

import android.content.Context
import android.os.Build
import java.io.File
import java.io.IOException

internal object MoshRuntime {

    private const val ASSET_ROOT = "mosh"
    private const val RUNTIME_VERSION = 1

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
        val marker = File(rootDir, ".ready-v$RUNTIME_VERSION")
        if (!marker.exists()) {
            rootDir.deleteRecursively()
            if (!rootDir.mkdirs() && !rootDir.exists()) {
                throw IOException("Failed to create mosh runtime directory: ${rootDir.absolutePath}")
            }
            copyAssetTree(context, "$ASSET_ROOT/$abi", rootDir)
            val client = File(rootDir, "bin/mosh-client")
            if (!client.setExecutable(true, true)) {
                client.setExecutable(true, false)
            }
            marker.writeText("ok")
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
