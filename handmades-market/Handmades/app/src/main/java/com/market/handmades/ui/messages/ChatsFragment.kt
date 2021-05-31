package com.market.handmades.ui.messages

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ListView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import com.market.handmades.R
import com.market.handmades.model.Chat
import com.market.handmades.model.User
import com.market.handmades.ui.CustomViewArrayAdapter

open class ChatsFragment(
    private val chats: LiveData<List<Chat>>,
    private val user: User,
): Fragment() {
    private var listener: IOnItemClickListener? = null
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_messages, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val list: ListView = view.findViewById(R.id.messages_list)
        val adapter = ChatArrayAdapter(requireContext(), user)
        list.adapter = adapter

        list.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            val item = adapter.getItem(position) ?: return@OnItemClickListener
            listener?.onClick(item)
        }

        chats.observe(viewLifecycleOwner) { chats ->
            adapter.update(chats)
        }
    }

    fun interface IOnItemClickListener {
        fun onClick(item: Chat)
    }

    fun setOnItemClickListener(listener: IOnItemClickListener) {
        this.listener = listener
    }
}

private class ChatArrayAdapter(
    context: Context,
    private val user: User
): CustomViewArrayAdapter<Chat, ChatArrayAdapter.ViewHolder>(context, R.layout.list_itm_chat) {
    private data class ViewHolder(
        val title: TextView,
        val status: TextView
    )

    override fun onGetView(holder: ViewHolder, item: Chat, position: Int) {
        val other = item.users.find { it.dbId != user.dbId }!!

        holder.title.text = other.strRole(context)
        if (item.recent == null) {
            holder.status.text = context.getString(R.string.str_empty)
        } else {
            if (item.recent.user.dbId == user.dbId) {
                holder.status.text = "${context.getString(R.string.chat_me)}: ${item.recent.message.body}"
            } else {
                holder.status.text = "${item.recent.user.fName}: ${item.recent.message.body}"
            }
        }
    }

    override fun getViewHolderInstance(view: View, position: Int): ViewHolder {
        return ViewHolder(
            view.findViewById(R.id.title),
            view.findViewById(R.id.status)
        )
    }

    fun update(items: Collection<Chat>) {
        val sorted = items.sortedWith { o1, o2 ->
            if (o1.recent == null && o2.recent == null) return@sortedWith 0
            if (o1.recent == null) return@sortedWith 1
            if (o2.recent == null) return@sortedWith -1
            o1.recent.message.time.compareTo(o2.recent.message.time)
        }

        clear()
        addAll(sorted)
    }
}