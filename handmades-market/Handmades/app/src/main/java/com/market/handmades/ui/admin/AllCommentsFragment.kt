package com.market.handmades.ui.admin

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ListView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.fragment.app.activityViewModels
import androidx.navigation.NavController
import androidx.navigation.Navigation
import com.market.handmades.AdminViewModel
import com.market.handmades.R
import com.market.handmades.model.Message
import com.market.handmades.remote.watchers.IWatcher
import com.market.handmades.ui.ControllableAdapter
import com.market.handmades.ui.ListFragment
import com.market.handmades.utils.ConnectionActivity
import com.market.handmades.utils.FilterMessagesDeleted
import com.market.handmades.utils.FilterMessagesUndeleted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AllCommentsFragment: ListFragment<Message>() {
    private val viewModel: AdminViewModel by activityViewModels()
    private var watcher: IWatcher<*>? = null
    private lateinit var navigation: NavController

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_admin_all_comments, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        navigation = Navigation.findNavController(view)

        val load: Button = view.findViewById(R.id.load)
        val list: ListView = view.findViewById(R.id.messages)
        val adapter = AllCommentsArrayAdapter(requireContext())
        list.adapter = adapter
        list.setOnItemClickListener { _, v, position, _ ->
            val item = adapter.getItem(position) ?: return@setOnItemClickListener
            selectAction(v, item)
        }

        load.setOnClickListener {
            load.visibility = View.GONE
            list.visibility = View.VISIBLE
            GlobalScope.launch(Dispatchers.IO) {
                val chatRepository = ConnectionActivity.getChatRepository()
                val watcher = chatRepository.watchComments()
                this@AllCommentsFragment.watcher = watcher

                withContext(Dispatchers.Main) {
                    watcher.getData().observe(viewLifecycleOwner) { res ->
                        val messages = res.getOrShowError(requireContext()) ?: return@observe
                        adapter.update(messages)
                    }
                }
            }
        }

        val filters = listOf<FilterItem<Message>>(
            FilterMessagesDeleted(),
            FilterMessagesUndeleted(),
        )

        val sorts = mapOf<String, (Message, Message) -> Int>(
            getString(R.string.admin_all_messages_by_content) to { m1, m2 -> m1.body.compareTo(m2.body) },
            getString(R.string.admin_all_messages_by_time) to { m1, m2 -> m1.time.compareTo(m2.time) }
        )

        setUpListControl(adapter, filters, sorts)
    }

    private fun selectAction(view: View, item: Message) {
        val popup = PopupMenu(requireContext(), view)
            popup.menuInflater.inflate(R.menu.admin_comment_menu, popup.menu)

        popup.setOnMenuItemClickListener { menuItem ->
            return@setOnMenuItemClickListener when(menuItem.itemId) {
                R.id.delete -> {
                    Utils.deleteMessage(requireContext(), item)
                    return@setOnMenuItemClickListener true
                }
                R.id.to_chat -> {
                    viewModel.selectedChat = item.chat
                    navigation.navigate(R.id.action_admin_all_comments_to_chatViewFragment)
                    return@setOnMenuItemClickListener  true
                }
                else -> false
            }
        }

        popup.show()
    }

    override fun onDestroy() {
        GlobalScope.launch(Dispatchers.IO) {
            watcher?.close()
        }
        super.onDestroy()
    }
}

private class AllCommentsArrayAdapter(
    context: Context
): ControllableAdapter<Message, AllCommentsArrayAdapter.ViewHolder>(context, android.R.layout.simple_list_item_1) {
    private data class ViewHolder(
        val text: TextView,
    )

    override fun onGetView(holder: ViewHolder, item: Message, position: Int) {
        if (item.deleted == null) {
            holder.text.text = item.body
        } else {
            holder.text.text = context.getString(R.string.admin_all_messages_deleted_mark, item.body)
        }
    }

    override fun getViewHolderInstance(view: View, position: Int): ViewHolder {
        return ViewHolder(
            view.findViewById(android.R.id.text1),
        )
    }
}