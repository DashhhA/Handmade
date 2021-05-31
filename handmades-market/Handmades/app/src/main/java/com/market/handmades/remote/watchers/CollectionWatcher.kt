package com.market.handmades.remote.watchers

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.gson.JsonObject
import com.google.gson.JsonSyntaxException
import com.market.handmades.utils.AsyncResult
import java.lang.Exception

abstract class CollectionWatcher <T> (
        private val watcher: DataWatcher,
): IWatcher<List<T>> {
    private val listeners: MutableList<IOnChangeListener<T>> = mutableListOf()
    private val state: MutableLiveData<AsyncResult<List<T>>> = MutableLiveData()
    private val elements: MutableMap<String, T> = mutableMapOf()
    private val watcherListener = DataWatcher.IOnDataChangeListener { updated ->
        when (updated) {
            is AsyncResult.Error -> respError(updated.exception)
            is AsyncResult.Success -> try {
                when (updated.data.event) {
                    "refresh" -> {
                        val arr = updated.data.updated?.asJsonArray
                                ?: throw JsonSyntaxException("'updated' must be defined on refresh")
                        val upd: MutableMap<String, T> = mutableMapOf()
                        arr.forEach {
                            val (k, v) = decodeWithId(it.asJsonObject)
                            upd[k] = v
                        }
                        respRefresh(upd)
                    }
                    "insert" -> {
                        val raw: JsonObject = updated.data.updated?.asJsonObject
                                ?: throw JsonSyntaxException("'updated' must be defined on insert")
                        val (k, v) = decodeWithId(raw)
                        respInsert(k, v)
                    }
                    "update" -> {
                        val raw: JsonObject = updated.data.updated?.asJsonObject
                                ?: throw JsonSyntaxException("'updated' must be defined on insert")
                        val (k, v) = decodeWithId(raw)
                        respUpdate(k, v)
                    }
                    "delete" -> {
                        val raw = updated.data.updated?.asJsonObject
                                ?: throw JsonSyntaxException("'updated' must be defined on remove")
                        val id = raw.get("dbId").asString
                        respDelete(id)
                    }
                }
            } catch (exception: JsonSyntaxException) {
                respError(exception)
            } catch (exception: IllegalStateException) {
                respError(exception)
            }
        }
    }
    init {
        watcher.addOnChangeListener(watcherListener)
    }

    interface IOnChangeListener<T> {
        fun insert(key: String, obj: T)
        fun delete(key: String)
        fun update(key: String, obj: T)
        fun refresh(all: Map<String, T>)
        fun error(exception: Exception)
    }

    @Throws(JsonSyntaxException::class)
    abstract fun decode(json: JsonObject, key: String): T

    fun addOnChangeListener(listener: IOnChangeListener<T>) {
        listeners.add(listener)
    }

    override suspend fun close(): AsyncResult<Boolean> {
        return when(val res = watcher.close()) {
            is AsyncResult.Error -> res
            is AsyncResult.Success -> AsyncResult.Success(res.data.success)
        }
    }

    override fun getData(): LiveData<AsyncResult<List<T>>> {
        return state
    }

    @Throws(JsonSyntaxException::class)
    private fun decodeWithId(data: JsonObject): Pair<String, T> {
        val id: String
        if (data.has("dbId")) {
            id = data.getAsJsonPrimitive("dbId").asString
            data.remove("dbId")
        } else {
            id = data.getAsJsonPrimitive("_id").asString
        }
        return id to decode(data, id)
    }

    private fun respInsert(key: String, obj: T) {
        listeners.forEach { it.insert(key, obj) }
        elements[key] = obj
        state.postValue(AsyncResult.Success(elements.values.toList()))
    }

    private fun respUpdate(key: String, obj: T) {
        listeners.forEach { it.update(key, obj) }
        elements[key] = obj
        state.postValue(AsyncResult.Success(elements.values.toList()))
    }

    private fun respDelete(key: String) {
        listeners.forEach { it.delete(key) }
        elements.remove(key)
        state.postValue(AsyncResult.Success(elements.values.toList()))
    }

    private fun respRefresh(map: Map<String, T>) {
        listeners.forEach { it.refresh(map) }
        elements.clear()
        elements.putAll(map)
        state.postValue(AsyncResult.Success(map.values.toList()))
    }

    private fun respError(exception: Exception) {
        listeners.forEach { it.error(exception) }
        state.postValue(AsyncResult.Error(exception))
    }
}