package com.market.handmades.ui.customer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.navigation.Navigation
import com.market.handmades.CustomerViewModel
import com.market.handmades.R
import com.market.handmades.model.Order
import com.market.handmades.ui.TintedProgressBar
import com.market.handmades.utils.ConnectionActivity
import com.market.handmades.utils.MTextWatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OrderCheckoutFragment: Fragment() {
    private val customerViewModel: CustomerViewModel by activityViewModels()
    private val viewModel: OrderViewModel by viewModels()
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_customer_checkout, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val navigation = Navigation.findNavController(view)

        val vendorName: TextView = view.findViewById(R.id.recipient_name)
        val vendorEmail: TextView = view.findViewById(R.id.recipient_email)
        val receiveWay: Spinner = view.findViewById(R.id.receive_way)
        val address: EditText = view.findViewById(R.id.address)
        val payType: Spinner = view.findViewById(R.id.payment_type)
        val comment: EditText = view.findViewById(R.id.comment)
        val gift: SwitchCompat = view.findViewById(R.id.gift)
        val urgent: SwitchCompat = view.findViewById(R.id.urgent)
        val btnCancel: Button = view.findViewById(R.id.btn_cancel)
        val btnSubmit: Button = view.findViewById(R.id.btn_submit)

        btnCancel.setOnClickListener { navigation.navigateUp() }

        val orderData = customerViewModel.orderData
        val delTypes: List<Order.DeliveryType> = listOf(
            Order.DeliveryType.Courier,
            Order.DeliveryType.Post,
        )
        val payTypes: List<Order.PaymentType> = listOf(
            Order.PaymentType.COD
        )
        val progress = TintedProgressBar(requireContext(), view as ViewGroup)
        if (orderData != null) {
            vendorName.text = orderData.user.nameLong()
            vendorEmail.text = orderData.user.login

            btnSubmit.setOnClickListener {
                progress.show()
                GlobalScope.launch(Dispatchers.IO) {
                    val orderRepository = ConnectionActivity.getOrderRepository()
                    val commentStr =
                        if (comment.text.isNullOrBlank()) null else comment.text.toString()
                    val res = orderRepository.newOrder(
                        orderData,
                        address.text.toString(),
                        payTypes[payType.selectedItemPosition],
                        delTypes[receiveWay.selectedItemPosition],
                        commentStr,
                        viewModel.gift,
                        viewModel.urgent,
                    )
                    withContext(Dispatchers.Main) {
                        progress.hide()

                        val success = res.getOrShowError(requireContext())
                        if (success != null) {
                            customerViewModel.productsInCart.removeAll { inCart ->
                                orderData.products.keys.find { p -> p.code == inCart.product.code } != null
                            }
                            navigation.navigateUp()
                        }
                    }
                }
            }
        }

        val recAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            delTypes.map { getString(it.userStrId) }
        )
        val payAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            payTypes.map { getString(it.userStrId) }
        )
        receiveWay.adapter = recAdapter
        payType.adapter = payAdapter

        viewModel.bind(address, comment)
        viewModel.addressError.observe(viewLifecycleOwner) {id ->
            if (id != null) address.error = getString(id)
        }
        viewModel.formValid.observe(viewLifecycleOwner) { valid ->
            btnSubmit.isEnabled = valid
        }
        gift.isChecked = viewModel.gift
        urgent.isChecked = viewModel.urgent
        gift.setOnCheckedChangeListener { _, isChecked -> viewModel.gift = isChecked }
        urgent.setOnCheckedChangeListener { _, isChecked -> viewModel.urgent = isChecked }
    }
}

class OrderViewModel: ViewModel() {
    var address: String? = null
    var comment: String? = null
    var gift: Boolean = false
    var urgent: Boolean = false
    val addressError: MutableLiveData<Int?> = MutableLiveData()
    val formValid: MutableLiveData<Boolean> = MutableLiveData(false)

    fun bind(address: EditText, comment: EditText) {
        address.setText(this.address)
        comment.setText(this.comment)

        address.addTextChangedListener(MTextWatcher {
            this.address = it
            validate()
        })

        comment.addTextChangedListener(MTextWatcher {
            this.comment = it
            validate()
        })
        validate()
    }

    private fun validateAddress(): Boolean {
        if (address.isNullOrBlank()) {
            addressError.value = R.string.str_should_not_empty
            return false
        }
        return true
    }

    private fun validate() {
        val valid = validateAddress()
        formValid.value = valid
    }
}