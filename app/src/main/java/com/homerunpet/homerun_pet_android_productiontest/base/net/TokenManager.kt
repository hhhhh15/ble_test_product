package com.homerunpet.homerun_pet_android_productiontest.base.net

object TokenManager {

    fun getAccessToken():String?{
        return SpManager.token
    }
    fun getRefreshToken():String?{
        return SpManager.refreshToken
    }
    //set更新数据都在SpManager当中

}