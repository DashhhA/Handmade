package com.market.handmades.ui.customer

import android.os.Bundle
import android.view.*
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.activityViewModels
import androidx.navigation.NavController
import androidx.navigation.ui.NavigationUI
import androidx.viewpager2.widget.ViewPager2
import com.market.handmades.CustomerViewModel
import com.market.handmades.R
import com.market.handmades.model.Product
import com.market.handmades.ui.FragmentWithProduct
import com.market.handmades.utils.ConnectionActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProductAboutFragment(private val navigation: NavController): FragmentWithProduct() {
    val viewModel: CustomerViewModel by activityViewModels()
    private lateinit var name: TextView
    private lateinit var price: TextView
    private lateinit var quantity: TextView
    private lateinit var description: TextView
    private lateinit var code: TextView
    private lateinit var tag: TextView

    companion object {
        const val titleResId = R.string.product_about
        fun getInstance(navigation: NavController): ProductAboutFragment {
            return ProductAboutFragment(navigation)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_customer_product, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState, viewModel.selectedProduct?.product?.dbId)

        setHasOptionsMenu(true)

        name = view.findViewById(R.id.name)
        price = view.findViewById(R.id.price)
        quantity = view.findViewById(R.id.quantity)
        description = view.findViewById(R.id.description)
        code = view.findViewById(R.id.code)
        tag = view.findViewById(R.id.tag)
        val pager: ViewPager2 = view.findViewById(R.id.pager)
        pager.adapter = adapter
    }

    override fun onProduct(product: Product) {
        name.text = product.name
        price.text = product.price.toString()
        quantity.text = product.quantity.toString()
        description.text = product.description
        code.text = product.code
        tag.text = product.tag ?: getString(R.string.str_none)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.cart_menu, menu)

        val inCart = viewModel.productsInCart.contains(viewModel.selectedProduct)
        if (inCart) {
            menu.findItem(R.id.menu_cart).setIcon(R.drawable.baseline_shopping_cart_24)
        } else {
            menu.findItem(R.id.menu_cart).setIcon(R.drawable.baseline_add_shopping_cart_24)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.menu_cart) {
            val selected = viewModel.selectedProduct ?: return true
            val inCart = viewModel.productsInCart.contains(selected)
            if (inCart) {
                item.setIcon(R.drawable.baseline_add_shopping_cart_24)
                viewModel.productsInCart.remove(selected)
            } else {
                item.setIcon(R.drawable.baseline_shopping_cart_24)
                viewModel.productsInCart.add(selected)
            }
            return true
        }
        return NavigationUI.onNavDestinationSelected(item, navigation) ||
                super.onOptionsItemSelected(item)
    }
}