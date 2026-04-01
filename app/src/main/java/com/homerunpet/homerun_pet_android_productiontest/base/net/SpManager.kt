package com.homerunpet.homerun_pet_android_productiontest.base.net

import android.content.Context
import android.content.SharedPreferences

object SpManager {
    //创建sharedparedes的文件名
    private const val SP_NAME="shu"
    private const val KEY_TOKEN="token"
    private const val KEY_REFRESH_TOKEN="refresh_token"

    //保存扫描获取到的sn
    private const val DEVICE_SN="sn"

    //保存获取到的用户账号
    private const val USER_ID="id"

    private lateinit var sp: SharedPreferences

    fun init(context: Context) {
        sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
    }

    var token: String?
        get() = sp.getString(KEY_TOKEN, null)
        set(value) {
            sp.edit().putString(KEY_TOKEN, value).apply()
        }

    var refreshToken: String?
        get() = sp.getString(KEY_REFRESH_TOKEN, null)
        set(value) {
            sp.edit().putString(KEY_REFRESH_TOKEN, value).apply()
        }

    var deviceSn:String?
        get() = sp.getString(DEVICE_SN,null)
        set(value) {
            sp.edit().putString(DEVICE_SN,value).apply()
        }

    var userId:String?
        get() = sp.getString(USER_ID,null)
        set(value) {
            sp.edit().putString(USER_ID,value).apply()
        }

    fun clear() {
        sp.edit().clear().apply()
    }
}