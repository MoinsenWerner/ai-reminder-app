package com.jarvis.assistant.receivers

import android.content.*
import com.jarvis.assistant.data.JarvisLogger
import com.jarvis.assistant.data.JarvisSettings
import com.jarvis.assistant.model.AssistantEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

abstract class BaseJarvisReceiver(private val kind: String) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val command = intent.getStringExtra("command") ?: intent.getStringExtra("text") ?: intent.getStringExtra("instructions") ?: intent.dataString.orEmpty()
        val address = intent.getStringExtra("address") ?: intent.getStringExtra("uri") ?: ""
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = JarvisSettings(context).flow.first()
                val result = AssistantEngine.process(context, command, settings)
                JarvisLogger.log(context, "tasker:$kind", "command=$command address=$address engine=${result.engine} action=${result.executedAction}")
            } catch (error: Exception) {
                JarvisLogger.log(context, "tasker:$kind:error", "command=$command address=$address error=${error.message}")
            } finally {
                pending.finish()
            }
        }
    }
}
class ReceiveNewCommand : BaseJarvisReceiver("receiveNewCommand")
class ReceiveNewReminder : BaseJarvisReceiver("receiveNewReminder")
class ReceiveNewData : BaseJarvisReceiver("receiveNewData")
class ReceiveNewVideo : BaseJarvisReceiver("receiveNewVideo")
class ReceiveNewAudio : BaseJarvisReceiver("receiveNewAudio")
