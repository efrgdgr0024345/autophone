package com.blackcat.remote

import android.app.Activity
import android.app.Dialog
import android.content.*
import android.graphics.Color
import android.graphics.Typeface
import android.os.*
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import kotlinx.coroutines.*

/** Navigation wraps the original manual HID mappings; the assistant never owns the transport. */
class MainActivity : Activity() {
 private lateinit var hid: HidManager
 private lateinit var diagnostics: Diagnostics
 private lateinit var bluetoothSetup: BluetoothSetupController
 private lateinit var contentHost: FrameLayout
 private lateinit var homeScreen: View
 private lateinit var keyboardScreen: View
 private lateinit var bluetoothScreen: View
 private lateinit var root: LinearLayout
 private lateinit var bottom: View
 private lateinit var log: TextView
 private val diagnosticViews = mutableListOf<TextView>()
 private val statusViews = mutableListOf<TextView>()
 private var bluetoothStatus = "Bluetooth not set up. Tap Set up Bluetooth to connect."
 private var cancelDictation: (() -> Unit)? = null
 private val scope=CoroutineScope(Dispatchers.Main+SupervisorJob())

 override fun onCreate(s: Bundle?) {
  super.onCreate(s)
  buildUi()
  diagnostics = Diagnostics { value -> runOnUiThread {
   log.text = value
   diagnosticViews.forEach { it.text = value }
  } }
  hid = HidManager(this) { event(it) }
  bluetoothSetup = BluetoothSetupController(this, { hid }, { setBluetoothStatus(it) }, { event(it) })
  if (intent?.action == BluetoothSessionService.ACTION_OPEN_SETTINGS) navigateSettings()
 }

 override fun onNewIntent(intent: Intent) {
  super.onNewIntent(intent)
  setIntent(intent)
  if (intent.action == BluetoothSessionService.ACTION_OPEN_SETTINGS) navigateSettings()
 }

 override fun onRequestPermissionsResult(r: Int, p: Array<out String>, g: IntArray) {
  super.onRequestPermissionsResult(r, p, g)
  if (::bluetoothSetup.isInitialized) bluetoothSetup.onPermissionResult(r, g)
 }

 @Suppress("DEPRECATION") private fun pair() {
  if (!bluetoothSetup.readyForDiscoverability()) return
  if (!hid.registered) { event("WAIT HID not registered"); return }
  event("PASS HID registered before discoverability")
  try {
   startActivityForResult(hid.discoverableIntent(300), 20)
   event("INFO DISCOVERABILITY_REQUESTED")
  } catch (_: Throwable) { event("FAIL discoverability: unavailable") }
 }

 @Deprecated("Compatibility") override fun onActivityResult(r: Int, result: Int, data: Intent?) {
  super.onActivityResult(r, result, data)
  if (bluetoothSetup.onActivityResult(r, result)) return
  if (r == 20) {
   if (result > 0) {
    setBluetoothStatus("Discoverable — add this phone from your computer's Bluetooth settings.")
    event("PASS DISCOVERABLE seconds=" + result)
   } else {
    setBluetoothStatus("Discoverability declined. Tap Make Discoverable to try again.")
    event("WARN discoverability declined")
   }
  }
 }

 override fun onResume() {
  super.onResume()
  if (::bluetoothSetup.isInitialized) bluetoothSetup.onResume()
 }

 override fun onPause() { cancelManualDictation(); super.onPause() }

 fun navigateHome() { showMainScreen(homeScreen, "Home") }
 fun navigateKeyboard() { showMainScreen(keyboardScreen, "Keyboard") }
 fun navigateAssistant() {
  cancelManualDictation()
  AiEntry.openAssistant(this@MainActivity){hid}
 }
 fun navigateSettings() { cancelManualDictation(); AiEntry.openSettings(this) }
 fun openBluetoothSetup() { showMainScreen(bluetoothScreen, "Home") }
 fun endBluetoothSession() {
  cancelManualDictation()
  hid.disconnect()
  bluetoothSetup.endSession()
 }

