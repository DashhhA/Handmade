package com.market.handmades.remote

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.market.handmades.model.User
import com.market.handmades.model.UserDTO
import com.market.handmades.model.UserRegistrationDTO
import com.market.handmades.utils.AsyncResult
import kotlinx.coroutines.runBlocking
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.junit.Assert.*
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ConnectionTest {
    private val connection: Connection
    private val user = UserRegistrationDTO(
            fName = "fName",
            login = "login",
            password = "qwerty",
            userType = User.UserType.Customer.dbName
    )
    private val appContext: Context
    init {
        appContext = InstrumentationRegistry.getInstrumentation().targetContext

        connection = Connection(appContext)
    }

    @Test
    fun aLogout() {
        // Logout request should fail
        runBlocking<Unit> { suspendCoroutine { continuation ->
            connection.requestServer(ServerRequest.Logout()) { result ->
                try {
                    assertTrue("Logout should fail", result is AsyncResult.Error)
                    val error = result as AsyncResult.Error
                    assertTrue("Wrong error type",
                            error.exception is ServerMessage.UserUnauthorizedError)
                    continuation.resume(Unit)
                } catch (e: Throwable) {
                    continuation.resumeWithException(e)
                }
            }
        } }
    }

    @Test
    fun bCreateUser() {
        runBlocking<Unit> { suspendCoroutine { continuation ->
            connection.requestServer(ServerRequest.NewUser(user)) { result ->
                try {
                    assertTrue("User creation should pass", result is AsyncResult.Success)
                    val ans = (result as AsyncResult.Success).data
                    assertEquals("Wrong response", true, ans.success)
                    continuation.resume(Unit)
                } catch (e: Throwable) {
                    continuation.resumeWithException(e)
                }
            }
        } }
    }

    @Test
    fun cCreateUserWithWrongType() {
        val wrongUser = UserRegistrationDTO(
                fName = "fName",
                login = "login",
                password = "qwerty",
                userType = "none"
        )
        runBlocking<Unit> { suspendCoroutine { continuation ->
            connection.requestServer(ServerRequest.NewUser(wrongUser)) { result ->
                try {
                    assertTrue("User creation should fail", result is AsyncResult.Error)
                    val exception = (result as AsyncResult.Error).exception
                    assertTrue("Wrong error type",
                            exception is ServerMessage.ServerUnknownError)
                    val detailed = (exception as ServerMessage.ServerUnknownError).detailed
                    assertEquals(detailed, "User validation failed: userType: `none` is not a valid enum value for path `userType`.");
                    continuation.resume(Unit)
                } catch (e: Throwable) {
                    continuation.resumeWithException(e)
                }
            }
        } }
    }

    @Test
    fun dCreateSameUser() {
        // Creation of user with a same login should fail
        runBlocking<Unit> { suspendCoroutine { continuation ->
            connection.requestServer(ServerRequest.NewUser(user)) { result ->
                try {
                    assertTrue("Same user creation should fail", result is AsyncResult.Error)
                    val ans = (result as AsyncResult.Error).exception
                    assertTrue("Wrong error", ans is ServerMessage.DuplicateKeyError)
                    val message = (ans as ServerMessage.DuplicateKeyError).getLocalizedMessage(appContext)
                    assertEquals("Wrong duplicate key", message, "User with this login already exists")
                    continuation.resume(Unit)
                } catch (e: Throwable) {
                    continuation.resumeWithException(e)
                }
            }
        } }
    }

    @Test
    fun eLoginAsWrongUser() {
        runBlocking<Unit> { suspendCoroutine { continuation ->
            val requestBody = ServerRequest.LoginRequest(UUID.randomUUID().toString(), user.password)
            connection.requestServer(ServerRequest.AuthUser(requestBody)) { result ->
                try {
                    assertTrue("Login as wrong user should fail", result is AsyncResult.Error)
                    val ans = (result as AsyncResult.Error).exception
                    assertTrue("Wrong error", ans is ServerMessage.NoSuchUserError)
                    continuation.resume(Unit)
                } catch (e: Throwable) {
                    continuation.resumeWithException(e)
                }
            }
        } }
    }

    @Test
    fun fLoginAsCreatedUserWithWrongPassword() {
        runBlocking<Unit> { suspendCoroutine { continuation ->
            val requestBody = ServerRequest.LoginRequest(user.login, "wrong_pass")
            connection.requestServer(ServerRequest.AuthUser(requestBody)) { result ->
                try {
                    assertTrue("Result should be success", result is AsyncResult.Success)
                    val ans = (result as AsyncResult.Success).data
                    assertEquals("Success should be false", false, ans.success)
                    assertFalse("User type should not be revealed",ans.data.has("type"))
                    continuation.resume(Unit)
                } catch (e: Throwable) {
                    continuation.resumeWithException(e)
                }
            }
        } }
    }

    @Test
    fun gLoginAsCreatedUserAndLogout() {
        runBlocking<Unit> { suspendCoroutine { continuation ->
            val requestBody = ServerRequest.LoginRequest(user.login, user.password)
            connection.requestServer(ServerRequest.AuthUser(requestBody)) { result ->
                try {
                    assertTrue("Result should be success", result is AsyncResult.Success)
                    val ans = (result as AsyncResult.Success).data
                    assertEquals("Success should be true", true, ans.success)
                    val userType = ans.data.get("type").asString
                    assertEquals("Incorrect user type", user.userType, userType)
                    continuation.resume(Unit)
                } catch (e: Throwable) {
                    continuation.resumeWithException(e)
                }
            }
        } }

        runBlocking<Unit> { suspendCoroutine { continuation ->
            connection.requestServer(ServerRequest.Logout()) { result ->
                try {
                    assertTrue("Logout from logged in account should succeed", result is AsyncResult.Success)
                    val ans = (result as AsyncResult.Success).data
                    assertEquals(true, ans.success)
                    continuation.resume(Unit)
                } catch (e: Throwable) {
                    continuation.resumeWithException(e)
                }
            }
        } }
    }

    @Test
    fun hLoginAsCreatedUserAndDelete() {
        runBlocking<Unit> { suspendCoroutine { continuation ->
            val requestBody = ServerRequest.LoginRequest(user.login, user.password)
            connection.requestServer(ServerRequest.AuthUser(requestBody)) { result ->
                try {
                    assertTrue("Result should be success", result is AsyncResult.Success)
                    val ans = (result as AsyncResult.Success).data
                    assertEquals("Success should be true", true, ans.success)
                    continuation.resume(Unit)
                } catch (e: Throwable) {
                    continuation.resumeWithException(e)
                }
            }
        } }

        runBlocking<Unit> { suspendCoroutine { continuation ->
            connection.requestServer(ServerRequest.RemoveUser()) { result ->
                try {
                    assertTrue("Account deletion from logged in account should succeed", result is AsyncResult.Success)
                    val ans = (result as AsyncResult.Success).data
                    assertEquals(true, ans.success)
                    continuation.resume(Unit)
                } catch (e: Throwable) {
                    continuation.resumeWithException(e)
                }
            }
        } }
    }

    @Test
    fun iCheckUserRemoved() {
        runBlocking<Unit> { suspendCoroutine { continuation ->
            val requestBody = ServerRequest.LoginRequest(user.login, user.password)
            connection.requestServer(ServerRequest.AuthUser(requestBody)) { result ->
                try {
                    assertTrue("Deleted user should not be found", result is AsyncResult.Error)
                    val ans = (result as AsyncResult.Error).exception
                    assertTrue("Wrong error", ans is ServerMessage.NoSuchUserError)
                    continuation.resume(Unit)
                } catch (e: Throwable) {
                    continuation.resumeWithException(e)
                }
            }
        } }
    }

    @Test
    fun jAttachWatcher() {
        // register user for watching
        runBlocking<Unit> { suspendCoroutine { continuation ->
            connection.requestServer(ServerRequest.NewUser(user)) { result ->
                try {
                    assertTrue("User creation should pass", result is AsyncResult.Success)
                    val ans = (result as AsyncResult.Success).data
                    assertEquals("Wrong response", true, ans.success)
                    continuation.resume(Unit)
                } catch (e: Throwable) {
                    continuation.resumeWithException(e)
                }
            }
        } }

        // authorise as this user
        runBlocking<Unit> { suspendCoroutine { continuation ->
            val requestBody = ServerRequest.LoginRequest(user.login, user.password)
            connection.requestServer(ServerRequest.AuthUser(requestBody)) { result ->
                try {
                    assertTrue("Result should be success", result is AsyncResult.Success)
                    val ans = (result as AsyncResult.Success).data
                    assertEquals("Success should be true", true, ans.success)
                    continuation.resume(Unit)
                } catch (e: Throwable) {
                    continuation.resumeWithException(e)
                }
            }
        } }

        runBlocking<Unit> { suspendCoroutine { continuation ->
            val req = ServerRequest.WatcherRequest.WatchModel(ServerRequest.ModelType.User(user.login))
            val watcher = connection.watch(req)
            watcher.addOnChangeListener { result ->
                try {
                    assertTrue("Watch addition should pass", result is AsyncResult.Success)
                    val ans = (result as AsyncResult.Success).data
                    assertEquals("Wrong event", "update", ans.event)
                    val updated = Gson().fromJson(ans.updated, UserDTO::class.java)
                    assertEquals("Wrong login", user.login, updated.login)
                    continuation.resume(Unit)
                } catch (e: Throwable) {
                    continuation.resumeWithException(e)
                }
            }
        } }

        runBlocking<Unit> { suspendCoroutine { continuation ->
            connection.requestServer(ServerRequest.RemoveUser()) { result ->
                try {
                    assertTrue("User should be deleted", result is AsyncResult.Success)
                    val ans = (result as AsyncResult.Success).data
                    assertEquals(true, ans.success)
                    continuation.resume(Unit)
                } catch (e: Throwable) {
                    continuation.resumeWithException(e)
                }
            }
        } }
    }

    // TODO: test update and delete user watcher events
}