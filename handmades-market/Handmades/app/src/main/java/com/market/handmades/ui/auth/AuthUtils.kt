package com.market.handmades.ui.auth

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.view.ViewGroup
import com.market.handmades.AdminActivity
import com.market.handmades.CustomerActivity
import com.market.handmades.R
import com.market.handmades.VendorActivity
import com.market.handmades.model.User
import com.market.handmades.remote.Connection
import com.market.handmades.remote.ServerRequest
import com.market.handmades.ui.TintedProgressBar
import java.lang.Exception

object AuthUtils {
    /**
     * Tries to login with given auth data, showing progress bar and an error message on failure.
     * Starts an activity, corresponding to user type on success
     * @param pass Password
     * @param email Email
     * @param rootView View, where to show progress bar
     * @param context Context for dialog construction
     * @param connection Connection to perform request
     * @param onAuth Function to call before starting activity (e. g. Finish auth activity)
     */
    fun logIn(
            pass: String, email: String,
            rootView: ViewGroup, context: Context, connection: Connection,
            onAuth: (() -> Unit)? = null
    ) {
        val request = ServerRequest.AuthUser(
                ServerRequest.LoginRequest(email, pass)
        )
        val progressBar = TintedProgressBar(context, rootView)
        progressBar.show()

        connection.requestServer(request) { result ->
            progressBar.hide()
            progressBar.remove()
            val ans = result.getOrShowError(context) ?: return@requestServer

            if (ans.success) {
                if (onAuth != null) { onAuth() }

                val userType = try {
                    ans.data.get("type").asString
                } catch (e: Exception) {
                    AlertDialog.Builder(context)
                            .setTitle(R.string.title_failure)
                            .setMessage(R.string.error_wrong_response)
                            .setPositiveButton(R.string.button_positive) { _, _, ->}
                            .show()
                    return@requestServer
                }
                launchByUserType(context, User.UserType.fromString(userType))
            } else {
                AlertDialog.Builder(context)
                        .setTitle(R.string.title_failure)
                        .setMessage(R.string.wrong_password)
                        .setPositiveButton(R.string.button_positive) { _, _, ->}
                        .show()
            }
        }
    }

    fun launchByUserType(context: Context, userType: User.UserType) {
        when(userType) {
            User.UserType.Customer -> {
                val intent = Intent(context, CustomerActivity::class.java)
                context.startActivity(intent)
            }
            User.UserType.Vendor -> {
                val intent = Intent(context, VendorActivity::class.java)
                context.startActivity(intent)
            }
            User.UserType.Admin -> {
                val intent = Intent(context, AdminActivity::class.java)
                context.startActivity(intent)
            }
            User.UserType.Moderator -> {
                //TODO
                // start activity for moderator
            }
        }
    }
}