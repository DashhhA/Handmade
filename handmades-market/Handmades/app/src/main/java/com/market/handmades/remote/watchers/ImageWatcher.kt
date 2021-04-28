package com.market.handmades.remote.watchers

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.market.handmades.remote.Connection
import com.market.handmades.remote.FileStream
import com.market.handmades.utils.AsyncResult

/**
 * Receives an image, and matches the watcher interface
 */
class ImageWatcher(fileStream: FileStream, name: String): IWatcher<FileStream.FileDescription> {
    private val data: MutableLiveData<AsyncResult<FileStream.FileDescription>> = MutableLiveData()
    private var open = true
    init {
        fileStream.getFile(name) { res ->
            if (open) data.postValue(res)
        }
    }
    override fun getData(): LiveData<AsyncResult<FileStream.FileDescription>> {
        return data
    }

    override suspend fun close(): AsyncResult<Boolean> {
        open = false
        return AsyncResult.Success(true)
    }
}