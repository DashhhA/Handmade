package com.market.handmades.ui

import android.content.Context
import android.view.View
import android.widget.TextView
import com.market.handmades.R
import com.market.handmades.model.Order

abstract class OrderArrayAdapter(context: Context):
        ControllableAdapter<Order, OrderArrayAdapter.ViewHolder>(context, R.layout.list_itm_order) {

    data class ViewHolder(
            val user: TextView,
            val name: TextView,
            val status: TextView,
            val price: TextView,
    )

    override fun getViewHolderInstance(view: View, position: Int): ViewHolder {
        return ViewHolder(
                view.findViewById(R.id.user),
                view.findViewById(R.id.name),
                view.findViewById(R.id.status),
                view.findViewById(R.id.price)
        )
    }
}