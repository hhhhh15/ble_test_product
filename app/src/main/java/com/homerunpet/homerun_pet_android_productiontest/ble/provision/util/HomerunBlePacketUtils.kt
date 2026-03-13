package com.homerunpet.homerun_pet_android_productiontest.ble.provision.util

/**
 * 对应数据传输协议包结构
 *
 * ## 物理层结构 (Physical Packet Structure)
 * 每个 BLE 包 (Max 244 bytes) 结构如下:
 * - **Header (5 bytes)**:
 *   - `Byte 0`: `0xCC` (帧头 1)
 *   - `Byte 1`: `0x33` (帧头 2)
 *   - `Byte 2`: `Length` (1 byte = Control 长度 + Payload 长度)
 *   - `Byte 3`: `Control High` (MsgID | Frames | Seq 的高8位)
 *   - `Byte 4`: `Control Low` (MsgID | Frames | Seq 的低8位)
 * - **Payload (Max 239 bytes)**: 数据分片
 *
 * ## 逻辑层结构 (Logical Transfer Packet)
 * 解包后提取出的字段:
 */
data class HomerunBleTransferPacket(
    val msgId: Int,      // 消息ID (6-bit)
    val frames: Int,     // 总帧数 (5-bit)
    val seq: Int,        // 当前帧序号 (5-bit)
    val payload: ByteArray // 有效数据
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as HomerunBleTransferPacket

        if (msgId != other.msgId) return false
        if (frames != other.frames) return false
        if (seq != other.seq) return false
        if (!payload.contentEquals(other.payload)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = msgId
        result = 31 * result + frames
        result = 31 * result + seq
        result = 31 * result + payload.contentHashCode()
        return result
    }
}

/**
 * 解析结果封装
 * @param packets 成功解析出的包列表
 * @param remainingData 未能解析的剩余字节 (需外部缓存等待下次拼接)
 */
data class BleParseResult(
    val packets: List<HomerunBleTransferPacket>,
    val remainingData: ByteArray
)

/**
 * 协议封包工具类
 */
object HomerunBlePacketUtils {

    private var currentMsgId = 1

    /**
     * 获取下一个 MsgID (1~63 循环)
     */
    @Synchronized
    fun getNextMsgId(): Int {
        val id = currentMsgId++
        if (currentMsgId > 63) currentMsgId = 1
        return id
    }

    /**
     * 计算累加和校验码 (CRC8)
     * 算法: (ControlBytes + PayloadBytes) Sum % 256
     */
    private fun calcChecksum(ctrlHigh: Byte, ctrlLow: Byte, payload: ByteArray): Byte {
        var sum = (ctrlHigh.toInt() and 0xFF) + (ctrlLow.toInt() and 0xFF)
        for (b in payload) {
            sum += (b.toInt() and 0xFF)
        }
        return (sum % 256).toByte()
    }

    /**
     * 协议层开销 (字节):
     * 3 (ATT Header) + 2 (Preamble) + 1 (Length) + 2 (Control) + 1 (CRC) = 9
     */
    const val MTU_OVERHEAD = 9

    /**
     * 封包: 将加密后的 ByteArray 切分为符合协议的分包列表
     * 结构: HEAD(CC 33) + LEN(1) + CTRL(2) + PAYLOAD + CRC(1)
     *
     * 关于 Payload 长度计算:
     * 场景 A (高效模式 - 默认):
     * - BLE MTU = 247 字节
     * - Max Payload = 247 - MTU_OVERHEAD = 238 字节
     * 
     * 场景 B (兼容模式/文档示例):
     * - BLE MTU = 64 字节
     * - Max Payload = 64 - MTU_OVERHEAD = 55 字节
     *
     * 示例 (场景 B, maxPayload=55):
     * Input (128 bytes): 51381CDC927C69D09C8087D4E08D1967DB2FA7842E926BC346795CBA92234E102A3F43F2232D0B13037EA3433421E9C47EF5AFA5FBF807237589EF9A8CB2C357B3EEAFD22AEE5EFA3EF43FE388ADDEAC0350335AF12FDBD0218BD8603A7664065B40BE643341D81C5AB0EC808B40174FA3A6BEE8C82BF43B2AD22A75C03505F7
     * Output (3 Packets, MsgID=1):
     * Pkt 1 (Seq 1):
     *   Hex: CC333A046151381CDC927C69D09C8087D4E08D1967DB2FA7842E926BC346795CBA92234E102A3F43F2232D0B13037EA3433421E9C47EF5AFA5FBF807D2
     *   (Len:3A, Ctrl:0461, Pay:55 bytes, CRC:D2)
     * Pkt 2 (Seq 2):
     *   Hex: CC333A0462237589EF9A8CB2C357B3EEAFD22AEE5EFA3EF43FE388ADDEAC0350335AF12FDBD0218BD8603A7664065B40BE643341D81C5AB0EC808B401C
     *   (Len:3A, Ctrl:0462, Pay:55 bytes, CRC:1C)
     * Pkt 3 (Seq 3):
     *   Hex: CC33150463174FA3A6BEE8C82BF43B2AD22A75C03505F76A
     *   (Len:15, Ctrl:0463, Pay:18 bytes, CRC:6A)
     *
     * @param data 加密后的完整数据
     * @param maxPayload 单包最大载荷 (应由调用者根据当前协商的 MTU 动态计算传入，例如 MTU - 9)
     */
    fun packData(data: ByteArray, maxPayload: Int): List<ByteArray> {
        // 计算总帧数
        val totalFrames = (data.size + maxPayload - 1) / maxPayload
        val frames = if (totalFrames == 0) 1 else totalFrames
        
        val msgId = getNextMsgId()
        val packets = mutableListOf<ByteArray>()

        for (seq in 0 until frames) {
            val start = seq * maxPayload
            val end = if (data.isEmpty()) 0 else Math.min(start + maxPayload, data.size)
            val chunk = if (data.isEmpty()) ByteArray(0) else data.copyOfRange(start, end)

            // Length: 2字节Control + Payload长度 + 1字节CRC (根据定义：从 MsgID 到 CRC)
            // 0x3A (58) = 2 (Ctrl) + 55 (Payload) + 1 (CRC)
            val length = 2 + chunk.size + 1

            // Header 16-bit: MsgID(6)|Frames(5)|Seq(5)
            // msgId(6) << 10 | frames(5) << 5 | seq(5)
            // 注意：协议文档中 Seq 从 1 开始 (例如 0x0461 表示 frame=3, seq=1)
            val header16 = ((msgId and 0x3F) shl 10) or ((frames and 0x1F) shl 5) or ((seq + 1) and 0x1F)
            val ctrlHigh = (header16 shr 8).toByte()
            val ctrlLow = (header16 and 0xFF).toByte()

            // 计算 CRC (MsgID 到 Payload)
            val crc = calcChecksum(ctrlHigh, ctrlLow, chunk)

            // Struct: Head(2)+Len(1)+Ctrl(2)+Payload(N)+CRC(1)
            val packet = ByteArray(2 + 1 + 2 + chunk.size + 1)
            // 1. 帧头 Preamble
            packet[0] = 0xCC.toByte()
            packet[1] = 0x33.toByte()
            // 2. 长度 Length
            packet[2] = length.toByte()
            // 3. 控制字 Control (Big Endian)
            packet[3] = ctrlHigh
            packet[4] = ctrlLow

            // 4. 数据载荷 Payload
            if (chunk.isNotEmpty()) {
                System.arraycopy(chunk, 0, packet, 5, chunk.size)
            }

            // 5. CRC
            packet[packet.size - 1] = crc

            packets.add(packet)
        }
        return packets
    }

