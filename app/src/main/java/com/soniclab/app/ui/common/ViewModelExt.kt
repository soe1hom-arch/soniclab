/*
 * Copyright 2026 soe1hom-arch
 * SPDX-License-Identifier: Apache-2.0
 */

package com.soniclab.app.ui.common

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import com.soniclab.app.SonicLabApp
import com.soniclab.app.di.AppContainer

/**
 * Resolves a ViewModel built from the app's [AppContainer] without a DI framework.
 */
@Composable
inline fun <reified VM : ViewModel> appViewModel(
    crossinline create: (AppContainer) -> VM
): VM {
    val factory = viewModelFactory {
        initializer {
            val app = this[APPLICATION_KEY] as SonicLabApp
            create(app.container)
        }
    }
    return viewModel(factory = factory)
}
