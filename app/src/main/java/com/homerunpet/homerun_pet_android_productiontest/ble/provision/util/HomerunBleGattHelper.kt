package com.homerunpet.homerun_pet_android_productiontest.ble.provision.util

import android.util.Log
import com.bhm.ble.BleManager
import com.bhm.ble.data.BleDescriptorGetType
import com.bhm.ble.device.BleDevice
import io.reactivex.rxjava3.core.Observable

/**
 * 霍曼自研协议 BLE GATT 通信辅助类
 *
 * ## 功能说明
 * 封装 BleCore (BhmBle) 的底层操作，将其回调模式转换为 RxJava 的 `Observable` 流。
 *
 * ## 特性
 * - **RxJava 适配**: 链式调用，方便组合配网流程。
 * - **全链路日志**: 统一 TAG `HomerunProvision`，详细记录操作状态及数据 Hex。
 * - **防抖与并发**: 对于连续写操作，建议外部配合 concatMap 使用。
 */
object HomerunBleGattHelper {

    private const val TAG = "HomerunProvision"

    /**
     * 设置 MTU (最大传输单元)
     *
     * @param mtu
     * @return Observable发射实际协商的 MTU 大小
     */
    fun setMtu(bleDevice: BleDevice, mtu: Int): Observable<Int> {
        return Observable.create { emitter ->
            BleManager.get().setMtu(bleDevice, mtu) {
                onMtuChanged { bleDevice: BleDevice, mtu: Int ->
                    if (!emitter.isDisposed) {
                        emitter.onNext(mtu)
                        emitter.onComplete()
                    }
                }

                onSetMtuFail { bleDevice: BleDevice, throwable: Throwable ->
                    if (!emitter.isDisposed) {
                        // 如果是超时（通常意味着底层任务队列阻塞或链路异常），建议抛出 Error
                        // 这样可以让配网流程及时中断，而不是在后续发生数据截断或解析崩溃
                        if (throwable.message?.contains("timeout", ignoreCase = true) == true) {
                            emitter.onError(throwable)
                        } else {
                            // 其他情况（如设备明确不支持 MTU 协商），降级回默认值 23 尝试继续
                            emitter.onNext(23)
                            emitter.onComplete()
                        }
                    }
                }
            }
        }
    }

    // region ---------------- 核心操作 (Read/Write) ----------------

    /**
     * 读取数据 (Read Characteristic)
     *
     * @return Observable发射读取到的 ByteArray 后结束 (onComplete)
     */
    fun read(
        bleDevice: BleDevice,
        serviceUuid: String,
        charUuid: String
    ): Observable<ByteArray> {
        Log.d(TAG, "[Gatt读] 开始: S=$serviceUuid, C=$charUuid")
        return Observable.create { emitter ->
            BleManager.get().readData(
                bleDevice,
                serviceUuid,
                charUuid
            ) {
                onReadSuccess { _, data ->
                    Log.d(TAG, "[Gatt读] 成功: ${dataToHex(data)}")
                    if (!emitter.isDisposed) {
                        emitter.onNext(data)
                        emitter.onComplete()
                    }
                }

                onReadFail { _, throwable ->
                    Log.e(TAG, "[Gatt读] 失败: ${throwable.message}")
                    if (!emitter.isDisposed) {
                        emitter.onError(Throwable("读取失败: ${throwable.message}"))
                    }
                }
            }
        }
    }

    /**
     * 发送数据 (Write Characteristic)
     *
     * @return Observable发射 true 后结束 (onComplete)
     */
    fun write(
        bleDevice: BleDevice,
        serviceUuid: String,
        charUuid: String,
        data: ByteArray
    ): Observable<Boolean> {
        Log.d(TAG, "[Gatt写] 开始: S=$serviceUuid, C=$charUuid, Data=${dataToHex(data)}")
        return Observable.create { emitter ->
            BleManager.get().writeData(
                bleDevice,
                serviceUuid,
                charUuid,
                data
            ) {
                onWriteSuccess { _, current, total, _ ->
                    // BleCore 的 onWriteSuccess 会回调分包进度，这里只在完成时响应
                    if (current == total) {
                        Log.d(TAG, "[Gatt写] 成功")
                        if (!emitter.isDisposed) {
                            emitter.onNext(true)
                            emitter.onComplete()
                        }
                    }
                }

                onWriteFail { _, _, _, throwable ->
                    Log.e(TAG, "[Gatt写] 失败: ${throwable.message}")
                    if (!emitter.isDisposed) {
                        emitter.onError(Throwable("写入失败: ${throwable.message}"))
                    }
                }
            }
        }
    }

