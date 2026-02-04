package com.homerunpet.homerun_pet_android_productiontest

import android.os.Bundle
import androidx.fragment.app.Fragment
import com.homerunpet.homerun_pet_android_productiontest.base.BaseViewModel
import com.homerunpet.homerun_pet_android_productiontest.base.HMBaseActivity
import com.homerunpet.homerun_pet_android_productiontest.databinding.ActivityMainBinding
import com.homerunpet.homerun_pet_android_productiontest.ui.ConnectFragment
import com.homerunpet.homerun_pet_android_productiontest.ui.DisplayFragment

class MainActivity : HMBaseActivity<BaseViewModel, ActivityMainBinding>() {

    override fun initView(savedInstanceState: Bundle?) {
        switchFragment(DisplayFragment())

        mBind.bottomNav.setOnItemSelectedListener { item->
            when(item.itemId){
                R.id.nav_display->switchFragment(DisplayFragment())
                R.id.nav_net->switchFragment(ConnectFragment())
            }
            true
        }
    }
    fun switchFragment(fragment: Fragment){
        supportFragmentManager.beginTransaction()
            .replace(R.id.ContainView,fragment)
            .commit()
    }
}
