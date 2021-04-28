package com.market.handmades.remote

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.market.handmades.model.ProductDTO
import com.market.handmades.model.UserRegistrationDTO
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
    }

    class UnwatchModel(id: String):
            ServerRequest("unwatch_model", UUID.randomUUID().toString(), UnwatchReqBody(id))

    class CurrentUser:
            ServerRequest("current", UUID.randomUUID().toString(), JsonObject())

    class AddMarket(name: String, description: String, imageUri: String? = null):
            ServerRequest("add_market", UUID.randomUUID().toString(), AddMarketBody(name, description, imageUri))

    class AddProduct(dto: ProductDTO):
            ServerRequest(
                    "add_product",
                    UUID.randomUUID().toString(),
                    AddProductBody(dto.marketId, dto.name, dto.description, dto.price, dto.quantity, dto.photoUrls)
            )

    class EditItem(modelType: ModelType, update: JsonObject):
            ServerRequest("edit_item", UUID.randomUUID().toString(), EditItemBody(modelType.id, modelType.model, update))

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
        class Market(id: String): ModelType(model, id) {
            companion object { const val model = "market" }
        }
        class Product(id: String): ModelType(model, id) {
            companion object { const val model = "product" }
        }
    }

    private data class UnwatchReqBody(val id: String)
    private data class WatchListBody(val model: String, val path: ListPath) {
        data class ListPath(val id: String, val props: String)
    }
    private data class EditItemBody(val id: String, val path: String, val update: JsonObject)
    private data class AddMarketBody(val name: String, val description: String, val imageUrl: String? = null)
    private data class AddProductBody(
            val marketId: String,
            val name: String,
            val description: String,
            val price: Float,
            val quantity: Int,
            val photoUrls: List<String>
    )
}