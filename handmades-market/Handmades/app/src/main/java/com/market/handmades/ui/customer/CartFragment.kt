package com.market.handmades.ui.customer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.view.children
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.NavController
import androidx.navigation.Navigation
import com.market.handmades.CustomerViewModel
import com.market.handmades.R
import com.market.handmades.model.*
import com.market.handmades.ui.TintedProgressBar
import com.market.handmades.ui.Utils
import com.market.handmades.utils.ConnectionActivity
import kotlinx.coroutines.*

class CartFragment: Fragment() {
    val viewModel: CustomerViewModel by activityViewModels()
    private lateinit var navigation: NavController
    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_customer_cart, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        navigation = Navigation.findNavController(view)

        val list: LinearLayout = view.findViewById(R.id.list)

        val progress = TintedProgressBar(requireContext(), view as ViewGroup)
        progress.show()
        GlobalScope.launch(Dispatchers.IO) {
            val marketRepository = ConnectionActivity.getMarketRepository()
            val vendorRepository = ConnectionActivity.getVendorRepository()
            val userRepository = ConnectionActivity.getUserRepository()

            val products = viewModel.productsInCart
            val withUsers = products.map { product -> async {
                val mRes = marketRepository.getMarketDTO(product.product.marketId)
                val market = mRes.getOrShowError(requireContext()) ?: return@async null
                val vRes = vendorRepository.getVendorDTO(market.vendorId)
                val vendor = vRes.getOrShowError(requireContext()) ?: return@async null
                val uRes = userRepository.getUser(vendor.userId)
                val user = uRes.getOrShowError(requireContext()) ?: return@async null

                return@async Pair(user, product)
            } }.awaitAll()

            withContext(Dispatchers.Main) { progress.hide(); progress.remove() }

            if (withUsers.find { it == null } != null) return@launch

            val noNull = withUsers.filterNotNull()
            val grouped: Map<String, List<Pair<User, ProductWP>>> = noNull.groupBy { it.first.login }
            val map: Map<User, List<ProductWP>> = grouped.map { it.value[0].first to it.value.map { it.second } }.toMap()
            GlobalScope.launch(Dispatchers.Main) { fillScrollView(list, map) }
        }
    }

    private fun fillScrollView(view: LinearLayout, items: Map<User, List<ProductWP>>) {
        for ((user, products) in items) {
            val row = layoutInflater.inflate(R.layout.list_itm_per_vendor_order, null)
            val vendor: TextView = row.findViewById(R.id.vendor)
            val orders: LinearLayout = row.findViewById(R.id.orders)
            val overall: TextView = row.findViewById(R.id.all_price)
            val checkout: Button = row.findViewById(R.id.checkout)

            vendor.text = user.nameLong()
            val updatePrice = {
                var price = 0f
                for (v in orders.children) {
                    val (productWP, quantity) = v.tag as Pair<ProductWP, Int>
                    price += productWP.product.price * quantity
                }
                overall.text = Utils.printPrice(price)
            }
            fillListView(orders, products, updatePrice)
            updatePrice()
            checkout.setOnClickListener { checkout(orders.children.toList(), user) }

            view.addView(row)
        }
    }

    private fun fillListView(view: LinearLayout, products: List<ProductWP>, updatePrice: () -> Unit) {
        for(productWP in products) {
            val row = layoutInflater.inflate(R.layout.list_item_cart_product, null)
            val image: ImageView = row.findViewById(R.id.image)
            val name: TextView = row.findViewById(R.id.name)
            val price: TextView = row.findViewById(R.id.price)
            val plus: ImageButton = row.findViewById(R.id.plus)
            val quantity: EditText = row.findViewById(R.id.quantity)
            val minus: ImageButton = row.findViewById(R.id.minus)
            val delete: ImageButton = row.findViewById(R.id.btn_delete)

            image.setImageBitmap(productWP.photo?.bitmap)
            if (productWP.photo == null) image.setImageResource(R.drawable.placeholder)
            name.text = productWP.product.name
            price.text = Utils.printPrice(productWP.product.price)
            quantity.setText("1")
            plus.setOnClickListener {
                quantity.setText("${quantity.text.toString().toInt() + 1}")
                row.tag = Pair(productWP, quantity.text.toString().toInt())
                updatePrice()
            }
            minus.setOnClickListener {
                val q = quantity.text.toString().toInt()
                if (q > 1) quantity.setText("${q - 1}")
                row.tag = Pair(productWP, quantity.text.toString().toInt())
                updatePrice()
            }

            delete.setOnClickListener {
                viewModel.productsInCart.remove(productWP)
                view.removeView(row)
                updatePrice()
            }

            row.tag = Pair(productWP, quantity.text.toString().toInt())

            view.addView(row)
        }
    }

    private fun checkout(views: List<View>, user: User) {
        val products: MutableMap<Product, Int> = mutableMapOf()
        for (view in views) {
            val (productWP, quantity) = view.tag as Pair<ProductWP, Int>

            products[productWP.product] = quantity
        }

        viewModel.orderData = CustomerViewModel.OrderData(products, user)
        navigation.navigate(R.id.action_customer_cart_to_orderCheckoutFragment)
    }
}