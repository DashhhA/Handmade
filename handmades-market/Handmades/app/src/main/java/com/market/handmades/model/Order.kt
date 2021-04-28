package com.market.handmades.model

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.market.handmades.remote.*
import com.market.handmades.remote.watchers.*

class Order(
        val customer: User,
        val vendor: User,
        val products: Map<Product, Int>,
        val status: OrderStatus
) {
    constructor(dto: OrderDTO, products: Map<Product, Int>, customer: User, vendor: User): this(
            customer,
            vendor,
            products,
            OrderStatus.fromString(dto.status)
    )
    sealed class OrderStatus(val str: String) {
        object Posted: OrderStatus("posted")
        object Processing: OrderStatus("processing")
        object Shipped: OrderStatus("shipped")
        object Delivered: OrderStatus("delivered")
        companion object {
            @Throws(IllegalArgumentException::class)
            fun fromString(str: String): OrderStatus {
                return when (str) {
                    Posted.str -> Posted
                    Processing.str -> Processing
                    Shipped.str -> Shipped
                    Delivered.str -> Delivered
                    else -> throw IllegalAccessException("Unexpected order status: $str")
                }
            }
        }
    }
}

/**
 * Data transmission object, used to send and retrieve the models to DB
 */
data class OrderDTO(
        var customerId: String = "",
        var vendorId: String = "",
        var products: List<ProductDescr> = listOf(),
        var status: String = "",
        var chatId: String = "",
) {
    data class ProductDescr(
            var product: String,
            var quantity: Int,
    )
}

class OrderRepository(
        private val connection: Connection,
        private val userRepository: UserRepository,
        private val productRepository: ProductRepository
) {
    private val gson = Gson()

    fun watchOrder(id: String): TransformWatcher<OrderDTO, Order> {
        val orderReq = ServerRequest.WatcherRequest.WatchModel(ServerRequest.ModelType.Order(id))
        val orderWatcher = connection.watch(orderReq)

        val dtoToOrder = TransformWatcher<OrderDTO, Order>(
                DTOWatcher(orderWatcher, gson)
        ) { orderDTO ->
            val customerWatcher = userRepository.watchUser(orderDTO.customerId)
            val vendorWatcher = userRepository.watchUser(orderDTO.vendorId)
            val cv = CustomerVendor(customerWatcher, vendorWatcher)
            val products = ObjectArrayWatcher(orderDTO.products.map { productDescr ->
                productRepository.watchProduct(productDescr.product)
            })
            val productsOnce = SingleUpdateWatcher(products)
            OrderCompiler(cv, productsOnce, orderDTO.products.map { it.quantity }, orderDTO.status)
        }

        return dtoToOrder
    }

    class CustomerVendor(
            watcher1: UserRepository.UserWatcher,
            watcher2: UserRepository.UserWatcher
    ): MediatorWatcher<User, User, Pair<User, User>>(watcher1, watcher2, { u1, u2 -> u1 to u2})

    class OrderCompiler(
            customerVendor: CustomerVendor,
            products: IWatcher<List<Product>>,
            prodQuantities: List<Int>,
            status: String
    ): MediatorWatcher<Pair<User, User>, List<Product>, Order>(customerVendor, products, { cv, prdcts ->
        Order(cv.first, cv.second, (prdcts zip prodQuantities).toMap(), Order.OrderStatus.fromString(status))
    })

    class DTOWatcher(
            watcher: DataWatcher,
            private val gson: Gson
    ): ObjectWatcher<OrderDTO>(watcher) {
        override fun decode(json: JsonElement): OrderDTO {
            return gson.fromJson(json, OrderDTO::class.java)
        }
    }
}