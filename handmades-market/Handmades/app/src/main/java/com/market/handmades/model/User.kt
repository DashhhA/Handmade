package com.market.handmades.model

import com.google.gson.*
import com.market.handmades.remote.*
import com.market.handmades.remote.watchers.DataWatcher
import com.market.handmades.remote.watchers.ObjectWatcher
import com.market.handmades.utils.AsyncResult
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class User(
    val fName: String,
    val sName: String? = null,
    val surName: String? = null,
    val login: String,
    val userType: UserType,
    val modelId: String,
    val dbId: String,
) {
    sealed class UserType(
            val dbName: String
    ) {
        object Customer: UserType("customer")
        object Vendor: UserType("vendor")
        object Admin: UserType("admin")
        object Moderator: UserType("moderator")
        object UnknownType: Exception("Unknown user type")

        override fun toString(): String {
            return dbName
        }
    }

    sealed class ChangableFieds(propertyName: String, update: JsonPrimitive?){
        val json = JsonObject()
        init {
            json.add(propertyName, update)
        }
        class fName(newVal: String):
                ChangableFieds("fName", JsonPrimitive(newVal))
        class sName(newVal: String?):
                ChangableFieds("sName", if(newVal != null) JsonPrimitive(newVal) else null)
        class surName(newVal: String?):
                ChangableFieds("surName", if(newVal != null) JsonPrimitive(newVal) else null)
        class login(newVal: String):
                ChangableFieds("login", JsonPrimitive(newVal))
    }

    constructor(dto: UserDTO): this(
        dto.fName,
        dto.sName,
        dto.surName,
        dto.login,
        when(dto.userType) {
            "customer" -> UserType.Customer
            "vendor" -> UserType.Vendor
            "admin" -> UserType.Admin
            "moderator" -> UserType.Moderator
            else -> throw UserType.UnknownType
        },
        dto.modelId,
        dto.dbId
    )

    /**
     * Formats user name full
     */
    fun nameLong(): String {
        val name = "$fName ${sName ?: ""} ${surName ?: ""}"
        return name.replace("\\s+".toRegex(), " ").trimEnd()
    }
}

// Data transmission object for remote database
data class UserDTO(
        var fName: String = "",
        var sName: String? = null,
        var surName: String? = null,
        var login: String = "",
        var userType: String = "",
        var modelId: String = "",
        var dbId: String = "",
)

// Object, send for user registration
data class UserRegistrationDTO(
        val fName: String,
        val sName: String? = null,
        val surName: String? = null,
        val login: String,
        val password: String,
        val userType: String
)

class StaticData(dto: UserDTO) {
    val dbId = dto.dbId
    val modelId = dto.modelId
    val login = dto.login
}

class UserRepository(
        private val connection: Connection
) {
    private val gson = Gson()
    private var staticData: StaticData? = null
    private val idListeners: MutableList<Continuation<StaticData>> = mutableListOf()
    /**
     * Registers new user
     * @return AsyncResult.Success(true) if registered, AsyncResult.Error if not
     */
    suspend fun newUser(user: UserRegistrationDTO): AsyncResult<Boolean> {
        return suspendCoroutine { continuation ->
            connection.requestServer(ServerRequest.NewUser(user)) { result ->
                continuation.resume(result.transform { message: ServerMessage ->
                    message.success
                })
            }
        }
    }

    suspend fun staticData(): StaticData {
        return suspendCoroutine { continuation ->
            if (staticData != null) continuation.resume(staticData!!)
            else {
                GlobalScope.launch { getCurrentUser() }
                idListeners.add(continuation)
            }
        }
    }

    suspend fun getCurrentUser(): AsyncResult<User> {
        return suspendCoroutine { continuation ->
            connection.requestServer(ServerRequest.CurrentUser()) { result ->
                val userMessage: JsonObject = when(result) {
                    is AsyncResult.Error -> {
                        continuation.resume(result)
                        return@requestServer
                    }
                    is AsyncResult.Success -> {
                        result.data.data
                    }
                }

                try {
                    val userRaw = userMessage.get("user")
                    val userDTO: UserDTO = gson.fromJson(userRaw, UserDTO::class.java)
                    val user = User(userDTO)
                    gotUserId(userDTO)
                    continuation.resume(AsyncResult.Success(user))
                } catch (e: JsonSyntaxException) {
                    continuation.resume(AsyncResult.Error(e))
                    return@requestServer
                }catch (e: User.UserType.UnknownType) {
                    continuation.resume(AsyncResult.Error(e))
                    return@requestServer
                }
            }
        }
    }

    /**
     * Authorization
     * @return AsyncResult.Success(true) if authorized, AsyncResult.Success(false) if wrong password
     * AsyncResult.Error if error
     */
    suspend fun auth(login: String, password: String): AsyncResult<Boolean> {
        return suspendCoroutine { continuation ->
            val req = ServerRequest.LoginRequest(login, password)
            connection.requestServer(ServerRequest.AuthUser(req)) { result ->
                continuation.resume(result.transform { message: ServerMessage ->
                    message.success
                })
            }
        }
    }

    fun watchUser(id: String): UserWatcher {
        val req = ServerRequest.WatcherRequest.WatchModel(ServerRequest.ModelType.User(id))
        val watcher = connection.watch(req)
        return UserWatcher(watcher, gson)
    }

    /**
     * Removes currently signed in user
     * @return AsyncResult.Success(true) if removed, AsyncResult.Error if not
     */
    suspend fun removeUser(): AsyncResult<Boolean> {
        return suspendCoroutine { continuation ->
            connection.requestServer(ServerRequest.RemoveUser()) { result ->
                continuation.resume(result.transform { message: ServerMessage ->
                    message.success
                })
            }
        }
    }

    suspend fun changeField(id: String, field: User.ChangableFieds): AsyncResult<Boolean> {
        val req = ServerRequest.EditItem(
                ServerRequest.ModelType.User(id),
                field.json
        )
        return suspendCoroutine { continuation ->
            connection.requestServer(req) { res ->
                continuation.resume(res.transform { it.success })
            }
        }
    }

    private fun gotUserId(dto: UserDTO) {
        staticData = StaticData(dto)
        idListeners.forEach { continuation -> continuation.resume(staticData!!) }
        idListeners.clear()
    }

    inner class UserWatcher(
            watcher: DataWatcher,
            private val gson: Gson
    ): ObjectWatcher<User>(watcher) {
        override fun decode(json: JsonElement): User {
            val userDTO = gson.fromJson(json, UserDTO::class.java)
            return User(userDTO)
        }
    }
}