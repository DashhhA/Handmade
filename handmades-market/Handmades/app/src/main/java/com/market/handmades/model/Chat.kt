package com.market.handmades.model

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.market.handmades.remote.Connection
import com.market.handmades.remote.ServerRequest
import com.market.handmades.remote.watchers.*
import com.market.handmades.utils.AsyncResult
import java.text.SimpleDateFormat
import java.util.*

class Chat(
    val users: List<User>,
    val type: ChatType,
    val recent: Recent?,
    val dbId: String,
) {
    constructor(
        users: List<User>,
        type: String,
        recent: ChatDTO.ChatRecent?,
        dbId: String
    ): this(
        users,
        ChatType.fromString(type),
        if (recent != null) Recent(
            users.find { user -> user.dbId == recent.user }!!, Message(recent.message, "")
        ) else null,
        dbId
    )
    data class Recent(val user: User, val message: Message)
    sealed class ChatType(protected val str: String) {
        override fun toString(): String {
            return str
        }
        object Private: ChatType("private")
        object Comments: ChatType("comments")
        companion object {
            @Throws(IllegalArgumentException::class)
            fun fromString(str: String): ChatType {
                return when (str) {
                    Private.str -> Private
                    Comments.str -> Comments
                    else -> throw IllegalArgumentException("Unexpected chat type: $str")
                }
            }
        }
    }
}

class Message(
        val time: Date,
        val from: String,
        val chat: String,
        val body: String,
        val read: Boolean,
        val deleted: String?,
        val dbId: String,
) {
    constructor(dto: MessageDTO, id: String): this(
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
                    .apply { timeZone = TimeZone.getTimeZone("UTC") }
                    .parse(dto.time)!!,
            dto.from,
            dto.chat,
            dto.body,
            dto.read,
            dto.deleted,
            id
    )
}

data class MessageDTO(
        var time: String = "",
        var from: String = "",
        var chat: String = "",
        var body: String = "",
        var read: Boolean = false,
        var deleted: String? = null
)

data class ChatDTO(
    var users: List<String> = listOf(),
    var type: String = "",
    var recent: ChatRecent? = null
) {
    data class ChatRecent(
        var message: MessageDTO = MessageDTO(),
        var user: String = "",
    )
}

class ChatRepository(
    private val connection: Connection,
    private val userRepository: UserRepository,
) {
    private val gson = Gson()
    suspend fun newMessage(chatId: String, message: String, date: Date): AsyncResult<Boolean> {
        val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS z", Locale.US)
        val time = df.format(date)
        val req = ServerRequest.Message(message, chatId, time)
        val res = connection.requestServer(req)
        return res.transform { it.success }
    }

    fun watchChat(id: String): CollectionWatcher<Message> {
        val req = ServerRequest.WatcherRequest.WatchChat(id)
        val watcher = connection.watch(req)
        return object : CollectionWatcher<Message>(watcher) {
            override fun decode(json: JsonObject, key: String): Message {
                val dto = this@ChatRepository.gson.fromJson(json, MessageDTO::class.java)
                return Message(dto, key)
            }
        }
    }

    fun watchComments(): CollectionWatcher<Message> {
        val req = ServerRequest.WatcherRequest.WatchComments()
        val watcher = connection.watch(req)
        return object : CollectionWatcher<Message>(watcher) {
            override fun decode(json: JsonObject, key: String): Message {
                val dto = this@ChatRepository.gson.fromJson(json, MessageDTO::class.java)
                return Message(dto, key)
            }
        }
    }

    suspend fun newChat(type: Chat.ChatType, users: List<User>): AsyncResult<NewChatAnswer> {
        val res = connection.requestServer(ServerRequest.NewChat(type, users))
        return res.transform { NewChatAnswer(it.data.get("data").asJsonObject.get("chatId").asString) }
    }

    /**
     * Sets message as read
     * @param id Message id
     */
    suspend fun setRead(id: String): AsyncResult<Boolean> {
        val json = JsonObject()
        json.add("read", JsonPrimitive(true))
        val req = ServerRequest.EditItem(ServerRequest.ModelType.Message(id), json)
        val res = connection.requestServer(req)
        return res.transform { it.success }
    }

    /**
     * Watches chats of authorised the user
     */
    fun watchChats(): IWatcher<List<Chat>> {
        val rawWatcher = connection.watch(ServerRequest.WatcherRequest.WatchChats())
        val dtoWatcher = object : CollectionWatcher<DTOwID>(rawWatcher) {
            override fun decode(json: JsonObject, key: String): DTOwID {
                return DTOwID(gson.fromJson(json, ChatDTO::class.java), key)
            }
        }

        return TransformWatcher(dtoWatcher) { dtoList ->
            val userWatchers = dtoList.map { dto ->
                dto to (dto.dto.users.map { userId ->
                    userRepository.watchUser(userId)
                })
            }.toMap()
            val userObjectWatchers = userWatchers.mapValues {
                val arrayWatcher = ObjectArrayWatcher(it.value)
                ProjectWatcher(arrayWatcher) { users ->
                    Chat(
                        users,
                        it.key.dto.type,
                        it.key.dto.recent,
                        it.key.id
                    )
                }
            }

            return@TransformWatcher ObjectArrayWatcher(userObjectWatchers.values.toList())
        }
    }

    suspend fun deleteMessage(id: String, reason: String): AsyncResult<Boolean> {
        val update = JsonObject()
        update.add("deleted", JsonPrimitive(reason))
        val req = ServerRequest.EditItem(ServerRequest.ModelType.Message(id), update)

        return connection.requestServer(req).transform { it.success }
    }

    private data class DTOwID(val dto: ChatDTO, val id: String)
    data class NewChatAnswer(val chatId: String)
}