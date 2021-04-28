package com.market.handmades.remote

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder

/**
 * Service, holding the connection to server, independent from the activities
 */
class ConnectionService : Service() {
    private val binder: ConnectionBinder by lazy {
        ConnectionBinder()
    }

    /**
     * Class, providing access to the connection for service clients
     */
    inner class ConnectionBinder: Binder() {
        private var connectionError: Throwable? = null
        private val connection = Connection(this@ConnectionService) { throwable ->
            connectionError = throwable
        }

        /**
         * @return Connection or null, if Connection throwed an error
         */
        fun getConnection(): Connection? {
            if (connectionError != null) return null
            return connection
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }
}