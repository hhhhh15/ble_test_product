package com.homerunpet.homerun_pet_android_productiontest.ui.popup

import android.content.Context
import com.homerunpet.homerun_pet_android_productiontest.R
import com.homerunpet.homerun_pet_android_productiontest.databinding.PopupModelValueEditorBinding
import com.lxj.xpopup.core.CenterPopupView

class ModelValueEditorPopup(
    context: Context,
    private val title: String,//标题
    private val values: List<Int>,//放specs的数据
    private val initialValue: Int?,//初始specs的
    private val valueFormatter: (Int) -> String,
    private val onConfirm: (Int) -> Unit,
) : CenterPopupView(context) {
    private lateinit var binding: PopupModelValueEditorBinding

    override fun getImplLayoutId(): Int {
        return R.layout.popup_model_value_editor
    }

    override fun onCreate() {
        super.onCreate()
        binding = PopupModelValueEditorBinding.bind(popupImplView)

        val picker = binding.numberPicker

        binding.tvTitle.text = title

        val displayed = values.map(valueFormatter).toTypedArray()
        picker.minValue = 0
        picker.maxValue = (displayed.size - 1).coerceAtLeast(0)//coerceAtLeast最小值不能小于0
        picker.displayedValues = displayed //要展示的数据必须是数组Array
        picker.wrapSelectorWheel = false//到头就停，不能无限循环

        //显示的初始值，但是默认填写的是values的第一个了，3.10后续要改，怎么获取到索引值的？

        val initialIndex = initialValue?.let { v -> values.indexOfFirst { it == v } }?.takeIf { it >= 0 } ?: 0

        //coerceIn表示的是范围，将索引值限制在范围内，得到的也是值，picker.value表示选中

        picker.value = initialIndex.coerceIn(0, picker.maxValue)
        binding.btnCancel.setOnClickListener { dismiss() }
        binding.btnConfirm.setOnClickListener {
            val idx = picker.value.coerceIn(0, values.lastIndex.coerceAtLeast(0))
            onConfirm(values.getOrElse(idx) { values.firstOrNull() ?: 0 })
            dismiss()
        }
    }
}

