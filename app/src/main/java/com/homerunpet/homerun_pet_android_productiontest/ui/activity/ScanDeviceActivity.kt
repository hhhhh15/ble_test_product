package com.homerunpet.homerun_pet_android_productiontest.ui.activity


import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.animation.LinearInterpolator
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.homerunpet.homerun_pet_android_productiontest.R
import com.homerunpet.homerun_pet_android_productiontest.base.BaseViewModel
import com.homerunpet.homerun_pet_android_productiontest.base.HMBaseActivity
import com.homerunpet.homerun_pet_android_productiontest.databinding.ProductionStaffSnScannerBinding


class ScanDeviceActivity: HMBaseActivity<BaseViewModel,ProductionStaffSnScannerBinding>() {

    private lateinit var camera: androidx.camera.core.Camera
    private lateinit var barcodeScanner: BarcodeScanner
    private var isProcessing = false
    private var isFlashOn = false
    private var currentZoom = 1.0f
    private val requestPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCamera()  // 用户点了允许，开相机
        } else {
            finish()  // 用户拒绝了，关掉这个页面
        }
    }

    //申请相册注册器
    private val galleryLauncher=registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ){uri->
        uri?:return@registerForActivityResult
        val inputImage=InputImage.fromFilePath(this,uri)
        barcodeScanner.process(inputImage)
            .addOnSuccessListener { barcodes->
                if (barcodes.isNotEmpty()) {
                    val intent = Intent()
                    intent.putExtra("SCAN_RESULT", barcodes[0].rawValue)
                    setResult(RESULT_OK, intent)
                    finish()
                }
            }

    }


    override fun initView(savedInstanceState: Bundle?) {
        initScanner()

        // 先检查有没有权限
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
            == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            // 有权限，直接开相机
            startCamera()
        } else {
            // 没权限，去申请
            requestPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }

        mBind.btnPhoto.setOnClickListener{
            galleryLauncher.launch("image/*")
        }

        //手势识别器
        val scaleGestureDetector = android.view.ScaleGestureDetector(this,
            object : android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: android.view.ScaleGestureDetector): Boolean {
                    currentZoom *= detector.scaleFactor  //检测到缩放的比例
                    currentZoom = currentZoom.coerceIn(1.0f, 8.0f)  // 限制在1到8倍之间
                    camera.cameraControl.setZoomRatio(currentZoom)
                    return true
                }
            }
        )

        mBind.previewView.setOnTouchListener  {view,event ->
            scaleGestureDetector.onTouchEvent(event)
            view.performClick()
            true
        }

        startScanAnimation()
    }

    //动线扫描动画
    private fun startScanAnimation() {
        val scanLine = mBind.scanLine
        val boxHeight = resources.getDimensionPixelSize(R.dimen.scan_box_height) // 240dp转px

        val animator = ObjectAnimator.ofFloat(scanLine, "translationY", 0f, boxHeight.toFloat())
        animator.duration = 1500  // 1.5秒从上到下
        animator.repeatCount = ObjectAnimator.INFINITE  // 无限循环
        animator.repeatMode = ObjectAnimator.RESTART    // 每次从头开始
        animator.interpolator = LinearInterpolator()    // 匀速
        animator.start()
    }


    @OptIn(ExperimentalGetImage::class)
    private fun startCamera(){
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()  //因为是异步的，所以要避免阻塞主线程。
            val preview = buildPreview()
            val imageAnalysis =buildImageAnalysis()
            //设置使用哪个摄像头，后置
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            //将摄像头和这个页面绑定
             camera =cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                imageAnalysis
            )

            //camera属性
            //绑定相机后设置自动对焦
            camera.cameraControl.startFocusAndMetering(
                androidx.camera.core.FocusMeteringAction.Builder(
                    mBind.previewView.meteringPointFactory.createPoint(0.5f, 0.5f)
                ).apply {
                    setAutoCancelDuration(2, java.util.concurrent.TimeUnit.SECONDS)
                }.build()
            )
            // 开启/关闭闪光灯，并切换图标
            mBind.btnFlashToggle.setOnClickListener {
                isFlashOn = !isFlashOn
                // 切换图标(假设ic_flash_on/ic_flash_off分别为开和关闭状态的drawable)
                if (isFlashOn) {
                    mBind.btnFlashToggle.setImageResource(R.drawable.ic_flash_on)
                } else {
                    mBind.btnFlashToggle.setImageResource(R.drawable.ic_flash_off)
                }
                camera.cameraControl.enableTorch(isFlashOn)
            }
        }, ContextCompat.getMainExecutor(this))  //表示是在在主线程执行
    }

    //preview
    private fun buildPreview():Preview{
        return Preview.Builder().build().also {
            it.setSurfaceProvider(mBind.previewView.surfaceProvider)
        }
    }

    //imageAnalysis图像分析
    private fun buildImageAnalysis():ImageAnalysis{
        return ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)  //背压（Backpressure）策略
            .build().also {analysis->
                analysis .setAnalyzer( ContextCompat.getMainExecutor(this)){ //excutor还是改一下用的线程池吧
                    imageProxy->processImageProxy(imageProxy,analysis)
                }
        }

    }

    //imageproxy，处理相机帧
    @OptIn(ExperimentalGetImage::class)
    private fun processImageProxy(imageProxy:androidx.camera.core.ImageProxy,analysis: ImageAnalysis){
        if (isProcessing) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            isProcessing = true   // 开始识别,导入ml kit模型
            val inputImage = InputImage.fromMediaImage(
                mediaImage, imageProxy.imageInfo.rotationDegrees
            )
            //调用ML kit的模型识别
            barcodeScanner.process(inputImage)
                .addOnSuccessListener { barcodes->
                    if (barcodes.isNotEmpty()) {

                        val barcode = barcodes[0]//扫描的的二维码的第一个
                        val rawValue = barcode.rawValue

                        println("识别结果: $rawValue")

                        // 返回结果给 Fragment.看不懂
                        val intent = Intent()
                        intent.putExtra("SCAN_RESULT", rawValue)
                        setResult(RESULT_OK, intent)
                        finish() // 关闭现在这个扫描页

                        // ⭐ 识别成功后可以停止相机
                        analysis.clearAnalyzer()
                    }
                }
                .addOnFailureListener {
                    it.printStackTrace()
                }
                .addOnCompleteListener {
                    isProcessing = false
                    imageProxy.close()
                }
        }

    }

    private fun initScanner() {
        //1.配置条形码扫描器
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_QR_CODE,
                Barcode.FORMAT_AZTEC,
                Barcode.FORMAT_CODE_128,
                Barcode.FORMAT_EAN_13,
            )
            .build()

        //2.准备输入图像，在上面

        //3.获取扫描机实例
        barcodeScanner = BarcodeScanning.getClient(options)

    }
    override fun onDestroy() {
        super.onDestroy()
        barcodeScanner.close()
        camera.cameraControl.enableTorch(false)  // 页面关闭时确保灯关掉
    }

    //2.10不懂，设置变量RESULT_OK啥意思
    companion object {
        const val RESULT_OK = android.app.Activity.RESULT_OK
    }

}