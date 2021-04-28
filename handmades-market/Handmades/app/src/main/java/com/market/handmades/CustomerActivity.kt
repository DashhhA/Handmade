package com.market.handmades

import android.os.Bundle
import android.widget.TextView
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.navigation.NavigationView
import com.market.handmades.model.User
import com.market.handmades.model.UserRepository
import com.market.handmades.utils.ConnectionActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class CustomerActivity: ConnectionActivity() {
    private lateinit var navigation: NavController
    private lateinit var appBarConfiguration: AppBarConfiguration

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_customer)

        navigation = findNavController(R.id.customer_nav_host_fragment)
        appBarConfiguration = AppBarConfiguration(setOf(
                R.id.customer_stores,
                R.id.customer_goods,
                R.id.customer_orders,
                R.id.customer_messages,
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
        GlobalScope.launch(Dispatchers.Unconfined) {
            val connection = awaitConnection()
            val userRepository = UserRepository(connection)

            val curUser: User = userRepository
                    .getCurrentUser()
                    .getOrShowError(this@CustomerActivity)
                    ?: return@launch

            val userLiveData = userRepository.watchUser(curUser.login).getData()

            userLiveData.observe(this@CustomerActivity) { result ->
                val userUpdate = result.getOrShowError(this@CustomerActivity) ?: return@observe
                val user = userUpdate

                nameView.text = user.nameLong()
                emailView.text = user.login
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return navigation.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}