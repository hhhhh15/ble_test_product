package com.homerunpet.homerun_pet_android_productiontest.activity

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.MotionEvent
import android.widget.EditText
import android.widget.Toast
import com.drake.net.Post
import com.drake.net.utils.scopeNetLife
import com.dylanc.longan.context
import com.homerunpet.homerun_pet_android_productiontest.MainActivity
import com.homerunpet.homerun_pet_android_productiontest.R
import com.homerunpet.homerun_pet_android_productiontest.base.BaseViewModel
import com.homerunpet.homerun_pet_android_productiontest.base.HMBaseActivity
import com.homerunpet.homerun_pet_android_productiontest.base.net.HmApi
import com.homerunpet.homerun_pet_android_productiontest.common.bean.HMBaseResponse
import com.homerunpet.homerun_pet_android_productiontest.data.LoginToken
import com.homerunpet.homerun_pet_android_productiontest.databinding.ActivityLoginBinding

class LoginActivity: HMBaseActivity<BaseViewModel, ActivityLoginBinding>() {
    private val tureAccount = "13760748522"
    private val turePassword = "homeRun@chance"
    private lateinit var loginToken: LoginToken
    private var isPasswordVisible = false

    override fun initView(savedInstanceState: Bundle?) {

    }
    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewClick() {
        mBind.etLoginPassword.setOnTouchListener { view, motionEvent ->
            if (motionEvent.action==MotionEvent.ACTION_UP){
                val editText=view as EditText
                val drawableEnd=editText.compoundDrawables[2]

                if (drawableEnd !=null){
                    val drawableWidth =drawableEnd.intrinsicWidth

                    val drawableStart = editText.width-editText.paddingEnd-drawableWidth

                    if(motionEvent.x >=drawableStart){
                        editText.performClick()
                        Log.d(TAG, "点到图标了")
                        //切换图标
                        togglePasswordVisibility()
                        return@setOnTouchListener   true
                    }
                }
            }
            false
        }
        mBind.btnLogin.setOnClickListener {
            login()
        }

    }
    private fun togglePasswordVisibility() {
        isPasswordVisible = !isPasswordVisible

        val editText = mBind.etLoginPassword

        if (isPasswordVisible) {
            // 显示密码
            editText.inputType = InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            editText.setCompoundDrawablesWithIntrinsicBounds(
                0, 0, R.drawable.ic_visibility, 0
            )
        } else {
            // 隐藏密码
            editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            editText.setCompoundDrawablesWithIntrinsicBounds(
                0, 0, R.drawable.ic_visibility_off, 0
            )
        }

        // 保持光标在末尾
        editText.setSelection(editText.text?.length ?: 0)
    }

    fun login(){
        val account = mBind.etLoginAccount.text.toString().trim()
        val password = mBind.etLoginPassword.text.toString().trim()

        Log.d(TAG, "login: 初次查看填写账户${account}和密码${password}")
        if(account!=tureAccount || password!=turePassword){
            Log.d(TAG, "login: 进入不正确的分支了，查看填写账户${account}和密码${password}")
            Toast.makeText(context,"密码或则账户错误，请检查",Toast.LENGTH_LONG).show()
            return
        }

        //网络请求
        scopeNetLife {
            val response=Post<HMBaseResponse<LoginToken>>(HmApi.PRODUCT_TEXT_LOGIN){
                json (mapOf(
                    "phone" to account,
                    "password" to password
                ))
            }.await()

            response.data?.let {
                loginToken = it

                val intent = Intent(context, MainActivity::class.java)
                startActivity(intent)
            } ?: run {
                Toast.makeText(context, "登录失败", Toast.LENGTH_SHORT).show()
            }

        }

    }



}