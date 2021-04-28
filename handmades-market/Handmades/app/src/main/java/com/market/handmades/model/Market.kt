package com.market.handmades.model

import android.graphics.drawable.Drawable
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.market.handmades.remote.Connection
import com.market.handmades.remote.FileStream
import com.market.handmades.remote.ServerRequest
import com.market.handmades.remote.watchers.*
import com.market.handmades.utils.AsyncResult
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.*

class MarketRaw(
        val vendorId: String,
        val products: List<String>,
        val name: String,
        val description: String,
        val status: MarketStatus,
        val image: FileStream.FileDescription?,
        val dbId: String
) {
    constructor(dto: MarketDTO, image: FileStream.FileDescription?, dbId: String): this(
            dto.vendorId,
            dto.products,
            dto.name,
            dto.description,
            MarketStatus.fromString(dto.status),
            image,
            dbId
    )

    sealed class MarketStatus(val str: String) {
        object Validating: MarketStatus("validating")
        object Approved: MarketStatus("approved")
        object Blocked: MarketStatus("blocked")
        companion object {
            @Throws(IllegalArgumentException::class)
            fun fromString(str: String): MarketStatus {
                return when(str) {
                    Validating.str -> Validating
                    Approved.str -> Approved
                    Blocked.str -> Blocked
                    else -> throw IllegalAccessException("Unexpected market status: $str")
                }
            }
        }
    }
}

data class MarketDTO(
        var vendorId: String = "",
        var products: List<String> = listOf(),
        var name: String = "",
        var description: String = "",
        var status: String = "",
        var imageUrl: String? = null,
)

class MarketRepository(
        private val connection: Connection,
        private val productRepository: ProductRepository
) {
    private val gson = Gson()
    fun watchMarketRaw(id: String): IWatcher<MarketRaw> {
        val marketReq = ServerRequest.WatcherRequest.WatchModel(ServerRequest.ModelType.Market(id))
        val watcher = connection.watch(marketReq)
        /*return object : ObjectWatcher<MarketRaw>(watcher) {
            override fun decode(json: JsonElement): MarketRaw {
                val raw = gson.fromJson(json, MarketDTO::class.java)
                return MarketRaw(raw, null)
            }
        }*/
        return TransformWatcher(
                MarketDTOWatcher(watcher, gson)
        ) { dto ->
            if (dto.imageUrl == null) {
                object : ObjectWatcher<MarketRaw>(watcher) {
                    override fun decode(json: JsonElement): MarketRaw {
                        val raw = gson.fromJson(json, MarketDTO::class.java)
                        return MarketRaw(raw, null, id)
                    }
                }.apply { manualUpdate(MarketRaw(dto, null, id)) }
            } else {
                val imageWatcher = ImageWatcher(connection.fileStream, dto.imageUrl!!)
                TransformWatcher(imageWatcher) { fileDescription ->
                    object : ObjectWatcher<MarketRaw>(watcher) {
                        override fun decode(json: JsonElement): MarketRaw {
                            val raw = gson.fromJson(json, MarketDTO::class.java)
                            return MarketRaw(raw, fileDescription, id)
                        }
                    }.apply { manualUpdate(MarketRaw(dto, fileDescription, id)) }
                }
            }
        }
    }

    fun watchProducts(marketId: String): IWatcher<List<Product>> {
        val productIdsReq =
                ServerRequest.WatcherRequest.WatchList(ServerRequest.ModelType.Market(marketId), "products")
        val idsListWatcher = object : ListWatcher<String>(connection.watch(productIdsReq)){
            override fun decode(json: JsonElement): String {
                return json.asString
            }
        }
        return IdListWatcher(idsListWatcher) { prodctId ->
            productRepository.watchProduct(prodctId)
        }
    }

    suspend fun addMarket(
            name: String,
            description: String,
            image: FileStream.FileDescription? = null
    ): AsyncResult<AddMarketResponse> {

        if (image == null) {
            val req = ServerRequest.AddMarket(name, description)
            val modelRes = connection.requestServer(req)
            return modelRes.transform { AddMarketResponse(it.data) }
        } else {
            val req = ServerRequest.AddMarket(name, description, image.name)
            val modelRes = connection.requestServer(req)
            if (modelRes is AsyncResult.Error) return modelRes
            val response = AddMarketResponse((modelRes as AsyncResult.Success).data.data)
            val imageRes = connection.fileStream.putFile(image)
            return when (imageRes) {
                is AsyncResult.Error -> imageRes
                is AsyncResult.Success -> AsyncResult.Success(response)
            }
        }
    }

    inner class MarketDTOWatcher(
            watcher: DataWatcher,
            private val gson: Gson
    ): ObjectWatcher<MarketDTO>(watcher) {
        override fun decode(json: JsonElement): MarketDTO {
            return gson.fromJson(json, MarketDTO::class.java)
        }
    }

    inner class AddMarketResponse( data: JsonObject ) {
        val marketId = data.get("data").asJsonObject.get("marketId").asString
    }
}