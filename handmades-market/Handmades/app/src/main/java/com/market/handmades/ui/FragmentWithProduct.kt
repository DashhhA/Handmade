package com.market.handmades.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.market.handmades.R
import com.market.handmades.model.Product
import com.market.handmades.remote.FileStream
import com.market.handmades.remote.watchers.IWatcher
import com.market.handmades.utils.ConnectionActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

abstract class FragmentWithProduct: Fragment() {
    private var watcher: IWatcher<Product>? = null
    private val photos: MutableLiveData<List<FileStream.FileDescription>> = MutableLiveData()
    protected lateinit var adapter: ViewPagerAdapter

    protected fun onViewCreated(view: View, savedInstanceState: Bundle?, productId: String?) {
        super.onViewCreated(view, savedInstanceState)
        productId ?: return // TODO show error
        adapter = ViewPagerAdapter(requireActivity(), photos)

        GlobalScope.launch(Dispatchers.IO) {
            val productRepository = ConnectionActivity.getProductRepository()
            val productWatcher = productRepository.watchProduct(productId)
            watcher = productWatcher

            GlobalScope.launch(Dispatchers.Main) {
                productWatcher.getData().observe(viewLifecycleOwner) { res ->
                    val product = res.getOrShowError(requireContext()) ?: return@observe
                    GlobalScope.launch(Dispatchers.IO) {
                        photos.postValue(updatePhotos(product.photoUrls))
                    }

                    onProduct(product)
                }
            }
        }
    }

    protected abstract fun onProduct(product: Product)

    override fun onDestroy() {
        GlobalScope.launch(Dispatchers.IO) {
            watcher?.close()
        }
        super.onDestroy()
    }

    protected suspend fun updatePhotos(update: List<String>): List<FileStream.FileDescription> {
        val photosState = photos.value ?: listOf()
        val removed = photosState.map { it.name } - update
        val inserted = update - photosState.map { it.name }
        val connection = ConnectionActivity.awaitConnection()
        val newPhotos = inserted.map { id ->
            val res = connection.fileStream.getFile(id)
            res.getOrShowError(requireContext())
        }.filterNotNull()

        val withoutRemoved = photosState.filter { !removed.contains(it.name) }
        return newPhotos + withoutRemoved
    }
}

class ViewPagerAdapter(
    fa: FragmentActivity,
    private val images: LiveData<List<FileStream.FileDescription>>
): FragmentStateAdapter(fa) {
    init {
        var prev = images.value ?: listOf()
        images.observe(fa) { list ->
            val inserted = list - prev
            if (inserted.isNotEmpty()) {
                notifyItemInserted(list.indexOf(inserted[0]))
            }
            val removed = prev - list
            if (removed.isNotEmpty()) {
                notifyItemRemoved(prev.indexOf(removed[0]) - 1)
            }
            prev = list
        }
    }

    override fun getItemCount(): Int {
        return images.value?.size ?: 0
    }

    override fun createFragment(position: Int): Fragment {
        if (!images.value.isNullOrEmpty())
            return ImageHolderFragment(images.value!![position])
        return PlaceholderImageFragment()
    }

    class ImageHolderFragment(private val image: FileStream.FileDescription): Fragment() {
        private lateinit var imageView: ImageView
        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View {
            imageView = ImageView(requireContext())
            return imageView
        }
        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)

            imageView.setImageBitmap(image.bitmap)
        }

    }

    class PlaceholderImageFragment: Fragment() {
        private lateinit var imageView: ImageView
        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View {
            imageView = ImageView(requireContext())
            return imageView
        }
        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)

            imageView.setImageResource(R.drawable.placeholder)
        }

    }
}