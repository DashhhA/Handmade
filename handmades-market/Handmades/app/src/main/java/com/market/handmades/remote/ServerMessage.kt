package com.market.handmades.remote

import android.content.Context
import com.google.gson.JsonObject
import com.market.handmades.R
import com.market.handmades.utils.AsyncResult
import java.lang.Error
import java.lang.Exception

/**
 * Class for representing server responses as objects or errors
 */
class ServerMessage(
    val success: Boolean,
    val data: JsonObject
) {
    companion object {
        fun fromResponse(response: JSONStreamReader.ServerResponse):
                AsyncResult<ServerMessage> {
            // TODO: process all server responses
            when (response.event) {
                "error" -> {
                    val message = response.message.get("message")
                            ?: return AsyncResult.Error(WrongResponseError())
                    val msgStr = message.asString
                    when (msgStr) {
                        "Duplicate key" -> {
                            return AsyncResult.Error(DuplicateKeyError(
                                    response.message.get("data").asJsonObject.get("dupKey").asJsonObject))
                        }
                        "User unauthorized" -> {
                            return AsyncResult.Error(UserUnauthorizedError())
                        }
                        "No such user" -> {
                            return AsyncResult.Error(NoSuchUserError())
                        }
                        "Unknown error" -> {
                            val detailed = response.message.get("data")?.asString
                            return AsyncResult.Error(ServerUnknownError(detailed))
                        }
                        else -> {
                            return AsyncResult.Error(ConnectionUnknownError(msgStr))
                        }
                    }
                }
                "response" -> {
                    val success = response.message.get("success")
                            ?: return AsyncResult.Error(WrongResponseError())

                    return AsyncResult.Success(ServerMessage(
                            success.asBoolean,
                            response.message
                    ))
                }
                "update" -> {
                    if (response.message.has("event") && response.message.has("updated")) {
                        return AsyncResult.Success(ServerMessage(true, response.message))
                    } else {
                        return AsyncResult.Error(WrongResponseError())
                    }
                }
                else -> {
                    return AsyncResult.Error(WrongResponseError())
                }
            }
        }
    }

    class WrongResponseError: LocalizedException("Wrong server response") {
        override fun getLocalizedMessage(context: Context): String {
            return context.getString(R.string.error_wrong_response)
        }

    }

    class DuplicateKeyError(private val dupKey: JsonObject?): LocalizedException("User already exists") {
        override fun getLocalizedMessage(context: Context): String {
            val key = dupKey?.keySet()?.first()
            val localizedKey = when(key) {
                "login" -> context.getString(R.string.dup_key_login)
                else -> null
            }
            val localized = if (localizedKey == null) {
                context.getString(R.string.error_dup_key)
            } else {
                String.format(context.getString(R.string.error_dup_key_key, localizedKey))
            }
            return localized
        }
    }

    class UserUnauthorizedError: LocalizedException("User unauthorized") {
        override fun getLocalizedMessage(context: Context): String {
            return context.getString(R.string.error_user_unauthorized)
        }
    }

    class ServerUnknownError(
            val detailed: String?
    ): LocalizedException("Server unknown error") {
        override fun getLocalizedMessage(context: Context): String {
            return context.getString(R.string.error_unknown_server)
        }
    }

    class ConnectionUnknownError(private val msg: String):
            LocalizedException("Connection unknown error") {
        override fun getLocalizedMessage(context: Context): String {
            return String.format(context.getString(R.string.error_unknown_connection), msg)
        }
    }

    class NoSuchUserError: LocalizedException("No such user") {
        override fun getLocalizedMessage(context: Context): String {
            return context.getString(R.string.error_no_user)
        }
    }

    abstract class LocalizedException(message: String): Exception(message) {
        abstract fun getLocalizedMessage(context: Context): String
    }
}