 /** Changing destinations closes the caller's panel before opening the next; no typing occurs. */
 fun createBottomNavigation(active: String, onBeforeNavigate: (() -> Unit)? = null): View {
  val bar = LinearLayout(this).apply {
   orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
   setPadding(dp(3), dp(3), dp(3), dp(3)); setBackgroundColor(0xff101714.toInt())
  }
  fun nav(icon: String, label: String, action: () -> Unit) = LinearLayout(this).apply {
   orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
   isClickable = true; isFocusable = true; contentDescription = label
   setPadding(dp(3), dp(3), dp(3), dp(3))
   val color = if (label == active) 0xff74f45b.toInt() else Color.WHITE
   addView(BlackCatStyle.label(this@MainActivity, icon, 18f, color, true).apply { gravity = Gravity.CENTER })
   addView(BlackCatStyle.label(this@MainActivity, label, 11f, color, true).apply { gravity = Gravity.CENTER })
   setOnClickListener { if (label != active) { AiEntry.leaveAssistantForNavigation(this@MainActivity); onBeforeNavigate?.invoke(); action() } }
  }
  bar.addView(nav("⌂", "Home") { navigateHome() }, LinearLayout.LayoutParams(0, dp(56), 1f))
  bar.addView(nav("⌨", "Keyboard") { navigateKeyboard() }, LinearLayout.LayoutParams(0, dp(56), 1f))
  bar.addView(nav("✣", "AI") { navigateAssistant() }, LinearLayout.LayoutParams(0, dp(56), 1f))
  bar.addView(nav("⚙", "Settings") { navigateSettings() }, LinearLayout.LayoutParams(0, dp(56), 1f))
  return bar
 }

 private fun showMainScreen(screen: View, active: String) {
  cancelManualDictation()
  (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(contentHost.windowToken, 0)
  contentHost.removeAllViews()
  contentHost.addView(screen, FrameLayout.LayoutParams(-1, -1))
  root.removeView(bottom)
  bottom = createBottomNavigation(active)
  root.addView(bottom, LinearLayout.LayoutParams(-1, -2))
 }

 private fun buildUi() {
  val stack = FrameLayout(this).apply { setBackgroundColor(Color.WHITE) }
  root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE) }
  BlackCatStyle.applySystemBarInsets(root)
  stack.addView(root, FrameLayout.LayoutParams(-1, -1))
  contentHost = FrameLayout(this).apply { setBackgroundColor(Color.WHITE) }
  root.addView(contentHost, LinearLayout.LayoutParams(-1, 0, 1f))
  log = TextView(this)

  val home = page(); homeScreen = home.first; val homeBody = home.second
  homeBody.addView(ImageView(this).apply {
   setImageResource(R.drawable.black_cat_full); scaleType = ImageView.ScaleType.CENTER_INSIDE; contentDescription = "Black Cat"
  }, LinearLayout.LayoutParams(-1, dp(190)))
  homeBody.addView(BlackCatStyle.label(this, "Black Cat AI", 27f, Color.BLACK, true).apply { gravity = Gravity.CENTER })
  homeBody.addView(BlackCatStyle.label(this, "Remote · Automate · Control", 13f, Color.DKGRAY).apply {
   gravity = Gravity.CENTER; setPadding(0, dp(4), 0, dp(12))
  })
  homeBody.addView(statusPanel(), sectionLp())
  homeBody.addView(greenButton("Set up Bluetooth") { bluetoothSetup.setup() }, buttonLp())
  homeBody.addView(menuButton("Bluetooth", "Pair from your computer; see connection status") { openBluetoothSetup() }, buttonLp())
  homeBody.addView(menuButton("Keyboard & mouse", "Phone typing, voice text and touchpad") { navigateKeyboard() }, buttonLp())
  homeBody.addView(menuButton("AI Assistant", "Plan, review and explain Linux commands") { navigateAssistant() }, buttonLp())

