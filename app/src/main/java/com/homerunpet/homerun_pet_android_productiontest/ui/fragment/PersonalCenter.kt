package com.homerunpet.homerun_pet_android_productiontest.ui.fragment

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import com.drake.net.Get
import com.drake.net.Post
import com.drake.net.utils.scopeNetLife
import com.homerunpet.homerun_pet_android_productiontest.base.BaseViewModel
import com.homerunpet.homerun_pet_android_productiontest.base.HMBaseFragment
import com.homerunpet.homerun_pet_android_productiontest.base.net.HmApi
import com.homerunpet.homerun_pet_android_productiontest.common.bean.HMBaseResponse
import com.homerunpet.homerun_pet_android_productiontest.data.UserMessage
import com.homerunpet.homerun_pet_android_productiontest.databinding.ProductionStaffMeBinding

class PersonalCenter : HMBaseFragment<BaseViewModel, ProductionStaffMeBinding>() {

    override fun initView(savedInstanceState: Bundle?) {
        scopeNetLife {
            val res=Get<HMBaseResponse<UserMessage>>(HmApi.PRODUCT_USER_MESSAGE).await()
            mBind.tvCurrentAccount.text =res.data.toString()
        }

    }

    override fun onBindViewClick() {
        mBind.btnLogout.setOnClickListener {
            //退出登录
            quitLogin()

        }

    }
    private fun quitLogin(){
        AlertDialog.Builder(requireContext())
//            .setTitle("SN绑定成功")
            .setMessage("确定要退出登录吗？")
            .setPositiveButton("确定"){dialog,_->
                dialog.dismiss()

                scopeNetLife {
                    Post<HMBaseResponse<Any>>(HmApi.PRODUCT_USER_LOGOUT)

                    // 启动模式为 FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK
                    val intent = android.content.Intent(
                        requireContext(),
                        com.homerunpet.homerun_pet_android_productiontest.ui.activity.LoginActivity::class.java
                    ).apply {
                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    startActivity(intent)
                }
            }
            .setPositiveButton("取消"){dialog,_->
                dialog.dismiss()
            }.show()
    }


}
