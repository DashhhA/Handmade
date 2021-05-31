package com.market.handmades.ui.vendor

import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.market.handmades.R
import com.market.handmades.model.ProductDTO
import com.market.handmades.remote.FileStream
import com.market.handmades.remote.ServerRequest
import com.market.handmades.ui.MessageProgressBar
import com.market.handmades.utils.AsyncResult
import com.market.handmades.utils.ConnectionActivity
import com.market.handmades.utils.MTextWatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AddProductActivity:  ConnectionActivity() {
    private val PICK_IMAGE = 1
    private val viewModel: AddProductViewModel by viewModels()
    private var marketId: String? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vendor_add_product)
        marketId = intent.getStringExtra(MarketProductsFragment.EXTRA_MARKET_ID)
        val tags = intent.getStringArrayExtra(MarketProductsFragment.EXTRA_MARKET_TAGS)
        if (marketId == null) {
            AlertDialog.Builder(this)
                    .setMessage(R.string.error_internal)
                    .setPositiveButton(R.string.str_positive) { _, _ -> finish() }
                    .show()
        }

        val pagerAdapter = AddProductPagerAdapter( this, viewModel) {
            val getIntent = Intent(Intent.ACTION_GET_CONTENT)
            getIntent.type = "image/*"

            val pickIntent = Intent(Intent.ACTION_PICK)
            pickIntent.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*")

            val chooserIntent = Intent.createChooser(getIntent, "Select Image")
            chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(pickIntent))

            startActivityForResult(pickIntent, PICK_IMAGE)
        }
        val pager: ViewPager2 = findViewById(R.id.pager)
        pager.adapter = pagerAdapter

        val btnCancel: Button = findViewById(R.id.btn_cancel)
        val btnSubmit: Button = findViewById(R.id.btn_submit)
        val name: EditText = findViewById(R.id.name)
        val price: EditText = findViewById(R.id.price)
        val quantity: EditText = findViewById(R.id.quantity)
        val description: EditText = findViewById(R.id.description)
        val tag: Spinner = findViewById(R.id.tag)

        tag.adapter = ArrayAdapter<String>(
            this,
            android.R.layout.simple_dropdown_item_1line,
            (tags ?: arrayOf()) + arrayOf(getString(R.string.str_none))
        )

        btnCancel.setOnClickListener { finish() }

        viewModel.formValid.observe(this) { btnSubmit.isEnabled = it }
        viewModel.nameError.observe(this) { id ->
            if (id != null) name.error = getString(id)
        }
        viewModel.priceError.observe(this) { id ->
            if (id != null) price.error = getString(id)
        }
        viewModel.quantityError.observe(this) { id ->
            if (id != null) quantity.error = getString(id)
        }
        viewModel.descriptionError.observe(this) { id ->
            if (id != null) description.error = getString(id)
        }
        viewModel.bind(name, price, quantity, description)

        val progress = MessageProgressBar(this, "Saving product")
        GlobalScope.launch(Dispatchers.IO) {
            val connection = awaitConnection()
            val click = View.OnClickListener {
                progress.show()
                GlobalScope.launch(Dispatchers.IO) save@{
                    val market = marketId ?: return@save
                    val photoUrls = (viewModel.imageUris.value?.map {
                        val inpS = this@AddProductActivity.contentResolver.openInputStream(it)
                        val drawable = Drawable.createFromStream(inpS, it.toString())
                        it to FileStream.FileDescription(drawable)
                    } ?: listOf()).toMap()

                    val selected = tag.selectedItem.toString()
                    val t = if ( selected == getString(R.string.str_none)) null else selected

                    val product = ProductDTO(
                            marketId = market,
                            name = viewModel.name!!,
                            description = viewModel.description!!,
                            price = viewModel.price!!,
                            quantity = viewModel.quantity!!,
                            photoUrls = photoUrls.values.map { it.name },
                            tag = t
                    )
                    val pRes = connection.requestServer(ServerRequest.AddProduct(product))
                    pRes.getOrShowError(this@AddProductActivity)
                    if (pRes is AsyncResult.Error) return@save

                    for (fd in photoUrls.values) {
                        connection.fileStream.putFile(fd) { res ->
                            res.getOrShowError(this@AddProductActivity)
                        }
                    }
                    withContext(Dispatchers.Main) {
                        progress.dismiss()
                        this@AddProductActivity.finish()
                    }
                }
            }
            withContext(Dispatchers.Main) {
                btnSubmit.setOnClickListener(click)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_IMAGE && resultCode == RESULT_OK && data != null) {
            val imgUri = data.data
            if (imgUri != null)
                viewModel.imageUris.value = viewModel.imageUris.value?.plus(imgUri)
        }
    }
}

