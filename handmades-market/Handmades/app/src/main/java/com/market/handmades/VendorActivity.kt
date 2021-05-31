package com.market.handmades

import android.os.Bundle
import androidx.activity.viewModels
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.LiveData
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
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class VendorActivity: ConnectionActivity() {
    private lateinit var navigation: NavController
    private lateinit var appBarConfiguration: AppBarConfiguration
    private var userData: IWatcher<*>? = null
    private var marketsData: IWatcher<*>? = null
    private var vendorData: IWatcher<*>? = null
    private var chatsData: IWatcher<*>? = null
    private val viewModel: VendorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vendor)

        navigation = findNavController(R.id.vendor_nav_host_fragment)

        appBarConfiguration = AppBarConfiguration(setOf(
                R.id.vendor_stores,
                R.id.vendor_orders,
                R.id.vendor_messages,
                R.id.vendor_settings,
        ), findViewById<DrawerLayout>(R.id.vendor_drawer_layout))

        val navView: NavigationView = findViewById(R.id.vendor_nav_view)
        navView.setupWithNavController(navigation)
        setupActionBarWithNavController(navigation, appBarConfiguration)

        GlobalScope.launch(Dispatchers.IO) {
            val staticData = ConnectionActivity.getUserRepository().staticData()
            val userData = ConnectionActivity.getUserRepository().watchUser(staticData.login)
            val marketsData = ConnectionActivity.getVendorRepository().watchMarkets(staticData.modelId)
            val vendorData = ConnectionActivity.getVendorRepository().watchVendor(staticData.modelId)
            val chatsData = ConnectionActivity.getChatRepository().watchChats()
            withContext(Dispatchers.Main) {
                userData.getData().observe(this@VendorActivity) { res ->
                    val userRes = res.getOrShowError(this@VendorActivity) ?: return@observe
                    viewModel.user.value = userRes
                }
                marketsData.getData().observe(this@VendorActivity) { res ->
                    val marketsRes = res.getOrShowError(this@VendorActivity) ?: return@observe
                    viewModel.markets.value = marketsRes
                }
                vendorData.getData().observe(this@VendorActivity) { res ->
                    val vendorRes = res.getOrShowError(this@VendorActivity) ?: return@observe
                    viewModel.vendor.value = vendorRes
                }
                chatsData.getData().observe(this@VendorActivity) { res ->
                    val chats = res.getOrShowError(this@VendorActivity) ?: return@observe
                    viewModel.chats.value = chats
                }
            }
            this@VendorActivity.userData = userData
            this@VendorActivity.marketsData = marketsData
            this@VendorActivity.vendorData = vendorData
            this@VendorActivity.chatsData = chatsData
        }
    }

    override fun onDestroy() {
        GlobalScope.launch(Dispatchers.IO) {
            userData?.close()
            marketsData?.close()
            vendorData?.close()
            chatsData?.close()
        }
        super.onDestroy()
    }

    override fun onSupportNavigateUp(): Boolean {
        return navigation.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}

class VendorViewModel: ViewModel() {
    var selectedMarket: MarketRaw? = null
    var selectedProduct: Product? = null
    var selectedOrder: Order? = null
    var selectedChat: String? = null

    val vendor: MutableLiveData<Vendor> = MutableLiveData()
    val user: MutableLiveData<User> = MutableLiveData()
    val markets: MutableLiveData<List<MarketRaw>> = MutableLiveData()

    val chats: MutableLiveData<List<Chat>> = MutableLiveData()
}