    /**
     * 解包: 解析 BLE 数据 (支持断包缓存与粘包处理)
     * 校验 33 CC 头 (接收方向反转)
     * 校验 CRC
     *
     * @param data 当前累积的原始字节数据
     * @return 解析结果 (packets + remaining)
     */
    fun parsePacket(data: ByteArray): BleParseResult {
        val packets = mutableListOf<HomerunBleTransferPacket>()
        var offset = 0

        while (offset < data.size) {
            // 1. 剩余数据是否足够解析 Header (至少 5 字节: Head(2)+Len(1)+Ctrl(2))
            // 实际上由于增加了CRC，最小包长度应该是 6 (Head(2)+Len(1)+Ctrl(2)+CRC(1)) where Payload=0
            if (data.size - offset < 6) {
                // 不够最小包长度，保留等待后续数据
                break
            }

            // 2. 校验帧头 (33 CC)
            if (data[offset] != 0x33.toByte() || data[offset + 1] != 0xCC.toByte()) {
                // 头不对，向后滑 1 字节尝试寻找下一个头
                offset++
                continue
            }

            // 3. 读取长度 Length (Control + Payload + CRC)
            val lengthArg = data[offset + 2].toInt() and 0xFF
            val packetTotalSize = 3 + lengthArg // 2(Head) + LenValue

            // 4. 校验剩余数据是否足够完整包
            if (data.size - offset < packetTotalSize) {
                // 数据不够完整包，保留等待后续数据
                break
            }

            // 5. 解析控制字
            val ctrlHigh = data[offset + 3]
            val ctrlLow = data[offset + 4]
            val header16 = ((ctrlHigh.toInt() and 0xFF) shl 8) or (ctrlLow.toInt() and 0xFF)

            val msgId = (header16 shr 10) and 0x3F
            val frames = (header16 shr 5) and 0x1F
            val seq = header16 and 0x1F

            // 6. 提取 Payload
            // Payload 长度 = lengthArg - 2 (Control) - 1 (CRC)
            val payloadLen = lengthArg - 3
            if (payloadLen < 0) {
                // 长度异常，跳过头部继续
                offset++
                continue
            }

            // 获取 CRC (包尾)
            val receivedCrc = data[offset + packetTotalSize - 1]

            // 提取 Payload 字节
            val payload = ByteArray(payloadLen)
            System.arraycopy(data, offset + 5, payload, 0, payloadLen)

            // 7. 校验 CRC
            val calculatedCrc = calcChecksum(ctrlHigh, ctrlLow, payload)
            if (calculatedCrc != receivedCrc) {
                // CRC 校验失败，丢弃该包
                // 注意：这里我们选择丢弃该包并移动 offset，而不是清空整个 buffer，
                // 这样可以避免因误判导致后续合法包丢失。
                // 但也可以选择 offset++ 来尝试重新同步头部。鉴于头部 33CC 已经匹配，
                // 且 Length 也读出来了，直接跳过整包通常是安全的。
                // 也可以 Log error
                offset += packetTotalSize
                continue
            }

            packets.add(HomerunBleTransferPacket(msgId, frames, seq, payload))

            // 移动 Offset，处理下一包
            offset += packetTotalSize
        }

        // 计算剩余未处理的数据
        val remaining = if (offset < data.size) {
            data.copyOfRange(offset, data.size)
        } else {
            ByteArray(0)
        }

        return BleParseResult(packets, remaining)
    }
}
