package com.homerunpet.homerun_pet_android_productiontest.adapter
import com.homerunpet.homerun_pet_android_productiontest.data.Property

//属性事件接口
interface PropertyItemActionListener {
    fun onEnumItemSelected(item: Property, value: String) {}
    fun onNumberValueSelected(item: Property, value: Int) {}
    fun onBoolValueChanged(item: Property, value: Boolean) {}

}