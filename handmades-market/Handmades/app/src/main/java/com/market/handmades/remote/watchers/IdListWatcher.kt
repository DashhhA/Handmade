package com.market.handmades.remote.watchers

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.market.handmades.utils.AsyncResult
import kotlinx.coroutines.*
import java.lang.Exception

/**
 * Class, watching a list of ids, and providing a lis of ObjectWatchers for this id's
 * @see ObjectWatcher
 * @param watcher Watcher for the list of id's
 * @param getElement Function to get the element from repository by id
 */
class IdListWatcher <T> (
        private val watcher: ListWatcher<String>,
        private val getElement: suspend (String) -> IWatcher<T>
): IWatcher<List<T>> {
    private var updates = 0
    private val watcherListener = object : ListWatcher.IOnChangeListener<String> {
        override fun refresh(els: List<String>) {
            val keys = elements.keys
            val new = els - keys
            val removed = keys - els
            new.forEach { newElement(it) }
            removed.forEach { elementDeleted(it) }
            updates += new.size + removed.size
            if (updates == 0) update()
        }

        override fun insert(el: String) {
            newElement(el)
            updates += 1
        }

        override fun error(exception: Exception) {
            this@IdListWatcher.error(exception)
        }

    }
    private val elements: MutableMap<String, T> = mutableMapOf()
    // map from id's to objects, listening to element state
    private val dbListeners: MutableMap<String, ObserveData> = mutableMapOf()
    private val state: MutableLiveData<AsyncResult<List<T>>> = MutableLiveData()
    init {
        watcher.addOnChangeListener(watcherListener)
    }

    override suspend fun close(): AsyncResult<Boolean> {
        val watcherClosed = watcher.close()
        val listenersClosed = dbListeners.map { el -> GlobalScope.async { el.value.close() } }.awaitAll()
        if (watcherClosed is AsyncResult.Error) return watcherClosed
        listenersClosed.forEach { if (it is AsyncResult.Error) return it }
        return AsyncResult.Success(true)
    }

    override fun getData(): LiveData<AsyncResult<List<T>>> {
        return state
    }

    private fun update() {
        state.postValue(AsyncResult.Success(elements.values.toList()))
    }

    private fun error(err: Exception) {
        state.value = AsyncResult.Error(err)
    }

    private fun update(key: String, value: T) {
        elements[key] = value
        updates -= 1
        if (updates < 1) update()
    }

    private fun remove(key: String) {
        elements.remove(key)
        update()
    }

    /**
     * Called when new element occurs in list of id's
     */
    private fun newElement(key: String) {
        GlobalScope.launch {
            val watcher = getElement(key)
            val data = watcher.getData()
            val observer = Observer<AsyncResult<T>> { res ->
                when(res) {
                    is AsyncResult.Error -> error(res.exception)
                    is AsyncResult.Success -> update(key, res.data)
                }
            }
            withContext(Dispatchers.Main) {
                data.observeForever(observer)
                dbListeners[key] = ObserveData(watcher, data, observer)
            }
        }
    }

    /**
     * Called when element removed from list of id's
     */
    private fun elementDeleted(key: String) {
        GlobalScope.launch(Dispatchers.IO) {
            dbListeners[key]?.close()
            withContext(Dispatchers.Main) {  remove(key) }
        }
    }

    private inner class ObserveData(
            private val watcher: IWatcher<T>,
            private val data: LiveData<AsyncResult<T>>,
            private val observer: Observer<AsyncResult<T>>,
    ) {
        suspend fun close(): AsyncResult<Boolean> {
            withContext(Dispatchers.Main) { data.removeObserver(observer) }
            return watcher.close()
        }
    }
}