package com.market.handmades.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.LiveData
import com.market.handmades.AdminViewModel
import com.market.handmades.R
import com.market.handmades.model.MarketRaw
import com.market.handmades.ui.TintedProgressBar
import com.market.handmades.utils.AsyncResult

class MarketInfoFragment(
    private val marketData: LiveData<AsyncResult<MarketRaw>>
): Fragment() {
    private val viewModel: AdminViewModel by activityViewModels()
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_vendor_market_distplay, container, false)
    }

    companion object {
        const val titleId = R.string.market_about
        fun getInstance(marketData: LiveData<AsyncResult<MarketRaw>>): MarketInfoFragment =
            MarketInfoFragment(marketData)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val image: ImageView = view.findViewById(R.id.image)
        val name: TextView = view.findViewById(R.id.name)
        val city: TextView = view.findViewById(R.id.city)
        val description: TextView = view.findViewById(R.id.description)
        val progress = TintedProgressBar(requireContext(), view as ViewGroup)
        progress.show()
        marketData.observe(viewLifecycleOwner) { res ->
            progress.hide()
            progress.remove()

            val market = res.getOrShowError(requireContext()) ?: return@observe
            val bitmap = market.image?.bitmap

            if (bitmap != null) image.setImageBitmap(bitmap)
            else image.setImageResource(R.drawable.placeholder)

            name.text = market.name
            city.text = market.city
            description.text = market.description
        }
    }
}