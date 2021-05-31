package com.market.handmades.remote

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonSyntaxException
import com.market.handmades.R
import com.market.handmades.utils.AsyncResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.*
import java.lang.Exception
import java.net.ConnectException
import java.net.Socket
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.*
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManagerFactory
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Class, listening for an input stream, and executing callbacks, when JSON object received
 * and writing data to the server
 */
class JSONStreamReader(
    private val hostDescription: HostDescription,
    context: Context,
    private var onResponse: IOnResponseListener,
    private val onError: IOnConnectionError? = null,
) {
    private val currentObject: StringBuilder = StringBuilder()
    private var parenthesesBalance = 0
    private val gson = Gson()
    private val writeQueue: Stack<Pair<String, IOnWriteListener>> = Stack()

    data class ServerResponse(
            var id: String = "",
            var event: String = "",
            var message: JsonObject = JsonObject()
    )

    fun interface IOnResponseListener {
        fun onResponse(result: AsyncResult<ServerResponse>)
    }

    fun interface IOnConnectionError {
        fun onError(e: Throwable)
    }

    private fun interface IOnWriteListener {
        fun onWrite(exception: IOException?)
    }

    init {
        val exceptionHandler = Thread.UncaughtExceptionHandler() { _, throwable, ->
            onError?.onError(throwable)
        }
        // launch a thread, to work with the socket
        val socketThread = Thread {
            val socket: SSLSocket = Utils.initSocket(hostDescription, context)

            val input = socket.inputStream
            val output = socket.outputStream

            Thread {
                while (true) {
                    while (!writeQueue.empty()) {
                        val (str, callback) = writeQueue.pop()
                        val exception = writeStr(output, str)
                        callback.onWrite(exception)
                    }
                    Thread.sleep(10)
                }
            }.start()

            val data = ByteArray(socket.receiveBufferSize)
            while (!socket.isClosed) {
                var c = input.read(data)
                // TODO check socket closed by c == 0, and trow event
                // TODO do Thread.sleep to decreace resource consuming
                while (c > 0) {
                    val str = data.decodeToString(0, c)
                    onNewStr(str)
                    c = input.read(data)
                }
                Thread.sleep(10)
            }
        }
        socketThread.uncaughtExceptionHandler = exceptionHandler
        socketThread.start()
    }

    /**
     * Called, when new part of data received
     * @param str received part of the message
     */
    private fun onNewStr(str: String) {
        val openParentheses = str.count { char -> char == '{' }
        val closedParentheses = str.count { char -> char == '}' }
        parenthesesBalance += openParentheses - closedParentheses
        currentObject.append(str)
        if (parenthesesBalance == 0) {
            val rem = parseJSONStr(currentObject.toString())
            currentObject.clear()
            if (rem.isNotBlank()) currentObject.append(rem)
        }
    }

    private fun onJSONText(jsonText: String) {
        try {
            val obj = gson.fromJson(jsonText, ServerResponse::class.java)
            onResponse.onResponse(AsyncResult.Success(obj))
            Log.v("I", jsonText)
        } catch (exception: JsonSyntaxException) {
            onResponse.onResponse(AsyncResult.Error(exception))
        } catch (e: Exception) {
            // TODO find error
            Log.v("E", e.message?:"")
        }
    }

    private fun writeStr(stream: OutputStream, str: String): IOException? {
        return try {
            stream.write(str.toByteArray())
            null
        } catch (exception: IOException) {
            exception
        }
    }

    fun setOnResponseListener(listener: IOnResponseListener) {
        onResponse = listener
    }

    suspend fun writeToServer(str: String): IOException? {
        return suspendCoroutine { continuation ->
            writeQueue.push(str to IOnWriteListener { exception ->
                continuation.resume(exception)
            })
        }
    }

    /**
     * Calls onJSONText for each separate JSON token in passed string, and returns the rest of the
     * string
     * @param str String to parse
     * @return No - JSON remainder
     * @see onJSONText
     */
    private fun parseJSONStr(str: String): String {
        var isString = false
        var isEscape = false
        var balance = 0
        var jsonStr = ""
        for (c in str) {
            if (!isString) {
                if (c == '{') balance += 1
                if (c == '}') balance -= 1
                if (c == '"') isString = true
            } else {
                if (!isEscape) {
                    if (c == '"') isString = false
                    if (c == '\\') isEscape = true
                } else {
                    isEscape = false
                }
            }

            jsonStr += c
            if (balance == 0 && jsonStr.isNotBlank()) {
                onJSONText(jsonStr)
                jsonStr = ""
            }
        }

        return jsonStr
    }
}