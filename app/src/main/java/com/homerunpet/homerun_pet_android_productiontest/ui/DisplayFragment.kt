package com.homerunpet.homerun_pet_android_productiontest.ui

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.drake.net.Get
import com.drake.net.utils.scopeNet
import com.homerunpet.homerun_pet_android_productiontest.R
import com.homerunpet.homerun_pet_android_productiontest.adapter.DeviceListAdapter
import com.homerunpet.homerun_pet_android_productiontest.base.BaseViewModel
import com.homerunpet.homerun_pet_android_productiontest.base.HMBaseFragment
import com.homerunpet.homerun_pet_android_productiontest.base.net.HmApi
import com.homerunpet.homerun_pet_android_productiontest.ble.model.CategoryProductData
import com.homerunpet.homerun_pet_android_productiontest.ble.model.HMFastBleDevice
import com.homerunpet.homerun_pet_android_productiontest.ble.model.Product
import com.homerunpet.homerun_pet_android_productiontest.ble.provision.ProvisionManager
import com.homerunpet.homerun_pet_android_productiontest.ble.scanner.HMBleScanner
import com.homerunpet.homerun_pet_android_productiontest.common.bean.HMBaseResponse
import com.homerunpet.homerun_pet_android_productiontest.databinding.DisplayScanBleBinding
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers

class DisplayFragment:HMBaseFragment<BaseViewModel,DisplayScanBleBinding>() {
    private lateinit var adapter: DeviceListAdapter
    private lateinit var bleScanner: HMBleScanner
    private lateinit var provision :ProvisionManager
    private var connectDisposable: Disposable? = null

    private val disposable = CompositeDisposable()

    private var productMap: Map<String?, Product> = emptyMap()


    // 内部保存的设备列表，作为 Adapter 数据源
    private val deviceList :MutableList<HMFastBleDevice> = mutableListOf()


    private val REQUEST_BLE_PERMISSIONS = 1001
    private val LOCATION_PERMISSION = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        arrayOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        )
    } else {
        arrayOf(
            android.Manifest.permission.BLUETOOTH_SCAN,
            android.Manifest.permission.BLUETOOTH_CONNECT
        )
    }

    override fun initView(savedInstanceState: Bundle?) {
        // 初始化 Adapter
        bleScanner = HMBleScanner.getInstance(requireContext().applicationContext)
        provision =ProvisionManager.getInstance(requireContext())

        loadProductsData()



        adapter = DeviceListAdapter{ device ->

            productMap[device.pk]?.let { product ->
                Log.d("display", "display中的adapter中的看看是否有${product},点击连接产品: ${product.name}, pk: ${product.product_key}")
                Log.d("display", "看看设备的配网协议${device.protocol}: ")
                // 这里收到的是 Product 对象
//                if (productMap[device.pk]){
//                    //开始组装Product类
//                    将device和product组装//
//                    val product=Product(product.)
//                }
                //拉取的productMap中有device.pk对应的，将这个device和这个product组装
                val localProduct = product.copy(
                    hmFastBleDevice = device,
                    deviceSerial = device.deviceSerial,
                    address = device.mac,
                    lastSeenTime = System.currentTimeMillis(), // 本地扫描到的时间
                    isSelected = true // UI 列表选中状态
                )

                //输出一下这个product的 product.hmFastBleDevice?.protocol 协议，得是自研的，才能创建这个
                Log.d("display", "看看产品的协议${localProduct.hmFastBleDevice?.protocol}")
                provision.setProduct(localProduct)

                connectDisposable = provision.connect()
                    .subscribeOn(Schedulers.io()) // 确保 connect() 在 IO 线程执行
                    .observeOn(AndroidSchedulers.mainThread()) // 结果回到主线程
                    .doOnSubscribe { Log.e("HM_UI", "[UI] connect 已开始订阅") }
                    .doOnNext { Log.e("HM_UI", "[UI] connect 发出事件: $it") }
                    .doOnError { Log.e("HM_UI", "[UI] connect 出错: ${it.message}") }
                    .doFinally { Log.e("HM_UI", "[UI] connect 流已结束") }
                    .subscribe(
                        { event -> Log.e("HM_UI", "[UI] connect 成功: $event") },
                        { error -> Log.e("HM_UI", "[UI] connect 捕获错误: ${error.message}") }
                    )
                provision.getDeviceInfo()
                //  弄一个蓝牙连接中的加载页面
            }
            val fragment=ConnectFragment()
            fragment.arguments=Bundle().apply{
                putString("device_sn",device.deviceSerial)
            }
            parentFragmentManager.beginTransaction()
                .replace(R.id.ContainView, fragment)
                .commit()


        }
        mBind.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        mBind.recyclerView.adapter = adapter


    }

    override fun onBindViewClick() {
        mBind.btnScan.setOnClickListener {
            checkPermissionsAndScan()
        }

    }
    // 检查权限并扫描
    private fun checkPermissionsAndScan() {
        val missingPermissions = LOCATION_PERMISSION.filter {
            ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(requireActivity(), missingPermissions.toTypedArray(), REQUEST_BLE_PERMISSIONS)
        } else {
            startScan()
        }
    }

    // 扫描蓝牙设备
    private fun startScan() {
        // 清空之前的列表
        deviceList.clear()
        adapter.submitList(deviceList.toList()) // 先刷新空列表
        Log.d("ooo", "${productMap.keys}: ")


        val d = bleScanner.scanDevices()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ device ->

                val mac = device.mac
                if (mac.isNullOrBlank()) return@subscribe

                val index = deviceList.indexOfFirst { it.mac == mac }

                if (index >= 0) {
                    // 2. 已存在设备 → 更新字段
                    val old = deviceList[index]

                    val merged = old.copy(
                        pk = device.pk ?: old.pk,
                        name = productMap[device.pk]?.name
                            ?: device.name,
                        rssi = device.rssi
                    )

                    deviceList[index] = merged
                    Log.d("BLE", "更新设备: mac=$mac pk=${merged.pk}")

                } else {
                    // 3. 新设备：过滤掉半成品
                    if (device.pk.isNullOrBlank()) {
                        Log.d("BLE", "跳过半成品设备 mac=$mac")
                        return@subscribe
                    }

                    productMap[device.pk]?.let {
                        device.name = it.name.toString()
                    }

                    deviceList.add(device)
                    Log.d("BLE", "新增设备: mac=$mac pk=${device.pk}")
                }

                // 4. 刷新 UI（必须是新 List）
                adapter.submitList(deviceList.toList())

            }, { error ->
                Log.e("BLE", "扫描出错", error)
            }, {
                Log.d("BLE", "扫描完成")
            })

        disposable.add(d)
    }
    private fun loadProductsData(){
        scopeNet {
            try {
                val response=Get<HMBaseResponse<List<CategoryProductData?>?>>(HmApi.CATEGORY_PRODUCTS).await()
                val categoryDataList = response.data ?: emptyList()

                // 1️⃣ 构建 productMap: product_key -> Product 对象
                 productMap = categoryDataList
                    .flatMap { it?.products ?: emptyList() }
                    .filterNotNull()
                    .mapNotNull { product ->
                        val key = product.product_key
                        if (key != null) key.uppercase() to product else null
                    }
                    .toMap()
                Log.d("ooo", "这个应该就是products了${productMap}: ")
            }catch (e:Exception){
                e.printStackTrace()
                println("${e.message}")
                Log.d("hhhh", "${e.message}: ")
                Toast.makeText(requireContext(),e.toString(), Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        disposable.clear() // 清理 RxJava 订阅，防止内存泄漏

    }
}
