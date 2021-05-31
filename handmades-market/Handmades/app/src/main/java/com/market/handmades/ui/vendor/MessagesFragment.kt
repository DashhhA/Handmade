package com.market.handmades.ui.vendor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.Navigation
import com.market.handmades.R
import com.market.handmades.VendorViewModel
import com.market.handmades.ui.messages.ChatsFragment
import com.market.handmades.ui.messages.ChatsWithAdminBtn

class MessagesFragment: Fragment() {
    private val viewModel: VendorViewModel by activityViewModels()
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.acticity_chat, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val navigation = Navigation.findNavController(view)

        val chatsFragment = ChatsWithAdminBtn(viewModel.chats, viewModel.user.value!!) { chatId ->
            viewModel.selectedChat = chatId
            navigation.navigate(R.id.action_vendor_messages_to_chatFragment2)
        }
        chatsFragment.setOnItemClickListener { chat ->
            viewModel.selectedChat = chat.dbId
            navigation.navigate(R.id.action_vendor_messages_to_chatFragment2)
        }

        childFragmentManager.beginTransaction()
            .replace(R.id.chat_container, chatsFragment)
            .commit()
    }
}