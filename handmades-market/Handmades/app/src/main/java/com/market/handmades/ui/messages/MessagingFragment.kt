package com.market.handmades.ui.messages

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ListView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModel
import com.market.handmades.R
import com.market.handmades.model.Message
import com.market.handmades.model.User
import com.market.handmades.remote.watchers.IWatcher
import com.market.handmades.ui.TintedProgressBar
import com.market.handmades.utils.ConnectionActivity
import com.market.handmades.utils.MTextWatcher
import kotlinx.coroutines.*
import java.util.*

open class MessagingFragment(private val user: User, private val chatId: String): Fragment() {
    private val viewModel: MessagingViewModel by activityViewModels()
    private var watcher: IWatcher<List<Message>>? = null
    private val users: MutableMap<String, User> = mutableMapOf()
    private var messages: List<Message>? = null
    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_messaging, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val messagesAdapter = MessagesArrayAdapter(requireContext(), user.dbId)

        val input: EditText = view.findViewById(R.id.input)
        val btnSend: ImageButton = view.findViewById(R.id.send)
        val list: ListView = view.findViewById(R.id.messages)
        list.adapter = messagesAdapter
        list.setOnItemLongClickListener { _, v, position, _ ->
            val item = messagesAdapter.getItem(position) ?: return@setOnItemLongClickListener false
            val message = messages?.find { it.dbId == item.dbId } ?: return@setOnItemLongClickListener false
            return@setOnItemLongClickListener onMessageSelected(v, message)
        }
        input.setText(viewModel.input)
        input.addTextChangedListener(MTextWatcher { msg ->
            viewModel.input = msg
            if (msg.isBlank()) {
                btnSend.visibility = ImageButton.GONE
            } else {
                btnSend.visibility = ImageButton.VISIBLE
            }
        })

        btnSend.setOnClickListener {
            if (viewModel.input.isBlank()) return@setOnClickListener

            GlobalScope.launch(Dispatchers.IO) {
                val msg = viewModel.input
                withContext(Dispatchers.Main) { input.text = null }
                val chatRepository = ConnectionActivity.getChatRepository()

                val time = Date()
                val listItm = MessagesArrayAdapter.Item(msg, time, user)
                withContext(Dispatchers.Main) { messagesAdapter.update(listItm) }

                val messageRes = chatRepository.newMessage(chatId, listItm.content, listItm.time)
                val res = messageRes.getOrShowError(requireContext())
                if (res == null) withContext(Dispatchers.Main) {
                    messagesAdapter.update(listItm.withError(listItm))
                }
            }
        }

        val progress = TintedProgressBar(requireContext(), view as ViewGroup)
        progress.show()
        GlobalScope.launch(Dispatchers.IO) {
            val chatRepository = ConnectionActivity.getChatRepository()
            val chatWatcher = chatRepository.watchChat(chatId)
            watcher = chatWatcher

            withContext(Dispatchers.Main) {
                chatWatcher.getData().observe(viewLifecycleOwner) { res ->
                    val messages = res.getOrShowError(requireContext()) ?: return@observe
                    this@MessagingFragment.messages = messages

                    GlobalScope.launch(Dispatchers.IO) l1@{
                        val withUsers = messages.map { async { it to getUser(it.from) } }.awaitAll()
                        if (withUsers.any { it.second == null }) return@l1
                        val noNull = withUsers.map { it.first to it.second!! }
                        val items = noNull.map { MessagesArrayAdapter.Item(it.first, it.second) }

                        withContext(Dispatchers.Main) {
                            progress.hide()
                            messagesAdapter.addReplace(items)
                        }

                        // set messages read
                        val unread = messages.filter { !it.read && it.from != user.dbId }
                        unread.map { msg -> async {
                            val r = chatRepository.setRead(msg.dbId)
                            GlobalScope.launch(Dispatchers.Main) { r.getOrShowError(requireContext()) }
                        } }.awaitAll()
                    }
                }
            }
        }
    }

    /**
     * Called on message long-press
     * @param view List row view
     * @param message Selected message
     *
     * @return true if the callback consumed the long click, false otherwise
     */
    open fun onMessageSelected(view: View, message: Message): Boolean {
        return true
    }

    override fun onDestroy() {
        GlobalScope.launch(Dispatchers.IO) {
            watcher?.close()
        }
        super.onDestroy()
    }

    private suspend fun getUser(dbId: String): User? {
        val user = users[dbId]
        if (user != null) return user

        val userRepository = ConnectionActivity.getUserRepository()
        val res = userRepository.getUser(dbId)
        val newUser = res.getOrShowError(requireContext()) ?: return null
        users[newUser.dbId] = newUser
        return newUser
    }
}

class MessagingViewModel: ViewModel() {
    var input: String = ""
}