package com.market.handmades.ui.vendor

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.navigation.Navigation
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.market.handmades.R
import com.market.handmades.VendorViewModel
import com.market.handmades.model.Product
import com.market.handmades.model.ProductWP
import com.market.handmades.remote.FileStream
import com.market.handmades.remote.watchers.IWatcher
import com.market.handmades.ui.CustomViewArrayAdapter
import com.market.handmades.ui.ProductsFragment
import com.market.handmades.ui.TintedProgressBar
import com.market.handmades.utils.ConnectionActivity
import com.market.handmades.utils.FilterProductIncludesWord
import com.market.handmades.utils.FilterProductsIncludeTags
import com.market.handmades.utils.FilterProductsPriceInterval
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MarketProductsFragment: ProductsFragment() {
    private val viewModel: VendorViewModel by activityViewModels()
    private var productsWatcher: IWatcher<*>? = null
    private val withPhotos: MutableLiveData<List<ProductWP>> = MutableLiveData()
    companion object {
        const val titleId = R.string.market_products
        const val EXTRA_MARKET_ID = "marketId"
        const val EXTRA_MARKET_TAGS = "tags"
        fun getInstance(): MarketProductsFragment {
            return MarketProductsFragment()
        }
    }
    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_vendor_market_products, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val btnAddProduct: FloatingActionButton = view.findViewById(R.id.btn_add_product)
        btnAddProduct.setOnClickListener {
            Intent(requireContext(), AddProductActivity::class.java).let {
                it.putExtra(EXTRA_MARKET_ID, viewModel.selectedMarket?.dbId)
                it.putExtra(EXTRA_MARKET_TAGS, viewModel.selectedMarket?.tags?.toTypedArray())
                startActivity(it)
            }
        }

        val list:ListView = view.findViewById(R.id.products_list)
        val adapter = ProductArrayAdapter(requireContext())
        list.adapter = adapter
        val navigation = Navigation.findNavController(requireActivity(), R.id.vendor_nav_host_fragment)
        adapter.listener = ProductArrayAdapter.IOnClickListener{ item ->
            viewModel.selectedProduct = item.product
            navigation.navigate(R.id.action_marketFragment_to_aboutProductFragment)
        }

        withPhotos.observe(viewLifecycleOwner) {
            adapter.update(it)
        }

        val progress = TintedProgressBar(requireContext(), view as ViewGroup)
        progress.show()
        GlobalScope.launch(Dispatchers.IO) {
            val selectedMarket = viewModel.selectedMarket ?: return@launch
            val marketRepository = ConnectionActivity.getMarketRepository()
            val productsWatcher = marketRepository.watchProducts(selectedMarket.dbId)
            this@MarketProductsFragment.productsWatcher = productsWatcher

            withContext(Dispatchers.Main) {
                productsWatcher.getData().observe(viewLifecycleOwner) { res ->
                    progress.hide()
                    progress.remove()

                    val products = res.getOrShowError(requireContext()) ?: return@observe
                    GlobalScope.launch(Dispatchers.IO) {
                        withPhotos.postValue(products.map { newProductWPhoto(it) })
                    }
                }
            }
        }

        val filters = listOf<FilterItem<ProductWP>>(
            FilterProductsPriceInterval(),
            FilterProductsIncludeTags(),
            FilterProductIncludesWord(),
        )
        val sorts = mapOf<String, (ProductWP, ProductWP) -> Int>(
            getString(R.string.sort_by_name) to { p1, p2 -> p1.product.name.compareTo(p2.product.name) },
            getString(R.string.sort_by_price_ascending) to { p1, p2 -> p1.product.price.compareTo(p2.product.price) },
            getString(R.string.sort_by_price_descending) to { p1, p2 -> -p1.product.price.compareTo(p2.product.price) }
        )

        setUpListControl(adapter, filters, sorts)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        GlobalScope.launch(Dispatchers.IO) {
            productsWatcher?.close()
        }
    }
}