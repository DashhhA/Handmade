package com.market.handmades.remote.watchers

import androidx.lifecycle.LiveData
import com.market.handmades.utils.AsyncResult

interface IWatcher <T> {
    fun getData(): LiveData<AsyncResult<T>>
    suspend fun close(): AsyncResult<Boolean>
}