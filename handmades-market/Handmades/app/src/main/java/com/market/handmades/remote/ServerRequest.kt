package com.market.handmades.remote

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.market.handmades.CustomerActivity
import com.market.handmades.CustomerViewModel
import com.market.handmades.R
import com.market.handmades.model.*
import java.util.*

sealed class ServerRequest (
    val type: String,
    val id: String,
    val body: Any
) {
    class NewUser(body: UserRegistrationDTO):
            ServerRequest("new_user", UUID.randomUUID().toString(), body)

    class AuthUser(body: LoginRequest):
            ServerRequest("auth_user", UUID.randomUUID().toString(), body)

    class Logout:
            ServerRequest("logout", UUID.randomUUID().toString(), JsonObject())

    class RemoveUser:
            ServerRequest("remove_user", UUID.randomUUID().toString(), JsonObject())

    sealed class WatcherRequest(type: String, body: Any):
            ServerRequest(type, UUID.randomUUID().toString(), body) {

        class WatchModel(modelType: ModelType):
                WatcherRequest("watch_model", modelType)

        class WatchList(model: ModelType, props: String):
                WatcherRequest("watch_list", WatchListBody(model.model, WatchListBody.ListPath(model.id, props)))

        class WatchCollection(model: ModelType):
                WatcherRequest("watch_list", WatchListBody(model.model))

        class WatchChat(chatId: String):
                WatcherRequest("watch_chat", WatchChatBody(chatId))

        class WatchChats:
                WatcherRequest("watch_chats", JsonObject())

        class WatchComments:
                WatcherRequest("watch_comments", JsonObject())
    }

    class UnwatchModel(id: String):
            ServerRequest("unwatch_model", UUID.randomUUID().toString(), UnwatchReqBody(id))

    class CurrentUser:
            ServerRequest("current", UUID.randomUUID().toString(), JsonObject())

    class AddMarket(
        name: String,
        city: String,
        description: String,
        tags: List<String>,
        imageUri: String? = null
    ):
            ServerRequest(
                "add_market",
                UUID.randomUUID().toString(),
                AddMarketBody(name, city, description, tags, imageUri)
            )

    class NewChat(type: Chat.ChatType, users: List<User>):
            ServerRequest("new_chat", UUID.randomUUID().toString(), NewChatBody(type.toString(), users.map { it.login }))

    class AddProduct(dto: ProductDTO):
            ServerRequest(
                    "add_product",
                    UUID.randomUUID().toString(),
                    AddProductBody(
                        dto.marketId,
                        dto.name,
                        dto.description,
                        dto.price,
                        dto.quantity,
                        dto.photoUrls,
                        dto.tag,
                    )
            )

    class EditItem(modelType: ModelType, update: JsonObject):
            ServerRequest("edit_item", UUID.randomUUID().toString(), EditItemBody(modelType.id, modelType.model, update))

    class Message(text: String, chatId: String, time: String):
            ServerRequest("message", UUID.randomUUID().toString(), MessageBody(text, chatId, time))

    class AddOrder(
        orderData: CustomerViewModel.OrderData,
        time: String,
        address: String,
        paymentType: Order.PaymentType,
        deliveryType: Order.DeliveryType,
        comment: String? = null,
        packing: Boolean,
        urgent: Boolean,
    ):
        ServerRequest(
            "make_purchase",
            UUID.randomUUID().toString(),
            NewOrderBody(
                orderData.products.map { NewOrderBody.Product(it.key.code, it.value) },
                time,
                address,
                paymentType.toString(),
                deliveryType.toString(),
                comment,
                packing,
                urgent,
            )
        )

    class AvailableAdmins:
            ServerRequest("available_admins", UUID.randomUUID().toString(), JsonObject())

    data class LoginRequest(
            val login: String,
            val password: String
    )

    sealed class ModelType(
            val model: String,
            val id: String
    ) {
        class User(id: String): ModelType(model, id) {
            companion object { const val model = "user"}
        }
        class Customer(id: String): ModelType(model, id) {
            companion object { const val model = "customer"}
        }
        class Vendor(id: String): ModelType(model, id) {
            companion object { const val model = "vendor" }
        }
        class Order(id: String): ModelType(model, id) {
            companion object { const val model = "order" }
        }
        open class Market(id: String): ModelType("market", id) {
            object NoId: ModelType.Market("")
        }
        open class Product(id: String): ModelType("product", id) {
            object NoId: ModelType.Product("")
        }

        open class Message(id: String): ModelType("message", id) {
            object NoId: ModelType.Message("")
        }
    }

    private data class UnwatchReqBody(val id: String)
    private data class WatchListBody(val model: String, val path: ListPath? = null) {
        data class ListPath(val id: String, val props: String)
    }
    private data class EditItemBody(val id: String, val path: String, val update: JsonObject)
    private data class AddMarketBody(
        val name: String,
        val city: String,
        val description: String,
        val tags: List<String>,
        val imageUrl: String? = null,
    )
    private data class MessageBody(val text: String, val chatId: String, val time: String)
    private data class WatchChatBody(val chatId: String)
    private data class NewChatBody(val type: String, val users: List<String>)
    private data class AddProductBody(
            val marketId: String,
            val name: String,
            val description: String,
            val price: Float,
            val quantity: Int,
            val photoUrls: List<String>,
            val tag: String?,
    )
    private data class NewOrderBody(
        val products: List<Product>,
        val time: String,
        val address: String,
        val paymentType: String,
        val deliveryType: String,
        val comment: String? = null,
        val packing: Boolean,
        val urgent: Boolean,
    ) {
        data class Product(val code: String, val quantity: Int)
    }
}