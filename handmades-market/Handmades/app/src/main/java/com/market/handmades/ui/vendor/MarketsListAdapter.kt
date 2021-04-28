package com.market.handmades.ui.vendor

import android.content.Context
import android.graphics.Bitmap
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import com.market.handmades.R
import com.market.handmades.model.MarketRaw
import com.market.handmades.ui.CustomViewArrayAdapter

class MarketsListAdapter(context: Context):
        CustomViewArrayAdapter<MarketRaw, MarketsListAdapter.ViewHolder>(context, R.layout.list_item_vendor_markets) {
    inner class ViewHolder(
            val lHolder: CardView,
            val rHolder: CardView,
            val lImage: ImageView,
            val rImage: ImageView,
            val title: TextView,
            val description: TextView,
    )

    override fun onGetView(holder: ViewHolder, item: MarketRaw, position: Int) {
        val imageView = if (position.rem(2) == 0) {
            holder.rHolder.visibility = View.GONE
            holder.lHolder.visibility = View.VISIBLE
            holder.lImage
        } else {
            holder.rHolder.visibility = View.VISIBLE
            holder.lHolder.visibility = View.GONE
            holder.rImage
        }
        val drawable = item.image?.bitmap
        if (drawable == null) {
            imageView.setImageResource(R.drawable.placeholder)
        } else {
            imageView.setImageBitmap(drawable)
        }
        holder.title.text = item.name
        holder.description.text = item.description
    }

    override fun getViewHolderInstance(view: View, position: Int): ViewHolder {
        return ViewHolder(
                view.findViewById(R.id.img_container_left),
                view.findViewById(R.id.img_container_right),
                view.findViewById(R.id.image_left),
                view.findViewById(R.id.image_right),
                view.findViewById(R.id.title),
                view.findViewById(R.id.description)
        )
    }

    override fun update(items: Collection<MarketRaw>) {
        val sorted = items.sortedWith { o1, o2 -> o1.name.compareTo(o2.name) }
        super.update(sorted)
    }
}