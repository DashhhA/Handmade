package com.market.handmades.ui.vendor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.market.handmades.R
import com.market.handmades.VendorViewModel
import com.market.handmades.remote.watchers.IWatcher
import com.market.handmades.ui.TintedProgressBar
import com.market.handmades.utils.ConnectionActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AboutMarketFragment: Fragment() {
    private val viewModel: VendorViewModel by activityViewModels()
    private var watcher: IWatcher<*>? = null
    companion object{
        const val titleId = R.string.market_about
         fun getInstance(): AboutMarketFragment {
             return AboutMarketFragment()
         }
    }

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_vendor_market_distplay, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val image: ImageView = view.findViewById(R.id.image)
        val name: TextView = view.findViewById(R.id.name)
        val city: TextView = view.findViewById(R.id.city)
        val description: TextView = view.findViewById(R.id.description)
        val tags: TextView = view.findViewById(R.id.tags)

        val progress = TintedProgressBar(requireContext(), view as ViewGroup)
        progress.show()
        GlobalScope.launch(Dispatchers.IO) {
            val marketId = viewModel.selectedMarket?.dbId ?: return@launch
            val marketRepository = ConnectionActivity.getMarketRepository()
            val marketWatcher = marketRepository.watchMarketRaw(marketId)
            watcher = marketWatcher

            withContext(Dispatchers.Main) {
                marketWatcher.getData().observe(viewLifecycleOwner) { res ->
                    progress.hide()
                    progress.remove()

                    val market = res.getOrShowError(requireContext()) ?: return@observe
                    val bitmap = market.image?.bitmap

                    if (bitmap != null) image.setImageBitmap(bitmap)
                    else image.setImageResource(R.drawable.placeholder)

                    name.text = market.name
                    city.text = market.city
                    description.text = market.description
                    tags.text = if (market.tags.isNotEmpty()) {
                        market.tags.reduce { acc, s -> "$acc, $s" }
                    } else getString(R.string.str_none)
                }
            }
        }
    }

    override fun onDestroyView() {
        GlobalScope.launch(Dispatchers.IO) {
            watcher?.close()
        }
        super.onDestroyView()
    }
}