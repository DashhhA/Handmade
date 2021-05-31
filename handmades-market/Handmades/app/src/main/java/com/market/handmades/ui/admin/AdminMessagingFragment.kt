package com.market.handmades.ui.admin

import android.view.View
import android.widget.PopupMenu
import com.market.handmades.R
import com.market.handmades.model.Message
import com.market.handmades.model.User
import com.market.handmades.ui.messages.MessagingFragment

class AdminMessagingFragment(
    user: User,
    chatId: String,
): MessagingFragment(user, chatId) {

    override fun onMessageSelected(view: View, message: Message): Boolean {
        val popup = PopupMenu(requireContext(), view)
        popup.menuInflater.inflate(R.menu.admin_message_menu, popup.menu)

        popup.setOnMenuItemClickListener { menuItem ->
            return@setOnMenuItemClickListener when (menuItem.itemId) {
                R.id.delete -> {
                    Utils.deleteMessage(requireContext(), message)
                    return@setOnMenuItemClickListener true
                }
                else -> false
            }
        }

        popup.show()

        return true
    }
}