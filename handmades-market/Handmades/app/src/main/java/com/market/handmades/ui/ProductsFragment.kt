package com.market.handmades.ui

import android.os.Bundle
import android.view.View
import com.market.handmades.model.MarketRaw
import com.market.handmades.model.Product
import com.market.handmades.model.ProductWP
import com.market.handmades.remote.FileStream
import com.market.handmades.utils.ConnectionActivity

abstract class ProductsFragment: ListFragment<ProductWP>() {
    private val photos: MutableList<FileStream.FileDescription> = mutableListOf()

    suspend fun newProductWPhoto(product: Product): ProductWP {
        if (product.photoUrls.isEmpty())
            return ProductWP(product, null)
        val knownPhotos = photos.map { it.name }
        val found = product.photoUrls.map { knownPhotos.indexOf(it) }
        val ind = found.find { it > 0 }
        if (ind != null)
            return ProductWP(product, photos[ind])
        val connection = ConnectionActivity.awaitConnection()
        val res = connection.fileStream.getFile(product.photoUrls[0])
        val newPhoto = res.getOrShowError(requireContext())

        if (newPhoto != null) {
            photos.add(newPhoto)
            return ProductWP(product, newPhoto)
        }

        return ProductWP(product, null)
    }
}