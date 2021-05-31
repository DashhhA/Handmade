package com.market.handmades.model

import android.content.Context
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.lifecycle.Observer
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.market.handmades.R
import com.market.handmades.remote.Connection
import com.market.handmades.remote.FileStream
import com.market.handmades.remote.ServerRequest
import com.market.handmades.remote.watchers.*
import com.market.handmades.utils.AsyncResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class MarketRaw(
        val vendorId: String,
        val products: List<String>,
        val name: String,
        val city: String,
        val description: String,
        val status: MarketStatus,
        val image: FileStream.FileDescription?,
        val tags: List<String>,
        val dbId: String
) {
    constructor(dto: MarketDTO, image: FileStream.FileDescription?, dbId: String): this(
            dto.vendorId,
            dto.products,
            dto.name,
            dto.city,
            dto.description,
            MarketStatus.fromString(dto.status),
            image,
            dto.tags,
            dbId
    )

    sealed class MarketStatus(val str: String, private val resId: Int) {
        fun toString(context: Context) = context.getString(resId)
        object Validating: MarketStatus("validating", R.string.market_status_validating)
        object Approved: MarketStatus("approved", R.string.market_status_approved)
        object Blocked: MarketStatus("blocked", R.string.market_status_blocked)
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

    override fun equals(other: Any?): Boolean {
        if (other is MarketRaw) {
            return this.dbId == other.dbId
        }
        return super.equals(other)
    }

    override fun hashCode(): Int {
        return dbId.hashCode()
    }

    sealed class ChangableFields(propertyName: String, update: JsonElement?) {
        val json = JsonObject()
        init {
            json.add(propertyName, update)
        }

        class status(newStatus: MarketStatus): ChangableFields("status", JsonPrimitive(newStatus.str))
    }
}

class Market(
    val vendorId: String,
    val products: List<String>,
    val name: String,
    val city: String,
    val description: String,
    val status: MarketRaw.MarketStatus,
    val imageUrl: String?,
    val tags: List<String>,
    val dbId: String,
    val dto: MarketDTO
) {
    constructor(dto: MarketDTO, dbId: String): this(
        dto.vendorId,
        dto.products,
        dto.name,
        dto.city,
        dto.description,
        MarketRaw.MarketStatus.fromString(dto.status),
        dto.imageUrl,
        dto.tags,
        dbId,
        dto
    )
}

data class MarketDTO(
        var vendorId: String = "",
        var products: List<String> = listOf(),
        var name: String = "",
        var city: String = "",
        var description: String = "",
        var status: String = "",
        var imageUrl: String? = null,
        var tags: List<String> = listOf()
)

class MarketRepository(
        private val connection: Connection,
        private val productRepository: ProductRepository
) {
    private val gson = Gson()
    fun watchMarketRaw(id: String): IWatcher<MarketRaw> {
        val marketReq = ServerRequest.WatcherRequest.WatchModel(ServerRequest.ModelType.Market(id))
        val watcher = connection.watch(marketReq)
        /*return TransformWatcher(
                MarketDTOWatcher(watcher, gson)
        ) { dto ->
            if (dto.imageUrl == null) {
                object : ObjectWatcher<MarketRaw>(watcher) {
                    override fun decode(json: JsonElement): MarketRaw {
                        val raw = gson.fromJson(json, MarketDTO::class.java)
                        return MarketRaw(raw, null, id)
                    }
                }
            } else {
                val imageWatcher = ImageWatcher(connection.fileStream, dto.imageUrl!!)
                TransformWatcher(imageWatcher) { fileDescription ->
                    object : ObjectWatcher<MarketRaw>(watcher) {
                        override fun decode(json: JsonElement): MarketRaw {
                            val raw = gson.fromJson(json, MarketDTO::class.java)
                            return MarketRaw(raw, fileDescription, id)
                        }
                    }
                }
            }
        }*/
        val mv = MarketDTOWatcher(watcher, gson)
        return TransformWatcher(mv) { dto ->
            if (dto.imageUrl == null) {
                return@TransformWatcher ProjectWatcher(mv) { dto_ ->
                    MarketRaw(dto_, null, id)
                }
            } else {
                val imageWatcher = ImageWatcher(connection.fileStream, dto.imageUrl!!)
                return@TransformWatcher TransformWatcher(imageWatcher) { fileDescription ->
                    ProjectWatcher(imageWatcher) { MarketRaw(dto, fileDescription, id) }
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

    suspend fun getMarketDTO(id: String): AsyncResult<MarketDTO> {
        val req = ServerRequest.WatcherRequest.WatchModel(ServerRequest.ModelType.Market(id))
        val watcher = connection.watch(req)
        val sWatcher = SingleUpdateWatcher(MarketDTOWatcher(watcher, gson))
        return suspendCoroutine { continuation ->
            var resumed = false
            GlobalScope.launch(Dispatchers.Main) {
                val lData = sWatcher.getData()
                val observer = object : Observer<AsyncResult<MarketDTO>> {
                    override fun onChanged(t: AsyncResult<MarketDTO>?) {
                        lData.removeObserver(this)
                        if (!resumed) continuation.resume(t!!)
                        resumed = true
                    }
                }
                lData.observeForever(observer)
            }
        }
    }

    suspend fun addMarket(
            name: String,
            city: String,
            description: String,
            tags: List<String>,
            image: FileStream.FileDescription? = null
    ): AsyncResult<AddMarketResponse> {

        if (image == null) {
            val req = ServerRequest.AddMarket(name, city, description, tags)
            val modelRes = connection.requestServer(req)
            return modelRes.transform { AddMarketResponse(it.data) }
        } else {
            val req = ServerRequest.AddMarket(name, city, description, tags, image.name)
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

    suspend fun changeField(id: String, change: MarketRaw.ChangableFields): AsyncResult<Boolean> {
        val req = ServerRequest.EditItem(
            ServerRequest.ModelType.Market(id),
            change.json,
        )
        return suspendCoroutine { continuation ->
            connection.requestServer(req) { res ->
                continuation.resume(res.transform { it.success })
            }
        }
    }

    /**
     * Watch all available markets
     */
    fun watchMarkets(): IWatcher<List<Market>> {
        val req = ServerRequest.WatcherRequest.WatchCollection(ServerRequest.ModelType.Market.NoId)
        val watcher = connection.watch(req)

        return object  : CollectionWatcher<Market>(watcher) {
            override fun decode(json: JsonObject, key: String): Market {
                val dto = gson.fromJson(json, MarketDTO::class.java)
                return Market(dto, key)
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