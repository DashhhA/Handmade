package com.market.handmades.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.MutableLiveData
import androidx.navigation.Navigation
import com.market.handmades.AdminViewModel
import com.market.handmades.R
import com.market.handmades.model.ProductWP
import com.market.handmades.remote.watchers.IWatcher
import com.market.handmades.ui.ProductsFragment
import com.market.handmades.ui.TintedProgressBar
import com.market.handmades.ui.vendor.ProductArrayAdapter
import com.market.handmades.utils.ConnectionActivity
import com.market.handmades.utils.FilterProductIncludesWord
import com.market.handmades.utils.FilterProductsIncludeTags
import com.market.handmades.utils.FilterProductsPriceInterval
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProductsCommentsFragment: ProductsFragment() {
    private var productsWatcher: IWatcher<*>? = null
    private val withPhotos: MutableLiveData<List<ProductWP>> = MutableLiveData()
    private val viewModel: AdminViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_admin_products, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val navigation = Navigation.findNavController(view)

        val list: ListView = view.findViewById(R.id.products_list)
        val adapter = ProductArrayAdapter(requireContext())
        list.adapter = adapter

        adapter.listener = ProductArrayAdapter.IOnClickListener { item ->
            viewModel.selectedProduct = item.product
            navigation.navigate(R.id.action_admin_products_to_productViewFragment)
        }

        withPhotos.observe(viewLifecycleOwner) {
            adapter.update(it)
        }

        val progress = TintedProgressBar(requireContext(), view as ViewGroup)
        progress.show()
        GlobalScope.launch(Dispatchers.IO) {
            val productsRepository = ConnectionActivity.getProductRepository()
            withContext(Dispatchers.Main) {
                productsRepository.watchAllProducts().getData().observe(viewLifecycleOwner) { res ->
                    val dtos = res.getOrShowError(requireContext()) ?: return@observe

                    GlobalScope.launch(Dispatchers.IO) {
                        withPhotos.postValue(dtos.map { newProductWPhoto(it) })
                        withContext(Dispatchers.Main) { progress.hide(); progress.remove() }
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