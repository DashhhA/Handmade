package com.market.handmades.utils

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import com.market.handmades.R
import com.market.handmades.remote.ServerMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class AsyncResult<out R> {
    data class Success<out T>(val data: T) : AsyncResult<T>() {
        override fun <N> transform(transformation: (T) -> N): AsyncResult<N> {
            return Success(transformation(data))
        }
    }
    data class Error(val exception: Exception) : AsyncResult<Nothing>() {
        override fun <N> transform(transformation: (Nothing) -> N): AsyncResult<N> {
            return this
        }
    }

    fun getOrShowError(context: Context): R? {
        return when (this) {
            is Error -> {
                if (context is Activity)
                    if (context.isFinishing) return null
                val message = when(this.exception) {
                    is ServerMessage.LocalizedException -> this.exception.getLocalizedMessage(context)
                    else -> context.getString(com.market.handmades.R.string.error_internal)
                }
                GlobalScope.launch (Dispatchers.Main) {
                    AlertDialog.Builder(context)
                            .setTitle(com.market.handmades.R.string.title_error)
                            .setMessage(message)
                            .setPositiveButton(com.market.handmades.R.string.button_positive) { _, _ -> }
                            .show()
                }
                null
            }
            is Success -> this.data
        }
    }

    /**
     * Applies transformation to data if result is Success, passes error else
     */
    abstract fun <N> transform(transformation: (R) -> N): AsyncResult<N>
}