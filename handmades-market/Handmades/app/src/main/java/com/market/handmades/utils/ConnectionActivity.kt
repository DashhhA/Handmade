package com.market.handmades.utils

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.market.handmades.R
import com.market.handmades.remote.Connection
import com.market.handmades.remote.ConnectionService

/**
 * Activity, that binds and unbinds self to a ConnectionService, and holds a connection or shows
 * error on connection error
 */
abstract class ConnectionActivity: AppCompatActivity() {
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val connectionBinder = service as ConnectionService.ConnectionBinder
            val connection = connectionBinder.getConnection()
            if (connection == null) {
                AlertDialog.Builder(this@ConnectionActivity)
                        .setTitle(R.string.title_error)
                        .setMessage(R.string.no_connection)
                        .setPositiveButton(R.string.button_positive) { _, _ -> }
                        .setCancelable(false)
                        .show()
            } else {
                onConnection(connection)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            // TODO handle service disconnection
        }
    }

    companion object: ConnectionObject()

    fun onConnection(connection: Connection) {
        ConnectionActivity.onConnection(connection)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Bind to the service
        Intent(this, ConnectionService::class.java).also { intent ->
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // Unbind from the service
        unbindService(serviceConnection)
    }
}