package com.homerunpet.homerun_pet_android_productiontest.ui.activity

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.MotionEvent
import android.widget.EditText
import android.widget.Toast
import com.drake.net.Get
import com.drake.net.Post
import com.drake.net.utils.scopeNetLife
import com.dylanc.longan.context
import com.homerunpet.homerun_pet_android_productiontest.R
import com.homerunpet.homerun_pet_android_productiontest.base.BaseViewModel
import com.homerunpet.homerun_pet_android_productiontest.base.HMBaseActivity
import com.homerunpet.homerun_pet_android_productiontest.base.net.HmApi
import com.homerunpet.homerun_pet_android_productiontest.base.net.SpManager
import com.homerunpet.homerun_pet_android_productiontest.common.bean.HMBaseResponse
import com.homerunpet.homerun_pet_android_productiontest.data.ResponseToken
import com.homerunpet.homerun_pet_android_productiontest.data.UserMessage
import com.homerunpet.homerun_pet_android_productiontest.data.UserRole
import com.homerunpet.homerun_pet_android_productiontest.databinding.ActivityLoginBinding
import java.security.MessageDigest


class LoginActivity: HMBaseActivity<BaseViewModel, ActivityLoginBinding>() {
    private val tureAccount = "13760748522"
    private val turePassword = "homeRun@chance"
    private lateinit var responseToken: ResponseToken
    private var isPasswordVisible = false

    override fun initView(savedInstanceState: Bundle?) {
        //每次登录页面前，先把这个之前登录的token删除
        SpManager.clear()
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
        val password1 = mBind.etLoginPassword.text.toString().trim()

        val password=MD5Pwd(password1)


        Log.d(TAG, "login: 初次查看填写账户${account}和密码${password}")
        if(account!=tureAccount || password1!=turePassword){
            Log.d(TAG, "login: 进入不正确的分支了，查看填写账户${account}和密码${password1}")
            Toast.makeText(context,"密码或则账户错误，请检查",Toast.LENGTH_LONG).show()
            return
        }

        //网络请求
        scopeNetLife {
            val response=Post<HMBaseResponse<ResponseToken>>(HmApi.PRODUCT_TEXT_LOGIN){
                json (mapOf(
                    "phone" to account,
                    "password" to password
                ))
            }.await()

            response.data?.let {
                responseToken = it
                Log.d(TAG, "login: 查看登录返回的响应token${responseToken.access_token}和refresh_token${responseToken.refresh_token}")
                SpManager.token=responseToken.access_token
                SpManager.refreshToken=responseToken.refresh_token

//                val intent=Intent(context, ProductionStaffActivity::class.java)
//                    startActivity(intent)
                            //登录成功获取用户消息，确定身份
                Log.e(TAG, "准备进行用户信息请求") // 新增日志，检查这一行是否会输出
                val userMessage=Get<HMBaseResponse<UserMessage>>(HmApi.PRODUCT_USER_MESSAGE).await()

                //有返回的数据，但是这个role是null，这里假设一个数字身份好了
                val role = userMessage.data?.role ?: UserRole.ProductionTest
                Log.e(TAG, "用户信息请求完成，返回数据: ${userMessage.data}")

                role.let {
                    val next = when(it) {
                        UserRole.ProductionTest -> Intent(context, ProductionStaffActivity::class.java)
                        UserRole.Admin -> Intent(context, ProductionAdminActivity::class.java)
                        else -> Intent(context, LoginActivity::class.java)
                    }
                    startActivity(next)
                    Toast.makeText(context, "登录的角色是：$it", Toast.LENGTH_SHORT).show()
                }

            } ?: run {
                Toast.makeText(context, "登录失败", Toast.LENGTH_SHORT).show()
            }


        }

    }
    fun MD5Pwd(pwd: String): String {
//        val md5Key = "#q&8UT6eRV79ol4*" // 混合将要加密的MD5密码串
//        val appkey = "akbumgokcupum36vh0okfp7hkfz4m2m5" //签名加密
//
//        val str: String = appkey + pwd + md5Key
        // 计算 MD5 并转成 16 进制字符串
        val md5str = md5(pwd)

        return md5str
    }
    fun md5(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())//将字符串转换成字节数组，再对字节数组做哈希，输出16byte
        return bytes.joinToString("") { "%02x".format(it) } // 128bit的数据转32位的16进制
    }



}