package com.market.handmades.model

import android.content.Context
import androidx.lifecycle.Observer
import com.google.gson.*
import com.market.handmades.R
import com.market.handmades.remote.*
import com.market.handmades.remote.watchers.DataWatcher
import com.market.handmades.remote.watchers.ObjectWatcher
import com.market.handmades.remote.watchers.SingleUpdateWatcher
import com.market.handmades.utils.AsyncResult
import kotlinx.coroutines.Dispatchers
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
    private val dto: UserDTO,
) {
    sealed class UserType(
            val dbName: String,
            val uiStrId: Int,
    ) {
        companion object {
            fun fromString(str: String): UserType {
                return when(str) {
                    Customer.dbName -> Customer
                    Vendor.dbName -> Vendor
                    Admin.dbName -> Admin
                    Moderator.dbName -> Moderator
                    else -> throw UnknownType
                }
            }
        }

        object Customer: UserType("customer", R.string.user_role_customer)
        object Vendor: UserType("vendor", R.string.user_role_vendor)
        object Admin: UserType("admin", R.string.user_role_admin)
        object Moderator: UserType("moderator", R.string.user_role_moderator)
        object UnknownType: Exception("Unknown user type")

        override fun toString(): String {
            return dbName
        }
    }

    sealed class ChangableFields(propertyName: String, update: JsonPrimitive?){
        val json = JsonObject()
        init {
            json.add(propertyName, update)
        }
        class fName(newVal: String):
                ChangableFields("fName", JsonPrimitive(newVal))
        class sName(newVal: String?):
                ChangableFields("sName", if(newVal != null) JsonPrimitive(newVal) else null)
        class surName(newVal: String?):
                ChangableFields("surName", if(newVal != null) JsonPrimitive(newVal) else null)
        class login(newVal: String):
                ChangableFields("login", JsonPrimitive(newVal))
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
        dto.dbId,
        dto
    )

    fun asDTO(): UserDTO {
        return dto
    }

    /**
     * Formats user name full
     */
    fun nameLong(): String {
        val name = "$fName ${sName ?: ""} ${surName ?: ""}"
        return name.replace("\\s+".toRegex(), " ").trimEnd()
    }

    /**
     * Forms user name with role
     */
    fun strRole(context: Context): String {
        val role = context.getString(userType.uiStrId)
        return context.getString(R.string.user_role_form, nameLong(), role)
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

    suspend fun getUser(id: String): AsyncResult<User> {
        val sWatcher = SingleUpdateWatcher(watchUser(id))
        return suspendCoroutine { continuation ->
            var resumed = false
            GlobalScope.launch(Dispatchers.Main) {
                val lData = sWatcher.getData()
                val observer = object : Observer<AsyncResult<User>> {
                    override fun onChanged(t: AsyncResult<User>?) {
                        lData.removeObserver(this)
                        if (!resumed) continuation.resume(t!!)
                        resumed = true
                    }
                }
                lData.observeForever(observer)
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

    suspend fun changeField(id: String, field: User.ChangableFields): AsyncResult<Boolean> {
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

    suspend fun getAvailableAdmins(): AsyncResult<List<User>> {
        val req = ServerRequest.AvailableAdmins()
        val res = connection.requestServer(req)

        return res.transform { raw ->
            val rawList = raw.data.get("data").asJsonArray
            return@transform rawList.map { json ->
                val dto = gson.fromJson(json, UserDTO::class.java)
                return@map User(dto)
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