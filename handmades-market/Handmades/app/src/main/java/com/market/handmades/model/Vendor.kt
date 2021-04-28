package com.market.handmades.model

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.market.handmades.remote.Connection
import com.market.handmades.remote.ServerRequest
import com.market.handmades.remote.watchers.*

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

    inner class VendorDTOWatcher(
            watcher: DataWatcher,
            private val gson: Gson
    ): ObjectWatcher<VendorDTO>(watcher) {
        override fun decode(json: JsonElement): VendorDTO {
            return gson.fromJson(json, VendorDTO::class.java)
        }
    }
}