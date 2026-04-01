package com.homerunpet.homerun_pet_android_productiontest.adapter
import com.homerunpet.homerun_pet_android_productiontest.data.Property1

//属性事件接口
interface PropertyItemActionListener {
    fun onEnumItemSelected(item: Property1, value: String) {}
    fun onNumberValueSelected(item: Property1, value: Int) {}
    fun onBoolValueChanged(item: Property1, value: Boolean) {}

}