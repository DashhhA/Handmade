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
import com.market.handmades.remote.FileStream
import com.market.handmades.remote.watchers.IWatcher
import com.market.handmades.ui.CustomViewArrayAdapter
import com.market.handmades.utils.ConnectionActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MarketProductsFragment: Fragment() {
    private val viewModel: VendorViewModel by activityViewModels()
    private var productsWatcher: IWatcher<*>? = null
    private val photos: MutableList<FileStream.FileDescription> = mutableListOf()
    private val withPhotos: MutableLiveData<List<ProductWithPhoto>> = MutableLiveData()
    companion object {
        const val titleId = R.string.market_products
        const val EXTRA_MARKET_ID = "marketId"
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

        GlobalScope.launch(Dispatchers.IO) {
            val selectedMarket = viewModel.selectedMarket ?: return@launch
            val marketRepository = ConnectionActivity.getMarketRepository()
            val productsWatcher = marketRepository.watchProducts(selectedMarket.dbId)
            this@MarketProductsFragment.productsWatcher = productsWatcher

            withContext(Dispatchers.Main) {
                productsWatcher.getData().observe(viewLifecycleOwner) { res ->
                    val products = res.getOrShowError(requireContext()) ?: return@observe
                    GlobalScope.launch(Dispatchers.IO) {
                        withPhotos.postValue(products.map { newProductWPhoto(it) })
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        GlobalScope.launch(Dispatchers.IO) {
            productsWatcher?.close()
        }
    }

    private suspend fun newProductWPhoto(product: Product): ProductWithPhoto {
        if (product.photoUrls.isEmpty())
            return ProductWithPhoto(product, null)
        val knownPhotos = photos.map { it.name }
        val found = product.photoUrls.map { knownPhotos.indexOf(it) }
        val ind = found.find { it > 0 }
        if (ind != null)
            return ProductWithPhoto(product, photos[ind])
        val connection = ConnectionActivity.awaitConnection()
        val res = connection.fileStream.getFile(product.photoUrls[0])
        val newPhoto = res.getOrShowError(requireContext())

        if (newPhoto != null) {
            photos.add(newPhoto)
            return ProductWithPhoto(product, newPhoto)
        }

        return ProductWithPhoto(product, null)
    }

    data class ProductWithPhoto(val product: Product, val photo: FileStream.FileDescription?)
}