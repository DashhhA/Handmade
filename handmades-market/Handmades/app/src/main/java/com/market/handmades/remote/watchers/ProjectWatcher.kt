package com.market.handmades.remote.watchers

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import com.market.handmades.utils.AsyncResult

class ProjectWatcher<T, R>(
    private val base: IWatcher<T>,
    transform: (T) -> R,
): IWatcher<R> {
    private val state: MediatorLiveData<AsyncResult<R>> = MediatorLiveData()
    init {
        state.addSource(base.getData()) { res ->
            when(res) {
                is AsyncResult.Error -> state.value = res
                is AsyncResult.Success -> state.value = res.transform(transform)
            }
        }
    }

    override fun getData(): LiveData<AsyncResult<R>> {
        return state
    }

    override suspend fun close(): AsyncResult<Boolean> {
        return base.close()
    }
}