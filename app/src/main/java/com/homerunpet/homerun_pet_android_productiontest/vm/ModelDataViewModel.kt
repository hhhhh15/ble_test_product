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
import com.homerunpet.homerun_pet_android_productiontest.data.TotalData

class ModelDataViewModel: BaseViewModel() {
    private val _totalData = MutableLiveData<TotalData?>()
    val totalData: LiveData<TotalData?> = _totalData
    private val TAG="ModelDataViewModel"

    fun setTotalData(data: TotalData?) {
        _totalData.postValue(data)
    }
    //看一下这个查询设备属性接口返回的啥数据
    fun checkProperty(deviceSerial:String){
        scopeNetLife {
            val res=Get<HMBaseResponse<Any>>(HmApi.getDeviceProperty(deviceSerial)).await()
            Log.d(TAG, "checkProperty: 查看一下这个数据${res.data}" +
                    "")
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

    fun updateEnum(){

    }
}