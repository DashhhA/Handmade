package com.market.handmades.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.market.handmades.AdminViewModel
import com.market.handmades.R
import com.market.handmades.ui.messages.MessagingFragment

class ChatViewFragment: Fragment() {
    private val viewModel: AdminViewModel by activityViewModels()
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.acticity_chat, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val msgFragment = AdminMessagingFragment(viewModel.user.value!!, viewModel.selectedChat!!) // TODO: check if user is null
        childFragmentManager.beginTransaction()
            .replace(R.id.chat_container, msgFragment)
            .commit()
    }
}