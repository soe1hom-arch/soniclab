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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Copyright
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.OfflineBolt
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.soniclab.app.ui.theme.PurpleAccent

private const val GITHUB_URL = "https://github.com/soe1hom-arch/soniclab"

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

        Spacer(Modifier.height(16.dp))

        AboutSection("Fitur Unggulan") {
            AboutRow(
                Icons.Rounded.GraphicEq,
                "DSP real-time",
                "Equalizer, 3D/8D, reverb, pitch & balance"
            )
            AboutRow(
                Icons.Rounded.AutoAwesome,
                "AI on-device",
                "Enhancer & pemisah vokal tanpa internet"
            )
            AboutRow(
                Icons.Rounded.OfflineBolt,
                "Fokus privasi",
                "Semua proses di perangkat, tanpa data dikirim"
            )
        }

        AboutSection("Informasi Aplikasi") {
            AboutRow(Icons.Rounded.Info, "Versi", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            AboutRow(Icons.Rounded.Description, "Lisensi", "Apache-2.0 — open source")
            AboutRow(
                Icons.Rounded.Copyright,
                "Kredit pihak ketiga",
                "Media3, Oboe, TensorFlow Lite, AndroidX, Kotlin — Apache-2.0",
                onClick = onOpenLicenses
            )
            AboutRow(
                Icons.Rounded.Code,
                "Kode sumber",
                "github.com/soe1hom-arch/soniclab",
                onClick = { openUrl(context, GITHUB_URL) }
            )
        }

        AboutSection("Tentang Developer") {
            AboutRow(
                Icons.Rounded.Person,
                "soe1hom-arch",
                "Developer Android — Kotlin, Compose, Media3"
            )
            Text(
                "Proyek pribadi yang dibuat dengan fokus pada kualitas suara, privasi, " +
                    "dan pengalaman premium. Umpan balik sangat dihargai — silakan buka " +
                    "issue di repositori GitHub.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "SonicLab v${BuildConfig.VERSION_NAME} · Apache-2.0",
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
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
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
                contentDescription = null,
                modifier = Modifier.size(76.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        Text("SonicLab", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "Versi ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Pemutar musik & toolkit audio premium, sepenuhnya offline.\n" +
                "Tanpa iklan, tanpa akun, tanpa internet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun AboutSection(title: String, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
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
                contentDescription = null,
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
