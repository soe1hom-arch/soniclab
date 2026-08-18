/*
 * Copyright 2026 soe1hom-arch
 * SPDX-License-Identifier: Apache-2.0
 */

package com.soniclab.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.soniclab.app.ui.navigation.SonicLabAppRoot
import com.soniclab.app.ui.theme.SonicLabTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as SonicLabApp).container
        setContent {
            val amoled by container.settingsRepository.amoledTheme.collectAsStateWithLifecycle(initialValue = false)
            SonicLabTheme(amoled = amoled) {
                SonicLabAppRoot(container)
            }
        }
    }
}
