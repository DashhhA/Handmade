package com.market.handmades.ui.customer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.MediatorLiveData
import com.market.handmades.CustomerViewModel
import com.market.handmades.R
import com.market.handmades.model.Order
import com.market.handmades.ui.OrderItemArrayAdapter
import com.market.handmades.ui.ProductsFragment
import com.market.handmades.ui.TintedProgressBar
import com.market.handmades.ui.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OrderContentFragment: ProductsFragment() {
    private val viewModel: CustomerViewModel by activityViewModels()
    companion object {
        const val titleId = R.string.order_content
        fun getInstance(): OrderContentFragment {
            return OrderContentFragment()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_customer_order_content, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val gift: TextView = view.findViewById(R.id.gift_price)
        val giftRow: LinearLayout = view.findViewById(R.id.gift_row)
        val urgent: TextView = view.findViewById(R.id.urgent_price)
        val urgentRow: LinearLayout = view.findViewById(R.id.urgent_row)
        val delivery: TextView = view.findViewById(R.id.delivery_price)
        val products: ListView = view.findViewById(R.id.products)
        val overall: TextView = view.findViewById(R.id.overall_price)

        val adapter = OrderItemArrayAdapter(requireContext())
        products.adapter = adapter

        val selected: Order = viewModel.selectedOrder!!

        val orderData = MediatorLiveData<Order>()
        orderData.addSource(viewModel.customer) { customer ->
            val orders = customer.orders
            val changes = orders.find { it.dbId == selected.dbId } ?: return@addSource
            orderData.value = changes
        }

        val progress = TintedProgressBar(requireContext(), view as ViewGroup)
        progress.show()
        orderData.observe(viewLifecycleOwner) { order ->
            if (order.packing.checked) {
                giftRow.visibility = LinearLayout.VISIBLE
                gift.text = priceOrNull(order.packing.price)
            } else {
                giftRow.visibility = LinearLayout.GONE
            }

            if (order.urgent.checked) {
                urgentRow.visibility = LinearLayout.VISIBLE
                urgent.text = priceOrNull(order.urgent.price)
            } else {
                urgentRow.visibility = LinearLayout.GONE
            }

            delivery.text = priceOrNull(order.deliveryPrice)
            overall.text = priceOrNull((listOf(
                if (!order.packing.checked) 0f else order.packing.price,
                if (!order.urgent.checked) 0f else order.urgent.price,
                order.deliveryPrice,
            ) + order.products.map { it.key.price * it.value }).reduce { acc, price ->
                if (acc == null) return@reduce null
                if (price == null) return@reduce null
                acc + price
            })

            GlobalScope.launch(Dispatchers.IO) {
                val wp = order.products.map { newProductWPhoto(it.key) }
                withContext(Dispatchers.Main) {
                    progress.hide(); progress.remove()
                    adapter.update(wp zip order.products.values)
                }
            }
        }
    }

    private fun priceOrNull(price: Float?): String {
        return if (price != null) {
            Utils.printPrice(price)
        } else {
            requireContext().getString(R.string.str_unknown_price)
        }
    }
}