package com.market.handmades.model

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.market.handmades.remote.Connection
import com.market.handmades.remote.watchers.DataWatcher
import com.market.handmades.remote.watchers.ObjectWatcher
import com.market.handmades.remote.ServerRequest

class Product(
        val marketId: String,
        val code: String,
        val name: String,
        val description: String,
        val price: Float,
        val quantity: Int,
        var photoUrls: List<String>,
        val dbId: String
) {
    constructor(dto: ProductDTO, dbId: String): this(
            dto.marketId,
            dto.code,
            dto.name,
            dto.description,
            dto.price,
            dto.quantity,
            dto.photoUrls,
            dbId,
    )
}

data class ProductDTO(
        var marketId: String = "",
        var code: String = "",
        var name: String = "",
        var description: String = "",
        var price: Float = 0f,
        var quantity: Int = 0,
        var photoUrls: List<String> = listOf()
)

class ProductRepository(
        private val connection: Connection,
) {
    private val gson = Gson()
    fun watchProduct(id: String): ProductWatcher {
        val req = ServerRequest.WatcherRequest.WatchModel(ServerRequest.ModelType.Product(id))
        val watcher = connection.watch(req)
        return ProductWatcher(watcher, id, gson)
    }

    inner class ProductWatcher(
            watcher: DataWatcher,
            private val id: String,
            private val gson: Gson
    ): ObjectWatcher<Product>(watcher) {
        override fun decode(json: JsonElement): Product {
            val dto = gson.fromJson(json, ProductDTO::class.java)
            return Product(dto, id)
        }
    }
}