class AddProductViewModel: ViewModel() {
    var name: String? = null
    var price: Float? = null
    var quantity: Int? = null
    var description: String? = null
    val imageUris: MutableLiveData<List<Uri>> = MutableLiveData(listOf())

    val nameError: MutableLiveData<Int?> = MutableLiveData(null)
    val priceError: MutableLiveData<Int?> = MutableLiveData(null)
    val quantityError: MutableLiveData<Int?> = MutableLiveData(null)
    val descriptionError: MutableLiveData<Int?> = MutableLiveData(null)

    val formValid: MutableLiveData<Boolean> = MutableLiveData(false)

    fun bind(name: EditText, price: EditText, quantity: EditText, description: EditText) {
        name.setText(this.name)
        price.setText(this.price?.toString())
        quantity.setText(this.quantity?.toString())
        description.setText(this.description)

        name.addTextChangedListener(MTextWatcher {
            this.name = it
            validate()
        })
        price.addTextChangedListener(MTextWatcher {
            this.price = it.toFloatOrNull()
            validate()
        })
        quantity.addTextChangedListener(MTextWatcher {
            this.quantity = it.toIntOrNull()
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
            nameError.value = R.string.str_should_not_empty
            return false
        }
        return true
    }
    private fun validatePrice(): Boolean {
        if (price == null) {
            priceError.value = R.string.str_should_not_empty
            return false
        }
        return true
    }
    private fun validateQuantity(): Boolean {
        if (quantity == null) {
            quantityError.value = R.string.str_should_not_empty
            return false
        }
        return true
    }
    private fun validateDescription(): Boolean {
        if (description.isNullOrBlank()) {
            descriptionError.value = R.string.str_should_not_empty
            return false
        }
        return true
    }

    private fun validate() {
        var valid = validateName()
        valid = validatePrice() && valid
        valid = validateQuantity() && valid
        valid = validateDescription() && valid
        formValid.value = valid
    }
}

private class AddProductPagerAdapter(
        fa: FragmentActivity,
        private val viewModel: AddProductViewModel,
        private val onClickListener: View.OnClickListener,
): FragmentStateAdapter(fa) {
    init {
        var prev = viewModel.imageUris.value ?: listOf()
        viewModel.imageUris.observe(fa) { list ->
            val inserted = list - prev
            if (inserted.isNotEmpty()) {
                notifyItemInserted(list.indexOf(inserted[0]) + 1)
            }
            val removed = prev - list
            if (removed.isNotEmpty()) {
                notifyItemRemoved(prev.indexOf(removed[0]))
            }
            prev = list
        }
    }
    override fun getItemCount(): Int {
        return (viewModel.imageUris.value?.size ?: 0) + 1
    }

    override fun createFragment(position: Int): Fragment {
        val imageUris: List<Uri> = viewModel.imageUris.value ?: listOf()
        return if (position > 0) {
            ImageHolderFragment(imageUris[position - 1])
        } else {
            AddButtonFragment(onClickListener)
        }
    }
}

class AddButtonFragment(private val onClickListener: View.OnClickListener): Fragment() {
    private lateinit var imageButton: ImageButton
    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        imageButton = ImageButton(requireContext())
        imageButton.setOnClickListener(onClickListener)
        return imageButton
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        imageButton.setImageResource(R.drawable.baseline_add_24)
    }
}

class ImageHolderFragment(private val imageUri: Uri): Fragment() {
    private lateinit var imageView: ImageView
    private val viewModel: AddProductViewModel by activityViewModels()
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

        imageView.setImageURI(imageUri)
        imageView.setOnClickListener {
            AlertDialog.Builder(requireContext())
                    .setMessage(R.string.v_add_product_delete_image)
                    .setPositiveButton(R.string.str_positive) { _, _ ->
                        viewModel.imageUris.value = viewModel.imageUris.value!! - imageUri
                    }
                    .setNegativeButton(R.string.str_cancel) { _, _ -> }
                    .show()
        }
    }
}