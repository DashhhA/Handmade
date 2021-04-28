package com.market.handmades.remote.watchers

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import com.market.handmades.utils.AsyncResult
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

/**
 * Combines results from two Watchers
 */
open class MediatorWatcher <T, K, R>(
        private val watcher1: IWatcher<T>,
        private val watcher2: IWatcher<K>,
        transform: (T, K) -> R
): IWatcher<R> {
    private val state = merge(watcher1.getData(), watcher2.getData(), transform)

    override suspend fun close(): AsyncResult<Boolean> {
        val closed = listOf(
                GlobalScope.async { watcher1.close() },
                GlobalScope.async { watcher2.close() }
        ).awaitAll()
        closed.forEach { if (it is AsyncResult.Error) return it }
        return AsyncResult.Success(true)
    }

    override fun getData(): LiveData<AsyncResult<R>> {
        return state
    }
    /**
     * Combines results of two live data into one
     */
    private fun <T, K, R> merge(
            data1: LiveData<AsyncResult<T>>,
            data2: LiveData<AsyncResult<K>>,
            transform: (T, K) -> R
    ): LiveData<AsyncResult<R>> {
        val mediator: MediatorLiveData<AsyncResult<R>> = MediatorLiveData()
        var value1: T? = null
        var value2: K? = null
        mediator.addSource(data1) { res ->
            when (res) {
                is AsyncResult.Error -> {
                    value1 = null
                    mediator.value = res
                }
                is AsyncResult.Success -> {
                    value1 = res.data
                    if (value2 != null)
                        mediator.value = AsyncResult.Success(transform(value1!!, value2!!))
                }
            }
        }
        mediator.addSource(data2) { res ->
            when (res) {
                is AsyncResult.Error -> {
                    value2 = null
                    mediator.value = res
                }
                is AsyncResult.Success -> {
                    value2 = res.data
                    if (value1 != null)
                        mediator.value = AsyncResult.Success(transform(value1!!, value2!!))
                }
            }
        }

        return mediator
    }
}