package com.market.handmades.remote.watchers

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.market.handmades.utils.AsyncResult
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import java.lang.Exception

/**
 * Controls list of object watchers
 */
class ObjectArrayWatcher <T>(
        private val watchers: List<ObjectWatcher<T>>
): IWatcher<List<T>> {
    private val state: MutableLiveData<AsyncResult<List<T>>> = MutableLiveData()
    private val values: MutableMap<ObjectWatcher<T>, T?> = watchers.map { it to null }
            .toMap()
            .toMutableMap()
    init {
        for (watcher in watchers) {
            watcher.addOnChangeListener { value ->
                when (value) {
                    is AsyncResult.Error -> {
                        onError(watcher, value.exception)
                    }
                    is AsyncResult.Success -> when(value.data) {
                        is ObjectWatcher.Change.Delete -> onError(watcher, Exception("Element deleted"))
                        is ObjectWatcher.Change.Update -> onUpdate(watcher, value.data.element)
                    }
                }
            }
        }
    }

    override suspend fun close(): AsyncResult<Boolean> {
        val allClosed = watchers.map { watcher -> GlobalScope.async {
            watcher.close()
        } }.awaitAll()
        allClosed.forEach { if (it is AsyncResult.Error) return it }
        return AsyncResult.Success(true)
    }

    override fun getData(): LiveData<AsyncResult<List<T>>> {
        return state
    }

    private fun onUpdate(key: ObjectWatcher<T>, value: T) {
        values[key] = value
        if (values.all { it.value != null })
            state.value = AsyncResult.Success(values.map { it.value!! })
    }

    fun onError(key: ObjectWatcher<T>, err: Exception) {
        values[key] = null
        state.value = AsyncResult.Error(err)
    }
}