  val remote = page(); keyboardScreen = remote.first; val remoteBody = remote.second
  remoteBody.addView(pageTitle("Keyboard & mouse"), sectionLp())
  remoteBody.addView(statusPanel(), sectionLp())
  val sendPanel = darkPanel()
  sendPanel.addView(BlackCatStyle.label(this, "Send text", 17f, Color.WHITE, true), sectionLp())
  val input=EditText(this).apply{hint="Text to type on the computer"};BlackCatStyle.styleInput(this,input)
  input.contentDescription = "Manual text to send to the computer"
  sendPanel.addView(input, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(7) })
  val dictationStatus = BlackCatStyle.label(this, "Type with your phone keyboard, or dictate and review the text before sending.", 12f, 0xffc3d0ca.toInt())
  sendPanel.addView(secondaryButton("Phone keyboard") {
   input.requestFocus()
   (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
  }, buttonLp())
  sendPanel.addView(secondaryButton("Speak text") {
   cancelManualDictation()
   cancelDictation = AiEntry.dictateManualText(this, { transcript ->
    input.setText(transcript); input.setSelection(input.text.length)
    dictationStatus.text = "Transcript ready. Review or edit it, then tap Send Text."
   }, { message -> dictationStatus.text = message })
  }, buttonLp())
  sendPanel.addView(dictationStatus, sectionLp())
  val send=greenButton("Send Text"){sendText(input.text.toString());input.text.clear()}
  sendPanel.addView(send, buttonLp())
  remoteBody.addView(sendPanel, sectionLp())

  val pointerPanel = darkPanel()
  pointerPanel.addView(BlackCatStyle.label(this, "Touchpad", 17f, Color.WHITE, true), sectionLp())
  val pad=TextView(this).apply{text="Drag here to move pointer";gravity=Gravity.CENTER;textSize=15f;setTextColor(0xffdff1e2.toInt());typeface=Typeface.create(Typeface.DEFAULT,Typeface.BOLD);background=BlackCatStyle.round(this@MainActivity,0xff0b1411.toInt(),14,0xff30423b.toInt(),1)}
  pad.contentDescription = "Mouse touchpad: drag to move the computer pointer"
  var x=0f;var y=0f
  pad.setOnTouchListener{_,e->
   // Prevent the surrounding scroll view from consuming pointer drags.
   if (e.actionMasked == MotionEvent.ACTION_DOWN) pad.parent.requestDisallowInterceptTouchEvent(true)
   if (e.actionMasked == MotionEvent.ACTION_UP || e.actionMasked == MotionEvent.ACTION_CANCEL) pad.parent.requestDisallowInterceptTouchEvent(false)
   when(e.actionMasked){MotionEvent.ACTION_DOWN->{x=e.x;y=e.y;true};MotionEvent.ACTION_MOVE->{hid.sendMouse(0,(e.x-x).toInt(),(e.y-y).toInt());x=e.x;y=e.y;true};else->true}
  }
  pointerPanel.addView(pad, LinearLayout.LayoutParams(-1, dp(165)).apply { bottomMargin = dp(8) })
  val clicks = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
  fun mb(label:String,mask:Int):Button {
   return Button(this).apply {
    text=label
    BlackCatStyle.styleButton(this@MainActivity,this,false,true)
    setOnClickListener { hid.sendMouse(mask,0,0); hid.sendMouse(0,0,0) }
   }
  }
  clicks.addView(mb("Left",HidReports.LEFT),weight());clicks.addView(mb("Middle",HidReports.MIDDLE),weight());clicks.addView(mb("Right",HidReports.RIGHT),weight())
  pointerPanel.addView(clicks)
  remoteBody.addView(pointerPanel, sectionLp())

  val keyboardPanel = darkPanel()
  val kb = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
  keyboardPanel.addView(secondaryButton("Show special keys") {
   val open = kb.visibility == View.GONE; kb.visibility = if (open) View.VISIBLE else View.GONE
   (keyboardPanel.getChildAt(0) as Button).text = if (open) "Hide special keys" else "Show special keys"
  }, buttonLp())
  fun k(label:String,key:Int,mod:Int=0):Button {
   return Button(this).apply{text=label;BlackCatStyle.styleButton(this@MainActivity,this,false,true);setOnClickListener{hid.sendKeyboard(mod,key)}}
  }
  fun row(vararg buttons:Button):LinearLayout {
   return LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;buttons.forEach{addView(it,weight())}}
  }
  kb.addView(row(k("ESC",41),k("TAB",43),k("BKSP",42),k("DEL",76),k("ENTER",40)))
  kb.addView(row(k("CTRL",0,HidReports.CTRL),k("SHIFT",0,HidReports.SHIFT),k("ALT",0,HidReports.ALT),k("GUI",0,HidReports.GUI)))
  kb.addView(row(k("INS",73),k("HOME",74),k("END",77),k("PGUP",75),k("PGDN",78)))
  kb.addView(row(k("←",80),k("↑",82),k("↓",81),k("→",79)))
  kb.addView(row(k("F1",58),k("F2",59),k("F3",60),k("F4",61),k("F5",62),k("F6",63)))
  kb.addView(row(k("F7",64),k("F8",65),k("F9",66),k("F10",67),k("F11",68),k("F12",69)))
  keyboardPanel.addView(kb)
  remoteBody.addView(keyboardPanel, sectionLp())
  remoteBody.addView(BlackCatStyle.label(this, "Special keys send only when you tap them. Manual Enter is available here; AI and library TYPE ONLY never sends Enter.", 12f, Color.DKGRAY), sectionLp())

  val bt = page(); bluetoothScreen = bt.first; val btBody = bt.second
  btBody.addView(pageTitle("Bluetooth"), sectionLp())
  btBody.addView(statusPanel(), sectionLp())
  btBody.addView(greenButton("Set up Bluetooth") { bluetoothSetup.setup() }, buttonLp())
  btBody.addView(greenButton("Make Discoverable"){pair()}, buttonLp())
  btBody.addView(BlackCatStyle.label(this, "Computer: Bluetooth → Add device → select this phone → Pair.\n\nIf Bluetooth is off, Android will ask you to turn it on. Return here and tap Make Discoverable when ready.", 14f, Color.DKGRAY), sectionLp())
  btBody.addView(menuButton("Remote Controls", "Open the working keyboard and touchpad") { navigateKeyboard() }, buttonLp())
  btBody.addView(secondaryButton("Settings and logs") { navigateSettings() }, buttonLp())

  contentHost.addView(homeScreen, FrameLayout.LayoutParams(-1, -1))
  bottom = createBottomNavigation("Home")
  root.addView(bottom, LinearLayout.LayoutParams(-1, -2))
  val splash=LinearLayout(this).apply{
   orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER_HORIZONTAL;setPadding(dp(22),dp(30),dp(22),dp(24));setBackgroundColor(Color.WHITE)
   addView(ImageView(this@MainActivity).apply{setImageResource(R.drawable.black_cat_full);scaleType=ImageView.ScaleType.CENTER_INSIDE;contentDescription="Black Cat"},LinearLayout.LayoutParams(-1,0,1f))
   addView(BlackCatStyle.label(this@MainActivity,"Black Cat AI",30f,Color.BLACK,true).apply{gravity=Gravity.CENTER})
   addView(BlackCatStyle.label(this@MainActivity,"Remote · Automate · Control",13f,Color.DKGRAY).apply{gravity=Gravity.CENTER})
  }
  stack.addView(splash,FrameLayout.LayoutParams(-1,-1))
  setContentView(stack)
  Handler(Looper.getMainLooper()).postDelayed({if(splash.parent!=null)stack.removeView(splash)},700)
 }

 /** Settings owns diagnostics; callers get a new collapsed view, never the transport object. */
 fun createDiagnosticsView(): View {
  val diagnosticsCard = darkPanel()
  val diagnosticsBody=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;visibility=View.GONE;setPadding(0,dp(7),0,0)}
  diagnosticsCard.addView(secondaryButton("Diagnostics") {
   val show = diagnosticsBody.visibility == View.GONE
   diagnosticsBody.visibility = if (show) View.VISIBLE else View.GONE
   (diagnosticsCard.getChildAt(0) as Button).text = if (show) "Hide diagnostics" else "Diagnostics"
  }, buttonLp())
  val logView = TextView(this).apply {
   text = if (::diagnostics.isInitialized) diagnostics.snapshot() else ""
   textSize = 11f; setTextColor(0xffb6c6bf.toInt()); typeface = Typeface.MONOSPACE
   setPadding(dp(5), dp(4), dp(5), dp(4))
  }
  logView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
   override fun onViewAttachedToWindow(v: View) { diagnosticViews.add(logView); logView.text = diagnostics.snapshot() }
   override fun onViewDetachedFromWindow(v: View) { diagnosticViews.remove(logView) }
  })
  diagnosticsBody.addView(ScrollView(this).apply { addView(logView) }, LinearLayout.LayoutParams(-1, dp(180)))
  val da = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
  da.addView(secondaryButton("Copy log") {
   (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Black Cat diagnostics", diagnostics.snapshot()))
  }, weight())
  da.addView(Button(this).apply {
   text = "Clear"; BlackCatStyle.styleButton(this@MainActivity, this, false, true)
   setOnClickListener{diagnostics.clear()}
  }, weight())
  diagnosticsBody.addView(da)
  diagnosticsCard.addView(diagnosticsBody)
  return diagnosticsCard
 }

 fun showDiagnostics() {
  val dialog = Dialog(this, android.R.style.Theme_Material_Light_NoActionBar)
  val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(10), dp(12), dp(10)); setBackgroundColor(Color.WHITE) }
  BlackCatStyle.applySystemBarInsets(layout)
  layout.addView(pageTitle("Settings · Diagnostics"), sectionLp())
  layout.addView(createDiagnosticsView(), LinearLayout.LayoutParams(-1, 0, 1f))
  layout.addView(secondaryButton("Back to settings") { dialog.dismiss(); navigateSettings() }, buttonLp())
  layout.addView(createBottomNavigation("Settings") { dialog.dismiss() }, LinearLayout.LayoutParams(-1, -2))
  dialog.setContentView(layout); dialog.show(); dialog.window?.setLayout(-1, -1)
 }

 private fun setBluetoothStatus(value: String) {
  runOnUiThread {
   bluetoothStatus = value
   val color = if (value.startsWith("Failed", ignoreCase = true)) 0xffffbdbd.toInt() else Color.WHITE
   statusViews.forEach { it.text = value; it.setTextColor(color) }
  }
 }
 private fun statusPanel() = darkPanel().apply {
  addView(BlackCatStyle.label(this@MainActivity, "Bluetooth", 12f, 0xff74f45b.toInt(), true), sectionLp())
  val view = BlackCatStyle.label(this@MainActivity, bluetoothStatus, 14f, Color.WHITE, true)
  statusViews.add(view); addView(view)
 }
 private fun cancelManualDictation() { val cancel = cancelDictation; cancelDictation = null; cancel?.invoke() }
 private fun event(s: String) {
  runOnUiThread {
   diagnostics.add(s)
   if (::bluetoothSetup.isInitialized) bluetoothSetup.observeEvent(s)
  }
 }
 private fun page(): Pair<ScrollView, LinearLayout> {
  val scroll = ScrollView(this).apply { isFillViewport = true; setBackgroundColor(Color.WHITE) }
  val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(8), dp(12), dp(12)) }
  scroll.addView(body, ViewGroup.LayoutParams(-1, -2))
  return Pair(scroll, body)
 }
 private fun pageTitle(value: String) = BlackCatStyle.label(this, value, 23f, Color.BLACK, true).apply { setPadding(0, dp(5), 0, dp(5)) }
 private fun darkPanel() = LinearLayout(this).apply {
  orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(11), dp(12), dp(11))
  background = BlackCatStyle.round(this@MainActivity, 0xff101714.toInt(), 16, 0xff30423b.toInt(), 1)
 }
 private fun menuButton(title: String, sub: String, action: () -> Unit) = Button(this).apply {
  text = "$title\n$sub"; isAllCaps = false; textSize = 15f; gravity = Gravity.CENTER_VERTICAL
  setTextColor(Color.WHITE); typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
  background = BlackCatStyle.round(this@MainActivity, 0xff151d1a.toInt(), 15, 0xff30423b.toInt(), 1)
  setPadding(dp(14), dp(10), dp(14), dp(10)); minHeight = dp(58)
  setOnClickListener { action() }
 }
 private fun greenButton(value: String, action: () -> Unit) = Button(this).apply {
  text = value; isAllCaps = false; textSize = 15f; minHeight = dp(48); setTextColor(Color.BLACK)
  typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
  background = BlackCatStyle.round(this@MainActivity, 0xff74f45b.toInt(), 12)
  setOnClickListener { action() }
 }
 private fun secondaryButton(value: String, action: () -> Unit) = Button(this).apply {
  text = value; BlackCatStyle.styleButton(this@MainActivity, this, false, true); minHeight = dp(48)
  setOnClickListener { action() }
 }
 private fun buttonLp() = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(7) }
 private fun sectionLp()=LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(8)}
 private fun weight()=LinearLayout.LayoutParams(0,dp(48),1f)
 private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
 private fun sendText(v:String){scope.launch{var unsupported=0;for(c in v){val p=HidReports.char(c);if(p!=null){hid.sendKeyboard(p.second,p.first);delay(12)}else unsupported++};event("PASS text submitted chars="+v.length+" unsupported="+unsupported+" content-not-logged")}}
 override fun onDestroy() {
  cancelManualDictation()
  scope.cancel()
  if (::bluetoothSetup.isInitialized) bluetoothSetup.close()
  hid.close()
  super.onDestroy()
 }
}
