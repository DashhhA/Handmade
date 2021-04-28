package com.market.handmades.remote

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.market.handmades.model.*
import com.market.handmades.model.CustomerRepository
import com.market.handmades.model.UserRepository
import com.market.handmades.remote.watchers.ObjectWatcher
import com.market.handmades.utils.AsyncResult
import kotlinx.coroutines.*
import org.junit.Assert
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class Interactions {
    private val customerDescr = UserRegistrationDTO(
            fName = "customer_name",
            login = "customer_login",
            password = "passwd",
            userType = User.UserType.Customer.dbName
    )

    private val vendorDescr = UserRegistrationDTO(
            fName = "vendor_name",
            login = "vendor_login",
            password = "passwd",
            userType = User.UserType.Vendor.dbName
    )

    private val appContext: Context
    private val userRepository: UserRepository
    private val customerRepository: CustomerRepository
    private val vendorRepository: VendorRepository
    private val marketRepository: MarketRepository
    init {
        appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val connection = Connection(appContext)

        userRepository = UserRepository(connection)
        val productRepository = ProductRepository(connection)
        marketRepository = MarketRepository(connection, productRepository)
        val orderRepository = OrderRepository(connection, userRepository, productRepository)
        customerRepository = CustomerRepository(connection, orderRepository)
        vendorRepository = VendorRepository(connection, marketRepository, userRepository)
    }

    @Test
    fun aRegisterCustomer() {
        runBlocking {
            val result = userRepository.newUser(customerDescr)
            Assert.assertTrue("User creation should pass", result is AsyncResult.Success)
            val ans = (result as AsyncResult.Success).data
            Assert.assertEquals("Wrong response", true, ans)
        }
    }

    @Test
    fun bWatcherNotifiesAboutUserModelChange() {
        runBlocking {
            userRepository.auth(customerDescr.login, customerDescr.password)
            val watcher = userRepository.watchUser(customerDescr.login)
            // Update with User current state should be thrown
            var firstTime = true
            val current = suspendCoroutine<User> { continuation -> watcher.addOnChangeListener {res ->
                if (firstTime) { try {
                    firstTime = false
                    Assert.assertTrue("Must be success", res is AsyncResult.Success)
                    val change = (res as AsyncResult.Success).data
                    Assert.assertTrue("Event must be update", change is ObjectWatcher.Change.Update)
                    continuation.resume((change as ObjectWatcher.Change.Update).element)
                } catch (e: Throwable) {
                    continuation.resumeWithException(e)
                } }
            } }

            suspendCoroutine<Unit> { continuation ->
                GlobalScope.launch { userRepository.changeField(current.dbId, User.ChangableFieds.fName("new_fname")) }
                watcher.addOnChangeListener { res ->
                    try {
                        Assert.assertTrue("Must be success", res is AsyncResult.Success)
                        val change = (res as AsyncResult.Success).data
                        Assert.assertTrue("Event must be update", change is ObjectWatcher.Change.Update)
                        val user = change as ObjectWatcher.Change.Update
                        Assert.assertEquals(user.element.fName, "new_fname")
                        continuation.resume(Unit)
                    } catch (e: Throwable) {
                        continuation.resumeWithException(e)
                    }
                }
            }
        }
    }

    @Test
    fun cRegisterVendor() {
        runBlocking {
            val result = userRepository.newUser(vendorDescr)
            Assert.assertTrue("User creation should pass", result is AsyncResult.Success)
            val ans = (result as AsyncResult.Success).data
            Assert.assertEquals("Wrong response", true, ans)
        }
    }

    @Test
    fun dVendorWatcherNotifiesAboutMarketAndProductCreation() {
        runBlocking {
            userRepository.auth(vendorDescr.login, vendorDescr.password)
            val vUser = (userRepository.getCurrentUser() as AsyncResult.Success).data

            val marketResult: Deferred<AsyncResult<MarketRepository.AddMarketResponse>>
            val marketWatcher = vendorRepository.watchMarkets(vUser.modelId)
            var resumed = false
            suspendCoroutine<Unit> { continuation ->
                GlobalScope.launch(Dispatchers.Main) { marketWatcher.getData().observeForever { res ->
                    try {
                        // TODO: complete test
                        if (!resumed) continuation.resume(Unit)
                        resumed = true
                    } catch (e: Throwable) {
                        continuation.resumeWithException(e)
                    }
                } }
                marketResult = GlobalScope.async {
                    marketRepository.addMarket("mrkt", "dscrptn")
                }
            }

            Assert.assertTrue(marketResult.await() is AsyncResult.Success)
            val closeRes = marketWatcher.close()
            Assert.assertTrue(closeRes is AsyncResult.Success)
        }
    }
    // TODO test orders watcher
    // TODO test market watcher on market change

    @Test
    fun zUsersDeletion(){
        runBlocking {
            userRepository.auth(customerDescr.login, customerDescr.password)
            userRepository.removeUser()
            userRepository.auth(vendorDescr.login, vendorDescr.password)
            userRepository.removeUser()
        }
    }
}