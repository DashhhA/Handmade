package com.market.handmades.ui.vendor

import android.os.Bundle
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.MediatorLiveData
import com.market.handmades.R
import com.market.handmades.VendorViewModel
import com.market.handmades.model.Order
import com.market.handmades.ui.OrderItemArrayAdapter
import com.market.handmades.ui.ProductsFragment
import com.market.handmades.ui.TintedProgressBar
import com.market.handmades.ui.Utils
import com.market.handmades.utils.AsyncResult
import com.market.handmades.utils.ConnectionActivity
import com.market.handmades.utils.MTextWatcher
import kotlinx.coroutines.*

class OrderContentFragment: ProductsFragment() {
    private val viewModel: VendorViewModel by activityViewModels()
    private lateinit var gift: EditText
    private lateinit var urgent: EditText
    private lateinit var delivery: EditText
    private lateinit var confirm: Button
    private var giftWatcher: TextWatcher? = null
    private lateinit var giftRow: LinearLayout
    private var urgentWatcher: TextWatcher? = null
    private lateinit var urgentRow: LinearLayout
    private var deliveryWatcher: TextWatcher? = null
    private lateinit var overall: TextView
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
        return inflater.inflate(R.layout.fragment_vendor_order_content, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        gift = view.findViewById(R.id.gift_price)
        giftRow = view.findViewById(R.id.gift_row)
        urgent = view.findViewById(R.id.urgent_price)
        urgentRow = view.findViewById(R.id.urgent_row)
        delivery = view.findViewById(R.id.delivery_price)
        val products: ListView = view.findViewById(R.id.products)
        overall = view.findViewById(R.id.overall_price)
        confirm = view.findViewById(R.id.confirm)

        val adapter = OrderItemArrayAdapter(requireContext())
        products.adapter = adapter

        val selected: Order = viewModel.selectedOrder!!

        val orderData = MediatorLiveData<Order>()
        orderData.addSource(viewModel.vendor) { vendor ->
            val orders = vendor.orders
            val changes = orders.find { it.dbId == selected.dbId } ?: return@addSource
            orderData.value = changes
        }

        val progress = TintedProgressBar(requireContext(), view as ViewGroup)
        progress.show()
        orderData.observe(viewLifecycleOwner) { order ->
            if (order.packing.checked) {
                giftRow.visibility = LinearLayout.VISIBLE
                gift.isEnabled = order.status is Order.OrderStatus.Posted
                gift.setText(printPrice(order.packing.price))
            } else {
                giftRow.visibility = LinearLayout.GONE
            }
            if (order.urgent.checked) {
                urgentRow.visibility = LinearLayout.VISIBLE
                urgent.isEnabled = order.status is Order.OrderStatus.Posted
                urgent.setText(printPrice(order.urgent.price))
            } else {
                urgentRow.visibility = LinearLayout.GONE
            }
            delivery.isEnabled = order.status is Order.OrderStatus.Posted
            delivery.setText(printPrice(order.deliveryPrice))

            GlobalScope.launch(Dispatchers.IO) {
                val wp = order.products.map { newProductWPhoto(it.key) }
                withContext(Dispatchers.Main) {
                    progress.hide();
                    adapter.update(wp zip order.products.values)
                }
            }

            gift.removeTextChangedListener(giftWatcher)
            giftWatcher = MTextWatcher { p ->
                if (p.isBlank()) gift.error = getString(R.string.str_should_not_empty)
                validate(order)
            }
            gift.addTextChangedListener(giftWatcher)
            urgent.removeTextChangedListener(urgentWatcher)
            urgentWatcher = MTextWatcher{ p ->
                if (p.isBlank()) urgent.error = getString(R.string.str_should_not_empty)
                validate(order)
            }
            urgent.addTextChangedListener(urgentWatcher)
            delivery.removeTextChangedListener(deliveryWatcher)
            deliveryWatcher = MTextWatcher{ p ->
                if (p.isBlank()) delivery.error = getString(R.string.str_should_not_empty)
                validate(order)
            }
            delivery.addTextChangedListener(deliveryWatcher)
            validate(order)

            confirm.setOnClickListener { GlobalScope.launch(Dispatchers.IO) {
                withContext(Dispatchers.Main) { progress.show() }
                val orderRepository = ConnectionActivity.getOrderRepository()
                val tasks: MutableList<Deferred<AsyncResult<Boolean>>> = mutableListOf()
                if (giftRow.visibility == LinearLayout.VISIBLE) {
                    val change = Order.ChangableFields.packing(gift.text.toString().toFloat())
                    tasks.add( async { orderRepository.changeField(order.dbId, change) } )
                }
                if (urgentRow.visibility == LinearLayout.VISIBLE) {
                    val change = Order.ChangableFields.urgent(urgent.text.toString().toFloat())
                    tasks.add( async { orderRepository.changeField(order.dbId, change) } )
                }
                val change = Order.ChangableFields.deliveryPrice(delivery.text.toString().toFloat())
                tasks.add( async { orderRepository.changeField(order.dbId, change) } )
                val status = Order.ChangableFields.status(Order.OrderStatus.AwaitPay)
                tasks.add(async { orderRepository.changeField(order.dbId, status) })
                tasks.awaitAll().forEach { it.getOrShowError(requireContext()) }
                withContext(Dispatchers.Main) { progress.hide() }
            } }
            if (gift.text.isNullOrBlank()) gift.error = getString(R.string.str_should_not_empty)
            if (urgent.text.isNullOrBlank()) urgent.error = getString(R.string.str_should_not_empty)
            if (delivery.text.isNullOrBlank()) delivery.error = getString(R.string.str_should_not_empty)
        }
    }

    private fun printPrice(price: Float?): String? {
        if (price == null) return null
        return price.toString()
    }

    private fun validate(order: Order) {
        if (order.status is Order.OrderStatus.Posted) {
            confirm.visibility = Button.VISIBLE
            confirm.isEnabled =
                (giftRow.visibility == LinearLayout.GONE || gift.text.toString().isNotBlank()) &&
                (urgentRow.visibility == LinearLayout.GONE || urgent.text.toString().isNotBlank()) &&
                (delivery.visibility == EditText.GONE || delivery.text.toString().isNotBlank())
        } else {
            confirm.visibility = Button.GONE
        }

        overall.text = (listOf(
            gift.text.toString().toFloatOrNull(),
            urgent.text.toString().toFloatOrNull(),
            delivery.text.toString().toFloatOrNull(),
        ) + order.products.map { it.key.price * it.value }).reduce { acc, price ->
            return@reduce (acc ?: 0f) + (price ?: 0f)
        }.toString()
    }
}