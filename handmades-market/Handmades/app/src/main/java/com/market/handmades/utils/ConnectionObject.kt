package com.market.handmades.utils

import com.market.handmades.model.*
import com.market.handmades.remote.Connection
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Abstract class for ConnectionActivity companion object, providing access to the connection
 */
abstract class ConnectionObject {
    private val continuations: MutableList<Continuation<Connection>> = mutableListOf()
    private var connection: Connection? = null

    private lateinit var userRepository: UserRepository
    private lateinit var customerRepository: CustomerRepository
    private lateinit var vendorRepository: VendorRepository
    private lateinit var marketRepository: MarketRepository
    private lateinit var orderRepository: OrderRepository
    private lateinit var productRepository: ProductRepository
    private lateinit var chatRepository: ChatRepository

    protected fun onConnection(connection: Connection) {
        this.connection = connection
        initRepositories(connection)

        continuations.forEach { continuation -> continuation.resume(connection) }
        continuations.clear()
    }

    suspend fun awaitConnection(): Connection {
        return suspendCoroutine { continuation ->
            if (connection == null) {
                continuations.add(continuation)
            } else {
                continuation.resume(connection!!)
            }
        }
    }

    suspend fun getUserRepository(): UserRepository {
        awaitConnection()
        return userRepository
    }

    suspend fun getCustomerRepository(): CustomerRepository {
        awaitConnection()
        return customerRepository
    }

    suspend fun getVendorRepository(): VendorRepository {
        awaitConnection()
        return vendorRepository
    }

    suspend fun getMarketRepository(): MarketRepository {
        awaitConnection()
        return marketRepository
    }

    suspend fun getOrderRepository(): OrderRepository {
        awaitConnection()
        return orderRepository
    }

    suspend fun getProductRepository(): ProductRepository {
        awaitConnection()
        return productRepository
    }

    suspend fun getChatRepository(): ChatRepository {
        awaitConnection()
        return chatRepository
    }

    private fun initRepositories(connection: Connection) {
        userRepository = UserRepository(connection)
        productRepository = ProductRepository(connection)
        marketRepository = MarketRepository(connection, productRepository)
        orderRepository = OrderRepository(connection, userRepository, productRepository)
        customerRepository = CustomerRepository(connection, orderRepository)
        vendorRepository = VendorRepository(connection, marketRepository, userRepository, orderRepository)
        chatRepository = ChatRepository(connection, userRepository)
    }
}