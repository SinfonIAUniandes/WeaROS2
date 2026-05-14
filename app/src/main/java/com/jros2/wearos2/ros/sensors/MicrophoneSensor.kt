package com.jros2.wearos2.ros.sensors

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import audio_common_msgs.AudioData
import com.jros2.wearos2.ros.WearSensor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import us.ihmc.jros2.ROS2Node
import us.ihmc.jros2.ROS2Publisher
import us.ihmc.jros2.ROS2Topic

class MicrophoneSensor : WearSensor {
    override val id = "microphone"
    override val name = "Mic"
    override val topicName = "audio"
    var resolvedTopicName: String = topicName

    override val enabled = MutableStateFlow(true)
    private val _messageCount = MutableStateFlow(0L)
    override val messageCount: StateFlow<Long> = _messageCount
    private val _displayValue = MutableStateFlow("16kHz mono PCM16")
    override val displayValue: StateFlow<String> = _displayValue

    private var publisher: ROS2Publisher<AudioData>? = null
    private var audioRecord: AudioRecord? = null
    private val audioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var audioJob: Job? = null

    override fun start(node: ROS2Node, context: Context) {
        publisher = node.createPublisher(ROS2Topic(resolvedTopicName, AudioData::class.java))
        val sampleRateHz = 16_000
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val minBuffer = AudioRecord.getMinBufferSize(sampleRateHz, channelConfig, encoding)
        if (minBuffer <= 0) {
            _displayValue.value = "Audio error"
            return
        }
        val bufferSize = (minBuffer * 2).coerceAtLeast(sampleRateHz / 10)
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRateHz,
            channelConfig,
            encoding,
            bufferSize
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            _displayValue.value = "Mic unavailable"
            recorder.release()
            return
        }
        audioRecord = recorder
        try {
            recorder.startRecording()
        } catch (e: SecurityException) {
            _displayValue.value = "Permission denied"
            recorder.release()
            audioRecord = null
            return
        }
        _displayValue.value = "Streaming audio"
        audioJob = audioScope.launch {
            val readBuffer = ByteArray(bufferSize)
            val msg = AudioData()
            publishWavHeader(msg)
            while (isActive) {
                if (!enabled.value) {
                    delay(50)
                    continue
                }
                val read = recorder.read(readBuffer, 0, readBuffer.size)
                if (read <= 0) continue
                val data = msg.getData()
                data.clear()
                for (i in 0 until read) {
                    data.add(readBuffer[i])
                }
                publisher?.publish(msg)
                _messageCount.value++
                _displayValue.value = "$read B"
            }
        }
    }

    private fun publishWavHeader(msg: AudioData) {
        val header = buildWavHeader()
        val data = msg.getData()
        data.clear()
        for (b in header) {
            data.add(b)
        }
        publisher?.publish(msg)
    }

    private fun buildWavHeader(): ByteArray {
        val channelCount = 1
        val bitsPerSample = 16
        val sampleRateHz = 16_000
        val blockAlign = (channelCount * bitsPerSample / 8).toShort()
        val byteRate = sampleRateHz * blockAlign.toInt()
        return ByteArray(44).apply {
            this[0] = 'R'.code.toByte()
            this[1] = 'I'.code.toByte()
            this[2] = 'F'.code.toByte()
            this[3] = 'F'.code.toByte()
            writeIntLE(4, 36)
            this[8] = 'W'.code.toByte()
            this[9] = 'A'.code.toByte()
            this[10] = 'V'.code.toByte()
            this[11] = 'E'.code.toByte()
            this[12] = 'f'.code.toByte()
            this[13] = 'm'.code.toByte()
            this[14] = 't'.code.toByte()
            this[15] = ' '.code.toByte()
            writeIntLE(16, 16)
            writeShortLE(20, 1)
            writeShortLE(22, channelCount.toShort())
            writeIntLE(24, sampleRateHz)
            writeIntLE(28, byteRate)
            writeShortLE(32, blockAlign)
            writeShortLE(34, bitsPerSample.toShort())
            this[36] = 'd'.code.toByte()
            this[37] = 'a'.code.toByte()
            this[38] = 't'.code.toByte()
            this[39] = 'a'.code.toByte()
            writeIntLE(40, 0)
        }
    }

    private fun ByteArray.writeShortLE(offset: Int, value: Short) {
        this[offset] = (value.toInt() and 0xFF).toByte()
        this[offset + 1] = ((value.toInt() shr 8) and 0xFF).toByte()
    }

    private fun ByteArray.writeIntLE(offset: Int, value: Int) {
        this[offset] = (value and 0xFF).toByte()
        this[offset + 1] = ((value shr 8) and 0xFF).toByte()
        this[offset + 2] = ((value shr 16) and 0xFF).toByte()
        this[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }

    override fun stop() {
        audioJob?.cancel()
        audioJob = null
        audioRecord?.let { recorder ->
            if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                recorder.stop()
            }
            recorder.release()
        }
        audioRecord = null
        publisher = null
    }
}