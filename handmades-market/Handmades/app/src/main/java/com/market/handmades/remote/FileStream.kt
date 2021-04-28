package com.market.handmades.remote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.toBitmap
import com.market.handmades.utils.AsyncResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.lang.Exception
import java.util.*
import javax.net.ssl.SSLSocket
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class FileStream(
        private val hostDescription: HostDescription,
        context: Context,
        private val onError: JSONStreamReader.IOnConnectionError? = null,
) {
    private val writeQueue: Stack<Pair<FileDescription, IWriteResult>> = Stack()
    private val getQueue: Stack<Pair<String, IGetResult>> = Stack()
    init {
        val exceptionHandler = Thread.UncaughtExceptionHandler { _, throwable, ->
            onError?.onError(throwable)
        }
        // launch a thread, to work with the socket
        val socketThread = Thread {
            val socket: SSLSocket = Utils.initSocket(hostDescription, context)

            val input = socket.inputStream
            val output = socket.outputStream

            val recv = ByteArray(socket.receiveBufferSize)
            fun awaitResp() {
                val c = input.read(recv)
                if (c == 1) return
                if (c > 1) {
                    val msg = recv.decodeToString(0, c)
                    throw IOException(msg)
                }
                if (c < 0) {
                    Thread.sleep(10)
                    awaitResp()
                }
            }
            fun awaitFIleSize(): Int {
                val c = input.read(recv)
                if (c < 0) {
                    Thread.sleep(10)
                    return awaitFIleSize()
                }
                val s = recv.decodeToString(0, c)
                return recv.decodeToString(0, c).toInt()
            }
            fun awaitFile(size: Int): ByteArray {
                val bytes = ByteArray(size)
                var offset = 0
                var  c = 0
                while (offset < size) {
                    c = input.read(bytes, offset, size)
                    offset += c
                }
                val v = size - offset
                return bytes
                // else throw IOException("Error receiving file")
            }
            while (true) {
                while (!writeQueue.empty()) {
                    val (descr, callback) = writeQueue.pop()
                    try {
                        writeStr(output, Req.Put(descr).str)
                        awaitResp()
                        writeBytes(output, descr.data)
                        awaitResp()
                        callback.onResult(AsyncResult.Success(true))
                    } catch (e: Exception) {
                        callback.onResult(AsyncResult.Error(e))
                    }
                }
                while (!getQueue.empty()) {
                    val (name, callback) = getQueue.pop()
                    try {
                        writeStr(output, Req.Get(name).str)
                        val fileSize = awaitFIleSize()
                        val file = awaitFile(fileSize)
                        awaitResp()
                        callback.onResult(AsyncResult.Success(FileDescription(name, file)))
                    } catch (e: Exception) {
                        callback.onResult(AsyncResult.Error(e))
                    }
                }
                Thread.sleep(10)
            }
        }
        socketThread.uncaughtExceptionHandler = exceptionHandler
        socketThread.start()
    }

    suspend fun putFile(descr: FileDescription): AsyncResult<Boolean> {
        return suspendCoroutine { continuation ->
            putFile(descr) { res-> continuation.resume(res) }
        }
    }

    suspend fun getFile(name: String): AsyncResult<FileDescription> {
        return suspendCoroutine { continuation ->
            getFile(name) { res -> continuation.resume(res) }
        }
    }

    fun putFile(descr: FileDescription, listener: IWriteResult) {
        writeQueue.push(Pair(descr, listener))
    }

    fun getFile(name: String, listener: IGetResult) {
        getQueue.push(Pair(name, listener))
    }

    private fun writeBytes(stream: OutputStream, data: ByteArray): IOException? {
        return try {
            stream.write(data)
            null
        } catch (exception: IOException) {
            exception
        }
    }

    @Throws(IOException::class)
    private fun writeStr(stream: OutputStream, str: String) {
        stream.write(str.toByteArray())
    }

    fun interface IWriteResult{
        fun onResult(res: AsyncResult<Boolean>)
    }

    fun interface IGetResult {
        fun onResult(res: AsyncResult<FileDescription>)
    }

    class FileDescription(val name: String, val data: ByteArray) {
        val bitmap by lazy {
            BitmapFactory.decodeByteArray(data, 0, data.size)
        }
        companion object {
            private fun drawableToByteArray(drawable: Drawable): ByteArray {
                val bitmap = drawable.toBitmap()
                val stream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                return  stream.toByteArray()
            }
        }
        constructor(name: String, drawable: Drawable): this(name, drawableToByteArray(drawable))
        constructor(drawable: Drawable): this("${UUID.randomUUID()}.png", drawableToByteArray(drawable))
    }

    private sealed class Req(
            action: String,
            name: String,
            size: Int = 0
    ){
        val str: String = "{\"action\": \"$action\", \"name\": \"$name\", \"size\": $size}"
        class Put(description: FileDescription): Req("save", description.name, description.data.size)
        class Get(name: String): Req("get", name)
    }
}