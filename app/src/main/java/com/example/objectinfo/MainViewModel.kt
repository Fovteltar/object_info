package com.example.objectinfo

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import kotlin.math.pow
import kotlin.math.sqrt

data class ModelInfo(
    val polygonsArea: MutableList<Double> = mutableListOf(),
    val min: Double = 0.0,
    val max: Double = 0.0,
    val moreThanLimit: Int = 0,
    val total: Int = polygonsArea.size,
)

class MainViewModel: ViewModel() {
    private val _selectedFileUriLiveData: MutableLiveData<Uri> = MutableLiveData()
    val selectedFileUriLiveData: LiveData<Uri> get() = _selectedFileUriLiveData

    private val _limitLiveData: MutableLiveData<Double> = MutableLiveData()
    val limitLiveData: LiveData<Double> get() = _limitLiveData

    private val _modelInfoLiveData: MutableLiveData<ModelInfo> = MutableLiveData()
    val modelInfoLiveData: LiveData<ModelInfo> get() = _modelInfoLiveData

    fun setSelectedFileUri(uri: Uri) = _selectedFileUriLiveData.postValue(uri)

    fun setLimit(limit: Double) = _limitLiveData.postValue(limit)

    fun setModelInfo(modelInfo: ModelInfo) = _modelInfoLiveData.postValue(modelInfo)

    @Throws(Exception::class)
    fun parseFile(contentResolver: ContentResolver) {
        val vertices = mutableListOf<Triple<Double, Double, Double>>()
        val polygons = mutableListOf<MutableList<Int>>()

        val lineSequence = contentResolver.openInputStream(selectedFileUriLiveData.value!!).use {
            it?.bufferedReader()?.readText() ?: ""
        }
        for (line in lineSequence.split("\n")) {
            val lineItems = line.split(" ")

            if (lineItems.isNotEmpty()) {
                when(lineItems.first()) {
                    "v" -> {
                        val vertex = mutableListOf<Double>()
                        // only 3d vertices
                        for (i in 1..3) {
                            val coordinate = lineItems[i].trim().toDouble()
                            vertex.add(coordinate)
                        }
                        vertices.add(Triple(vertex[0], vertex[1], vertex[2]))
                    }
                    "f" -> {
                        val polygon = mutableListOf<Int>()
                        for (i in 1..lineItems.lastIndex) {
                            /*
                                Possible f formats:
                                1) f v1 v2 v3
                                2) f v1/vt1 v2/vt2 v3/vt3
                                3) f v1/vt1/vn1 v2/vt2/vn2 v3/vt3/vn3
                                4) f v1//vn1 v2//vn2 v3//vn3
                                Always getting v1, v2, v3 by splitting
                             */
                            val vertexIndex = lineItems[i].split("/").first().trim().toInt()
                            polygon.add(vertexIndex)
                        }
                        polygons.add(polygon)
                    }
                }
            }
        }

        val polygonsArea = mutableListOf<Double>()
        for (polygon in polygons) {
            val area = calculatePolygonArea(vertices, polygon)
            polygonsArea.add(area)
        }
        setModelInfo(ModelInfo(
            polygonsArea,
            polygonsArea.min(),
            polygonsArea.max(),
            if (limitLiveData.value != null) polygonsArea.count { it > limitLiveData.value!! }
                else polygonsArea.size
        ))
    }

    @Throws(IndexOutOfBoundsException::class)
    private fun calculatePolygonArea(
        vertices: MutableList<Triple<Double, Double, Double>>,
        polygon: MutableList<Int>
    ): Double {
        var totalArea = 0.0
        // taking indices: Triple(0, i, i + 1)
        for (i in 1 until polygon.lastIndex) {
            /*
                don't forget that vertex indexing in obj file starts with 1,
                but in list it starts from 0
            */
            val triangle = Triple(
                vertices[polygon[0] - 1],
                vertices[polygon[i] - 1],
                vertices[polygon[i + 1] - 1]
            )

            val calculateDistance = {v1: Triple<Double, Double, Double>, v2: Triple<Double, Double, Double> ->
                sqrt((v1.first - v2.first).pow(2) +
                        (v1.second - v2.second).pow(2) +
                        (v1.third - v2.third).pow(2)
                )
            }

            val triangleSides = with(triangle) {
                Triple(
                    calculateDistance(first, second),
                    calculateDistance(second, third),
                    calculateDistance(first, third),
                )
            }

            /** semi-perimeter */
            val s = with(triangleSides) {
                (first + second + third) / 2
            }
            // area by Heron's formula
            val triangleArea = with(triangleSides) {
                sqrt(s * (s - first) * (s - second) * (s - third))
            }
            totalArea += triangleArea
        }
        return totalArea
    }

    fun recalculateModelInfo() {
        if (modelInfoLiveData.value != null) {
            val modelInfo = modelInfoLiveData.value!!
            val updatedInfo = modelInfo.copy(
                moreThanLimit = if (limitLiveData.value != null) modelInfo.polygonsArea.count { it > limitLiveData.value!! }
                    else modelInfo.polygonsArea.size
            )
            _modelInfoLiveData.postValue(updatedInfo)
        }
    }
}