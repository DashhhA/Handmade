package com.market.handmades.ui.vendor

import android.app.Activity.RESULT_OK
import android.app.AlertDialog
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ListView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.navigation.Navigation
import com.market.handmades.R
import com.market.handmades.remote.FileStream
import com.market.handmades.ui.TintedProgressBar
import com.market.handmades.utils.AsyncResult
import com.market.handmades.utils.ConnectionActivity
import com.market.handmades.utils.ConnectionObject
import com.market.handmades.utils.MTextWatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream


class AddMarketFragment: Fragment() {
    private val viewModel: AddMarketViewModel by viewModels()
    private val PICK_IMAGE = 1
    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_vendor_add_market, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val navigation = Navigation.findNavController(view)

        val tagListAdapter = TagListAdapter(requireContext())
        tagListAdapter.addView.setOnClickListener {
            addTag(tagListAdapter)
        }

        val btnCancel: Button = view.findViewById(R.id.btn_cancel)
        val btnSubmit: Button = view.findViewById(R.id.btn_submit)
        val btnTags: Button = view.findViewById(R.id.btn_tags)
        btnCancel.setOnClickListener {
            navigation.navigateUp()
        }
        btnTags.setOnClickListener {
            editTags(tagListAdapter)
        }

        btnSubmit.setOnClickListener {
            GlobalScope.launch(Dispatchers.IO) {
                val marketRepository = ConnectionActivity.getMarketRepository()
                val uri = viewModel.imageUri.value
                val image = if (uri != null) {
                    val inpS = requireContext().contentResolver.openInputStream(uri)
                    Drawable.createFromStream(inpS, uri.toString())
                } else {
                    null
                }
                val progress = withContext(Dispatchers.Main) {
                    val pb = TintedProgressBar(requireContext(), view as ViewGroup)
                    pb.show()
                    return@withContext pb
                }
                val res = marketRepository.addMarket(
                        viewModel.name!!,
                        viewModel.city!!,
                        viewModel.description!!,
                        tagListAdapter.getAll(),
                        if (image != null) FileStream.FileDescription(image) else null
                )
                withContext(Dispatchers.Main) {
                    progress.hide()
                    progress.remove()
                    val succes = res.getOrShowError(requireContext())
                    if (succes != null) navigation.navigateUp()
                }
            }
        }

        val imageView: ImageView = view.findViewById(R.id.image)
        imageView.setOnClickListener {
            val getIntent = Intent(Intent.ACTION_GET_CONTENT)
            getIntent.type = "image/*"

            val pickIntent = Intent(Intent.ACTION_PICK)
            pickIntent.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*")

            val chooserIntent = Intent.createChooser(getIntent, "Select Image")
            chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(pickIntent))

            startActivityForResult(pickIntent, PICK_IMAGE)
        }

        val nameText: EditText = view.findViewById(R.id.name)
        val cityText: EditText = view.findViewById(R.id.city)
        val descriptionText: EditText = view.findViewById(R.id.description)

        viewModel.bind(nameText, cityText, descriptionText)

        viewModel.imageUri.observe(this.viewLifecycleOwner) { uri ->
            if (uri == null) {
                imageView.setImageResource(R.drawable.placeholder)
            } else {
                imageView.setImageURI(uri)
            }
        }
        viewModel.formValid.observe(this.viewLifecycleOwner) { valid ->
            btnSubmit.isEnabled = valid
        }
        viewModel.nameError.observe(this.viewLifecycleOwner) { id ->
            if (id != null) nameText.error = getString(id)
        }
        viewModel.cityError.observe(this.viewLifecycleOwner) { id ->
            if (id != null) cityText.error = getString(id)
        }
        viewModel.descriptionError.observe(this.viewLifecycleOwner) { id ->
            if (id != null) descriptionText.error = getString(id)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_IMAGE && resultCode == RESULT_OK && data != null) {
            val imgUri = data.data
            viewModel.imageUri.value = imgUri
        }
    }

    private fun editTags(adapter: TagListAdapter) {
        val list = ListView(requireContext())
        list.adapter = adapter
        AlertDialog.Builder(requireContext())
            .setView(list)
            .setTitle(R.string.v_add_market_edit_tags_title)
            .setPositiveButton(R.string.button_positive) { _, _ -> }
            .show()
    }

    private fun addTag(adapter: TagListAdapter) {
        val text = EditText(requireContext())
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.v_add_market_add_tag_title)
            .setNegativeButton(R.string.str_cancel) { _, _, -> }
            .setPositiveButton(R.string.button_positive) { _, _ ->
                if (text.text.isNotBlank()) adapter.add(text.text.toString())
            }
            .setView(text)
            .show()
    }
}

class AddMarketViewModel: ViewModel() {
    var name: String? = null
    var city: String? = null
    var description: String? = null
    val imageUri: MutableLiveData<Uri?> = MutableLiveData(null)
    val nameError: MutableLiveData<Int?> = MutableLiveData(null)
    val cityError: MutableLiveData<Int?> = MutableLiveData(null)
    val descriptionError: MutableLiveData<Int?> = MutableLiveData(null)
    val formValid: MutableLiveData<Boolean> = MutableLiveData(false)

    fun bind(
            name: EditText,
            city: EditText,
            description: EditText,
    ) {
        name.setText(this.name)
        city.setText(this.city)
        description.setText(this.description)

        name.addTextChangedListener(MTextWatcher {
            this.name = it
            validate()
        })
        city.addTextChangedListener(MTextWatcher {
            this.city = it
            validate()
        })
        description.addTextChangedListener(MTextWatcher {
            this.description = it
            validate()
        })
        validate()
    }

    private fun validateName(): Boolean {
        if (name.isNullOrBlank()) {
            nameError.value = R.string.v_add_market_name_empty
            return false
        }
        return true
    }

    private fun validateCity(): Boolean {
        if (city.isNullOrBlank()) {
            cityError.value = R.string.v_add_market_city_empty
            return false
        }
        return true
    }

    private fun validateDescription(): Boolean {
        if (description.isNullOrBlank()) {
            descriptionError.value = R.string.v_add_market_descr_empty
            return false
        }
        return true
    }

    private fun validate() {
        var valid = validateName()
        valid = validateCity() && valid
        valid = validateDescription() && valid
        formValid.value = valid
    }
}