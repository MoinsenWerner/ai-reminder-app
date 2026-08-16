package com.jarvis.assistant.data

import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class HuggingFaceModel(val id: String, val title: String, val fileName: String)

object HuggingFaceModels {
    private const val ID_FILE = "jarvis_model.json"
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
                .filter { model -> model.opt("gated") == false && model.optJSONArray("siblings")?.let { siblings ->
                    val files = (0 until siblings.length()).map { siblings.getJSONObject(it).optString("rfilename") }.toSet()
                    setOf("model.safetensors", "config.json", "tokenizer.json", "tokenizer_config.json").all(files::contains)
                } == true }
                .map { model -> model.getString("id") }
                .filterNot { it.contains("GGUF", ignoreCase = true) }
                .filter(::isMobileCandidate)
                .map { HuggingFaceModel(it, it.substringAfter('/'), "model.safetensors") }
        }
    }

    suspend fun download(model: HuggingFaceModel, progress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        val targetDir = directoryFor(model)
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
        File(targetDir, ID_FILE).writeText(JSONObject().put("id", model.id).put("title", model.title).toString())
        targetDir
    }

    suspend fun downloaded(): List<HuggingFaceModel> = withContext(Dispatchers.IO) {
        modelsDirectory().listFiles().orEmpty().mapNotNull { directory ->
            runCatching {
                val metadata = JSONObject(File(directory, ID_FILE).readText())
                val model = HuggingFaceModel(metadata.getString("id"), metadata.getString("title"), "model.safetensors")
                model.takeIf { requiredFiles(directory, it).all(File::isFile) }
            }.getOrNull()
        }.sortedBy { it.title.lowercase() }
    }

    fun directoryFor(model: HuggingFaceModel): File = File(modelsDirectory(), model.id.replace('/', '_'))

    fun isDownloaded(model: HuggingFaceModel): Boolean = requiredFiles(directoryFor(model), model).all(File::isFile)

    internal fun isMobileCandidate(id: String): Boolean {
        val value = id.lowercase()
        if (Regex("(?:^|[-_/])(4|5|6|7|8|9|1[0-9]|[2-9][0-9])b(?:$|[-_/])").containsMatchIn(value)) return false
        return Regex("(?:0\\.[1-9]b|[1-3]b|[1-9][0-9]{1,3}m|tiny|small|mini|mobile|smol)").containsMatchIn(value)
    }

    private fun modelsDirectory() = File(Environment.getExternalStorageDirectory(), "Jarvis/Models")

    private fun requiredFiles(directory: File, model: HuggingFaceModel) =
        listOf("config.json", "tokenizer.json", "tokenizer_config.json", model.fileName, ID_FILE).map { File(directory, it) }
}
