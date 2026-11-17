/*
 * Copyright 2026 soe1hom-arch
 * SPDX-License-Identifier: Apache-2.0
 */

package com.soniclab.app.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.Copyright
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.OfflineBolt
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.soniclab.app.BuildConfig
import com.soniclab.app.R

private const val REPOSITORY_URL = "https://github.com/soe1hom-arch/soniclab"
private const val DEVELOPER_URL = "https://github.com/soe1hom-arch"
private const val DONATE_URL = "https://github.com/soe1hom-arch/soniclab/blob/main/DONATE.md"

@Composable
fun AboutScreen(onBack: () -> Unit, onOpenLicenses: () -> Unit = {}) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Kembali", tint = MaterialTheme.colorScheme.onBackground)
            }
            Text(
                "Tentang Aplikasi",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        HeroSection()

        AboutSection("FITUR UNGGULAN") {
            AboutRow(
                Icons.Rounded.GraphicEq,
                "Pemutar & DSP real-time",
                "Pemutaran lokal, equalizer, tone, reverb, dan efek spasial"
            )
            AboutRow(
                Icons.Rounded.Info,
                "Toolkit audio",
                "Analisis, normalisasi, pitch/tempo, dan vocal remover stereo"
            )
            AboutRow(
                Icons.Rounded.OfflineBolt,
                "Privasi & pemrosesan lokal",
                "Aplikasi tidak meminta izin internet; audio diproses di perangkat"
            )
        }

        AboutSection("INFORMASI APLIKASI") {
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
                external = true,
                onClick = { openUrl(context, REPOSITORY_URL) }
            )
        }

        AboutSection("TENTANG DEVELOPER") {
            DeveloperProfile(
                onOpenProfile = { openUrl(context, DEVELOPER_URL) },
                onDonate = { openUrl(context, DONATE_URL) }
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun HeroSection() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = "Logo SonicLab",
                modifier = Modifier.size(68.dp),
                tint = Color.Unspecified
            )
        }
        Spacer(Modifier.height(10.dp))
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = CircleShape) {
            Text(
                "AUDIO, REFINED",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text("SonicLab", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Text(
            "Versi ${BuildConfig.VERSION_NAME} · Build ${BuildConfig.VERSION_CODE}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Musik lokal. Kendali suara yang lebih personal.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Pemutar musik dan toolkit audio yang dirancang untuk bekerja langsung di perangkat Anda.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun AboutSection(title: String, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 0.dp
    ) {
        Column(Modifier.padding(vertical = 14.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            )
            content()
        }
    }
}

@Composable
private fun AboutRow(
    icon: ImageVector,
    title: String,
    subtitle: String = "",
    external: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val rowContent: @Composable RowScope.() -> Unit = {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 6.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
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
                if (external) Icons.Rounded.OpenInNew else Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }

    if (onClick != null) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 18.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = rowContent
        )
    } else {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = rowContent
        )
    }
}

@Composable
private fun DeveloperProfile(onOpenProfile: () -> Unit, onDonate: () -> Unit) {
    Column(Modifier.padding(horizontal = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(
                    Icons.Rounded.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(12.dp).size(24.dp)
                )
            }
            Column(Modifier.padding(start = 12.dp)) {
                Text("soe1hom-arch", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text("Pengembang SonicLab", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            "SonicLab adalah proyek independen untuk menghadirkan pemutar musik dan alat audio " +
                "yang praktis, dengan fokus pada pemrosesan lokal. Masukan dan laporan masalah " +
                "dapat disampaikan melalui repositori GitHub.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
        )
        OutlinedButton(onClick = onOpenProfile, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.Code, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text("Profil GitHub")
            Spacer(Modifier.size(8.dp))
            Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        OutlinedButton(onClick = onDonate, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.Coffee, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text("Dukung pengembang")
            Spacer(Modifier.size(8.dp))
            Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        Text(
            "Dukungan bersifat sukarela dan tidak membuka fitur berbayar.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
        )
    }
}

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "Tidak ada aplikasi untuk membuka tautan", Toast.LENGTH_SHORT).show()
    }
}
