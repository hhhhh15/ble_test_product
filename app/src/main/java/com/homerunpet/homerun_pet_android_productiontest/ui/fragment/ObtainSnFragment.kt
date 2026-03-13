package com.homerunpet.homerun_pet_android_productiontest.ui.fragment


import android.content.Intent
import android.os.Bundle
import com.homerunpet.homerun_pet_android_productiontest.R

import com.homerunpet.homerun_pet_android_productiontest.base.BaseViewModel
import com.homerunpet.homerun_pet_android_productiontest.base.HMBaseFragment
import com.homerunpet.homerun_pet_android_productiontest.base.net.SpManager
import com.homerunpet.homerun_pet_android_productiontest.databinding.ProductionStaffDisplaySnBinding
import com.homerunpet.homerun_pet_android_productiontest.ui.activity.ScanDeviceActivity

class ObtainSnFragment:HMBaseFragment<BaseViewModel,ProductionStaffDisplaySnBinding>() {


    private val scanLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == ScanDeviceActivity.RESULT_OK) {   //这个RESULT_OK是ScanDeviceActivity的静态变量，
            val sn = result.data?.getStringExtra("SCAN_RESULT")
            mBind.tvSn.text = sn
            SpManager.deviceSn=sn
        }
    }

    override fun initView(savedInstanceState: Bundle?) {

    }

    override fun onBindViewClick() {
        mBind.imageScan.setOnClickListener{
            val intent = Intent(requireContext(),ScanDeviceActivity::class.java)
            scanLauncher.launch(intent)
        }
        mBind.bnEnterBle.setOnClickListener{
            parentFragmentManager.beginTransaction()
                .replace(R.id.ContainView_product,BleDisplayFragment())
                .commit()
        }
    }


}