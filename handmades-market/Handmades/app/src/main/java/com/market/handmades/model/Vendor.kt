package com.market.handmades.model

import androidx.lifecycle.Observer
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.market.handmades.remote.Connection
import com.market.handmades.remote.ServerRequest
import com.market.handmades.remote.watchers.*
import com.market.handmades.utils.AsyncResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class Vendor(
        val userId: String,
        val markets: List<String>,
        val orders: List<Order>,
) {
    constructor(dto: VendorDTO, orders: List<Order>): this(
            dto.userId,
            dto.markets,
            orders
    )
}

data class VendorNoOrders(
        val user: User,
        val markets: List<MarketRaw>
)

data class VendorDTO(
        var userId: String = "",
        var orders: List<String> = listOf(),
        var markets: List<String> = listOf(),
)

class VendorRepository(
        private val connection: Connection,
        private val marketRepository: MarketRepository,
        private val userRepository: UserRepository,
        private val orderRepository: OrderRepository
) {
    private val gson = Gson()
    /** For displaying to others*/
    fun watchVendorNoOrders(id: String): IWatcher<VendorNoOrders> {
        val vendorReq = ServerRequest.WatcherRequest.WatchModel(ServerRequest.ModelType.Vendor(id))
        val vendorState = SingleUpdateWatcher(VendorDTOWatcher(connection.watch(vendorReq), gson))
        val vendorUser = TransformWatcher<VendorDTO, User>(vendorState) { vendorDTO ->
            userRepository.watchUser(vendorDTO.userId)
        }

        val marketsReq = ServerRequest.WatcherRequest.WatchList(ServerRequest.ModelType.Vendor(id), "markets")
        val marketsListWatcher = object : ListWatcher<String>(connection.watch(marketsReq)) {
            override fun decode(json: JsonElement): String {
                return json.asString
            }
        }

        val marketsWatcher = IdListWatcher<MarketRaw>(marketsListWatcher) { marketId ->
            SingleUpdateWatcher(marketRepository.watchMarketRaw(marketId))
        }

        return MediatorWatcher<User, List<MarketRaw>, VendorNoOrders>(
                vendorUser, marketsWatcher, { user, list -> VendorNoOrders(user, list) }
        )
    }

    fun watchMarkets(id: String): IWatcher<List<MarketRaw>> {
        val req = ServerRequest.WatcherRequest.WatchList(ServerRequest.ModelType.Vendor(id), "markets")
        val listWatcher = object : ListWatcher<String>(connection.watch(req)) {
            override fun decode(json: JsonElement): String {
                return json.asString
            }
        }
        return IdListWatcher(listWatcher) { mId -> marketRepository.watchMarketRaw(mId) }
    }

    fun watchVendor(id: String): IWatcher<Vendor> {
        val req = ServerRequest.WatcherRequest.WatchModel(ServerRequest.ModelType.Vendor(id))
        val dtoWatcher = VendorDTOWatcher(connection.watch(req), gson)
        val ordersReq = ServerRequest.WatcherRequest.WatchList(ServerRequest.ModelType.Vendor(id), "orders")
        val orderIdsWatcher = object : ListWatcher<String>(connection.watch(ordersReq)) {
            override fun decode(json: JsonElement): String {
                return json.asString
            }
        }
        val ordersWatcher = IdListWatcher(orderIdsWatcher) { orderId ->
            orderRepository.watchOrder(orderId)
        }
        return MediatorWatcher(dtoWatcher, ordersWatcher) { dto, orders -> Vendor(dto, orders) }
    }

    suspend fun getVendorDTO(id: String): AsyncResult<VendorDTO> {
        val req = ServerRequest.WatcherRequest.WatchModel(ServerRequest.ModelType.Vendor(id))
        val watcher = connection.watch(req)
        val sWatcher = SingleUpdateWatcher(VendorDTOWatcher(watcher, gson))
        return suspendCoroutine { continuation ->
            var resumed = false
            GlobalScope.launch(Dispatchers.Main) {
                val lData = sWatcher.getData()
                val observer = object : Observer<AsyncResult<VendorDTO>> {
                    override fun onChanged(t: AsyncResult<VendorDTO>?) {
                        lData.removeObserver(this)
                        if (!resumed) continuation.resume(t!!)
                        resumed = true
                    }
                }
                lData.observeForever(observer)
            }
        }
    }

    inner class VendorDTOWatcher(
            watcher: DataWatcher,
            private val gson: Gson
    ): ObjectWatcher<VendorDTO>(watcher) {
        override fun decode(json: JsonElement): VendorDTO {
            return gson.fromJson(json, VendorDTO::class.java)
        }
    }
}