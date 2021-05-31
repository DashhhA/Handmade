package com.market.handmades.remote

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.market.handmades.model.*
import com.market.handmades.model.ProductRepository
import com.market.handmades.model.UserRepository
import com.market.handmades.utils.AsyncResult
import kotlinx.coroutines.*
import org.junit.Test
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class ProductRepository {
    private val tmpUser = UserRegistrationDTO(
            fName = "customer_name",
            login = "customer_login",
            password = "passwd",
            userType = User.UserType.Customer.dbName
    )
    private val appContext: Context
    private val userRepository: UserRepository
    private val productRepository: ProductRepository
    init {
        appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val connection = Connection(appContext)

        userRepository = UserRepository(connection)
        productRepository = ProductRepository(connection)
        val marketRepository = MarketRepository(connection, productRepository)
        val orderRepository = OrderRepository(connection, userRepository, productRepository)
        val customerRepository = CustomerRepository(connection, orderRepository)
        val vendorRepository = VendorRepository(connection, marketRepository, userRepository)
    }

    @Test
    fun loadProducts() {
        runBlocking {
            var res = userRepository.newUser(tmpUser)
            res = userRepository.auth(tmpUser.login, tmpUser.password)

            suspendCoroutine<Unit> { continuation -> GlobalScope.launch(Dispatchers.Main) {
                productRepository.watchAllProducts().getData().observeForever { res ->
                    val list = (res as AsyncResult.Success).data
                    print(list)
                    continuation.resume(Unit)
                }
            } }
        }

        runBlocking {
            userRepository.removeUser()
        }
    }
}