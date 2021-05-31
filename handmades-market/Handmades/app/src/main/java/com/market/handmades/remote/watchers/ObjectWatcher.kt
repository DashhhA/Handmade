package com.market.handmades.remote.watchers

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSyntaxException
import com.market.handmades.remote.ServerMessage
import com.market.handmades.utils.AsyncResult
import java.lang.Exception

/**
 * Class, providing access to some watchable value current state (Holds a DataWatcher and transforms
 * it's data)
 * @see DataWatcher
 */
abstract class ObjectWatcher <T> (
        private val watcher: DataWatcher,
): IWatcher<T> {
    private val state = MediatorLiveData<AsyncResult<T>>()
    private val listeners: MutableList<IOnChangeListener<T>> = mutableListOf()
    init {
        state.addSource(watcher.getData()) { res ->
            val result = transform(res)
            state.value = when (result) {
                is AsyncResult.Error -> result
                is AsyncResult.Success -> when(result.data) {
                    is Change.Delete -> AsyncResult.Error(ServerMessage.ObjectDeletedError())
                    is Change.Update -> AsyncResult.Success(result.data.element)
                }
            }
        }
    }
    @Throws(JsonSyntaxException::class)
    abstract fun decode(json: JsonElement): T

    fun interface IOnChangeListener<T> {
        fun onChange(data: AsyncResult<Change<T>>)
    }

    override suspend fun close(): AsyncResult<Boolean> {
        return when(val res = watcher.close()) {
            is AsyncResult.Error -> res
            is AsyncResult.Success -> AsyncResult.Success(res.data.success)
        }
    }

    override fun getData(): LiveData<AsyncResult<T>> {
        return state
    }

    fun addOnChangeListener(listener: IOnChangeListener<T>) {
        listeners.add(listener)
        watcher.addOnChangeListener { result ->
            listener.onChange(transform(result))
        }
    }

    fun manualUpdate(upd: T) {
        state.postValue(AsyncResult.Success(upd))
        listeners.forEach { it.onChange(AsyncResult.Success(Change.Update(upd))) }
    }

    private fun transform(result: AsyncResult<DataWatcher.ModelUpdate>): AsyncResult<Change<T>> {
        return when (result) {
            is AsyncResult.Error -> result
            is AsyncResult.Success -> try {
                val updated: JsonElement? = result.data.updated
                val decoded: T? = if (updated != null){
                    decode(updated)
                } else { null }
                val update = Change.fromUpdate(result.data.event, decoded)
                AsyncResult.Success(update)
            } catch (e: JsonSyntaxException) {
                AsyncResult.Error(e)
            } catch (e: IllegalArgumentException) {
                AsyncResult.Error(e)
            }
        }
    }

    sealed class Change<out T> {
        data class Update <R> (val element: R): Change<R>()
        object Delete : Change<Nothing>()
        companion object {
            @Throws(IllegalArgumentException::class)
            fun <T> fromUpdate(event: String, update: T?): Change<T> {
                return when (event) {
                    "update" -> Update(update!!)
                    "delete" -> Delete
                    else -> throw IllegalArgumentException("Unexpected object update event: $event")
                }
            }
        }
    }
}