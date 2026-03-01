package com.majordaftapps.sshpeaches.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.json.JSONArray

private data class BundledNotice(
    val component: String,
    val license: String,
    val note: String,
    val projectUrl: String? = null
)

private data class MavenLicense(
    val name: String,
    val url: String?
)

private data class MavenNotice(
    val coordinate: String,
    val displayName: String,
    val projectUrl: String?,
    val licenses: List<MavenLicense>
)

@Composable
fun OpenSourceLicensesScreen() {
    val context = LocalContext.current
    val bundledNotices = remember {
        listOf(
            BundledNotice(
                component = "SSHPeaches",
                license = "GPL-3.0",
                note = "Project source is licensed under GNU GPL v3."
            ),
            BundledNotice(
                component = "Bundled mosh runtime (from Termux packages)",
                license = "GPL-3.0 + component licenses",
                note = "Includes mosh, abseil-cpp, libc++, openssl, protobuf, ncurses, zlib, and libandroid-support for app-bundled runtime.",
                projectUrl = "https://packages.termux.dev/"
            )
        )
    }
    val mavenNotices = remember {
        runCatching {
            val raw = context.assets.open("licenses/maven_licenses.json")
                .bufferedReader()
                .use { it.readText() }
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val obj = array.optJSONObject(index) ?: continue
                    val licenseArray = obj.optJSONArray("licenses")
                    val licenses = buildList {
                        if (licenseArray != null) {
                            for (licenseIndex in 0 until licenseArray.length()) {
                                val licenseObj = licenseArray.optJSONObject(licenseIndex) ?: continue
                                add(
                                    MavenLicense(
                                        name = licenseObj.optString("name").ifBlank { "Unknown" },
                                        url = licenseObj.optString("url").takeIf { it.isNotBlank() }
                                    )
                                )
                            }
                        }
                        if (isEmpty()) add(MavenLicense(name = "Unknown", url = null))
                    }
                    add(
                        MavenNotice(
                            coordinate = obj.optString("coordinate"),
                            displayName = obj.optString("displayName").ifBlank {
                                obj.optString("coordinate")
                            },
                            projectUrl = obj.optString("projectUrl").takeIf { it.isNotBlank() },
                            licenses = licenses
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                text = "Open Source License Notices",
                style = MaterialTheme.typography.titleLarge
            )
        }
        item {
            Text(
                text = "This screen includes bundled runtime notices and all resolved Maven runtime dependencies.",
                style = MaterialTheme.typography.bodySmall
            )
        }
        item {
            Text(
                text = "Bundled Runtime Notices",
                style = MaterialTheme.typography.titleMedium
            )
        }
        items(bundledNotices) { notice ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(notice.component, style = MaterialTheme.typography.titleMedium)
                    Text("License: ${notice.license}", style = MaterialTheme.typography.labelMedium)
                    Text(notice.note, style = MaterialTheme.typography.bodySmall)
                    notice.projectUrl?.let { link ->
                        Text(
                            text = link,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.clickable {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
                            }
                        )
                    }
                }
            }
        }
        item {
            Text(
                text = "Maven Runtime Dependencies (${mavenNotices.size})",
                style = MaterialTheme.typography.titleMedium
            )
        }
        if (mavenNotices.isEmpty()) {
            item {
                Text(
                    text = "No generated Maven license inventory found.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        } else {
            items(mavenNotices, key = { it.coordinate }) { notice ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(notice.displayName, style = MaterialTheme.typography.titleSmall)
                        Text(notice.coordinate, style = MaterialTheme.typography.bodySmall)
                        notice.licenses.forEach { license ->
                            Text(
                                text = "License: ${license.name}",
                                style = MaterialTheme.typography.labelSmall
                            )
                            license.url?.let { link ->
                                Text(
                                    text = link,
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.clickable {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
                                    }
                                )
                            }
                        }
                        notice.projectUrl?.let { link ->
                            Text(
                                text = "Project: $link",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.clickable {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
