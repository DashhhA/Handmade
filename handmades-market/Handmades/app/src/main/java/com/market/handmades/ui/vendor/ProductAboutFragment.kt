package com.market.handmades.ui.vendor

import android.os.Bundle
import android.view.*
import android.widget.TextView
import androidx.fragment.app.activityViewModels
import androidx.navigation.NavController
import androidx.viewpager2.widget.ViewPager2
import com.market.handmades.R
import com.market.handmades.VendorViewModel
import com.market.handmades.model.Product
import com.market.handmades.ui.FragmentWithProduct

class ProductAboutFragment(private val navigation: NavController): FragmentWithProduct() {
    val viewModel: VendorViewModel by activityViewModels()
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
        return inflater.inflate(R.layout.fragment_vendor_product_display, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState, viewModel.selectedProduct?.dbId)

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
}