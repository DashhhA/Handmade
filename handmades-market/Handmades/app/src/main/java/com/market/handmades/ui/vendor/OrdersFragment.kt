package com.market.handmades.ui.vendor

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.Navigation
import com.market.handmades.R
import com.market.handmades.VendorViewModel
import com.market.handmades.model.Order
import com.market.handmades.ui.ListFragment
import com.market.handmades.ui.OrderArrayAdapter
import com.market.handmades.ui.Utils
import com.market.handmades.utils.FilterOrderGift
import com.market.handmades.utils.FilterOrderUrgent
import com.market.handmades.utils.FilterOrdersByStatus

class OrdersFragment: ListFragment<Order>() {
    val viewModel: VendorViewModel by activityViewModels()
    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_vendor_orders, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val navigation = Navigation.findNavController(view)

        val list: ListView = view.findViewById(R.id.orders_list)
        val adapter = VendorOrdersArrayAdapter(requireContext())
        list.adapter = adapter

        list.setOnItemClickListener { _, _, position, _ ->
            val order = adapter.getItem(position) ?: return@setOnItemClickListener
            viewModel.selectedOrder = order
            navigation.navigate(R.id.action_vendor_orders_to_orderViewFragmentV)
        }

        viewModel.vendor.observe(viewLifecycleOwner) { vendor ->
            adapter.update(vendor.orders)
        }

        val filters = listOf<FilterItem<Order>>(
            FilterOrdersByStatus(),
            FilterOrderUrgent(),
            FilterOrderGift()
        )
        val sorts = mapOf<String, (Order, Order) -> Int>(
            getString(R.string.sort_orders_by_recent) to { o1, o2 -> o1.time.compareTo(o2.time) }
        )

        setUpListControl(adapter, filters, sorts)
    }
}

private class VendorOrdersArrayAdapter(context: Context): OrderArrayAdapter(context) {
    override fun onGetView(holder: ViewHolder, item: Order, position: Int) {
        holder.user.text = context.getString(R.string.str_customer)
        holder.name.text = item.customer.nameLong()
        holder.price.text = Utils.printPrice(item.products.map { it.key.price * it.value }.sum())
        holder.status.text = context.getString(item.status.userStrId)
    }
}