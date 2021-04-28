package com.market.handmades.remote.watchers

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import com.market.handmades.utils.AsyncResult
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * Watcher, that transforms given result
 */
class TransformWatcher <T, R> (
        base: IWatcher<T>,
        transform: (T) -> IWatcher<R>
): IWatcher<R> {
    private val state: MediatorLiveData<AsyncResult<R>> = MediatorLiveData()
    private var watcher: IWatcher<R>? = null
    init {
        state.addSource(base.getData()) { res ->
            when(res) {
                is AsyncResult.Error -> {
                    state.value = res
                }
                is AsyncResult.Success -> {
                    val toClose = watcher
                    watcher = transform(res.data)
                    state.addSource(watcher!!.getData()) { state.postValue(it) }
                    GlobalScope.launch {
                        toClose?.close()
                    }
                }
            }
        }
    }

    override suspend fun close(): AsyncResult<Boolean> {
        return watcher?.close() ?: AsyncResult.Success(true)
    }

    override fun getData(): LiveData<AsyncResult<R>> {
        val v = state.value
        print(v)
        return state
    }

    fun manualUpdate(upd: R) {
        state.postValue(AsyncResult.Success(upd))
    }
}