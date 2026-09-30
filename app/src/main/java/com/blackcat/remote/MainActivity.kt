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
 override fun onRequestPermissionsResult(r:Int,p:Array<out String>,g:IntArray){super.onRequestPermissionsResult(r,p,g);if(r==10&&g.isNotEmpty()&&g.all{it==PackageManager.PERMISSION_GRANTED}){event("PASS BLUETOOTH_PERMISSIONS");initHid()}else event("FAIL Bluetooth permission denied")}
 @Suppress("DEPRECATION") private fun pair(){if(!hid.registered){event("WAIT HID not registered");return};event("PASS HID registered before discoverability");try{startActivityForResult(hid.discoverableIntent(300),20);event("INFO DISCOVERABILITY_REQUESTED")}catch(t:Throwable){event("FAIL discoverability: "+(t.message?:"unknown"))}}
 @Deprecated("Compatibility") override fun onActivityResult(r:Int,result:Int,data:Intent?){super.onActivityResult(r,result,data);if(r==20){if(result>0){status.text="DISCOVERABLE — ADD ON COMPUTER";event("PASS DISCOVERABLE seconds="+result)}else event("WARN discoverability declined")}}
 private fun buildUi(){
  val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(BlackCatStyle.BG)}
  BlackCatStyle.applySystemBarInsets(root)

  val header=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(12),dp(8),dp(10),dp(7));setBackgroundColor(BlackCatStyle.PAPER)}
  val logo=ImageView(this).apply{setImageResource(R.drawable.black_cat_emblem);scaleType=ImageView.ScaleType.CENTER_CROP;contentDescription="Black Cat";background=BlackCatStyle.round(this@MainActivity,BlackCatStyle.PAPER,30,BlackCatStyle.LINE,1);clipToOutline=true}
  header.addView(logo,LinearLayout.LayoutParams(dp(46),dp(46)))
  val brand=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(9),0,0,0);addView(BlackCatStyle.label(this@MainActivity,"BLACK CAT",15f,BlackCatStyle.INK,true).apply{letterSpacing=.12f});addView(BlackCatStyle.label(this@MainActivity,"Remote control",11f,BlackCatStyle.MUTED))}
  header.addView(brand,LinearLayout.LayoutParams(0,-2,1f))
  root.addView(header)

  val statusRow=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(12),dp(7),dp(12),dp(7));setBackgroundColor(BlackCatStyle.BG)}
  status=TextView(this).apply{text="STARTING"};BlackCatStyle.styleStatus(this,status);statusRow.addView(status)
  statusRow.addView(BlackCatStyle.label(this,"Bluetooth HID · no computer app required",11f,BlackCatStyle.MUTED).apply{setPadding(dp(10),0,0,0);maxLines=2},LinearLayout.LayoutParams(0,-2,1f))
  root.addView(statusRow)

  val scroll=ScrollView(this).apply{isFillViewport=true;overScrollMode=View.OVER_SCROLL_IF_CONTENT_SCROLLS}
  val body=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(10),0,dp(10),dp(12))}
  scroll.addView(body,ViewGroup.LayoutParams(-1,-2))
  root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))

  val connect=BlackCatStyle.card(this)
  connect.addView(BlackCatStyle.label(this,"1 · Connect your computer",16f,BlackCatStyle.INK,true))
  connect.addView(BlackCatStyle.label(this,"On the computer, open Bluetooth → Add device. Then make this phone discoverable.",12f,BlackCatStyle.MUTED).apply{setPadding(0,dp(5),0,dp(9))})
  val pairButton=Button(this).apply{text="Make phone discoverable";setOnClickListener{pair()}}
  BlackCatStyle.styleButton(this,pairButton,true)
  connect.addView(pairButton,LinearLayout.LayoutParams(-1,dp(48)))
  body.addView(connect,sectionLp())

  val assistant=BlackCatStyle.card(this)
  assistant.addView(BlackCatStyle.label(this,"2 · Choose how to control it",16f,BlackCatStyle.INK,true))
  assistant.addView(BlackCatStyle.label(this,"Use the remote controls below, or open the Linux Assistant for reviewed command suggestions.",12f,BlackCatStyle.MUTED).apply{setPadding(0,dp(5),0,dp(8))})
  val assistantRow=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
  // BEGIN AI-ONLY ENTRY
  AiEntry.attach(this,assistantRow){hid}
  // END AI-ONLY ENTRY
  assistant.addView(assistantRow,LinearLayout.LayoutParams(-1,-2))
  body.addView(assistant,sectionLp())

  val typeCard=BlackCatStyle.card(this)
  typeCard.addView(BlackCatStyle.label(this,"Type to computer",14f,BlackCatStyle.INK,true).apply{setPadding(0,0,0,dp(7))})
  val sendRow=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
  val input=EditText(this).apply{hint="Text to type"}
  BlackCatStyle.styleInput(this,input)
  sendRow.addView(input,LinearLayout.LayoutParams(0,dp(48),1f).apply{rightMargin=dp(6)})
  val send=Button(this).apply{text="Send";setOnClickListener{sendText(input.text.toString());input.text.clear()}}
  BlackCatStyle.styleButton(this,send,true)
  sendRow.addView(send,LinearLayout.LayoutParams(dp(82),dp(48)))
  typeCard.addView(sendRow)
  body.addView(typeCard,sectionLp())

  val pointerCard=BlackCatStyle.card(this)
  pointerCard.addView(BlackCatStyle.label(this,"Pointer",14f,BlackCatStyle.INK,true).apply{setPadding(0,0,0,dp(7))})
  val pad=TextView(this).apply{text="TOUCHPAD\nDrag to move pointer";gravity=Gravity.CENTER;textSize=15f;setTextColor(BlackCatStyle.COMMAND_TEXT);typeface=android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);background=BlackCatStyle.round(this@MainActivity,BlackCatStyle.COMMAND_BG,16)}
  var x=0f;var y=0f
  pad.setOnTouchListener{_,e->when(e.actionMasked){MotionEvent.ACTION_DOWN->{x=e.x;y=e.y;true};MotionEvent.ACTION_MOVE->{hid.sendMouse(0,(e.x-x).toInt(),(e.y-y).toInt());x=e.x;y=e.y;true};else->true}}
  pointerCard.addView(pad,LinearLayout.LayoutParams(-1,dp(200)).apply{bottomMargin=dp(7)})
  val clicks=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
  fun mb(label:String,mask:Int):Button {
   return Button(this).apply {
    text=label
    BlackCatStyle.styleButton(this@MainActivity,this,false,true)
    setOnClickListener { hid.sendMouse(mask,0,0); hid.sendMouse(0,0,0) }
   }
  }
  clicks.addView(mb("Left",HidReports.LEFT),weight());clicks.addView(mb("Middle",HidReports.MIDDLE),weight());clicks.addView(mb("Right",HidReports.RIGHT),weight())
  pointerCard.addView(clicks)
  body.addView(pointerCard,sectionLp())

  val keyboardCard=BlackCatStyle.card(this)
  val kb=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;visibility=View.GONE;setPadding(0,dp(7),0,0)}
  val keyboardToggle=Button(this).apply{text="Show full keyboard";setOnClickListener{val show=kb.visibility==View.GONE;kb.visibility=if(show)View.VISIBLE else View.GONE;text=if(show)"Hide full keyboard" else "Show full keyboard"}}
  BlackCatStyle.styleButton(this,keyboardToggle,false)
  keyboardCard.addView(keyboardToggle,LinearLayout.LayoutParams(-1,dp(46)))
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
  keyboardCard.addView(kb)
  body.addView(keyboardCard,sectionLp())

  val diagnosticsCard=BlackCatStyle.card(this)
  val diagnosticsBody=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;visibility=View.GONE;setPadding(0,dp(7),0,0)}
  val diagToggle=Button(this).apply{text="Diagnostics";setOnClickListener{val show=diagnosticsBody.visibility==View.GONE;diagnosticsBody.visibility=if(show)View.VISIBLE else View.GONE;text=if(show)"Hide diagnostics" else "Diagnostics"}}
  BlackCatStyle.styleButton(this,diagToggle,false,true)
  diagnosticsCard.addView(diagToggle,LinearLayout.LayoutParams(-1,dp(42)))
  log=TextView(this).apply{textSize=10f;setTextColor(BlackCatStyle.MUTED);typeface=android.graphics.Typeface.MONOSPACE;setPadding(dp(5),dp(3),dp(5),dp(3))}
  val ls=ScrollView(this).apply{addView(log);background=BlackCatStyle.round(this@MainActivity,BlackCatStyle.FIELD,10,BlackCatStyle.LINE,1)}
  diagnosticsBody.addView(ls,LinearLayout.LayoutParams(-1,dp(120)))
  val da=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;setPadding(0,dp(6),0,0)}
  val copy=Button(this).apply{text="Copy log";setOnClickListener{(getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("Black Cat diagnostics",diagnostics.snapshot()))}}
  BlackCatStyle.styleButton(this,copy,false,true);da.addView(copy,weight())
  val clear=Button(this).apply{text="Clear";setOnClickListener{diagnostics.clear()}}
  BlackCatStyle.styleButton(this,clear,false,true);da.addView(clear,weight())
  diagnosticsBody.addView(da)
  diagnosticsCard.addView(diagnosticsBody)
  body.addView(diagnosticsCard,sectionLp())

  setContentView(root)
 }
 private fun sendText(v:String){scope.launch{var unsupported=0;for(c in v){val p=HidReports.char(c);if(p!=null){hid.sendKeyboard(p.second,p.first);delay(12)}else unsupported++};event("PASS text submitted chars="+v.length+" unsupported="+unsupported+" content-not-logged")}}
 private fun event(s:String){diagnostics.add(s);runOnUiThread{if(s.contains("HID_REGISTERED"))status.text="READY TO PAIR";if(s.contains("HID_CONNECTED"))status.text="READY";if(s.contains("Bluetooth off"))status.text="BLUETOOTH OFF"}}
 private fun sectionLp()=LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(8)}
 private fun weight()=LinearLayout.LayoutParams(0,dp(46),1f)
 private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
 override fun onDestroy(){scope.cancel();hid.close();super.onDestroy()}
}