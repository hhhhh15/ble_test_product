package com.homerunpet.homerun_pet_android_productiontest.ui.fragment

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
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
import com.homerunpet.homerun_pet_android_productiontest.ble.model.Product
import com.homerunpet.homerun_pet_android_productiontest.ble.model.ProvisionProtocol
import com.homerunpet.homerun_pet_android_productiontest.ble.provision.ProvisionManager
import com.homerunpet.homerun_pet_android_productiontest.ble.scanner.HMBleScanner
import com.homerunpet.homerun_pet_android_productiontest.common.bean.HMBaseResponse
import com.homerunpet.homerun_pet_android_productiontest.databinding.ProductionStaffDisplayScanBleBinding
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import java.util.concurrent.TimeUnit

class BleDisplayFragment:HMBaseFragment<BaseViewModel,ProductionStaffDisplayScanBleBinding>() {
    private lateinit var adapter: DeviceListAdapter
    private lateinit var bleScanner: HMBleScanner
    private lateinit var provision :ProvisionManager
    private var connectDisposable: Disposable? = null

    private val disposable = CompositeDisposable()

    private var productMap: Map<String?, Product> = emptyMap()

    // 内部保存的设备列表，作为 Adapter 数据源
    private val mixProductList:MutableList<Product> = mutableListOf()
    private var isScan:Boolean=false


