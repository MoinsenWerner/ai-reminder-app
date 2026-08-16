package com.jarvis.assistant.data

import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class HuggingFaceModel(val id: String, val title: String, val fileName: String)

object HuggingFaceModels {
    val presets = listOf(
        HuggingFaceModel("HuggingFaceTB/SmolLM2-360M-Instruct", "SmolLM2 360M Instruct", "model.safetensors"),
        HuggingFaceModel("Qwen/Qwen2.5-0.5B-Instruct", "Qwen 2.5 0.5B Instruct", "model.safetensors"),
        HuggingFaceModel("TinyLlama/TinyLlama-1.1B-Chat-v1.0", "TinyLlama 1.1B Chat", "model.safetensors"),
        HuggingFaceModel("Qwen/Qwen2.5-1.5B-Instruct", "Qwen 2.5 1.5B Instruct", "model.safetensors"),
        HuggingFaceModel("HuggingFaceTB/SmolLM2-1.7B-Instruct", "SmolLM2 1.7B Instruct", "model.safetensors")
    )

    suspend fun search(): List<HuggingFaceModel> = withContext(Dispatchers.IO) {
        val connection = URL("https://huggingface.co/api/models?pipeline_tag=text-generation&sort=downloads&direction=-1&limit=100&full=true").openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        connection.setRequestProperty("User-Agent", "Jarvis-Android/1.0")
        connection.inputStream.bufferedReader().use { reader ->
            val json = JSONArray(reader.readText())
            (0 until json.length()).map { json.getJSONObject(it) }
                .filter { model -> !model.optBoolean("gated") && model.optJSONArray("siblings")?.let { siblings ->
                    (0 until siblings.length()).any { siblings.getJSONObject(it).optString("rfilename") == "model.safetensors" }
                } == true }
                .map { model -> model.getString("id") }
                .filterNot { it.contains("GGUF", ignoreCase = true) }
                .map { HuggingFaceModel(it, it.substringAfter('/'), "model.safetensors") }
        }
    }

    suspend fun download(model: HuggingFaceModel, progress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        val targetDir = File(Environment.getExternalStorageDirectory(), "Jarvis/Models/${model.id.replace('/', '_')}")
        targetDir.mkdirs()
        listOf("config.json", "tokenizer.json", "tokenizer_config.json", model.fileName).forEachIndexed { index, fileName ->
            val target = File(targetDir, fileName)
            if (target.exists() && target.length() > 0) return@forEachIndexed
            val partial = File(targetDir, "$fileName.part")
            val connection = URL("https://huggingface.co/${model.id}/resolve/main/$fileName").openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 30_000
            connection.readTimeout = 30_000
            connection.inputStream.use { input -> partial.outputStream().use { output ->
                val total = connection.contentLengthLong
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var copied = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                    copied += count
                    if (total > 0) progress(((index * 100L + copied * 100 / total) / 4).toInt())
                }
            } }
            check(partial.renameTo(target)) { "Download konnte nicht abgeschlossen werden" }
        }
        targetDir
    }
}
