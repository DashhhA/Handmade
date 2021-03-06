package com.market.handmades.remote.watchers

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.Observer
import com.market.handmades.utils.AsyncResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Listens for single update, then closes self. In fact gets the current state without updates
 */
class SingleUpdateWatcher<T>(
        private val watcher: IWatcher<T>
): IWatcher<T> {
    private val data = MediatorLiveData<AsyncResult<T>>()
    init {
        val source = watcher.getData()
        data.addSource(source) { res ->
            data.value = res
            GlobalScope.launch(Dispatchers.IO) { watcher.close() }
            data.removeSource(source)
        }
    }

    override fun getData(): LiveData<AsyncResult<T>> {
        return data
    }

    override suspend fun close(): AsyncResult<Boolean> {
        withContext(Dispatchers.Main) { data.removeSource(watcher.getData()) }
        return watcher.close()
    }
}