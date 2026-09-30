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
 private val scope=CoroutineScope(Dispatchers.Main+SupervisorJob())
 override fun onCreate(s:Bundle?){super.onCreate(s);buildUi();diagnostics=Diagnostics{runOnUiThread{log.text=it}};hid=HidManager(this){event(it)};permissionsOrInit()}
 private fun permissionsOrInit(){if(Build.VERSION.SDK_INT>=31){val n=mutableListOf<String>();if(checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)n+=Manifest.permission.BLUETOOTH_CONNECT;if(checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE)!=PackageManager.PERMISSION_GRANTED)n+=Manifest.permission.BLUETOOTH_ADVERTISE;if(n.isNotEmpty()){requestPermissions(n.toTypedArray(),10);return}};initHid()}
 private fun initHid(){status.text="HID REGISTERING";event("INFO APP_START");if(!hid.init())status.text="BLUETOOTH/HID ERROR"}
 override fun onRequestPermissionsResult(r:Int,p:Array<out String>,g:IntArray){super.onRequestPermissionsResult(r,p,g);if(r!=10)return;if(g.isNotEmpty()&&g.all{it==PackageManager.PERMISSION_GRANTED}){event("PASS BLUETOOTH_PERMISSIONS");initHid()}else event("FAIL Bluetooth permission denied")}
 @Suppress("DEPRECATION") private fun pair(){if(!hid.registered){event("WAIT HID not registered");return};event("PASS HID registered before discoverability");try{startActivityForResult(hid.discoverableIntent(300),20);event("INFO DISCOVERABILITY_REQUESTED")}catch(t:Throwable){event("FAIL discoverability: "+(t.message?:"unknown"))}}
 @Deprecated("Compatibility") override fun onActivityResult(r:Int,result:Int,data:Intent?){super.onActivityResult(r,result,data);if(r==20){if(result>0){status.text="DISCOVERABLE — ADD ON COMPUTER";event("PASS DISCOVERABLE seconds="+result)}else event("WARN discoverability declined")}}
 private fun buildUi(){
  val stack=FrameLayout(this).apply{setBackgroundColor(android.graphics.Color.WHITE)}
  val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(android.graphics.Color.WHITE)}
  BlackCatStyle.applySystemBarInsets(root)
  stack.addView(root,FrameLayout.LayoutParams(-1,-1))

  val contentHost=FrameLayout(this).apply{setBackgroundColor(android.graphics.Color.WHITE)}
  root.addView(contentHost,LinearLayout.LayoutParams(-1,0,1f))

  fun darkPanel()=LinearLayout(this).apply{
   orientation=LinearLayout.VERTICAL
   setPadding(dp(12),dp(11),dp(12),dp(11))
   background=BlackCatStyle.round(this@MainActivity,0xff101714.toInt(),18,0xff30423b.toInt(),1)
  }
  fun menuButton(title:String,sub:String,action:()->Unit)=Button(this).apply{
   text="$title\n$sub"
   isAllCaps=false
   textSize=15f
   gravity=Gravity.CENTER_VERTICAL
   setTextColor(android.graphics.Color.WHITE)
   typeface=android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD)
   background=BlackCatStyle.round(this@MainActivity,0xff151d1a.toInt(),20,0xff30423b.toInt(),1)
   setPadding(dp(18),dp(8),dp(16),dp(8))
   setOnClickListener{action()}
  }
  fun greenButton(textValue:String,action:()->Unit)=Button(this).apply{
   text=textValue
   isAllCaps=false
   textSize=15f
   gravity=Gravity.CENTER
   setTextColor(android.graphics.Color.BLACK)
   typeface=android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD)
   background=BlackCatStyle.round(this@MainActivity,0xff74f45b.toInt(),14)
   setOnClickListener{action()}
  }
  fun screenHeader(title:String,imageRes:Int,back:(()->Unit)?=null)=LinearLayout(this).apply{
   orientation=LinearLayout.VERTICAL
   setBackgroundColor(android.graphics.Color.WHITE)
   val top=LinearLayout(this@MainActivity).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(8),dp(3),dp(8),0)}
   if(back!=null){
    val b=TextView(this@MainActivity).apply{text="‹";textSize=34f;setTextColor(android.graphics.Color.BLACK);gravity=Gravity.CENTER;isClickable=true;isFocusable=true;contentDescription="Back";setOnClickListener{back()}}
    top.addView(b,LinearLayout.LayoutParams(dp(42),dp(42)))
   }else top.addView(View(this@MainActivity),LinearLayout.LayoutParams(dp(42),dp(42)))
   top.addView(BlackCatStyle.label(this@MainActivity,title,18f,android.graphics.Color.BLACK,true).apply{gravity=Gravity.CENTER},LinearLayout.LayoutParams(0,dp(42),1f))
   top.addView(TextView(this@MainActivity).apply{text="⋮";textSize=24f;setTextColor(android.graphics.Color.BLACK);gravity=Gravity.CENTER},LinearLayout.LayoutParams(dp(42),dp(42)))
   addView(top)
   addView(ImageView(this@MainActivity).apply{setImageResource(imageRes);scaleType=ImageView.ScaleType.CENTER_CROP;contentDescription="Black Cat"},LinearLayout.LayoutParams(-1,dp(148)))
  }

  lateinit var showHome:()->Unit
  lateinit var showBluetooth:()->Unit

  val sendPanel=darkPanel().apply{visibility=View.GONE}
  sendPanel.addView(BlackCatStyle.label(this,"Send text",16f,android.graphics.Color.WHITE,true).apply{setPadding(0,0,0,dp(7))})
  val input=EditText(this).apply{hint="Text to type on the computer"};BlackCatStyle.styleInput(this,input)
  val send=greenButton("Send Text"){sendText(input.text.toString());input.text.clear()}
  sendPanel.addView(input,LinearLayout.LayoutParams(-1,dp(52)).apply{bottomMargin=dp(8)})
  sendPanel.addView(send,LinearLayout.LayoutParams(-1,dp(48)))

  val pointerPanel=darkPanel().apply{visibility=View.GONE}
  pointerPanel.addView(BlackCatStyle.label(this,"Touchpad",16f,android.graphics.Color.WHITE,true).apply{setPadding(0,0,0,dp(7))})
  val pad=TextView(this).apply{text="Drag here to move pointer";gravity=Gravity.CENTER;textSize=15f;setTextColor(0xffdff1e2.toInt());typeface=android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);background=BlackCatStyle.round(this@MainActivity,0xff0b1411.toInt(),14,0xff30423b.toInt(),1)}
  var x=0f;var y=0f
  pad.setOnTouchListener{_,e->when(e.actionMasked){MotionEvent.ACTION_DOWN->{x=e.x;y=e.y;true};MotionEvent.ACTION_MOVE->{hid.sendMouse(0,(e.x-x).toInt(),(e.y-y).toInt());x=e.x;y=e.y;true};else->true}}
  pointerPanel.addView(pad,LinearLayout.LayoutParams(-1,dp(210)).apply{bottomMargin=dp(8)})
  val clicks=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
  fun mb(label:String,mask:Int):Button {
   return Button(this).apply {
    text=label
    BlackCatStyle.styleButton(this@MainActivity,this,false,true)
    setOnClickListener { hid.sendMouse(mask,0,0); hid.sendMouse(0,0,0) }
   }
  }
  clicks.addView(mb("Left",HidReports.LEFT),weight());clicks.addView(mb("Middle",HidReports.MIDDLE),weight());clicks.addView(mb("Right",HidReports.RIGHT),weight())
  pointerPanel.addView(clicks)

  val keyboardPanel=darkPanel().apply{visibility=View.GONE}
  keyboardPanel.addView(BlackCatStyle.label(this,"Keyboard",16f,android.graphics.Color.WHITE,true).apply{setPadding(0,0,0,dp(7))})
  val kb=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
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

  fun hideRemotePanels(){sendPanel.visibility=View.GONE;pointerPanel.visibility=View.GONE;keyboardPanel.visibility=View.GONE}
  fun showRemote(panel:View){hideRemotePanels();panel.visibility=View.VISIBLE}

  val homeScreen=ScrollView(this).apply{isFillViewport=true;setBackgroundColor(android.graphics.Color.WHITE)}
  val homeBody=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(14),dp(2),dp(14),dp(18));setBackgroundColor(android.graphics.Color.WHITE)}
  homeScreen.addView(homeBody,ViewGroup.LayoutParams(-1,-2))
  val hero=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER_HORIZONTAL;setBackgroundColor(android.graphics.Color.WHITE)}
  hero.addView(ImageView(this).apply{setImageResource(R.drawable.black_cat_full);scaleType=ImageView.ScaleType.CENTER_INSIDE;contentDescription="Black Cat"},LinearLayout.LayoutParams(-1,dp(260)))
  hero.addView(BlackCatStyle.label(this,"Black Cat AI",28f,android.graphics.Color.BLACK,true).apply{gravity=Gravity.CENTER})
  hero.addView(BlackCatStyle.label(this,"Remote · Automate · Control",13f,android.graphics.Color.DKGRAY).apply{gravity=Gravity.CENTER;setPadding(0,dp(3),0,dp(12))})
  homeBody.addView(hero)
  homeBody.addView(menuButton("Bluetooth","Connect and manage devices"){showBluetooth()},LinearLayout.LayoutParams(-1,dp(72)).apply{bottomMargin=dp(7)})
  homeBody.addView(menuButton("AI Assistant","Plan, create and review"){AiEntry.openAssistant(this@MainActivity){hid}},LinearLayout.LayoutParams(-1,dp(72)).apply{bottomMargin=dp(7)})
  homeBody.addView(menuButton("Target System","Select your Linux system"){AiEntry.openTargetSystem(this@MainActivity)},LinearLayout.LayoutParams(-1,dp(72)).apply{bottomMargin=dp(7)})
  homeBody.addView(menuButton("Settings","API key, model and preferences"){AiEntry.openSettings(this@MainActivity)},LinearLayout.LayoutParams(-1,dp(72)))

  val bluetoothScreen=ScrollView(this).apply{isFillViewport=true;setBackgroundColor(android.graphics.Color.WHITE)}
  val btBody=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(10),0,dp(10),dp(18));setBackgroundColor(android.graphics.Color.WHITE)}
  bluetoothScreen.addView(btBody,ViewGroup.LayoutParams(-1,-2))
  btBody.addView(screenHeader("Bluetooth",R.drawable.black_cat_peek){showHome()})

  val statusCard=darkPanel()
  val statusLine=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
  val btIcon=TextView(this).apply{text="✦";textSize=24f;gravity=Gravity.CENTER;setTextColor(android.graphics.Color.WHITE);background=BlackCatStyle.round(this@MainActivity,0xff1689ff.toInt(),28)}
  statusLine.addView(btIcon,LinearLayout.LayoutParams(dp(50),dp(50)).apply{rightMargin=dp(10)})
  val statusTextBox=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
  status=TextView(this).apply{text="STARTING";textSize=15f;setTextColor(android.graphics.Color.WHITE);typeface=android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD)}
  statusTextBox.addView(status)
  statusTextBox.addView(BlackCatStyle.label(this,"Pair from the computer's Bluetooth settings",11f,0xffc3d0ca.toInt()))
  statusLine.addView(statusTextBox,LinearLayout.LayoutParams(0,-2,1f))
  statusCard.addView(statusLine)
  btBody.addView(statusCard,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(8)})
  btBody.addView(greenButton("Make Discoverable"){pair()},LinearLayout.LayoutParams(-1,dp(50)).apply{bottomMargin=dp(10)})
  btBody.addView(BlackCatStyle.label(this,"Computer: Bluetooth → Add device → select this phone → Pair",12f,android.graphics.Color.DKGRAY).apply{setPadding(dp(5),0,dp(5),dp(12))})

  btBody.addView(BlackCatStyle.label(this,"Remote Controls",15f,0xff376b45.toInt(),true).apply{setPadding(dp(5),0,0,dp(6))})
  val toolRow=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
  toolRow.addView(menuButton("Send Text","Type on computer"){showRemote(sendPanel)},LinearLayout.LayoutParams(0,dp(84),1f).apply{rightMargin=dp(4)})
  toolRow.addView(menuButton("Touchpad","Move and click"){showRemote(pointerPanel)},LinearLayout.LayoutParams(0,dp(84),1f).apply{leftMargin=dp(4)})
  btBody.addView(toolRow,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(8)})
  btBody.addView(menuButton("Keyboard","Full HID keys"){showRemote(keyboardPanel)},LinearLayout.LayoutParams(-1,dp(70)).apply{bottomMargin=dp(8)})
  btBody.addView(sendPanel,sectionLp());btBody.addView(pointerPanel,sectionLp());btBody.addView(keyboardPanel,sectionLp())

  val diagnosticsCard=darkPanel()
  val diagnosticsBody=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;visibility=View.GONE;setPadding(0,dp(7),0,0)}
  val diagToggle=Button(this).apply{text="Diagnostics";setOnClickListener{val show=diagnosticsBody.visibility==View.GONE;diagnosticsBody.visibility=if(show)View.VISIBLE else View.GONE;text=if(show)"Hide diagnostics" else "Diagnostics"}}
  BlackCatStyle.styleButton(this,diagToggle,false,true)
  diagnosticsCard.addView(diagToggle,LinearLayout.LayoutParams(-1,dp(42)))
  log=TextView(this).apply{textSize=10f;setTextColor(0xffb6c6bf.toInt());typeface=android.graphics.Typeface.MONOSPACE;setPadding(dp(5),dp(3),dp(5),dp(3))}
  val ls=ScrollView(this).apply{addView(log);background=BlackCatStyle.round(this@MainActivity,0xff111916.toInt(),10,0xff30423b.toInt(),1)}
  diagnosticsBody.addView(ls,LinearLayout.LayoutParams(-1,dp(120)))
  val da=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;setPadding(0,dp(6),0,0)}
  val copy=Button(this).apply{text="Copy log";setOnClickListener{(getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("Black Cat diagnostics",diagnostics.snapshot()))}}
  BlackCatStyle.styleButton(this,copy,false,true);da.addView(copy,weight())
  val clear=Button(this).apply{text="Clear";setOnClickListener{diagnostics.clear()}}
  BlackCatStyle.styleButton(this,clear,false,true);da.addView(clear,weight())
  diagnosticsBody.addView(da)
  diagnosticsCard.addView(diagnosticsBody)
  btBody.addView(diagnosticsCard,sectionLp())

  showHome={contentHost.removeAllViews();contentHost.addView(homeScreen,FrameLayout.LayoutParams(-1,-1))}
  showBluetooth={contentHost.removeAllViews();contentHost.addView(bluetoothScreen,FrameLayout.LayoutParams(-1,-1))}
  showHome()

  val bottom=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER;setPadding(dp(4),dp(4),dp(4),dp(4));setBackgroundColor(0xff101714.toInt())}
  fun nav(icon:String,label:String,action:()->Unit)=LinearLayout(this).apply{
   orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;isClickable=true;isFocusable=true;contentDescription=label;setPadding(dp(3),dp(3),dp(3),dp(3))
   addView(BlackCatStyle.label(this@MainActivity,icon,17f,0xff74f45b.toInt(),true).apply{gravity=Gravity.CENTER})
   addView(BlackCatStyle.label(this@MainActivity,label,10f,android.graphics.Color.WHITE,true).apply{gravity=Gravity.CENTER})
   setOnClickListener{action()}
  }
  bottom.addView(nav("⌂","Home"){showHome()},LinearLayout.LayoutParams(0,dp(58),1f))
  bottom.addView(nav("✣","AI"){AiEntry.openAssistant(this){hid}},LinearLayout.LayoutParams(0,dp(58),1f))
  bottom.addView(nav("▣","Target"){AiEntry.openTargetSystem(this)},LinearLayout.LayoutParams(0,dp(58),1f))
  bottom.addView(nav("⚙","Settings"){AiEntry.openSettings(this)},LinearLayout.LayoutParams(0,dp(58),1f))
  root.addView(bottom,LinearLayout.LayoutParams(-1,dp(58)))

  val splash=LinearLayout(this).apply{
   orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER_HORIZONTAL;setPadding(dp(22),dp(30),dp(22),dp(24));setBackgroundColor(android.graphics.Color.WHITE)
   addView(ImageView(this@MainActivity).apply{setImageResource(R.drawable.black_cat_full);scaleType=ImageView.ScaleType.CENTER_INSIDE;contentDescription="Black Cat"},LinearLayout.LayoutParams(-1,0,1f))
   addView(BlackCatStyle.label(this@MainActivity,"Black Cat AI",30f,android.graphics.Color.BLACK,true).apply{gravity=Gravity.CENTER})
   addView(BlackCatStyle.label(this@MainActivity,"Remote · Automate · Control",13f,android.graphics.Color.DKGRAY).apply{gravity=Gravity.CENTER;setPadding(0,dp(4),0,dp(16))})
   addView(ProgressBar(this@MainActivity,null,android.R.attr.progressBarStyleHorizontal).apply{isIndeterminate=false;max=100;progress=72;progressTintList=android.content.res.ColorStateList.valueOf(0xff74f45b.toInt());progressBackgroundTintList=android.content.res.ColorStateList.valueOf(0xff45515a.toInt())},LinearLayout.LayoutParams(-1,dp(8)).apply{leftMargin=dp(38);rightMargin=dp(38)})
  }
  stack.addView(splash,FrameLayout.LayoutParams(-1,-1))
  setContentView(stack)
  Handler(Looper.getMainLooper()).postDelayed({if(splash.parent!=null)stack.removeView(splash)},700)
 }
 private fun sendText(v:String){scope.launch{var unsupported=0;for(c in v){val p=HidReports.char(c);if(p!=null){hid.sendKeyboard(p.second,p.first);delay(12)}else unsupported++};event("PASS text submitted chars="+v.length+" unsupported="+unsupported+" content-not-logged")}}
 private fun event(s:String){diagnostics.add(s);runOnUiThread{if(s.contains("HID_REGISTERED"))status.text="READY TO PAIR";if(s.contains("HID_CONNECTED"))status.text="READY";if(s.contains("Bluetooth off"))status.text="BLUETOOTH OFF"}}
 private fun sectionLp()=LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(8)}
 private fun weight()=LinearLayout.LayoutParams(0,dp(46),1f)
 private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
 override fun onDestroy(){scope.cancel();hid.close();super.onDestroy()}
}