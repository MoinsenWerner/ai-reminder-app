package com.jarvis.assistant.receivers

import android.content.*
import com.jarvis.assistant.data.JarvisLogger
import com.jarvis.assistant.model.JarvisModel

abstract class BaseJarvisReceiver(private val kind: String) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val command = intent.getStringExtra("command") ?: intent.getStringExtra("text") ?: intent.getStringExtra("instructions") ?: intent.dataString.orEmpty()
        val address = intent.getStringExtra("address") ?: intent.getStringExtra("uri") ?: ""
        val inferred = JarvisModel().infer(command)
        JarvisLogger.log(context, "tasker:$kind", "command=$command address=$address inferred=${inferred.type}:${inferred.title}")
    }
}
class ReceiveNewCommand : BaseJarvisReceiver("receiveNewCommand")
class ReceiveNewReminder : BaseJarvisReceiver("receiveNewReminder")
class ReceiveNewData : BaseJarvisReceiver("receiveNewData")
class ReceiveNewVideo : BaseJarvisReceiver("receiveNewVideo")
class ReceiveNewAudio : BaseJarvisReceiver("receiveNewAudio")
