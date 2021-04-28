package com.market.handmades.remote.watchers

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSyntaxException
import com.market.handmades.utils.AsyncResult
import java.lang.Exception

abstract class ListWatcher <T> (
        private val watcher: DataWatcher,
) {
    private val listeners: MutableList<IOnChangeListener<T>> = mutableListOf()
    private val watcherListener = DataWatcher.IOnDataChangeListener { updated ->
        when(updated) {
            is AsyncResult.Error -> respError(updated.exception)
            is AsyncResult.Success -> try {
                when (updated.data.event) {
                    "refresh" -> {
                        val arr = updated.data.updated?.asJsonArray
                                ?: throw JsonSyntaxException("'updated' must be defined on refresh")
                        val upd: MutableList<T> = mutableListOf()
                        arr.forEach { upd.add(decode(it)) }
                        respRefresh(upd)
                    }
                    "insert" -> {
                        val el = updated.data.updated
                                ?: throw JsonSyntaxException("'updated' must be defined on refresh")
                        respInsert(decode(el))
                    }
                    else -> throw JsonSyntaxException("Unexpected update event: ${updated.data.event}")
                }
            } catch (exception: JsonSyntaxException) {
                respError(exception)
            }
        }
    }
    init {
        watcher.addOnChangeListener(watcherListener)
    }

    @Throws(JsonSyntaxException::class)
    abstract fun decode(json: JsonElement): T

    interface IOnChangeListener<T> {
        fun refresh(els: List<T>)
        fun insert(el: T)
        fun error(exception: Exception)
    }

    fun addOnChangeListener(listener: IOnChangeListener<T>) {
        listeners.add(listener)
    }

    suspend fun close(): AsyncResult<Boolean> {
        return when(val res = watcher.close()) {
            is AsyncResult.Error -> res
            is AsyncResult.Success -> AsyncResult.Success(res.data.success)
        }
    }

    private fun respError(exception: Exception) {
        listeners.forEach { it.error(exception) }
    }

    private fun respRefresh(updates: List<T>) {
        listeners.forEach { it.refresh(updates) }
    }

    private fun respInsert(update: T) {
        listeners.forEach { it.insert(update) }
    }
}