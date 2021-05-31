package com.market.handmades.ui.messages

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.LiveData
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.market.handmades.R
import com.market.handmades.model.Chat
import com.market.handmades.model.User
import com.market.handmades.ui.TintedProgressBar
import com.market.handmades.utils.ConnectionActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatsWithAdminBtn(
    chats: LiveData<List<Chat>>,
    user: User,
    private val onNewChat: (chatId: String) -> Unit
): ChatsFragment(chats, user) {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_messages_fab, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnAdmin: ExtendedFloatingActionButton = view.findViewById(R.id.btn_admin)
        val process = TintedProgressBar(requireContext(), view as ViewGroup)
        btnAdmin.setOnClickListener {
            process.show()

            GlobalScope.launch(Dispatchers.IO) {
                val userRepository = ConnectionActivity.getUserRepository()
                val chatRepository = ConnectionActivity.getChatRepository()
                val res = userRepository.getAvailableAdmins()

                withContext(Dispatchers.Main) { process.hide() }

                val admins = res.getOrShowError(requireContext()) ?: return@launch

                withContext(Dispatchers.Main) {
                    var selected = 0
                    val names = admins.map { it.nameLong() }.toTypedArray()
                    AlertDialog.Builder(requireContext())
                        .setTitle(R.string.available_admins)
                        .setSingleChoiceItems(names, 0) { _, which ->
                            selected = which
                        }
                        .setNegativeButton(R.string.str_cancel) { _, _ -> }
                        .setPositiveButton(R.string.button_positive) { _, _ ->
                            val admin = admins[selected]
                            GlobalScope.launch(Dispatchers.IO) l1@{
                                val nc = chatRepository.newChat(Chat.ChatType.Private, listOf(admin))
                                    .getOrShowError(requireContext()) ?: return@l1
                                withContext(Dispatchers.Main) { onNewChat(nc.chatId) }
                            }
                        }
                        .show()
                }
            }
        }
    }
}