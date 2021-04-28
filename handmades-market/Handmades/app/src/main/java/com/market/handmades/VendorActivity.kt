package com.market.handmades

import android.os.Bundle
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
import com.market.handmades.model.MarketRaw
import com.market.handmades.model.Product
import com.market.handmades.model.User
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

    companion object {
        private var data: SynchronizedData? = null
        private val listeners: MutableList<Continuation<SynchronizedData>> = mutableListOf()
        suspend fun getSynchronizedData(): SynchronizedData {
            return suspendCoroutine { continuation ->
                if (data != null) continuation.resume(data!!)
                else listeners.add(continuation)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vendor)

        navigation = findNavController(R.id.vendor_nav_host_fragment)

        appBarConfiguration = AppBarConfiguration(setOf(
                R.id.vendor_stores,
        ), findViewById<DrawerLayout>(R.id.vendor_drawer_layout))

        val navView: NavigationView = findViewById(R.id.vendor_nav_view)
        navView.setupWithNavController(navigation)
        setupActionBarWithNavController(navigation, appBarConfiguration)

        GlobalScope.launch(Dispatchers.IO) {
            val staticData = ConnectionActivity.getUserRepository().staticData()
            val userData = ConnectionActivity.getUserRepository().watchUser(staticData.login)
            val marketsData = ConnectionActivity.getVendorRepository().watchMarkets(staticData.modelId)
            val user = MutableLiveData<User>()
            val markets = MutableLiveData<List<MarketRaw>>()
            withContext(Dispatchers.Main) {
                userData.getData().observe(this@VendorActivity) { res ->
                    val userRes = res.getOrShowError(this@VendorActivity) ?: return@observe
                    user.value = userRes
                }
                marketsData.getData().observe(this@VendorActivity) { res ->
                    val marketsRes = res.getOrShowError(this@VendorActivity) ?: return@observe
                    markets.value = marketsRes
                }
            }

            onSynchronizedData(SynchronizedData(user, markets))
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return navigation.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    data class SynchronizedData(
            val user: LiveData<User>,
            val markets: LiveData<List<MarketRaw>>,
    )

    private fun onSynchronizedData(data: SynchronizedData) {
        VendorActivity.data = data
        listeners.forEach { it.resume(data) }
        listeners.clear()
    }
}

class VendorViewModel: ViewModel() {
    var selectedMarket: MarketRaw? = null
    var selectedProduct: Product? = null
}