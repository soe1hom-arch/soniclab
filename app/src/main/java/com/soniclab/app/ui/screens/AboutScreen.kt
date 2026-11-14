/*
 * Copyright 2026 soe1hom-arch
 * SPDX-License-Identifier: Apache-2.0
 */

package com.soniclab.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.Copyright
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.OfflineBolt
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.soniclab.app.BuildConfig
import com.soniclab.app.R
import com.soniclab.app.ui.common.SonicCard
import com.soniclab.app.ui.theme.PurpleAccent

private const val REPOSITORY_URL = "https://github.com/soe1hom-arch/soniclab"
private const val DEVELOPER_URL = "https://github.com/soe1hom-arch"
private const val DONATE_URL = "https://github.com/soe1hom-arch/soniclab/blob/main/DONATE.md"

@Composable
fun AboutScreen(onBack: () -> Unit, onOpenLicenses: () -> Unit = {}) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Kembali")
            }
            Text(
                "Tentang Aplikasi",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        }

        HeroSection()

        AboutSection("Fitur Unggulan") {
            AboutRow(
                Icons.Rounded.GraphicEq,
                "Pemutar & DSP real-time",
                "Pemutaran lokal, equalizer, tone, reverb, dan efek spasial"
            )
            AboutRow(
                Icons.Rounded.Info,
                "Toolkit audio",
                "Analisis, normalisasi, pengaturan pitch/tempo, dan vocal remover stereo"
            )
            AboutRow(
                Icons.Rounded.OfflineBolt,
                "Privasi & pemrosesan lokal",
                "Aplikasi tidak meminta izin internet; audio diproses di perangkat"
            )
        }

        AboutSection("Informasi Aplikasi") {
            AboutRow(
                Icons.Rounded.Description,
                "Lisensi aplikasi",
                "Apache License 2.0 — kode sumber terbuka"
            )
            AboutRow(
                Icons.Rounded.Copyright,
                "Lisensi pihak ketiga",
                "Lihat lisensi dan atribusi tiap komponen",
                onClick = onOpenLicenses
            )
            AboutRow(
                Icons.Rounded.Code,
                "Kode sumber",
                "github.com/soe1hom-arch/soniclab",
                onClick = { openUrl(context, REPOSITORY_URL) }
            )
        }

        AboutSection("Tentang Developer") {
            AboutRow(
                Icons.Rounded.Person,
                "soe1hom-arch",
                "Pengembang SonicLab",
                onClick = { openUrl(context, DEVELOPER_URL) }
            )
            Text(
                "SonicLab adalah proyek independen untuk menghadirkan pemutar musik dan alat audio " +
                    "yang praktis, dengan fokus pada pemrosesan lokal. Masukan dan laporan masalah " +
                    "dapat disampaikan melalui repositori GitHub.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        Button(
            onClick = { openUrl(context, DONATE_URL) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Icon(Icons.Rounded.Coffee, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("Traktir developer secangkir kopi")
            Spacer(Modifier.size(4.dp))
            Icon(Icons.Rounded.Favorite, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        Text(
            "Dukungan bersifat sukarela dan tidak membuka fitur berbayar.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(20.dp))
        Text(
            "Versi ${BuildConfig.VERSION_NAME} · Build ${BuildConfig.VERSION_CODE}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun HeroSection() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF140A2E), Color(0xFF3D1B96), PurpleAccent))),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = "Logo SonicLab",
                modifier = Modifier.size(76.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        Text("SonicLab", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "Versi ${BuildConfig.VERSION_NAME} · Build ${BuildConfig.VERSION_CODE}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Pemutar musik dan toolkit audio untuk koleksi lokal Anda.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun AboutSection(title: String, content: @Composable () -> Unit) {
    SonicCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(vertical = 12.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            content()
        }
    }
}

@Composable
private fun AboutRow(
    icon: ImageVector,
    title: String,
    subtitle: String = "",
    onClick: (() -> Unit)? = null
) {
    val content: @Composable RowScope.() -> Unit = {
        Icon(icon, contentDescription = null, tint = PurpleAccent, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 6.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (onClick != null) {
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = "Buka $title",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    if (onClick != null) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    } else {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: Exception) {
        // No browser available; ignore silently.
    }
}
