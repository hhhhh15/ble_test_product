package com.homerunpet.homerun_pet_android_productiontest.ble.provision.util

import android.util.Log
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * 霍曼自研协议 BLE 加解密工具类
 * 负责数据的 AES 加密和解密
 */
object HomerunBleCipher {

    private const val TRANSFORMATION = "AES/ECB/PKCS5Padding"

    /**
     * 加密数据 (AES-ECB)
     * 使用标准 PKCS5Padding (兼容 PKCS7)
     * @param data 原始数据
     * @param key 16字节密钥
     */
    fun encrypt(data: ByteArray, key: ByteArray): ByteArray {
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val secretKeySpec = SecretKeySpec(key, "AES")
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec)
            cipher.doFinal(data)
        } catch (e: Exception) {
            e.printStackTrace()
            ByteArray(0)
        }
    }

    /**
     * 解密数据 (AES-ECB)
     * 使用标准 PKCS5Padding 自动去除填充
     * @param encryptedData 加密数据
     * @param key 16字节密钥
     */
    fun decrypt(encryptedData: ByteArray, key: ByteArray): ByteArray {
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val secretKeySpec = SecretKeySpec(key, "AES")
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec)
            cipher.doFinal(encryptedData)
        } catch (e: Exception) {
            e.printStackTrace()
            ByteArray(0)
        }
    }

    /**
     * 辅助方法：生成密钥
     * 将 32位十六进制字符串 (Product Secret) 解析为 16字节密钥
     */
    fun generateKey(secretHex: String): ByteArray {
        return hexToBytes(secretHex)
    }

    /**
     * Hex String -> ByteArray
     */
    fun hexToBytes(hexString: String): ByteArray {
        val hex = hexString.trim()
        val len = hex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) +
                    Character.digit(hex[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    /**
     * ByteArray -> Hex String (用于日志打印对比)
     */
    fun bytesToHex(bytes: ByteArray): String {
        val sb = StringBuilder()
        for (b in bytes) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }


    /**
     * 验证加密算法输出
     */
    fun runSelfTest() {
        val testKeyHex = "0b10591a92a7486aa41aafbca5bbfc3f"
        val testPayload = "{\"d\":[\"Wi-Fi Name1|-59\",\"Wi-Fi Name2|-24\",\"Wi-Fi Name3|-36\",\"Wi-Fi Name4|-36\",\"Wi-Fi Name5|-36\",\"Wi-Fi Name6|-36\",\"Wi-Fi Name7|-36\",\"Wi-Fi Name3|-36\",\"Wi-Fi Name8|-36\",\"Wi-Fi Name9|-36\",\"Wi-Fi Name10|-36\",\"Wi-Fi Name11|-36\",\"Wi-Fi Name12|-36\",\"Wi-Fi Name13|-36\",\"Wi-Fi Name14|-36\",\"Wi-Fi Name15|-36\",\"Wi-Fi Name16|-36\",\"Wi-Fi Name17|-36\",\"Wi-Fi Name18-36\",\"Wi-Fi Name19|-36\",\"Wi-Fi Name20|-36\"]}"

        val key = generateKey(testKeyHex)
        val encrypted = encrypt(testPayload.toByteArray(Charsets.UTF_8), key)
        val resultHex = bytesToHex(encrypted)

        Log.d("HomerunBleCipher", "=== Self Test Start ===")
        Log.d("HomerunBleCipher", "Key: $testKeyHex")
        Log.d("HomerunBleCipher", "Payload: $testPayload")
        Log.d("HomerunBleCipher", "Encrypted Result: $resultHex")
        Log.d("HomerunBleCipher", "=== Self Test End ===")
    }
}
