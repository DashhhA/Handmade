package com.market.handmades

import android.os.Bundle
import android.os.IBinder
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.ui.NavigationUI
import com.market.handmades.remote.Connection
import com.market.handmades.ui.auth.AuthUtils
import com.market.handmades.utils.AsyncResult
import com.market.handmades.utils.ConnectionActivity
import com.market.handmades.utils.ConnectionObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


class AuthActivity : ConnectionActivity() {
    private lateinit var authNavigation: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Handmades)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.launch_screen)
        GlobalScope.launch(Dispatchers.IO) {
            val userRepository = getUserRepository()
            val current = userRepository.getCurrentUser()
            withContext(Dispatchers.Main) {
                if (current is AsyncResult.Success) {
                    AuthUtils.launchByUserType(this@AuthActivity, current.data.userType)
                } else {
                    setContentView(R.layout.activity_auth)

                    authNavigation = findNavController(R.id.auth_nav_host_fragment)
                    NavigationUI.setupActionBarWithNavController(this@AuthActivity, authNavigation)
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return authNavigation.navigateUp() || super.onSupportNavigateUp()
    }
}