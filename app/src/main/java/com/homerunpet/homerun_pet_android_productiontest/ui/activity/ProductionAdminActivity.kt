package com.homerunpet.homerun_pet_android_productiontest.ui.activity

import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.homerunpet.homerun_pet_android_productiontest.R
import com.homerunpet.homerun_pet_android_productiontest.base.BaseViewModel
import com.homerunpet.homerun_pet_android_productiontest.base.HMBaseActivity
import com.homerunpet.homerun_pet_android_productiontest.databinding.ProductionAdminMainBinding
import com.homerunpet.homerun_pet_android_productiontest.ui.fragment.TestReportDisplayFragment

class ProductionAdminActivity: HMBaseActivity<BaseViewModel,ProductionAdminMainBinding >(){
    override fun initView(savedInstanceState: Bundle?) {

    }
    override fun onBindViewClick() {
        mBind.btnQuery.setOnClickListener {
            val sn = mBind.etSn.text.toString().trim().orEmpty()
            val dn = mBind.etDn.text.toString().trim().orEmpty()
            // 查询报告的后端接口get是没有参数要求的，sn，dn为空都能查询，这里至少填写一个
            if (sn.isEmpty() || dn.isEmpty()) {
                Toast.makeText(this, "请输入SN和DN", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // 将dn传递到TestReportDisplayFragment
            val fra = TestReportDisplayFragment().apply {
                arguments = Bundle().apply { putString("dn", dn) }
            }
            //直接切换页面，由TestReportDisplayFragment自己去请求并展示数据
            switchFragment(fra)
        }
    }
    fun switchFragment(fragment: Fragment){
        supportFragmentManager.beginTransaction()
        .replace(R.id.containView_test, fragment)
            .commit()
    }

}