    // endregion

    // region ---------------- 通知监听 (Notify/Indicate) ----------------

    /**
     * 开启 Notify 通知
     *
     * @param onDataReceived 数据回调函数
     * @return Observable发射 true 表示"开启监听成功" (onComplete)
     */
    fun enableNotify(
        bleDevice: BleDevice,
        serviceUuid: String,
        charUuid: String,
        onDataReceived: (ByteArray) -> Unit
    ): Observable<Boolean> {
        Log.d(TAG, "[Notify] 开启监听: C=$charUuid")
        return Observable.create { emitter ->
            BleManager.get().notify(
                bleDevice,
                serviceUuid,
                charUuid,
                BleDescriptorGetType.Default
            ) {
                onNotifySuccess { _, _ ->
                    Log.d(TAG, "[Notify] 开启成功")
                    if (!emitter.isDisposed) {
                        emitter.onNext(true)
                        // 注意：这里不调用 onComplete，因为 BhmBle 可能会复用回调? 
                        // 但 Observable<Boolean> 语义是"开启操作完成"，所以可以结束。
                        // 实际数据是通过 onDataReceived 回调出去的。
                        emitter.onComplete()
                    }
                }

                onNotifyFail { _, _, throwable ->
                    Log.e(TAG, "[Notify] 开启失败: ${throwable.message}")
                    if (!emitter.isDisposed) {
                        emitter.onError(Throwable("Notify失败: ${throwable.message}"))
                    }
                }

                onCharacteristicChanged { _, _, data ->
                    Log.d(TAG, "[Notify] 收到数据: ${dataToHex(data)}")
                    onDataReceived(data)
                }
            }
        }
    }

    /**
     * 开启 Indicate 通知 (推荐)
     *
     * @return 连续数据流 Observable<ByteArray>。注意：该流不会自动结束，需外部管理生命周期。
     */
    fun indicate(
        bleDevice: BleDevice,
        serviceUuid: String,
        charUuid: String,
        onEnableSuccess: () -> Unit = {}
    ): Observable<ByteArray> {
        Log.d(TAG, "[Indicate] 开启监听: C=$charUuid")
        return Observable.create { emitter ->
            BleManager.get().indicate(
                bleDevice,
                serviceUuid,
                charUuid,
                BleDescriptorGetType.Default
            ) {
                onIndicateSuccess { _, _ ->
                    Log.d(TAG, "[Indicate] 开启成功")
                    onEnableSuccess()
                }

                onIndicateFail { _, _, throwable ->
                    Log.e(TAG, "[Indicate] 开启失败: ${throwable.message}")
                    if (!emitter.isDisposed) {
                        emitter.onError(Throwable("Indicate失败: ${throwable.message}"))
                    }
                }

                onCharacteristicChanged { _, _, data ->
                    Log.d(TAG, "[Indicate] 收到数据: ${dataToHex(data)}")
                    if (!emitter.isDisposed) {
                        emitter.onNext(data)
                    }
                }
            }
        }
    }

    // endregion

    // region ---------------- 调试与工具 ----------------

    /**
     * 打印 GATT 服务列表
     */
    /**
     * 打印并返回 GATT 服务列表
     */
    fun dumpGattServices(bleDevice: BleDevice): String {
        val gatt = BleManager.get().getBluetoothGatt(bleDevice) ?: return "Dump失败: GATT is null"

        val sb = StringBuilder()
        sb.append("=== 服务列表: ${bleDevice.deviceAddress} ===\n")
        gatt.services.forEach { service ->
            sb.append("Service: ${service.uuid}\n")
            service.characteristics.forEach { char ->
                val props = StringBuilder()
                if ((char.properties and android.bluetooth.BluetoothGattCharacteristic.PROPERTY_READ) != 0) props.append("R ")
                if ((char.properties and android.bluetooth.BluetoothGattCharacteristic.PROPERTY_WRITE) != 0) props.append("W ")
                if ((char.properties and android.bluetooth.BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) props.append("N ")
                if ((char.properties and android.bluetooth.BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) props.append("I ")

                sb.append("  -> Char: ${char.uuid} [$props]\n")
            }
        }
        sb.append("=== Dump End ===")
        return sb.toString()
    }

    private fun dataToHex(data: ByteArray): String {
        return data.joinToString("") { "%02X".format(it) }
    }

    // endregion
}
