package com.market.handmades.ui.messages

import android.os.Bundle
import android.view.MenuItem
import com.google.gson.Gson
import com.market.handmades.R
import com.market.handmades.model.User
import com.market.handmades.model.UserDTO
import com.market.handmades.utils.ConnectionActivity

class ChatActivity: ConnectionActivity() {
    companion object {
        const val EXTRA_CHAT_ID = "chatId"
        const val EXTRA_USER_DTO = "userDTO"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.acticity_chat)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val chatId = intent.getStringExtra(EXTRA_CHAT_ID)!!
        val dtoStr = intent.getStringExtra(EXTRA_USER_DTO)
        val dto = Gson().fromJson(dtoStr, UserDTO::class.java)
        val user = User(dto)

        val msgFragment = MessagingFragment(user, chatId)
        supportFragmentManager.beginTransaction()
            .replace(R.id.chat_container, msgFragment)
            .commit()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home){
            finish()
        }
        return super.onOptionsItemSelected(item)
    }
}