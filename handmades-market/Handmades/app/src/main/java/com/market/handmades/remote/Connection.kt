package com.market.handmades.remote

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonIOException
import com.market.handmades.R
import com.market.handmades.remote.watchers.DataWatcher
import com.market.handmades.utils.AsyncResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Class, responsible of establishing a connection to server, sending and receiving data
 * through session
 */
//const val SERVER_HOST = "192.168.1.15"
//const val SERVER_HOST = "10.0.2.2"
//const val SERVER_HOST = "192.168.0.103"
const val SERVER_HOST = "20.82.177.236"
const val SERVER_PORT = 8042
const val FILES_PORT = 8043
class Connection(
        context: Context,
        onError: JSONStreamReader.IOnConnectionError? = null
) {
    private val responseHandler: JSONStreamReader
    private val listeners: MutableMap<String, IOnDataListener> = mutableMapOf()
    private val watchers: MutableMap<String, DataWatcher> = mutableMapOf()
    private val gson = Gson()
    private val responseListener = JSONStreamReader.IOnResponseListener { result ->
        when (result) {
            is AsyncResult.Error -> {
                // TODO handle error
                throw result.exception
            }
            is AsyncResult.Success -> onDataReceived(result.data)
        }
    }
    val fileStream: FileStream
    init {
        responseHandler = JSONStreamReader(
            HostDescription(SERVER_HOST, SERVER_PORT, R.raw.ca),
            context,
            responseListener,
            onError
        )
        fileStream = FileStream(
                HostDescription(SERVER_HOST, FILES_PORT, R.raw.ca),
                context,
                onError
        )
    }

    fun interface IOnDataListener {
        fun onData(data: AsyncResult<ServerMessage>)
    }

    /**
     * Sends a request to server and sets a listener for response
     */
    fun requestServer(request: ServerRequest, listener: IOnDataListener) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val data = gson.toJson(request)
                listeners[request.id] = listener
                val exception: IOException? = responseHandler.writeToServer(data)
                if (exception != null) {
                    listeners.remove(request.id)
                    throw exception
                }
            } catch (exception: JsonIOException) {
                listener.onData(AsyncResult.Error(exception))
            } catch (exception: NullPointerException) {
                listener.onData(AsyncResult.Error(exception))
            }
        }
    }

    suspend fun requestServer(request: ServerRequest): AsyncResult<ServerMessage> {
        return suspendCoroutine { continuation ->
            requestServer(request) { res -> continuation.resume(res) }
        }
    }

    /**
     * Sends a request to server without attaching a listener (for watchers)
     */
    private fun requestServer(request: ServerRequest, watcher: DataWatcher) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val data = gson.toJson(request)
                watchers[request.id] = watcher
                val exception: IOException? = responseHandler.writeToServer(data)
                if (exception != null) {
                    watchers.remove(request.id)
                    throw exception
                }
            } catch (exception: JsonIOException) {
                watcher.onChange(AsyncResult.Error(exception))
            } catch (exception: JsonIOException) {
                watcher.onChange(AsyncResult.Error(exception))
            }
        }
    }

    fun removeWatcher(watcher: DataWatcher) {
        watchers.remove(watcher.id)
    }

    /**
     * Sends a request to server to listen to model updates
     */
    fun watch(request: ServerRequest.WatcherRequest): DataWatcher {
        val watcher = DataWatcher(request, this)
        requestServer(request, watcher)
        return watcher
    }

    /**
     * Called when data received and passes the result to a callback, if there is a listener
     * for server response
     * @param data Pre-parsed server response
     */
    private fun onDataReceived(data: JSONStreamReader.ServerResponse) {
        // parsing the server response to an object or error
        val result = ServerMessage.fromResponse(data)
        if (listeners.containsKey(data.id)) {
            GlobalScope.launch(Dispatchers.Main) {
                listeners[data.id]!!.onData(result)
                listeners.remove(data.id)
            }
        } else if (watchers.containsKey(data.id)) {
            watchers[data.id]!!.onChange(result)
        }
    }
}