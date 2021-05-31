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

class AdminActivity: ConnectionActivity() {
    private lateinit var navigation: NavController
    private lateinit var appBarConfiguration: AppBarConfiguration

    val viewModel: AdminViewModel by viewModels()
    private var userData: IWatcher<*>? = null
    private var marketsData: IWatcher<*>? = null
    private var chatsData: IWatcher<*>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin)

        navigation = findNavController(R.id.admin_nav_host_fragment)
        appBarConfiguration = AppBarConfiguration(setOf(
            R.id.admin_markets,
            R.id.admin_all_comments,
            R.id.admin_products,
            R.id.admin_messages,
            R.id.admin_settings,
        ), findViewById<DrawerLayout>(R.id.admin_drawer_layout))

        val navView: NavigationView = findViewById(R.id.admin_nav_view)
        navView.setupWithNavController(navigation)
        setupActionBarWithNavController(navigation, appBarConfiguration)

        val headerView = navView.getHeaderView(0)
        val nameView: TextView = headerView.findViewById(R.id.sidebar_name)
        val emailView: TextView = headerView.findViewById(R.id.sidebar_email)

        GlobalScope.launch(Dispatchers.IO) {
            val staticData = ConnectionActivity.getUserRepository().staticData()
            val marketRepository = getMarketRepository()
            val chatRepository = getChatRepository()

            val userData = ConnectionActivity.getUserRepository().watchUser(staticData.login)
            val marketsData = marketRepository.watchMarkets()
            val chatsData = chatRepository.watchChats()

            withContext(Dispatchers.Main) {
                userData.getData().observe(this@AdminActivity) { result ->
                    val userUpdate = result.getOrShowError(this@AdminActivity) ?: return@observe
                    viewModel.user.postValue(userUpdate)

                    nameView.text = userUpdate.nameLong()
                    emailView.text = userUpdate.login
                }

                marketsData.getData().observe(this@AdminActivity) { result ->
                    val markets = result.getOrShowError(this@AdminActivity) ?: return@observe
                    viewModel.markets.postValue(markets)
                }

                chatsData.getData().observe(this@AdminActivity) { res ->
                    val chats = res.getOrShowError(this@AdminActivity) ?: return@observe
                    viewModel.chats.postValue(chats)
                }
            }

            this@AdminActivity.userData = userData
            this@AdminActivity.marketsData = marketsData
            this@AdminActivity.chatsData = chatsData
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return navigation.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    override fun onDestroy() {
        GlobalScope.launch(Dispatchers.IO) {
            userData?.close()
            marketsData?.close()
        }
        super.onDestroy()
    }
}

class AdminViewModel: ViewModel() {
    val user: MutableLiveData<User> = MutableLiveData()
    val markets: MutableLiveData<List<Market>> = MutableLiveData()
    val chats: MutableLiveData<List<Chat>> = MutableLiveData()

    var selectedMarket: MarketRaw? = null
    var selectedProduct: Product? = null
    var selectedChat: String? = null
}