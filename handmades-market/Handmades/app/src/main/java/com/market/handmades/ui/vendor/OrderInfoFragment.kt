package com.market.handmades.ui.vendor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.MediatorLiveData
import com.market.handmades.R
import com.market.handmades.VendorViewModel
import com.market.handmades.model.Order
import com.market.handmades.ui.TintedProgressBar
import com.market.handmades.ui.Utils
import com.market.handmades.ui.messages.MessagingFragment
import com.market.handmades.utils.ConnectionActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OrderInfoFragment: Fragment() {
    private val viewModel: VendorViewModel by activityViewModels()
    companion object {
        const val titleId = R.string.order_info
        fun getInstance(): OrderInfoFragment {
            return OrderInfoFragment()
        }
    }
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_vendor_show_order, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val name: TextView = view.findViewById(R.id.customer_name)
        val email: TextView = view.findViewById(R.id.customer_email)
        val address: TextView = view.findViewById(R.id.address)
        val receiveWay: TextView = view.findViewById(R.id.receive_way)
        val status: TextView = view.findViewById(R.id.status)
        val btnPayed: Button = view.findViewById(R.id.btn_payed)
        val btnShipped: Button = view.findViewById(R.id.btn_shipped)
        val viewsToGone: List<View> = listOf(btnPayed, btnShipped)

        val selected: Order = viewModel.selectedOrder!!

        val progress = TintedProgressBar(requireContext(), view as ViewGroup)
        btnPayed.setOnClickListener {
            changeStatus(Order.OrderStatus.Paid, progress)
        }
        btnShipped.setOnClickListener {
            changeStatus(Order.OrderStatus.Shipped, progress)
        }

        val orderData = MediatorLiveData<Order>()
        orderData.addSource(viewModel.vendor) { vendor ->
            val orders = vendor.orders
            val changes = orders.find { it.dbId == selected.dbId } ?: return@addSource
            orderData.value = changes
        }

        orderData.observe(viewLifecycleOwner) { order ->
            name.text = order.customer.nameLong()
            email.text = order.customer.login
            address.text = order.address
            receiveWay.text = getString(order.deliveryType.userStrId)
            when (order.status) {
                is Order.OrderStatus.Posted -> {
                    Utils.setOneVisible(null, viewsToGone)
                }
                is Order.OrderStatus.AwaitPay -> {
                    Utils.setOneVisible(btnPayed, viewsToGone)
                }
                is Order.OrderStatus.Paid -> {
                    Utils.setOneVisible(btnShipped, viewsToGone)
                }
                is Order.OrderStatus.Shipped -> {
                    Utils.setOneVisible(null, viewsToGone)
                }
                is Order.OrderStatus.Delivered -> {
                    Utils.setOneVisible(null, viewsToGone)
                }
            }
            status.setText(order.status.userStrId)
        }

        // place chat fragment
        val msgFragment = MessagingFragment(viewModel.user.value!!, selected.chatId) // TODO check if no user
        childFragmentManager.beginTransaction()
            .replace(R.id.chat_container, msgFragment)
            .commit()
    }

    private fun changeStatus(status: Order.OrderStatus, progress: TintedProgressBar) {
        val selected: Order = viewModel.selectedOrder!!
        progress.show()
        GlobalScope.launch(Dispatchers.IO) {
            val orderRepository = ConnectionActivity.getOrderRepository()
            val res = orderRepository.changeField(
                selected.dbId,
                Order.ChangableFields.status(status)
            )
            res.getOrShowError(requireContext())
            withContext(Dispatchers.Main) { progress.hide() }
        }
    }
}