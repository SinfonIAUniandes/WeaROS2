package com.jros2.wearos2.ros.sensors

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import audio_common_msgs.AudioData
import com.jros2.wearos2.ros.BaseWearSensor
import com.jros2.wearos2.ros.reliableQos
import us.ihmc.fastddsjava.cdr.idl.IDLByteSequence
import us.ihmc.jros2.ROS2Node
import us.ihmc.jros2.ROS2SubscriptionCallback
import us.ihmc.jros2.ROS2Subscription
import us.ihmc.jros2.ROS2Topic
import java.util.concurrent.LinkedBlockingQueue

/**
 * Subscribes to an audio topic (same wire format as [MicrophoneSensor]: an optional
 * one-off 44-byte WAV header message followed by raw 16kHz mono PCM16 chunks) and
 * plays the incoming audio through the watch speaker.
 *
 * The Fast-DDS subscription callback runs on a native reader thread, so it only copies
 * each chunk into [audioQueue] and returns immediately. A dedicated [playbackThread]
 * drains the queue into [AudioTrack] with the blocking write, so DDS delivery is never
 * stalled by audio back-pressure (which would otherwise starve the track into silence).
 */
class AudioPlayerSensor : BaseWearSensor("audio_playback", "Speaker", "play_audio", "No audio yet") {
    private var subscription: ROS2Subscription<AudioData>? = null
    private var audioTrack: AudioTrack? = null
    private var playbackThread: Thread? = null
    private val audioQueue = LinkedBlockingQueue<ByteArray>()

    @Volatile
    private var receivedChunks = 0L

    override fun start(node: ROS2Node, context: Context) {
        val sampleRateHz = 16_000
        val channelConfig = AudioFormat.CHANNEL_OUT_MONO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val minBuffer = AudioTrack.getMinBufferSize(sampleRateHz, channelConfig, encoding)
        if (minBuffer <= 0) {
            _displayValue.value = "Playback unavailable"
            return
        }
        val track = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
            AudioFormat.Builder()
                .setSampleRate(sampleRateHz)
                .setChannelMask(channelConfig)
                .setEncoding(encoding)
                .build(),
            (minBuffer * 2).coerceAtLeast(sampleRateHz / 5),
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )
        if (track.state != AudioTrack.STATE_INITIALIZED) {
            _displayValue.value = "Speaker unavailable"
            track.release()
            return
        }
        track.setVolume(AudioTrack.getMaxVolume())
        audioTrack = track
        audioQueue.clear()
        receivedChunks = 0L
        track.play()
        _displayValue.value = "Listening for audio"

        playbackThread = Thread {
            try {
                while (!Thread.currentThread().isInterrupted) {
                    val chunk = audioQueue.take()
                    val played = track.write(chunk, 0, chunk.size)
                    if (played > 0) {
                        _messageCount.value++
                    } else if (played < 0) {
                        _displayValue.value = "Write error $played"
                    }
                }
            } catch (_: InterruptedException) {
                // Normal shutdown
            }
        }.apply {
            name = "audio-playback"
            start()
        }

        val callback = ROS2SubscriptionCallback<AudioData> { reader ->
            // Read a fresh instance each time (matching the known-good jros2 demo).
            // Reusing one AudioData across reads risks a deserialize error on this
            // native reader thread, which would silently kill all further callbacks.
            // The try/catch is belt-and-suspenders against the same failure mode.
            try {
                val received = reader.read() ?: return@ROS2SubscriptionCallback
                if (!enabled.value) return@ROS2SubscriptionCallback
                val data = received.getData()
                val size = data.size()
                if (size <= 0 || isWavHeader(data, size)) return@ROS2SubscriptionCallback
                val pcm = ByteArray(size)
                for (i in 0 until size) {
                    pcm[i] = data.get(i)
                }
                receivedChunks++
                audioQueue.offer(pcm)
                _displayValue.value = "Rx $receivedChunks"
            } catch (t: Throwable) {
                _displayValue.value = "Rx err: ${t.javaClass.simpleName}"
            }
        }
        subscription = node.createSubscription(
            ROS2Topic(resolvedTopicName, AudioData::class.java),
            callback,
            reliableQos()
        )
    }

    private fun isWavHeader(data: IDLByteSequence, size: Int): Boolean {
        if (size != 44) return false
        return data.get(0) == 'R'.code.toByte() && data.get(1) == 'I'.code.toByte() &&
            data.get(2) == 'F'.code.toByte() && data.get(3) == 'F'.code.toByte() &&
            data.get(8) == 'W'.code.toByte() && data.get(9) == 'A'.code.toByte() &&
            data.get(10) == 'V'.code.toByte() && data.get(11) == 'E'.code.toByte()
    }

    override fun stop() {
        subscription = null
        playbackThread?.interrupt()
        playbackThread = null
        audioQueue.clear()
        audioTrack?.let { track ->
            try {
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop()
            } catch (_: Exception) {
            }
            track.release()
        }
        audioTrack = null
        _displayValue.value = "No audio yet"
    }
}
