package com.market.handmades.model

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.market.handmades.remote.Connection
import com.market.handmades.remote.ServerRequest
import com.market.handmades.remote.watchers.*

class Customer(
        val userId: String,
        val orders: List<Order>,
)

/**
 * Data transmission object, used to send and retrieve the models to DB
 */
data class CustomerDTO(
    var userId: String = "",
    var orders: List<String> = listOf(),
)

class CustomerRepository(
        private val connection: Connection,
        private val orderRepository: OrderRepository
) {
    private val gson = Gson()

    fun watchCustomer(id: String): CustomerCompiler {
        val req = ServerRequest.WatcherRequest.WatchModel(ServerRequest.ModelType.Customer(id))
        val dtoWatcher = CustomerDTOWatcher(connection.watch(req), gson)
        val ordersReq = ServerRequest.WatcherRequest.WatchList(ServerRequest.ModelType.Customer(id), "orders")
        val ordersIdsWatcher = OrdersListWatcher(connection.watch(ordersReq))
        val ordersWatcher = IdListWatcher(
                ordersIdsWatcher
        ) { orderId -> orderRepository.watchOrder(orderId) }
        return CustomerCompiler(dtoWatcher, ordersWatcher)
    }

    inner class CustomerDTOWatcher(
            watcher: DataWatcher,
            private val gson: Gson
    ): ObjectWatcher<CustomerDTO>(watcher) {
        override fun decode(json: JsonElement): CustomerDTO {
            return gson.fromJson(json, CustomerDTO::class.java)
        }
    }

    inner class OrdersListWatcher(
            watcher: DataWatcher
    ): ListWatcher<String>(watcher) {
        override fun decode(json: JsonElement): String {
            return json.asString
        }

    }

    inner class CustomerCompiler(
            customerDTOWatcher: CustomerDTOWatcher,
            ordersWatcher: IWatcher<List<Order>>
    ): MediatorWatcher<CustomerDTO, List<Order>, Customer>(customerDTOWatcher, ordersWatcher, {dto, orders ->
        Customer(dto.userId, orders)
    })
}