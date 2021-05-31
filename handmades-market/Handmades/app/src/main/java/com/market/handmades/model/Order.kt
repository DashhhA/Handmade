package com.market.handmades.model

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.market.handmades.CustomerViewModel
import com.market.handmades.R
import com.market.handmades.remote.*
import com.market.handmades.remote.watchers.*
import com.market.handmades.utils.AsyncResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class Order(
        val customer: User,
        val vendor: User,
        val products: Map<Product, Int>,
        val time: Date,
        val status: OrderStatus,
        val chatId: String,
        val address: String,
        val paymentType: PaymentType,
        val deliveryType: DeliveryType,
        val packing: OrderDTO.Extra,
        val urgent: OrderDTO.Extra,
        val deliveryPrice: Float?,
        val dbId: String
) {
    constructor(dto: OrderDTO, products: Map<Product, Int>, customer: User, vendor: User, id: String): this(
        customer,
        vendor,
        products,
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .parse(dto.time)!!,
        OrderStatus.fromString(dto.status),
        dto.chatId,
        dto.address,
        PaymentType.fromString(dto.paymentType),
        DeliveryType.fromString(dto.deliveryType),
        dto.packing,
        dto.urgent,
        dto.deliveryPrice,
        id
    )
    sealed class OrderStatus(val str: String, val userStrId: Int) {
        object Posted: OrderStatus("posted", R.string.order_status_posted)
        object AwaitPay: OrderStatus("awaiting_payment", R.string.order_status_await_pay)
        object Paid: OrderStatus("paid", R.string.order_status_paid)
        object Shipped: OrderStatus("shipped", R.string.order_status_shipped)
        object Delivered: OrderStatus("delivered", R.string.order_status_delivered)
        companion object {
            @Throws(IllegalArgumentException::class)
            fun fromString(str: String): OrderStatus {
                return when (str) {
                    Posted.str -> Posted
                    AwaitPay.str -> AwaitPay
                    Paid.str -> Paid
                    Shipped.str -> Shipped
                    Delivered.str -> Delivered
                    else -> throw IllegalAccessException("Unexpected order status: $str")
                }
            }
        }
    }

    sealed class DeliveryType(private val str: String, val userStrId: Int) {
        override fun toString(): String {
            return str
        }
        companion object {
            @Throws(IllegalArgumentException::class)
            fun fromString(str: String): DeliveryType {
                return when (str) {
                    Courier.toString() -> Courier
                    Post.toString() -> Post
                    else -> throw IllegalAccessException("Unexpected delivery type: $str")
                }
            }
        }
        object Courier: DeliveryType("courier", R.string.delivery_courier)
        object Post: DeliveryType("post", R.string.delivery_post)
    }

    sealed class PaymentType(private val str: String, val userStrId: Int) {
        override fun toString(): String {
            return str
        }
        companion object {
            @Throws(IllegalArgumentException::class)
            fun fromString(str: String): PaymentType {
                return when (str) {
                    COD.toString() -> COD
                    else -> throw IllegalAccessException("Unexpected payment type: $str")
                }
            }
        }
        object COD: PaymentType("COD", R.string.payment_cod)
    }

    sealed class ChangableFields(propertyName: String, update: JsonElement?){
        val json = JsonObject()
        init {
            json.add(propertyName, update)
        }
        class status(status: OrderStatus):
                ChangableFields("status", JsonPrimitive(status.str))
        class packing(price: Float):
                ChangableFields("packing", Extra(price).json)
        class urgent(price: Float):
            ChangableFields("urgent", Extra(price).json)
        class deliveryPrice(price: Float):
            ChangableFields("deliveryPrice", JsonPrimitive(price))

        private class Extra(price: Float) {
            val json = JsonObject()
            init {
                json.add("checked", JsonPrimitive(true))
                json.add("price", JsonPrimitive(price))
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
        var time: String = "",
        var status: String = "",
        var chatId: String = "",
        var address: String = "",
        var paymentType: String = "",
        var deliveryType: String = "",
        var packing: Extra = Extra(),
        var urgent: Extra = Extra(),
        var deliveryPrice: Float? = null
) {
    data class ProductDescr(
            var product: String,
            var quantity: Int,
    )
    data class Extra(
        var checked: Boolean = false,
        var price: Float? = null
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
            OrderCompiler(cv, productsOnce, orderDTO.products.map { it.quantity }, orderDTO, id)
        }

        return dtoToOrder
    }

    suspend fun newOrder(
        orderData: CustomerViewModel.OrderData,
        address: String,
        paymentType: Order.PaymentType,
        deliveryType: Order.DeliveryType,
        comment: String? = null,
        packing: Boolean,
        urgent: Boolean,
    ): AsyncResult<Boolean> {
        val date = Date()
        val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS z", Locale.US)
        val time = df.format(date)
        val req = ServerRequest.AddOrder(
            orderData,
            time,
            address,
            paymentType,
            deliveryType,
            comment,
            packing,
            urgent
        )
        return suspendCoroutine { continuation ->
            connection.requestServer(req) { res ->
                continuation.resume(res.transform { it.success })
            }
        }
    }

    suspend fun changeField(id: String, field: Order.ChangableFields): AsyncResult<Boolean> {
        val req = ServerRequest.EditItem(
            ServerRequest.ModelType.Order(id),
            field.json
        )
        return suspendCoroutine { continuation ->
            connection.requestServer(req) { res ->
                continuation.resume(res.transform { it.success })
            }
        }
    }

    class CustomerVendor(
            watcher1: UserRepository.UserWatcher,
            watcher2: UserRepository.UserWatcher
    ): MediatorWatcher<User, User, Pair<User, User>>(watcher1, watcher2, { u1, u2 -> u1 to u2})

    class OrderCompiler(
            customerVendor: CustomerVendor,
            products: IWatcher<List<Product>>,
            prodQuantities: List<Int>,
            dto: OrderDTO,
            dbId: String
    ): MediatorWatcher<Pair<User, User>, List<Product>, Order>(customerVendor, products, { cv, prdcts ->
        Order(dto, (prdcts zip prodQuantities).toMap(), cv.first, cv.second, dbId)
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