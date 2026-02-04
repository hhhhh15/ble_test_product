package com.homerunpet.homerun_pet_android_productiontest.vm

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.homerunpet.homerun_pet_android_productiontest.base.BaseViewModel

class ConnectViewModel: BaseViewModel() {
    private val _ssid = MutableLiveData<String>()
    val ssid: LiveData<String> get() = _ssid

    private val _password = MutableLiveData<String>()
    val password: LiveData<String> get() = _password






}