    private val REQUEST_BLE_PERMISSIONS = 1001
    private val BLE_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // Android 12+ (API 31+)
        arrayOf(
            android.Manifest.permission.BLUETOOTH_SCAN,
            android.Manifest.permission.BLUETOOTH_CONNECT,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        )
    } else {
        // Android 11 及以下
        arrayOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION
        )}
    // 检查蓝牙是否开启，再检查权限
    private val bluetoothEnableLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // 用户同意开启蓝牙，继续检查权限
            checkPermissionsAndScan()
        } else {
            // 用户拒绝开启蓝牙
            Toast.makeText(requireContext(), "需要开启蓝牙才能扫描设备", Toast.LENGTH_SHORT).show()
        }
    }


    override fun initView(savedInstanceState: Bundle?) {
        // 初始化 Adapter
        bleScanner = HMBleScanner.getInstance(requireContext().applicationContext)
        provision =ProvisionManager.getInstance(requireContext())

        loadProductsData()



        adapter = DeviceListAdapter{ mixProduct ->

            Log.d("displayFragment", "组装产品${mixProduct},点击连接产品: ${mixProduct.name}, pk: ${mixProduct.product_key}")
            Log.d("displayFragment", "看看组装的product中设备的配网协议${mixProduct.hmFastBleDevice?.protocol}: ")

            provision.setProduct(mixProduct)
            val productForConnect = provision.getProduct()
            Log.d("Provision", "点击连接, product=$productForConnect, MAC=${productForConnect?.hmFastBleDevice?.mac}")


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
            //  弄一个蓝牙连接中的加载页面，后面再说
            val fragment= BleConnectFragment()

            Log.d("displayFragment", "🚀 准备跳转，Fragment: $fragment")

            parentFragmentManager.beginTransaction()
                .replace(R.id.ContainView_product, fragment)
                .addToBackStack(null)
                .commit()
        }
        Log.d("displayFragment", "✅ commit 已执行")
        mBind.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        mBind.recyclerView.adapter = adapter


        //初始化就检查权限，开始搜索
        checkPermissionsAndScan()

    }

    override fun onBindViewClick() {
        mBind.btnStartScan.setOnClickListener {
            startScan()
        }
        mBind.btnStoptScan.setOnClickListener{
            isScan=false
            bleScanner.stopScan()
            disposable.clear()
            mixProductList.clear()
            adapter.submitList(emptyList())



        }

    }

    @SuppressLint("ServiceCast")
    private fun checkPermissionsAndScan() {
        val bluetoothManager = requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter

        // 先判断设备是否支持蓝牙
        if (bluetoothAdapter == null) {
            Toast.makeText(requireContext(), "该设备不支持蓝牙", Toast.LENGTH_SHORT).show()
            return
        }

        // 蓝牙未开启，弹出系统请求
        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            bluetoothEnableLauncher.launch(enableBtIntent)
            return
        }

        // 蓝牙已开启，再检查权限
        //筛选出没有授权的权限
        val missingPermissions = BLE_PERMISSIONS.filter {
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
        mixProductList.clear()
        if (!isScan){
            isScan=true
            adapter.submitList(mixProductList.toList()) // 先刷新空列表
            Log.d("displayFragment", "${productMap.keys}: ")


            val d = bleScanner.scanDevicesByProtocol(ProvisionProtocol.HOMERUN_CUSTOM)
                .delay(1000, TimeUnit.MILLISECONDS)   // 👈 稍微延迟，等 PK 解析完成
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ device ->

                    val mac = device.mac
//                    if (mac.isNullOrBlank()) {
//                        Log.d("BLE", "跳过无MAC地址设备")
//                        return@subscribe
//                    }
//                if (device.pk.isNullOrBlank()) {
//                    Log.d("BLE", "跳过无PK设备: mac=$mac")
//                    return@subscribe
//                }
                    Log.d(
                        "XXX",
                        "发现设备 -> mac=${device.mac}, pk=${device.pk}, name=${device.name}, rssi=${device.rssi}"
                    )

                        // 使用反射打印所有字段（终极大招）
                        Log.d("BLE1", "========== 目标设备完整信息 ==========")
                        device.javaClass.declaredFields.forEach { field ->
                            field.isAccessible = true
                            try {
                                val value = field.get(device)
                                Log.d("BLE1", "${field.name}: $value")
                            } catch (e: Exception) {
                                Log.d("BLE1", "${field.name}: 无法访问")
                            }
                        }
                        Log.d("BLE1", "======================================")


                    // 2. 从云端产品映射中查找对应产品并组装
                    val mixProduct = productMap[device.pk]?.copy(
                        product_key=device.pk?.lowercase(),
                        data_proto= 1,
                        deviceSerial = device.deviceSerial,   //product获取到的devcie的序列号不准确啊。
                        address = device.mac,
                        lastSeenTime = System.currentTimeMillis(),
                        hmFastBleDevice = device,
                    ) ?: Product(
                        // 没有云端信息时创建基础对象。
                        deviceSerial=device.deviceSerial,
                        product_key=device.pk?.lowercase(),
                        data_proto = 1,
                        address = device.mac,
                        name = device.name.takeIf { it.isNotBlank() } ?: "未知设备",
                        lastSeenTime = System.currentTimeMillis(),
                        hmFastBleDevice = device,
                    ).also {
                        Log.d("BLE", "未找到云端产品信息，使用设备基础信息: pk=${device.pk}, mac=$mac")
                    }

                    // 3. 检查列表中是否已存在(通过 MAC 匹配)
                    val existingIndex = mixProductList.indexOfFirst {
                        it.hmFastBleDevice?.mac == mac
                    }


                    if (existingIndex >= 0) {
                        // 更新已存在的设备
                        mixProductList[existingIndex] = mixProduct
                        Log.d("BLE", "更新设备: mac=$mac, pk=${device.pk}, rssi=${device.rssi}")
                    } else {
                        // 添加新设备
                        mixProductList.add(mixProduct)
                        Log.d("BLE", "新增设备: mac=$mac, pk=${device.pk}, name=${mixProduct.name}")
                    }
                    //2.12因为云端没有数据，不能返回到这个product_key.我先自己填充
//                    val targetMac = "D0:CF:13:E7:4E:AA"
//
//                    val product = mixProductList.find {
//                        it.hmFastBleDevice?.mac.equals(targetMac, ignoreCase = true)
//                    }
//                    product?.apply {
//                        product_key="a9015201"
//                        lastSeenTime = System.currentTimeMillis()
//                    }

                    // 4. 刷新 UI（必须是新 List）
                    adapter.submitList(mixProductList.toList())

                }, { error ->  // ⚠️ 问题2：缺少错误处理回调
                    Log.e("BLE", "扫描出错", error)
                }, {  // ⚠️ 问题3：缺少完成回调
                    Log.d("BLE", "扫描完成")
                })

            disposable.add(d)
        }


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
