package com.market.handmades.ui.vendor

import android.content.Context
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import com.market.handmades.R
import com.market.handmades.model.ProductWP
import com.market.handmades.ui.ControllableAdapter
import com.market.handmades.ui.ControllableAdapterA
import com.market.handmades.ui.CustomViewArrayAdapter
import com.market.handmades.ui.Utils
class ProductArrayAdapter(context: Context):
        ControllableAdapterA<
                ProductWP,
                Pair<ProductWP, ProductWP?>,
                ProductArrayAdapter.ViewHolder>
        (context, R.layout.list_item_2_product_row) {
    var listener: IOnClickListener? = null
    inner class ViewHolder(
            val lImage: ImageView,
            val rImage: ImageView,
            val lName: TextView,
            val rName: TextView,
            val lPrice: TextView,
            val rPrice: TextView,
            val rCard: CardView
    )

    override fun getViewHolderInstance(view: View, position: Int): ViewHolder {
        val lCardView: CardView = view.findViewById(R.id.l_card)
        val rCardView: CardView = view.findViewById(R.id.r_card)
        val item = getItem(position)
        if (item != null) {
            lCardView.setOnClickListener {
                listener?.onClick(item.first)
            }
            val rItem = item.second
            if (rItem != null) {
                rCardView.setOnClickListener {
                    listener?.onClick(rItem)
                }
            }
        }
        return ViewHolder(
                view.findViewById(R.id.l_image),
                view.findViewById(R.id.r_image),
                view.findViewById(R.id.l_name),
                view.findViewById(R.id.r_name),
                view.findViewById(R.id.l_price),
                view.findViewById(R.id.r_price),
                rCardView
        )
    }

    override fun onGetView(
            holder: ViewHolder,
            item: Pair<ProductWP, ProductWP?>,
            position: Int) {
        setLeft(holder, item.first)
        if (item.second != null) setRight(holder, item.second!!)
        else holder.rCard.visibility = CardView.INVISIBLE
    }

    fun interface IOnClickListener {
        fun onClick(item: ProductWP)
    }

    private fun setLeft(holder: ViewHolder, item: ProductWP) {
        val lPhoto = item.photo?.bitmap
        if (lPhoto != null)
            holder.lImage.setImageBitmap(lPhoto)
        else
            holder.lImage.setImageResource(R.drawable.placeholder)

        holder.lName.text = item.product.name
        holder.lPrice.text = Utils.printPrice(item.product.price)
    }

    private fun setRight(holder: ViewHolder, item: ProductWP) {
        holder.rCard.visibility = CardView.VISIBLE
        val rPhoto = item.photo?.bitmap
        if (rPhoto != null)
            holder.rImage.setImageBitmap(rPhoto)
        else
            holder.rImage.setImageResource(R.drawable.placeholder)

        holder.rName.text = item.product.name
        holder.rPrice.text = Utils.printPrice(item.product.price)
    }

    override fun addAll(items: Collection<ProductWP>) {
        val pairs: MutableList<Pair<ProductWP, ProductWP?>> = mutableListOf()
        for ((i, itm) in items.withIndex()) {
            if (i.rem(2) == 0) {
                pairs.add(Pair(itm, null))
            } else {
                pairs[pairs.size - 1] = Pair(pairs[pairs.size - 1].first, itm)
            }
        }

        addAll(pairs)
    }
}