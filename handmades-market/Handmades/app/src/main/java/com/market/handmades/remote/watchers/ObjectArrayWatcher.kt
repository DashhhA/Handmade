package com.market.handmades.remote.watchers

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.market.handmades.utils.AsyncResult
import kotlinx.coroutines.*
import java.lang.Exception

/**
 * Controls list of object watchers
 */
class ObjectArrayWatcher <T>(
        watchers: List<IWatcher<T>>,
): IWatcher<List<T>> {
    private val state: MutableLiveData<AsyncResult<List<T>>> = MutableLiveData()
    private val values: MutableMap<IWatcher<T>, T?> = watchers.map { it to null }
            .toMap()
            .toMutableMap()
    private val withObservers: List<WatcherDO<T>> = watchers.map { watcher ->
        val observer = Observer<AsyncResult<T>> { value ->
            when (value) {
                is AsyncResult.Error -> {
                    onError(watcher, value.exception)
                }
                is AsyncResult.Success -> onUpdate(watcher, value.data)
            }
        }
        val data = watcher.getData()
        data.observeForever(observer)
        WatcherDO(watcher, data, observer)
    }

    override suspend fun close(): AsyncResult<Boolean> {
        val watchers = withObservers.map { it.watcher }
        withContext(Dispatchers.Main) { withObservers.forEach { it.data.removeObserver(it.observer) } }
        val allClosed = watchers.map { watcher -> GlobalScope.async {
            watcher.close()
        } }.awaitAll()
        allClosed.forEach { if (it is AsyncResult.Error) return it }
        return AsyncResult.Success(true)
    }

    override fun getData(): LiveData<AsyncResult<List<T>>> {
        return state
    }

    private fun onUpdate(key: IWatcher<T>, value: T) {
        values[key] = value
        if (values.all { it.value != null })
            state.postValue(AsyncResult.Success(values.map { it.value!! }))
    }

    fun onError(key: IWatcher<T>, err: Exception) {
        values[key] = null
        state.postValue(AsyncResult.Error(err))
    }

    private data class WatcherDO<T>(
            val watcher: IWatcher<T>,
            val data: LiveData<AsyncResult<T>>,
            val observer: Observer<AsyncResult<T>>
    )
}