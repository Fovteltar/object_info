package com.example.objectinfo

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.objectinfo.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity: AppCompatActivity(R.layout.activity_main) {
    lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    private val selectObjLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            viewModel.setSelectedFileUri(uri)
//            val isObjType = uri.toString().endsWith(".obj")
//            if (isObjType) {
//                viewModel.setSelectedFileUri(uri)
//            }
//            else {
//                Toast.makeText(this, "Wrong file type(.obj required)", Toast.LENGTH_SHORT)
//                    .show()
//            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        observeLiveData()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        with(binding) {
            btnMainSelect.setOnClickListener {
                selectObjLauncher.launch("application/octet-stream")
            }
            etMainLimitCounter.addTextChangedListener(object: TextWatcher {
                override fun onTextChanged(text: CharSequence, p1: Int, p2: Int, p3: Int) {
                    try {
                        viewModel.setLimit(if (text.isNotEmpty()) {
                            val limit = text.toString().toDouble()
                            limit
                        }
                        else 0.0)
                    }
                    catch (_: Exception) {}
                }

                override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}

                override fun afterTextChanged(p0: Editable?) {}
            })
        }
    }

    private fun observeLiveData() {
        viewModel.selectedFileUriLiveData.observe(this) {
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    try {
                        viewModel.parseFile(contentResolver)
                    }
                    catch (e: Exception) {
                        e.printStackTrace()
                        Handler(mainLooper).post{
                            Toast.makeText(this@MainActivity, "Broken file", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
        viewModel.limitLiveData.observe(this) {
            lifecycleScope.launch {
                withContext(Dispatchers.Default) {
                    viewModel.recalculateModelInfo()
                }
            }
        }
        viewModel.modelInfoLiveData.observe(this) {
            with(binding) {
                tvMainTotal.text = it.total.toString()
                tvMainMin.text = String.format("%.6f", it.min)
                tvMainMax.text = String.format("%.6f", it.max)
                tvMainMoreThanLimit.text = it.moreThanLimit.toString()
            }
        }
    }
}