package com.market.handmades.ui.vendor

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.TextView
import com.market.handmades.R

class TagListAdapter(
    context: Context
): ArrayAdapter<String>(context, R.layout.list_itm_filters_dialog) {
    private val mInflater: LayoutInflater = LayoutInflater.from(context)
    val addView: ImageButton = ImageButton(context)

    init {
        addView.setImageResource(R.drawable.baseline_add_24)
    }

    private data class ViewHolder(
        val name: TextView,
        val remove: ImageButton,
    )

    override fun getCount(): Int {
        return super.getCount() + 1
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        if (position == count - 1) {
            return addView
        } else {
            val rowView: View
            val holder: ViewHolder
            if (convertView == null || convertView.tag == null) {
                rowView = mInflater.inflate(R.layout.list_itm_filters_dialog, parent, false)
                holder = ViewHolder(
                    rowView.findViewById(R.id.name),
                    rowView.findViewById(R.id.remove)
                )
                rowView.tag = holder
            } else {
                rowView = convertView
                holder = rowView.tag as ViewHolder
            }

            val item = getItem(position) ?: return rowView

            holder.name.text = item
            holder.remove.setImageResource(R.drawable.baseline_remove_circle_outline_24)
            holder.remove.setOnClickListener {
                remove(item)
                notifyDataSetChanged()
            }

            return rowView
        }
    }

    fun getAll(): List<String> {
        val all = mutableListOf<String>()
        for (i in 0 until count - 1) {
            getItem(i)?.let { all.add(it) }
        }

        return all
    }
}