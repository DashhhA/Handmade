package com.market.handmades.ui.messages

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import com.market.handmades.R
import com.market.handmades.model.Message
import com.market.handmades.model.User
import java.util.*

class MessagesArrayAdapter(
    context: Context,
    private val ownId: String
): ArrayAdapter<MessagesArrayAdapter.Item>(context, R.layout.list_itm_msg_box_own) {

    private val mLayoutInflater = LayoutInflater.from(context)

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val item = getItem(position) ?: return super.getView(position, convertView, parent)

        fun formatContent(item: Item): String {
            return if (item.deleted == null) {
                item.content
            } else {
                context.getString(R.string.msg_deleted, item.deleted)
            }
        }

        return if (item.owner.dbId == ownId) {
            val rowView = mLayoutInflater.inflate(R.layout.list_itm_msg_box_own, parent, false)
            val content: TextView = rowView.findViewById(R.id.content)
            val status: TextView = rowView.findViewById(R.id.status)
            content.text = formatContent(item)
            status.text = context.getString(item.status.uiResId)

            rowView
        } else {
            val rowView = mLayoutInflater.inflate(R.layout.list_itm_msg_box_other, parent, false)
            val content: TextView = rowView.findViewById(R.id.content)
            val role: TextView = rowView.findViewById(R.id.role)
            content.text = formatContent(item)
            role.text = item.owner.strRole(context)

            rowView
        }
    }

    sealed class MessageStatus(val uiResId: Int) {
        object Sending: MessageStatus(R.string.msg_status_sending)
        object Sent: MessageStatus(R.string.msg_status_sent)
        object Received: MessageStatus(R.string.msg_status_received)
        object Error: MessageStatus(R.string.msg_status_error)
    }

    class Item private constructor (
        val status: MessageStatus,
        val content: String,
        val time: Date,
        val owner: User,
        val deleted: String?,
        val dbId: String?
    ) {
        constructor(receivedMsg: Message, owner: User):
                this(
                        if (receivedMsg.read) MessageStatus.Received else MessageStatus.Sent,
                        receivedMsg.body,
                        receivedMsg.time,
                        owner,
                        receivedMsg.deleted,
                        receivedMsg.dbId
                )
        constructor(sentMsg: String, time: Date, owner: User):
                this(MessageStatus.Sending, sentMsg, time, owner, null, null)

        fun withError(item: Item): Item {
            return Item(MessageStatus.Error, item.content, item.time, item.owner, item.deleted, item.dbId)
        }

        override fun equals(other: Any?): Boolean {
            if (other is Item) {
                return owner.dbId == other.owner.dbId && time == other.time
            }
            return super.equals(other)
        }

        override fun hashCode(): Int {
            var result = time.hashCode()
            result = 31 * result + owner.dbId.hashCode()
            return result
        }
    }

    fun update(item: Item) {
        val i = getPosition(item)
        if (i > 0) {
            remove(item)
            insert(item, i)
        } else {
            add(item)
        }
        sortByTime()
        notifyDataSetChanged()
    }

    fun addReplace(items: Collection<Item>) {
        val l = count
        // remove all items to update
        items.forEach{ remove(it) }

        val l1 = count
        print(l)
        print(l1)

        addAll(items)
        sortByTime()
        notifyDataSetChanged()
    }

    private fun sortByTime() {
        sort { o1, o2 -> o1.time.compareTo(o2.time) }
    }
}