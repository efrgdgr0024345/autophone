package com.blackcat.remote
import android.Manifest
import android.app.Activity
import android.content.*
import android.content.pm.PackageManager
import android.os.*
import android.view.*
import android.widget.*
import kotlinx.coroutines.*

class MainActivity:Activity(){
 private lateinit var hid:HidManager;private lateinit var status:TextView;private lateinit var log:TextView;private lateinit var diagnostics:Diagnostics
 private lateinit var root:LinearLayout;private lateinit var contentHost:FrameLayout;private lateinit var bottomNav:LinearLayout
 private val scope=CoroutineScope(Dispatchers.Main+SupervisorJob())
 private val targetPrefs by lazy{getSharedPreferences("blackcat_ai_ui",MODE_PRIVATE)}
 private var currentMainScreen="home"
 override fun onCreate(s:Bundle?){super.onCreate(s);buildUi();diagnostics=Diagnostics{runOnUiThread{log.text=it}};hid=HidManager(this){event(it)};permissionsOrInit()}
 private fun permissionsOrInit(){if(Build.VERSION.SDK_INT>=31){val n=mutableListOf<String>();if(checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)n+=Manifest.permission.BLUETOOTH_CONNECT;if(checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE)!=PackageManager.PERMISSION_GRANTED)n+=Manifest.permission.BLUETOOTH_ADVERTISE;if(n.isNotEmpty()){requestPermissions(n.toTypedArray(),10);return}};initHid()}
 private fun initHid(){status.text="HID REGISTERING";event("INFO APP_START");if(!hid.init())status.text="BLUETOOTH/HID ERROR"}
 override fun onRequestPermissionsResult(r:Int,p:Array<out String>,g:IntArray){super.onRequestPermissionsResult(r,p,g);if(r!=10)return;if(g.isNotEmpty()&&g.all{it==PackageManager.PERMISSION_GRANTED}){event("PASS BLUETOOTH_PERMISSIONS");initHid()}else event("FAIL Bluetooth permission denied")}
 @Suppress("DEPRECATION") private fun pair(){if(!hid.registered){event("WAIT HID not registered");return};event("PASS HID registered before discoverability");try{startActivityForResult(hid.discoverableIntent(300),20);event("INFO DISCOVERABILITY_REQUESTED")}catch(t:Throwable){event("FAIL discoverability: "+(t.message?:"unknown"))}}
 @Deprecated("Compatibility") override fun onActivityResult(r:Int,result:Int,data:Intent?){super.onActivityResult(r,result,data);if(r==20){if(result>0){status.text="DISCOVERABLE — ADD ON COMPUTER";event("PASS DISCOVERABLE seconds="+result)}else event("WARN discoverability declined")}}
 private fun buildUi(){
  root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(android.graphics.Color.WHITE)}
  BlackCatStyle.applySystemBarInsets(root)
  status=TextView(this).apply{text="STARTING"}
  log=TextView(this).apply{textSize=10f;setTextColor(0xffb6c6bf.toInt());typeface=android.graphics.Typeface.MONOSPACE;setPadding(dp(5),dp(3),dp(5),dp(3))}
  contentHost=FrameLayout(this).apply{setBackgroundColor(android.graphics.Color.WHITE)}
  root.addView(contentHost,LinearLayout.LayoutParams(-1,0,1f))
  bottomNav=buildMainNav()
  root.addView(bottomNav,LinearLayout.LayoutParams(-1,dp(62)))
  setContentView(root)
  showSplash()
  android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({if(currentMainScreen=="splash")showHome()},900)
 }

 private fun showSplash(){
  currentMainScreen="splash";bottomNav.visibility=View.GONE;contentHost.removeAllViews()
  val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER_HORIZONTAL;setPadding(dp(26),dp(20),dp(26),dp(24));setBackgroundColor(android.graphics.Color.WHITE)}
  val cat=ImageView(this).apply{setImageResource(R.drawable.black_cat_full);scaleType=ImageView.ScaleType.FIT_CENTER;contentDescription="Black Cat"}
  box.addView(cat,LinearLayout.LayoutParams(-1,0,1f))
  box.addView(BlackCatStyle.label(this,"Black Cat AI",29f,android.graphics.Color.BLACK,true).apply{gravity=Gravity.CENTER},LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(8)})
  box.addView(BlackCatStyle.label(this,"Remote · Automate · Control",13f,android.graphics.Color.DKGRAY).apply{gravity=Gravity.CENTER},LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(4);bottomMargin=dp(14)})
  val p=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply{isIndeterminate=true;indeterminateTintList=android.content.res.ColorStateList.valueOf(0xff59ed59.toInt())}
  box.addView(p,LinearLayout.LayoutParams(-1,dp(7)).apply{leftMargin=dp(26);rightMargin=dp(26)})
  contentHost.addView(box,FrameLayout.LayoutParams(-1,-1))
 }

 private fun buildMainNav():LinearLayout{
  val bar=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER;background=BlackCatStyle.round(this@MainActivity,0xff0b1411.toInt(),0,0xff30423b.toInt(),1);setPadding(dp(4),dp(3),dp(4),dp(3))}
  fun item(tagName:String,icon:String,label:String,action:()->Unit):LinearLayout{
   return LinearLayout(this).apply{
    tag=tagName;orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;isClickable=true;isFocusable=true;setPadding(dp(2),dp(3),dp(2),dp(3))
    addView(BlackCatStyle.label(this@MainActivity,icon,17f,0xffd7e3de.toInt(),true).apply{gravity=Gravity.CENTER})
    addView(BlackCatStyle.label(this@MainActivity,label,10f,0xffd7e3de.toInt(),true).apply{gravity=Gravity.CENTER})
    setOnClickListener{action()}
   }
  }
  bar.addView(item("home","⌂","Home"){showHome()},LinearLayout.LayoutParams(0,-1,1f))
  bar.addView(item("ai","✣","AI"){AiEntry.openAssistant(this,{hid})},LinearLayout.LayoutParams(0,-1,1f))
  bar.addView(item("target","▣","Target"){showTarget()},LinearLayout.LayoutParams(0,-1,1f))
  bar.addView(item("settings","⚙","Settings"){AiEntry.openSettings(this)},LinearLayout.LayoutParams(0,-1,1f))
  return bar
 }

 private fun setNavActive(name:String){
  if(!::bottomNav.isInitialized)return
  for(i in 0 until bottomNav.childCount){
   val v=bottomNav.getChildAt(i) as? LinearLayout?:continue
   val active=v.tag==name
   v.background=if(active)BlackCatStyle.round(this,0xff193427.toInt(),12) else android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
   for(j in 0 until v.childCount)(v.getChildAt(j) as? TextView)?.setTextColor(if(active)0xff73ff69.toInt() else 0xffd7e3de.toInt())
  }
 }

 private fun screenBody():LinearLayout=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(8),dp(12),dp(16));setBackgroundColor(android.graphics.Color.WHITE)}
 private fun darkPanel():LinearLayout=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(13),dp(12),dp(13),dp(12));background=BlackCatStyle.round(this@MainActivity,0xff0d1513.toInt(),18,0xff30423b.toInt(),1)}
 private fun catHeader(height:Int=155,title:String?=null,subtitle:String?=null):LinearLayout{
  return LinearLayout(this).apply{
   orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER_HORIZONTAL;setBackgroundColor(android.graphics.Color.WHITE)
   addView(ImageView(this@MainActivity).apply{setImageResource(R.drawable.black_cat_full);scaleType=ImageView.ScaleType.CENTER_CROP;contentDescription="Black Cat"},LinearLayout.LayoutParams(-1,dp(height)))
   if(title!=null)addView(BlackCatStyle.label(this@MainActivity,title,20f,android.graphics.Color.BLACK,true).apply{gravity=Gravity.CENTER},LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(3)})
   if(subtitle!=null)addView(BlackCatStyle.label(this@MainActivity,subtitle,11f,android.graphics.Color.DKGRAY).apply{gravity=Gravity.CENTER},LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(2);bottomMargin=dp(5)})
  }
 }
 private fun showInScroll(body:LinearLayout){
  contentHost.removeAllViews();val sc=ScrollView(this).apply{isFillViewport=true;overScrollMode=View.OVER_SCROLL_IF_CONTENT_SCROLLS};sc.addView(body,ViewGroup.LayoutParams(-1,-2));contentHost.addView(sc,FrameLayout.LayoutParams(-1,-1))
 }

 private fun menuTile(title:String,subtitle:String,icon:String,action:()->Unit):View{
  val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(13),dp(11),dp(13),dp(11));background=BlackCatStyle.round(this@MainActivity,0xff111a18.toInt(),16,0xff34463f.toInt(),1);isClickable=true;isFocusable=true;setOnClickListener{action()}}
  row.addView(BlackCatStyle.label(this,icon,28f,0xff6cff63.toInt(),true).apply{gravity=Gravity.CENTER},LinearLayout.LayoutParams(dp(46),dp(46)))
  val copy=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(8),0,0,0);addView(BlackCatStyle.label(this@MainActivity,title,15f,android.graphics.Color.WHITE,true));addView(BlackCatStyle.label(this@MainActivity,subtitle,11f,0xffc3d0ca.toInt()))}
  row.addView(copy,LinearLayout.LayoutParams(0,-2,1f))
  row.addView(BlackCatStyle.label(this,"›",27f,0xffc3d0ca.toInt(),false))
  return row
 }

 private fun showHome(){
  currentMainScreen="home";bottomNav.visibility=View.VISIBLE;setNavActive("home")
  val body=screenBody();body.addView(catHeader(245,"Black Cat AI","Remote · Automate · Control"))
  body.addView(menuTile("Bluetooth","Connect and manage devices","◉"){showBluetooth()},sectionLp())
  body.addView(menuTile("AI Assistant","Plan, create and automate","▣"){AiEntry.openAssistant(this,{hid})},sectionLp())
  body.addView(menuTile("Target System","Select your Linux system",">_"){showTarget()},sectionLp())
  body.addView(menuTile("Settings","API key, model and preferences","⚙"){AiEntry.openSettings(this)},sectionLp())
  showInScroll(body)
 }

 fun openHomeFromChild(){showHome()}
 fun openTargetFromChild(){showTarget()}
 fun openSettingsFromChild(){AiEntry.openSettings(this)}

 private fun showBluetooth(){
  currentMainScreen="bluetooth";bottomNav.visibility=View.VISIBLE;setNavActive("home")
  val body=screenBody();body.addView(catHeader(138,"Bluetooth","Computer-initiated HID pairing"))
  val card=darkPanel()
  (status.parent as? ViewGroup)?.removeView(status)
  status.apply{textSize=16f;setTextColor(android.graphics.Color.WHITE);typeface=android.graphics.Typeface.DEFAULT_BOLD;background=BlackCatStyle.round(this@MainActivity,0xff15211e.toInt(),13,0xff40564d.toInt(),1);setPadding(dp(13),dp(12),dp(13),dp(12))}
  card.addView(status,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(9)})
  val pairButton=Button(this).apply{text="Make Discoverable / Pair New Device";setOnClickListener{pair()}};BlackCatStyle.styleButton(this,pairButton,true)
  card.addView(pairButton,LinearLayout.LayoutParams(-1,dp(50)).apply{bottomMargin=dp(9)})
  card.addView(BlackCatStyle.label(this,"On the computer: Bluetooth → Add device → select this phone → Pair.",12f,0xffc4d0ca.toInt()),LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(10)})
  val connected=hid.connected!=null
  val host=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(11),dp(10),dp(11),dp(10));background=BlackCatStyle.round(this@MainActivity,0xff17231f.toInt(),12,0xff40564d.toInt(),1)}
  host.addView(BlackCatStyle.label(this,if(connected)"Connected host" else "Paired / connected device",12f,0xff72ff68.toInt(),true))
  host.addView(BlackCatStyle.label(this,if(connected)"Computer connected as Bluetooth HID keyboard/mouse." else "No host connected yet. Pair from the computer side.",13f,android.graphics.Color.WHITE))
  card.addView(host,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(10)})
  card.addView(BlackCatStyle.label(this,"Remote controls",13f,0xff72ff68.toInt(),true).apply{setPadding(0,0,0,dp(7))})
  val remote=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
  fun rb(t:String,a:()->Unit)=Button(this).apply{text=t;BlackCatStyle.styleButton(this@MainActivity,this,false,true);setOnClickListener{a()}}
  remote.addView(rb("Send Text"){showSendText()},weight());remote.addView(rb("Touchpad"){showTouchpad()},weight());remote.addView(rb("Keyboard"){showKeyboard()},weight())
  card.addView(remote)
  body.addView(card,sectionLp())
  addDiagnostics(body)
  showInScroll(body)
 }

 private fun showSendText(){
  currentMainScreen="send";bottomNav.visibility=View.VISIBLE;setNavActive("home")
  val body=screenBody();body.addView(catHeader(125,"Send Text","Type on the connected computer"))
  val panel=darkPanel();val input=EditText(this).apply{hint="Text to type on the computer"};BlackCatStyle.styleInput(this,input)
  val send=Button(this).apply{text="Send Text";setOnClickListener{sendText(input.text.toString());input.text.clear()}};BlackCatStyle.styleButton(this,send,true)
  panel.addView(input,LinearLayout.LayoutParams(-1,dp(90)).apply{bottomMargin=dp(9)});panel.addView(send,LinearLayout.LayoutParams(-1,dp(50)));body.addView(panel,sectionLp());showInScroll(body)
 }

 private fun showTouchpad(){
  currentMainScreen="touchpad";bottomNav.visibility=View.VISIBLE;setNavActive("home")
  val body=screenBody();body.addView(catHeader(110,"Touchpad","Move and click"))
  val panel=darkPanel();val pad=TextView(this).apply{text="Drag here to move pointer";gravity=Gravity.CENTER;textSize=15f;setTextColor(0xffd7e3de.toInt());background=BlackCatStyle.round(this@MainActivity,0xff0b1411.toInt(),14,0xff40564d.toInt(),1)}
  var x=0f;var y=0f
  pad.setOnTouchListener{_,e->when(e.actionMasked){MotionEvent.ACTION_DOWN->{x=e.x;y=e.y;true};MotionEvent.ACTION_MOVE->{hid.sendMouse(0,(e.x-x).toInt(),(e.y-y).toInt());x=e.x;y=e.y;true};else->true}}
  panel.addView(pad,LinearLayout.LayoutParams(-1,dp(260)).apply{bottomMargin=dp(8)})
  val clicks=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
  fun mb(label:String,mask:Int)=Button(this).apply{text=label;BlackCatStyle.styleButton(this@MainActivity,this,false,true);setOnClickListener{hid.sendMouse(mask,0,0); hid.sendMouse(0,0,0)}}
  clicks.addView(mb("Left",HidReports.LEFT),weight());clicks.addView(mb("Middle",HidReports.MIDDLE),weight());clicks.addView(mb("Right",HidReports.RIGHT),weight());panel.addView(clicks);body.addView(panel,sectionLp());showInScroll(body)
 }

 private fun showKeyboard(){
  currentMainScreen="keyboard";bottomNav.visibility=View.VISIBLE;setNavActive("home")
  val body=screenBody();body.addView(catHeader(90,"Keyboard","Full HID keys"))
  val panel=darkPanel();val kb=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
  fun k(label:String,key:Int,mod:Int=0):Button{return Button(this).apply{text=label;BlackCatStyle.styleButton(this@MainActivity,this,false,true);setOnClickListener{hid.sendKeyboard(mod,key)}}}
  fun row(vararg buttons:Button)=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;buttons.forEach{addView(it,weight())}}
  kb.addView(row(k("ESC",41),k("TAB",43),k("BKSP",42),k("DEL",76),k("ENTER",40)))
  kb.addView(row(k("CTRL",0,HidReports.CTRL),k("SHIFT",0,HidReports.SHIFT),k("ALT",0,HidReports.ALT),k("GUI",0,HidReports.GUI)))
  kb.addView(row(k("INS",73),k("HOME",74),k("END",77),k("PGUP",75),k("PGDN",78)))
  kb.addView(row(k("←",80),k("↑",82),k("↓",81),k("→",79)))
  kb.addView(row(k("F1",58),k("F2",59),k("F3",60),k("F4",61),k("F5",62),k("F6",63)))
  kb.addView(row(k("F7",64),k("F8",65),k("F9",66),k("F10",67),k("F11",68),k("F12",69)))
  panel.addView(kb);body.addView(panel,sectionLp());showInScroll(body)
 }

 private fun showTarget(){
  currentMainScreen="target";bottomNav.visibility=View.VISIBLE;setNavActive("target")
  val body=screenBody();body.addView(catHeader(125,"Target System","Quick selection with edit"))
  val panel=darkPanel();panel.addView(BlackCatStyle.label(this,"Select Target System",13f,0xff72ff68.toInt(),true).apply{setPadding(0,0,0,dp(7))})
  val selected=targetPrefs.getString("target_system","Ubuntu Linux / Bash")?:"Ubuntu Linux / Bash"
  val options=listOf(
   "Ubuntu Linux / Bash" to "Ubuntu (Default)",
   "Debian Linux / Bash" to "Debian",
   "Linux Mint / Bash" to "Linux Mint",
   "Kali Linux / Bash" to "Kali Linux",
   "Fedora Linux / Bash" to "Fedora",
   "openSUSE Linux / Bash" to "openSUSE",
   "Arch Linux / Bash" to "Arch Linux"
  )
  options.forEach{(value,label)->
   val b=Button(this).apply{text=(if(selected==value)"✓  " else "")+label;gravity=Gravity.START or Gravity.CENTER_VERTICAL;setOnClickListener{targetPrefs.edit().putString("target_system",value).apply();showTarget()}}
   BlackCatStyle.styleButton(this,b,false,true);panel.addView(b,LinearLayout.LayoutParams(-1,dp(45)).apply{bottomMargin=dp(5)})
  }
  val edit=Button(this).apply{text="✎  Edit / Custom…";gravity=Gravity.START or Gravity.CENTER_VERTICAL;setOnClickListener{showCustomTarget()}};BlackCatStyle.styleButton(this,edit,false,true);panel.addView(edit,LinearLayout.LayoutParams(-1,dp(46)))
  body.addView(panel,sectionLp());showInScroll(body)
 }

 private fun showCustomTarget(){
  currentMainScreen="custom-target";bottomNav.visibility=View.VISIBLE;setNavActive("target")
  val body=screenBody();body.addView(catHeader(118,"Custom System","Add your own system"))
  val panel=darkPanel()
  val name=EditText(this).apply{hint="System name";setText(targetPrefs.getString("target_custom","")?:"")};BlackCatStyle.styleInput(this,name)
  val details=EditText(this).apply{hint="Optional details (e.g. OS info)";minLines=3};BlackCatStyle.styleInput(this,details)
  panel.addView(BlackCatStyle.label(this,"System Name",12f,android.graphics.Color.WHITE,true));panel.addView(name,LinearLayout.LayoutParams(-1,dp(50)).apply{bottomMargin=dp(10)})
  panel.addView(BlackCatStyle.label(this,"Optional Details",12f,android.graphics.Color.WHITE,true));panel.addView(details,LinearLayout.LayoutParams(-1,dp(95)).apply{bottomMargin=dp(10)})
  val save=Button(this).apply{text="Save";setOnClickListener{
   val v=name.text.toString().trim()
   if(v.isNotBlank()){val full=if(details.text.toString().trim().isBlank())v else v+" / "+details.text.toString().trim();targetPrefs.edit().putString("target_custom",full).putString("target_system",full).apply();showTarget()}
  }};BlackCatStyle.styleButton(this,save,true);panel.addView(save,LinearLayout.LayoutParams(-1,dp(50)))
  body.addView(panel,sectionLp());showInScroll(body)
 }

 private fun addDiagnostics(body:LinearLayout){
  val diagnosticsCard=darkPanel()
  val diagnosticsBody=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;visibility=View.GONE;setPadding(0,dp(7),0,0)}
  val diagToggle=Button(this).apply{text="Diagnostics";setOnClickListener{val show=diagnosticsBody.visibility==View.GONE;diagnosticsBody.visibility=if(show)View.VISIBLE else View.GONE;text=if(show)"Hide diagnostics" else "Diagnostics"}};BlackCatStyle.styleButton(this,diagToggle,false,true)
  diagnosticsCard.addView(diagToggle,LinearLayout.LayoutParams(-1,dp(42)))
  (log.parent as? ViewGroup)?.removeView(log)
  val ls=ScrollView(this).apply{addView(log);background=BlackCatStyle.round(this@MainActivity,0xff111916.toInt(),10,0xff30423b.toInt(),1)};diagnosticsBody.addView(ls,LinearLayout.LayoutParams(-1,dp(120)))
  val actions=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;setPadding(0,dp(6),0,0)}
  val copy=Button(this).apply{text="Copy log";setOnClickListener{(getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("Black Cat diagnostics",diagnostics.snapshot()))}};BlackCatStyle.styleButton(this,copy,false,true)
  val clear=Button(this).apply{text="Clear";setOnClickListener{diagnostics.clear()}};BlackCatStyle.styleButton(this,clear,false,true)
  actions.addView(copy,weight());actions.addView(clear,weight());diagnosticsBody.addView(actions);diagnosticsCard.addView(diagnosticsBody);body.addView(diagnosticsCard,sectionLp())
 }
 private fun sendText(v:String){scope.launch{var unsupported=0;for(c in v){val p=HidReports.char(c);if(p!=null){hid.sendKeyboard(p.second,p.first);delay(12)}else unsupported++};event("PASS text submitted chars="+v.length+" unsupported="+unsupported+" content-not-logged")}}
 private fun event(s:String){diagnostics.add(s);runOnUiThread{if(s.contains("HID_REGISTERED"))status.text="READY TO PAIR";if(s.contains("HID_CONNECTED"))status.text="READY";if(s.contains("Bluetooth off"))status.text="BLUETOOTH OFF"}}
 private fun sectionLp()=LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(8)}
 private fun weight()=LinearLayout.LayoutParams(0,dp(46),1f)
 private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
 override fun onDestroy(){scope.cancel();hid.close();super.onDestroy()}
}