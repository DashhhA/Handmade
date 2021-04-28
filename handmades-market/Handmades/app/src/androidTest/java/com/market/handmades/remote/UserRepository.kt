package com.market.handmades.remote

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.market.handmades.model.User
import com.market.handmades.model.UserRegistrationDTO
import com.market.handmades.model.UserRepository
import com.market.handmades.utils.AsyncResult
import kotlinx.coroutines.*
import org.junit.Assert
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class UserRepository {
    private val connection: Connection
    private val appContext: Context
    private val user = UserRegistrationDTO(
        fName = "fName",
        login = "user_repo_login",
        password = "passwd",
        userType = User.UserType.Customer.dbName
    )
    val repository: UserRepository
    init {
        appContext = InstrumentationRegistry.getInstrumentation().targetContext

        connection = Connection(appContext)
        repository = UserRepository(connection)
    }

    @Test
    fun aUserRegistrationAndLogin() {
        runBlocking {
            val result = repository.newUser(user)
            Assert.assertTrue("User creation should pass", result is AsyncResult.Success)
            val ans = (result as AsyncResult.Success).data
            Assert.assertEquals("Wrong response", true, ans)
        }

        runBlocking {
            val result = repository.auth(user.login, user.password)
            Assert.assertTrue("User should login successfully", result is AsyncResult.Success)
            val ans = (result as AsyncResult.Success).data
            Assert.assertEquals("Wrong response", true, ans)
        }
    }

    @Test
    fun zUserDeletion(){
        runBlocking {
            repository.auth(user.login, user.password)
            val result = repository.removeUser()
            Assert.assertTrue("User should be successfully removed", result is AsyncResult.Success)
            Assert.assertTrue( (result as AsyncResult.Success).data )
        }

        // Check user removed
        runBlocking {
            val result = repository.getCurrentUser()
            Assert.assertTrue("Cannot get removed user", result is AsyncResult.Error)
        }
    }
}