package com.pranav.ai.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.*
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.speech.*
import android.speech.tts.TextToSpeech
import android.view.View
import android.webkit.*
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import com.pranav.ai.brain.PranavBrain
import com.pranav.ai.service.HologramWallpaperService
import com.pranav.ai.service.OrbOverlayService
import com.pranav.ai.service.TermuxBridge
import java.util.*

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private lateinit var uiWebView: WebView
    private lateinit var browserWebView: WebView
    private lateinit var tts: TextToSpeech
    private var speechRecognizer: SpeechRecognizer? = null

    private val voiceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { startVoice() }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        registerReceiver(voiceReceiver, IntentFilter("com.pranav.ai.TOGGLE_VOICE"), RECEIVER_EXPORTED)
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA), 101)

        val root = FrameLayout(this)

        uiWebView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = WebViewClient()
            addJavascriptInterface(PranavBridge(), "PranavNative")
            loadDataWithBaseURL(null, buildSuperHtml(), "text/html", "UTF-8", null)
        }

        browserWebView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            webViewClient = WebViewClient()
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            ).apply {
                topMargin = (60 * resources.displayMetrics.density).toInt()
                bottomMargin = (82 * resources.displayMetrics.density).toInt()
            }
        }

        root.addView(uiWebView)
        root.addView(browserWebView)
        setContentView(root)
    }

    override fun onInit(status: Int) { if (status == TextToSpeech.SUCCESS) tts.language = Locale("hi", "IN") }
    fun speak(text: String) { tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "pranav_tts") }

    fun openUrl(raw: String) {
        runOnUiThread {
            val url = when {
                raw.startsWith("http://") || raw.startsWith("https://") -> raw
                raw.contains(".") && !raw.contains(" ") -> "https://$raw"
                else -> "https://www.google.com/search?q=${Uri.encode(raw)}"
            }
            browserWebView.visibility = View.VISIBLE
            browserWebView.loadUrl(url)
            uiWebView.evaluateJavascript("showBrowser('$url')", null)
        }
    }

    fun startVoice() {
        runOnUiThread {
            if (speechRecognizer == null) speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            }
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onResults(res: Bundle?) {
                    val text = res?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: return
                    uiWebView.evaluateJavascript("setStatus('Suna: $text')", null)
                    PranavBrain.processCommand(this@MainActivity, text, { u -> openUrl(u) }) { reply ->
                        runOnUiThread { uiWebView.evaluateJavascript("setStatus('$reply')", null); speak(reply) }
                    }
                }
                override fun onReadyForSpeech(p0: Bundle?) { uiWebView.evaluateJavascript("setStatus('Bol Pranav Ji...')", null) }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(p0: Float) {}
                override fun onBufferReceived(p0: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(e: Int) { uiWebView.evaluateJavascript("setStatus('Ready — Unlimited Mode')", null) }
                override fun onPartialResults(p0: Bundle?) {}
                override fun onEvent(p0: Int, p1: Bundle?) {}
            })
            speechRecognizer?.startListening(intent)
        }
    }

    inner class PranavBridge {
        @JavascriptInterface fun saveKeys(gemini: String, groq: String) {
            getSharedPreferences("pranav_prefs", MODE_PRIVATE).edit()
                .putString("gemini_key", gemini.trim())
                .putString("groq_key", groq.trim()).apply()
            runOnUiThread { Toast.makeText(this@MainActivity, "Keys Saved!", Toast.LENGTH_SHORT).show() }
        }
        @JavascriptInterface fun setOrb(hex: String, size: Int) {
            getSharedPreferences("pranav_prefs", MODE_PRIVATE).edit()
                .putInt("orb_color", Color.parseColor(hex)).putInt("orb_size", size).apply()
            stopService(Intent(this@MainActivity, OrbOverlayService::class.java))
            startForegroundService(Intent(this@MainActivity, OrbOverlayService::class.java))
        }
        @JavascriptInterface fun startOrb() {
            if (!Settings.canDrawOverlays(this@MainActivity)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            } else startForegroundService(Intent(this@MainActivity, OrbOverlayService::class.java))
        }
        @JavascriptInterface fun setLiveWallpaper() {
            val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, ComponentName(this@MainActivity, HologramWallpaperService::class.java))
            }
            startActivity(intent)
        }
        @JavascriptInterface fun openAccessibility() { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        @JavascriptInterface fun listen() { startVoice() }
        @JavascriptInterface fun browse(u: String) { openUrl(u) }
        @JavascriptInterface fun closeBrowser() { runOnUiThread { browserWebView.visibility = View.GONE } }
        @JavascriptInterface fun backBrowser() { runOnUiThread { if (browserWebView.canGoBack()) browserWebView.goBack() } }
        @JavascriptInterface fun toggleDesktop(on: Boolean) {
            runOnUiThread {
                browserWebView.settings.userAgentString = if (on) "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/122.0 Safari/537.36" else null
                browserWebView.reload()
            }
        }
        @JavascriptInterface fun extractPageToAI() {
            runOnUiThread {
                browserWebView.evaluateJavascript("document.body.innerText.slice(0, 2500)") { txt ->
                    PranavBrain.processCommand(this@MainActivity, "Is webpage ka nichod Hinglish mein samjhao: $txt", {}) { reply ->
                        runOnUiThread { uiWebView.evaluateJavascript("setStatus('$reply')", null); speak(reply) }
                    }
                }
            }
        }
        @JavascriptInterface fun execShell(cmd: String) {
            Thread {
                val out = TermuxBridge.runLocalShell(cmd).replace("'", "\\'").replace("\n", "\\n")
                TermuxBridge.runSilentTermux(this@MainActivity, cmd)
                runOnUiThread { uiWebView.evaluateJavascript("appendTerm('$out')", null) }
            }.start()
        }
        @JavascriptInterface fun sendCmd(cmd: String) {
            PranavBrain.processCommand(this@MainActivity, cmd, { u -> openUrl(u) }) { reply ->
                runOnUiThread { uiWebView.evaluateJavascript("setStatus('$reply')", null); speak(reply) }
            }
        }
    }

    private fun buildSuperHtml(): String = """
    <!DOCTYPE html>
    <html>
    <head>
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <style>
      :root { --bg:#e6eef8; --card:#e6eef8; --sh1:#c5d0de; --sh2:#ffffff; --txt:#0f172a; --sub:#64748b; }
      body.dark { --bg:#0b1120; --card:#111827; --sh1:#050811; --sh2:#1e293b; --txt:#f8fafc; --sub:#94a3b8; }
      body { margin:0; font-family:sans-serif; background:var(--bg); color:var(--txt); padding-bottom:92px; transition:0.3s; }
      .top { display:flex; justify-content:space-between; align-items:center; padding:14px 18px; }
      .c-btn { width:42px; height:42px; border-radius:50%; background:var(--card); box-shadow:5px 5px 10px var(--sh1), -5px -5px 10px var(--sh2); display:flex; align-items:center; justify-content:center; font-weight:bold; cursor:pointer; }
      .hero { padding:0 22px; }
      .hero h1 { margin:2px 0; font-size:28px; font-weight:900; }
      .orb-box { display:flex; flex-direction:column; align-items:center; margin:12px 0; }
      .core { width:150px; height:150px; border-radius:50%; background:radial-gradient(circle at 30% 30%, #1e1b4b, #060913); box-shadow:0 0 35px rgba(255,23,68,0.5), inset 0 0 22px #ff1744; display:flex; align-items:center; justify-content:center; animation:pulse 3s infinite; }
      .ring { width:120px; height:120px; border-radius:50%; border:2px dashed #00e5ff; animation:spin 5s linear infinite; }
      @keyframes pulse { 50% { transform:scale(1.05); } }
      @keyframes spin { 100% { transform:rotate(360deg); } }
      #status { margin-top:10px; font-size:13px; font-weight:700; color:#ff1744; text-align:center; padding:0 18px; }
      .grid { display:grid; grid-template-columns:repeat(2,1fr); gap:12px; padding:12px 18px; }
      .card { background:var(--card); border-radius:22px; padding:14px; box-shadow:7px 7px 14px var(--sh1), -7px -7px 14px var(--sh2); cursor:pointer; }
      .card h3 { margin:0 0 4px 0; font-size:14px; }
      .card p { margin:0; font-size:11px; color:var(--sub); }
      .bar { display:flex; margin:8px 18px; background:var(--card); border-radius:30px; padding:5px 12px; box-shadow:inset 4px 4px 8px var(--sh1), inset -4px -4px 8px var(--sh2); }
      .bar input { flex:1; border:none; background:transparent; outline:none; color:var(--txt); padding:8px; font-size:14px; }
      .dock { position:fixed; bottom:12px; left:14px; right:14px; height:62px; background:var(--card); border-radius:32px; box-shadow:7px 7px 16px var(--sh1), -7px -7px 16px var(--sh2); display:flex; align-items:center; justify-content:space-around; z-index:50; font-size:12px; font-weight:700; }
      .mic { width:60px; height:60px; border-radius:50%; background:linear-gradient(145deg,#ff1744,#d50000); color:#fff; display:flex; align-items:center; justify-content:center; font-size:22px; margin-top:-22px; box-shadow:0 8px 20px rgba(255,23,68,0.5); }
      #bBar { display:none; position:fixed; top:0; left:0; right:0; height:58px; background:#090d16; color:#fff; align-items:center; padding:0 8px; gap:6px; z-index:70; }
      #bBar input { flex:1; padding:8px; border-radius:14px; border:none; background:#1e293b; color:#fff; font-size:12px; }
      .panel { display:none; position:fixed; inset:0; background:rgba(0,0,0,0.75); z-index:90; align-items:center; justify-content:center; }
      .p-box { background:var(--bg); color:var(--txt); width:88%; max-height:82vh; overflow-y:auto; border-radius:24px; padding:18px; }
      .p-box input { width:92%; padding:10px; border-radius:12px; border:1px solid #475569; margin:6px 0; background:var(--card); color:var(--txt); }
      .btn { background:#2563eb; color:#fff; border:none; padding:10px; border-radius:12px; font-weight:bold; width:100%; margin-top:6px; }
      #termOut { background:#050811; color:#00ff88; font-family:monospace; font-size:11px; padding:10px; border-radius:12px; height:180px; overflow-y:auto; white-space:pre-wrap; margin-top:8px; }
    </style>
    </head>
    <body>
      <div id="bBar">
        <button onclick="PranavNative.backBrowser()" style="background:#334155;color:#fff;border:none;padding:7px 9px;border-radius:8px;">&#8592;</button>
        <input id="uBox" onkeydown="if(event.key==='Enter') PranavNative.browse(this.value)" />
        <button onclick="PranavNative.toggleDesktop(true)" style="background:#2563eb;color:#fff;border:none;padding:7px;border-radius:8px;font-size:10px;">PC</button>
        <button onclick="PranavNative.extractPageToAI()" style="background:#10b981;color:#fff;border:none;padding:7px;border-radius:8px;font-size:10px;">AI Read</button>
        <button onclick="hideBrowser()" style="background:#ef4444;color:#fff;border:none;padding:7px 9px;border-radius:8px;">X</button>
      </div>

      <div class="top">
        <div class="c-btn" onclick="openPanel('setPanel')">&#9776;</div>
        <div style="font-weight:900;letter-spacing:1.5px;">PRANAV AI v2</div>
        <div class="c-btn" onclick="document.body.classList.toggle('dark')">&#9790;</div>
      </div>

      <div class="hero">
        <div style="font-size:12px;color:var(--sub);">Unlimited Autonomous Jarvis</div>
        <h1>BOSS</h1>
      </div>

      <div class="orb-box">
        <div class="core" onclick="PranavNative.listen()"><div class="ring"></div></div>
        <div id="status">Tap Core or Mic — Ready</div>
      </div>

      <div class="grid">
        <div class="card" onclick="PranavNative.startOrb()"><h3>&#128308; Bottom Red Orb</h3><p>Neeche beech mein laal ghera</p></div>
        <div class="card" onclick="PranavNative.browse('https://www.google.com')"><h3>&#127760; Cyber Browser</h3><p>Google, GitHub + AI Reader</p></div>
        <div class="card" onclick="openPanel('termPanel')"><h3>&#128187; Cyber Terminal</h3><p>Live Shell + Termux Bridge</p></div>
        <div class="card" onclick="PranavNative.openAccessibility()"><h3>&#9889; Phone Control</h3><p>Auto-Tap, Type & Apps ON</p></div>
        <div class="card" onclick="PranavNative.setLiveWallpaper()"><h3>&#127756; 3D Wallpaper</h3><p>Home screen 3D Hologram</p></div>
        <div class="card" onclick="PranavNative.sendCmd('GitHub repository status check kar aur auto push kar')"><h3>&#128640; GitHub Auto</h3><p>Ek command mein repo push</p></div>
      </div>

      <div class="bar">
        <input id="cmdIn" placeholder="PRANAV AI ko koi bhi hukm do..." onkeydown="if(event.key==='Enter') runTxt()" />
        <div class="c-btn" style="width:36px;height:36px;" onclick="runTxt()">&#10148;</div>
      </div>

      <div class="dock">
        <div onclick="hideBrowser()">Home</div>
        <div onclick="PranavNative.browse('https://github.com')">Browser</div>
        <div class="mic" onclick="PranavNative.listen()">&#127908;</div>
        <div onclick="openPanel('termPanel')">Terminal</div>
        <div onclick="openPanel('setPanel')">Settings</div>
      </div>

      <!-- Cyber Terminal Panel -->
      <div class="panel" id="termPanel">
        <div class="p-box">
          <h3>PRANAV Cyber & Dev Terminal</h3>
          <input id="shCmd" placeholder="Command likho (e.g. ping -c 2 google.com)" onkeydown="if(event.key==='Enter') runSh()" />
          <button class="btn" onclick="runSh()">Run Command</button>
          <div id="termOut">pranav@cyber-core:~$ Ready...</div>
          <button class="btn" style="background:#475569;" onclick="closePanels()">Close</button>
        </div>
      </div>

      <!-- Settings Panel -->
      <div class="panel" id="setPanel">
        <div class="p-box">
          <h3>PRANAV AI Control Center</h3>
          <input id="gKey" placeholder="Gemini API Key (AIza... / AQ...)" />
          <input id="qKey" placeholder="Groq API Key (gsk_... Fast Brain)" />
          <button class="btn" onclick="PranavNative.saveKeys(document.getElementById('gKey').value, document.getElementById('qKey').value)">Save API Keys</button>
          <p style="font-size:12px;margin-top:10px;">Bottom Orb Colour:</p>
          <div style="display:flex;gap:8px;">
            <button onclick="PranavNative.setOrb('#FF1744',46)" style="background:#FF1744;color:#fff;border:none;padding:8px;border-radius:10px;">Neon Red</button>
            <button onclick="PranavNative.setOrb('#00E5FF',46)" style="background:#00E5FF;color:#000;border:none;padding:8px;border-radius:10px;">Cyber Cyan</button>
            <button onclick="PranavNative.setOrb('#FFD600',46)" style="background:#FFD600;color:#000;border:none;padding:8px;border-radius:10px;">Iron Gold</button>
          </div>
          <button class="btn" style="background:#475569;margin-top:12px;" onclick="closePanels()">Close</button>
        </div>
      </div>

      <script>
        function setStatus(m) { document.getElementById('status').innerText = m; }
        function openPanel(id) { document.getElementById(id).style.display = 'flex'; }
        function closePanels() { document.querySelectorAll('.panel').forEach(p => p.style.display='none'); }
        function showBrowser(u) { document.getElementById('bBar').style.display='flex'; document.getElementById('uBox').value=u; }
        function hideBrowser() { document.getElementById('bBar').style.display='none'; PranavNative.closeBrowser(); }
        function runTxt() {
          var v = document.getElementById('cmdIn').value;
          if(v) { setStatus('Working: '+v); PranavNative.sendCmd(v); document.getElementById('cmdIn').value=''; }
        }
        function runSh() {
          var c = document.getElementById('shCmd').value;
          if(c) { appendTerm('\n$ '+c); PranavNative.execShell(c); document.getElementById('shCmd').value=''; }
        }
        function appendTerm(t) { var o=document.getElementById('termOut'); o.innerText += '\n'+t; o.scrollTop=o.scrollHeight; }
      </script>
    </body>
    </html>
    """

    override fun onDestroy() {
        unregisterReceiver(voiceReceiver)
        tts.shutdown()
        super.onDestroy()
    }
}
