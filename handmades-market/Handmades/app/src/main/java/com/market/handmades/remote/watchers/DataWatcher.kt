package com.market.handmades.remote.watchers

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSyntaxException
import com.market.handmades.remote.Connection
import com.market.handmades.remote.ServerMessage
import com.market.handmades.remote.ServerRequest
import com.market.handmades.utils.AsyncResult
import java.lang.IllegalStateException
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Class, responsible for listening about a model state on server
 * @param request Contains data about the model to listen
 */
class DataWatcher (
        private val request: ServerRequest.WatcherRequest,
        private val connection: Connection
) {
    private val data: MutableLiveData<AsyncResult<ModelUpdate>> = MutableLiveData()
    private val listeners: MutableList<IOnDataChangeListener> = mutableListOf()
    val id: String
        get() {
            return request.id
        }

    fun interface IOnDataChangeListener {
        fun onChange(updated: AsyncResult<ModelUpdate>)
    }

    fun onChange(data: AsyncResult<ServerMessage>) {
        fun performUpdate(result: AsyncResult<ModelUpdate>) {
            this.data.postValue(result)
            listeners.forEach { it.onChange(result) }
        }
        when(data) {
            is AsyncResult.Error -> performUpdate(data)
            is AsyncResult.Success -> {
                val update = try {
                    AsyncResult.Success(ModelUpdate(data.data))
                } catch (e: JsonSyntaxException) {
                    AsyncResult.Error(e)
                } catch (e: IllegalStateException) {
                    AsyncResult.Error(e)
                }
                performUpdate(update)
            }
        }
    }

    fun getData(): LiveData<AsyncResult<ModelUpdate>> {
        return data
    }

    fun addOnChangeListener(listener: IOnDataChangeListener) {
        listeners.add(listener)
    }

    suspend fun close(): AsyncResult<ServerMessage> {
        return suspendCoroutine { continuation ->
            val request = ServerRequest.UnwatchModel(request.id)
            connection.requestServer(request) { result: AsyncResult<ServerMessage> ->
                connection.removeWatcher(this)
                continuation.resume(result)
                listeners.clear()
            }
        }
    }

    class ModelUpdate(
            message: ServerMessage,
    ) {
        val event: String = message.data.get("event").asString
        val updated: JsonElement? = when(event) {
            "delete" -> null
            else -> {
                val upd = message.data.get("updated")
                when {
                    upd.isJsonArray -> upd.asJsonArray
                    upd.isJsonObject -> upd.asJsonObject
                    upd.isJsonPrimitive -> upd.asJsonPrimitive
                    else -> throw IllegalStateException("Not a JSON Element")
                }
            }
        }
    }
}