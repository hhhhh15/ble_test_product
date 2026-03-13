package com.homerunpet.homerun_pet_android_productiontest.ui.activity

import android.os.Bundle
import androidx.fragment.app.Fragment
import com.homerunpet.homerun_pet_android_productiontest.R
import com.homerunpet.homerun_pet_android_productiontest.base.BaseViewModel
import com.homerunpet.homerun_pet_android_productiontest.base.HMBaseActivity
import com.homerunpet.homerun_pet_android_productiontest.databinding.ProductionStaffMainBinding
import com.homerunpet.homerun_pet_android_productiontest.ui.fragment.ObtainSnFragment
import com.homerunpet.homerun_pet_android_productiontest.ui.fragment.PersonalCenter
import com.homerunpet.homerun_pet_android_productiontest.ui.fragment.PhysicalModelControlFragment

class ProductionStaffActivity: HMBaseActivity<BaseViewModel,ProductionStaffMainBinding>() {

    override fun initView(savedInstanceState: Bundle?) {
        //默认是参测页面
        if (savedInstanceState == null) {
            switchFragment(ObtainSnFragment())
        }
    }

    override fun onBindViewClick() {
        mBind.bottomNav.setOnItemSelectedListener {
            when(it.itemId){
                R.id.nav_test->switchFragment(ObtainSnFragment())
                R.id.nav_mine->switchFragment((PersonalCenter()))
                R.id.nav_model_display->switchFragment(PhysicalModelControlFragment())
            }
            true
        }
    }

    private fun switchFragment(fragment: Fragment){
        supportFragmentManager.beginTransaction()
            .replace(R.id.ContainView_product,fragment)
            .commit()
    }
}