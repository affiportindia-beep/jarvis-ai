// Modified by JARVIS X (2026). See third_party/microwakeword/MODIFICATIONS.md.
package com.example.ava.wakewords.microwakeword

import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer

class MicroWakeWordModel(
    val id: String,
    model: ByteBuffer,
    private val probabilityCutoff: Float,
    private val slidingWindowSize: Int
) : AutoCloseable {
    private val interpreter: Interpreter = Interpreter(model)
    private val inputTensorBuffer: TensorBuffer
    private val outputScale: Float
    private val outputZeroPoint: Int
    private val probabilities = ArrayDeque<Float>(slidingWindowSize)

    /** Latest raw probability, for the diagnostics screen. */
    @Volatile var lastProbability: Float = 0f
        private set

    /** Forget recent audio, e.g. after the wake word fired, so it does not fire twice. */
    fun reset() {
        probabilities.clear()
        inputTensorBuffer.clear()
    }

    init {
        interpreter.allocateTensors()

        val inputDetails = interpreter.getInputTensor(0)
        val inputQuantParams = inputDetails.quantizationParams()
        inputTensorBuffer = TensorBuffer.create(
            inputDetails.dataType(),
            inputDetails.shape(),
            inputQuantParams.scale,
            inputQuantParams.zeroPoint
        )

        val outputDetails = interpreter.getOutputTensor(0)

        val outputQuantParams = outputDetails.quantizationParams()
        outputScale = outputQuantParams.scale
        outputZeroPoint = outputQuantParams.zeroPoint
    }

    fun processAudioFeatures(features: FloatArray): Boolean {
        if (features.isEmpty())
            return false

        inputTensorBuffer.put(features)
        if (!inputTensorBuffer.isComplete)
            return false

        val probability = getWakeWordProbability(inputTensorBuffer.getTensor())
        inputTensorBuffer.clear()
        return isWakeWordDetected(probability)
    }

    private fun getWakeWordProbability(input: ByteBuffer): Float {
        val output = Array(1) { ByteArray(1) }
        interpreter.run(input, output)
        val probability = (output[0][0].toUByte().toFloat() - outputZeroPoint) * outputScale
        return probability
    }

    private fun isWakeWordDetected(probability: Float): Boolean {
        lastProbability = probability
        if (probabilities.size == slidingWindowSize)
            probabilities.removeFirst()
        probabilities.add(probability)
        return probabilities.size == slidingWindowSize && probabilities.average() > probabilityCutoff
    }

    override fun close() {
        interpreter.close()
    }
}