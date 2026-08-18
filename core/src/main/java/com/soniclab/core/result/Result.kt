/*
 * Copyright 2026 soe1hom-arch
 * SPDX-License-Identifier: Apache-2.0
 */

package com.soniclab.core.result

/**
 * Simple result wrapper used by repositories and toolkit operations.
 */
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val message: String, val cause: Throwable? = null) : Result<Nothing>()

    val isSuccess: Boolean get() = this is Success
    val dataOrNull: T? get() = (this as? Success)?.data
}

fun <T> Result<T>.onSuccess(block: (T) -> Unit): Result<T> {
    if (this is Result.Success) block(data)
    return this
}

fun <T> Result<T>.onError(block: (String, Throwable?) -> Unit): Result<T> {
    if (this is Result.Error) block(message, cause)
    return this
}
