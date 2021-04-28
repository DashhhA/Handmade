package com.market.handmades.remote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.core.graphics.drawable.toBitmap
import androidx.test.platform.app.InstrumentationRegistry
import com.market.handmades.R
import com.market.handmades.utils.AsyncResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import java.io.ByteArrayOutputStream

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class Files {
    private val appContext: Context
    private val connection: Connection
    init {
        appContext = InstrumentationRegistry.getInstrumentation().targetContext
        connection = Connection(appContext)
    }

    @Test
    fun aPutFileOnServer() {
        runBlocking {
            val drawable = appContext.getDrawable(R.drawable.left_pattern)!!
            val ans = connection.fileStream.putFile(FileStream.FileDescription("test.png", drawable))
            Assert.assertTrue(ans is AsyncResult.Success)
        }

        runBlocking {
            val ans = connection.fileStream.getFile("test.png")
            Assert.assertTrue(ans is AsyncResult.Success)
            val descr = (ans as AsyncResult.Success).data
            val image = descr.bitmap
            print(image)
        }
    }

    @Test
    fun bPutUri() {
        runBlocking {
            val ans = connection.fileStream.getFile("f.png")
            Assert.assertTrue(ans is AsyncResult.Success)
            val descr = (ans as AsyncResult.Success).data
            val image = descr.bitmap
            print(image)
        }
    }
}