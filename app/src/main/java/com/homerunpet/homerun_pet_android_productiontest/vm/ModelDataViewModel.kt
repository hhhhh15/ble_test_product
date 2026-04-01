package com.homerunpet.homerun_pet_android_productiontest.vm

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.scopeNetLife
import com.drake.net.Get
import com.drake.net.Post
import com.homerunpet.homerun_pet_android_productiontest.base.BaseViewModel
import com.homerunpet.homerun_pet_android_productiontest.base.net.HmApi
import com.homerunpet.homerun_pet_android_productiontest.common.bean.HMBaseResponse
import com.homerunpet.homerun_pet_android_productiontest.data.Property
import com.homerunpet.homerun_pet_android_productiontest.data.Property1
import com.homerunpet.homerun_pet_android_productiontest.data.TotalData
import com.homerunpet.homerun_pet_android_productiontest.data.toProperty1

class ModelDataViewModel: BaseViewModel() {
    private val _totalData = MutableLiveData<TotalData?>()
    val totalData: LiveData<TotalData?> = _totalData

    private val _property1Data=MutableLiveData<List<Property1>>()
    val property1Data:LiveData<List<Property1>> = _property1Data
    private val TAG="ModelDataViewModel"

    fun setTotalData(data: TotalData?) {
        if (data ==null){
            Log.d(TAG, "setTotalData: 传过来的TotalData数据为null")
            return
        }
        _totalData.value = data  // 改用 value，同步赋值

        val properties=data.properties
        if (properties==null){
            Log.d(TAG, "setTotalData: 传过来的TotalData中的属性数据为null")
            return
        }
        propertyTo(properties)
    }
    private fun propertyTo(data:List<Property>){
        //改的是从获取物模型的接口的数据，但是没有找到获取物模型中那个key是存储当前的属性对应的值3.16
        val list= data.map { it.toProperty1() }
        _property1Data.value = list
        Log.d(TAG, "propertyTo: 1.查看赋值的_property1Data${_property1Data.value}")
    }

    private fun updateProperty(attribute:Map<String,Any>,data:List<Property1>){
        val property1Map=HashMap<String,Property1>()
        data.forEach{item->
            property1Map[item.identifier]=item
        }
        attribute.forEach{(key,value)->
            property1Map[key]?.current_value=value
        }
        //修改完property1Map中的property1的值，需要赋值返回给_property1Data
        _property1Data.value=property1Map.values.toList()
        Log.d(TAG, "updateProperty:2.查看转换后的_property1Data${_property1Data.value} ")
    }


    //看一下这个查询设备属性接口返回的啥数据
    private fun checkProperty(deviceSerial:String){
        scopeNetLife {
            val res=Get<HMBaseResponse<Map<String, Any>>>(HmApi.getDeviceProperty(deviceSerial)).await()
            val data = res.data ?: return@scopeNetLife
            val currentList = _property1Data.value ?: emptyList()
            updateProperty(data, currentList)
            Log.d(TAG, "checkProperty: 查看一下这个数据${res.data}" + "")
        }

    }
    //更新设备属性网络请求
    fun issuePropertyControlRequest(identifier: String,deviceSerial:String,value:Any){
        checkProperty(deviceSerial)
        Log.d(TAG, "issuePropertyControlRequest: 查看准备修改的数据 value = $value, type = ${value::class}")
        scopeNetLife {
            try {
                val res=Post<HMBaseResponse<Any>>(HmApi.getUpdateDeviceProperty(deviceSerial)) {
                    json(
                        mapOf(
                            "data" to mapOf(
                                identifier to value
                            )))
                }
                println("更新设备属性请求成功: $res")
            }catch (e:Exception){
                println("更新设备属性请求失败: ${e.message}")
            }

        }
    }

//   下发设备服务网络请求
    fun issueServiceControlRequest(deviceSerial:String,identifier:String,value:Any){
        scopeNetLife {
            try {
                val res=Post<HMBaseResponse<Any>>(HmApi.issueDeviceService(deviceSerial,identifier)){
                    json(mapOf(
                        "data" to mapOf(
                            "property1" to value
                        )))
                }
                println("下发设备服务请求成功: $res")
            }catch (e:Exception){
                println("下发设备服务请求失败: ${e.message}")
            }

        }
    }


}