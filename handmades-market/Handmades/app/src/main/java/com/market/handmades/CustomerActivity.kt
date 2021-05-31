package com.market.handmades

import android.os.Bundle
import android.widget.TextView
import androidx.activity.viewModels
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.navigation.NavigationView
import com.market.handmades.model.*
import com.market.handmades.remote.watchers.IWatcher
import com.market.handmades.utils.ConnectionActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CustomerActivity: ConnectionActivity() {
    private lateinit var navigation: NavController
    private lateinit var appBarConfiguration: AppBarConfiguration
    private val viewModel: CustomerViewModel by viewModels()
    private var customerWatcher: IWatcher<Customer>? = null
    private var userData: IWatcher<*>? = null
    private var chatsData: IWatcher<*>? = null
    private var marketsData: IWatcher<*>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_customer)

        navigation = findNavController(R.id.customer_nav_host_fragment)
        appBarConfiguration = AppBarConfiguration(setOf(
                R.id.customer_stores,
                R.id.customer_goods,
                R.id.customer_cart,
                R.id.customer_orders,
                R.id.customer_messages,
                R.id.customer_settings,
                R.id.customer_info
        ), findViewById<DrawerLayout>(R.id.customer_drawer_layout))

        val navView: NavigationView = findViewById(R.id.customer_nav_view)
        navView.setupWithNavController(navigation)
        setupActionBarWithNavController(navigation, appBarConfiguration)

        // update sidebar header with user information
        val headerView = navView.getHeaderView(0)
        val nameView: TextView = headerView.findViewById(R.id.sidebar_name)
        val emailView: TextView = headerView.findViewById(R.id.sidebar_email)

        // observe changes to user
        GlobalScope.launch(Dispatchers.IO) {
            val staticData = ConnectionActivity.getUserRepository().staticData()
            val customerRepository = getCustomerRepository()
            val chatRepository = getChatRepository()
            val marketRepository = getMarketRepository()

            val userData = ConnectionActivity.getUserRepository().watchUser(staticData.login)
            val customerData = customerRepository.watchCustomer(staticData.modelId)
            val chatsData = chatRepository.watchChats()
            val marketsData = marketRepository.watchMarkets()

            withContext(Dispatchers.Main) {
                userData.getData().observe(this@CustomerActivity) { result ->
                    val userUpdate = result.getOrShowError(this@CustomerActivity) ?: return@observe
                    viewModel.user.postValue(userUpdate)

                    nameView.text = userUpdate.nameLong()
                    emailView.text = userUpdate.login
                }

                customerData.getData().observe(this@CustomerActivity) { res ->
                    val customer = res.getOrShowError(this@CustomerActivity) ?: return@observe
                    viewModel.customer.postValue(customer)
                }

                chatsData.getData().observe(this@CustomerActivity) { res ->
                    val chats = res.getOrShowError(this@CustomerActivity) ?: return@observe
                    viewModel.chats.postValue(chats)
                }

                marketsData.getData().observe(this@CustomerActivity) { res ->
                    val markets = res.getOrShowError(this@CustomerActivity) ?: return@observe
                    viewModel.markets.postValue(markets)
                }
            }

            this@CustomerActivity.userData = userData
            this@CustomerActivity.customerWatcher = customerData
            this@CustomerActivity.chatsData = chatsData
            this@CustomerActivity.marketsData = marketsData
        }
    }

    override fun onDestroy() {
        GlobalScope.launch(Dispatchers.IO) {
            userData?.close()
            customerWatcher?.close()
            chatsData?.close()
            marketsData?.close()
        }
        super.onDestroy()
    }

    override fun onSupportNavigateUp(): Boolean {
        return navigation.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}

class CustomerViewModel: ViewModel() {
    data class OrderData(
        val products: Map<Product, Int>,
        val user: User,
    )
    var selectedProduct: ProductWP? = null
    var selectedVendor: User? = null
    var selectedMarket: MarketRaw? = null
    var selectedMarketId: String? = null
    var selectedChat: String? = null
    val productsInCart: MutableSet<ProductWP> = mutableSetOf()
    val customer: MutableLiveData<Customer> = MutableLiveData()
    val user: MutableLiveData<User> = MutableLiveData()
    val chats: MutableLiveData<List<Chat>> = MutableLiveData()
    val markets: MutableLiveData<List<Market>> = MutableLiveData()
    var selectedOrder: Order? = null
    var orderData: OrderData? = null
}