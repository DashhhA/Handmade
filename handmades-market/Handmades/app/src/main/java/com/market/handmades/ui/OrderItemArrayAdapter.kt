package com.market.handmades.ui

import android.content.Context
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.market.handmades.R
import com.market.handmades.model.ProductWP

class OrderItemArrayAdapter(context: Context):
    CustomViewArrayAdapter<Pair<ProductWP, Int>, OrderItemArrayAdapter.ViewHolder>(context, R.layout.list_itm_order_item) {
    data class ViewHolder(
        val image: ImageView,
        val code: TextView,
        val name: TextView,
        val price: TextView,
        val quantity: TextView,
    )

    override fun onGetView(holder: ViewHolder, item: Pair<ProductWP, Int>, position: Int) {
        val product = item.first.product
        val photo = item.first.photo
        val quantity = item.second
        if (photo != null) {
            holder.image.setImageBitmap(photo.bitmap)
        } else {
            holder.image.setImageResource(R.drawable.placeholder)
        }
        holder.code.text = product.code
        holder.name.text = product.name
        holder.price.text = Utils.printPrice(product.price)
        holder.quantity.text = quantity.toString()
    }

    override fun getViewHolderInstance(view: View, position: Int): ViewHolder {
        return ViewHolder(
            view.findViewById(R.id.image),
            view.findViewById(R.id.code),
            view.findViewById(R.id.name),
            view.findViewById(R.id.price),
            view.findViewById(R.id.quantity),
        )
    }

    fun update(items: Collection<Pair<ProductWP, Int>>) {
        val sorted = items.sortedWith { o1, o2 -> o1.first.product.name.compareTo(o2.first.product.name) }
        clear()
        addAll(sorted)
    }
}