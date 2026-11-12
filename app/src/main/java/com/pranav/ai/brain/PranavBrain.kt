package com.pranav.ai.brain

import android.content.Context
import com.pranav.ai.service.PranavAccessibilityService
import com.pranav.ai.service.TermuxBridge
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object PranavBrain {
    private const val SYSTEM_PROMPT = """
        Tu PRANAV AI v2.0 hai — Pranav Ji ka Ultimate Autonomous Jarvis Commander.
        Rules:
        1. Kabhi confirmation ya faltu sawal mat pooch. Seedha best action le.
        2. Website kholni ho ya Google/GitHub search karna ho -> action: "BROWSE", target: "url_or_query"
        3. Phone mein koi app kholna ho (WhatsApp, YouTube, Instagram, Settings) -> action: "OPEN_APP", target: "AppName"
        4. Screen par kisi button par tap karna ho -> action: "CLICK", target: "ButtonText"
        5. Type karna ho -> action: "TYPE", target: "TextToType"
        6. Scroll/Reels aage badhana ho -> action: "SWIPE", target: "down"
        7. Coding, Website banana, Python script, ya GitHub push karna ho -> action: "TERMUX", target: "bash_command"
        8. Sirf valid JSON return kar: {"action":"BROWSE|OPEN_APP|CLICK|TYPE|SWIPE|TERMUX|SPEAK","target":"...","reply":"short Hinglish status"}
    """

    fun processCommand(context: Context, userCommand: String, onBrowse: (String) -> Unit, onResult: (String) -> Unit) {
        Thread {
            try {
                val prefs = context.getSharedPreferences("pranav_prefs", Context.MODE_PRIVATE)
                val geminiKey = prefs.getString("gemini_key", "") ?: ""
                val groqKey = prefs.getString("groq_key", "") ?: ""

                if (geminiKey.isBlank() && groqKey.isBlank()) {
                    onResult("Pranav Ji, Settings mein Gemini ya Groq API key daal do.")
                    return@Thread
                }

                val screen = PranavAccessibilityService.instance?.dumpScreenText()?.take(1400) ?: ""
                val fullPrompt = "$SYSTEM_PROMPT\nCurrent Screen: $screen\nUser Command: $userCommand"

                val rawText = if (groqKey.isNotBlank() && !userCommand.contains("screen", true)) {
                    callGroq(groqKey, fullPrompt)
                } else {
                    callGemini(geminiKey, fullPrompt)
                }

                val cleanJson = rawText.replace("```json", "").replace("```", "").trim()
                val obj = JSONObject(cleanJson)
                val target = obj.optString("target", "")
                when (obj.optString("action", "SPEAK")) {
                    "BROWSE" -> onBrowse(target)
                    "OPEN_APP" -> PranavAccessibilityService.instance?.openAppByName(target)
                    "CLICK" -> PranavAccessibilityService.instance?.clickByText(target)
                    "TYPE" -> PranavAccessibilityService.instance?.typeText(target)
                    "SWIPE" -> PranavAccessibilityService.instance?.swipe(540f, 1600f, 540f, 400f)
                    "TERMUX" -> TermuxBridge.runSilentTermux(context, target)
                }
                onResult(obj.optString("reply", "Kaam ho gaya boss."))
            } catch (e: Exception) {
                onResult("Error: ${e.message}")
            }
        }.start()
    }

    private fun callGemini(key: String, prompt: String): String {
        val conn = (URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$key").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; setRequestProperty("Content-Type", "application/json"); doOutput = true
        }
        val payload = JSONObject().put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
        conn.outputStream.use { it.write(payload.toString().toByteArray()) }
        return JSONObject(conn.inputStream.bufferedReader().readText())
            .getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
    }

    private fun callGroq(key: String, prompt: String): String {
        val conn = (URL("https://api.groq.com/openai/v1/chat/completions").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $key")
            doOutput = true
        }
        val msg = JSONArray().put(JSONObject().put("role", "user").put("content", prompt))
        val payload = JSONObject().put("model", "llama-3.3-70b-versatile").put("messages", msg)
        conn.outputStream.use { it.write(payload.toString().toByteArray()) }
        return JSONObject(conn.inputStream.bufferedReader().readText())
            .getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
    }
}
