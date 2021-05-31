package com.market.handmades.ui.customer

import android.content.Context
import android.view.View
import android.widget.*
import androidx.cardview.widget.CardView
import com.market.handmades.CustomerViewModel
import com.market.handmades.R
import com.market.handmades.model.ProductWP
import com.market.handmades.ui.ControllableAdapterA
import com.market.handmades.ui.CustomViewArrayAdapter
import com.market.handmades.ui.Utils
import com.market.handmades.ui.vendor.ProductArrayAdapter

class ProductCartArrayAdapter(context: Context, private val viewModel: CustomerViewModel):
        ControllableAdapterA<
                ProductWP,
                Pair<ProductWP, ProductWP?>,
                ProductCartArrayAdapter.ViewHolder>
        (context, R.layout.list_item_2_product_row_cart) {
    var listener: ProductArrayAdapter.IOnClickListener? = null
    inner class ViewHolder(
            val lImage: ImageView,
            val rImage: ImageView,
            val lName: TextView,
            val rName: TextView,
            val lPrice: TextView,
            val rPrice: TextView,
            val lCart: ToggleButton,
            val rCart: ToggleButton,
            val rCard: CardView
    )

    override fun getViewHolderInstance(view: View, position: Int): ViewHolder {
        val lCardView: CardView = view.findViewById(R.id.l_card)
        val rCardView: CardView = view.findViewById(R.id.r_card)
        val lCart: ToggleButton = view.findViewById(R.id.l_cart)
        val rCart: ToggleButton = view.findViewById(R.id.r_cart)
        val item = getItem(position)
        if (item != null) {
            lCardView.setOnClickListener {
                listener?.onClick(item.first)
            }
            if (viewModel.productsInCart.contains(item.first))
                lCart.isChecked = true
            lCart.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) viewModel.productsInCart.add(item.first)
                else viewModel.productsInCart.remove(item.first)
            }
            val rItem = item.second
            if (rItem != null) {
                rCardView.setOnClickListener {
                    listener?.onClick(rItem)
                }
                if (viewModel.productsInCart.contains(rItem))
                    rCart.isChecked = true
                rCart.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) viewModel.productsInCart.add(rItem)
                    else viewModel.productsInCart.remove(rItem)
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
                lCart,
                rCart,
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
}