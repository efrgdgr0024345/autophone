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
  val header=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(12),dp(8),dp(10),dp(7));setBackgroundColor(BlackCatStyle.PAPER)}
  val logo=ImageView(this).apply{setImageResource(R.drawable.black_cat_emblem);scaleType=ImageView.ScaleType.CENTER_CROP;contentDescription="Black Cat";background=BlackCatStyle.round(this@MainActivity,BlackCatStyle.PAPER,30,BlackCatStyle.LINE,1);clipToOutline=true}
  header.addView(logo,LinearLayout.LayoutParams(dp(46),dp(46)))
  val brand=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(9),0,0,0);addView(BlackCatStyle.label(this@MainActivity,"BLACK CAT",15f,BlackCatStyle.INK,true).apply{letterSpacing=.12f});addView(BlackCatStyle.label(this@MainActivity,"Bluetooth keyboard & mouse",11f,BlackCatStyle.MUTED))}
  header.addView(brand,LinearLayout.LayoutParams(0,-2,1f));root.addView(header)
  val statusRow=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(12),dp(7),dp(12),dp(7));setBackgroundColor(BlackCatStyle.BG)}
  status=TextView(this).apply{text="STARTING"};BlackCatStyle.styleStatus(this,status);statusRow.addView(status)
  statusRow.addView(BlackCatStyle.label(this,"Standard Bluetooth HID",11f,BlackCatStyle.MUTED).apply{setPadding(dp(10),0,0,0)},LinearLayout.LayoutParams(0,-2,1f));root.addView(statusRow)
  val body=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(10),0,dp(10),dp(8))};root.addView(body,LinearLayout.LayoutParams(-1,0,1f))
  val pairCard=BlackCatStyle.card(this)
  val pairButton=Button(this).apply{text="Pair as keyboard / mouse";setOnClickListener{pair()}};BlackCatStyle.styleButton(this,pairButton,true);pairCard.addView(pairButton,LinearLayout.LayoutParams(-1,dp(48)))
  pairCard.addView(BlackCatStyle.label(this,"Computer Bluetooth → Add device → select this phone",11f,BlackCatStyle.MUTED).apply{setPadding(dp(2),dp(7),0,0)});body.addView(pairCard,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(7)})
  val tools=BlackCatStyle.card(this).apply{setPadding(dp(10),dp(8),dp(10),dp(8))}
  log=TextView(this).apply{textSize=10f;setTextColor(BlackCatStyle.MUTED);typeface=android.graphics.Typeface.MONOSPACE;setPadding(dp(5),dp(3),dp(5),dp(3))}
  val ls=ScrollView(this).apply{addView(log);background=BlackCatStyle.round(this@MainActivity,BlackCatStyle.FIELD,10,BlackCatStyle.LINE,1);setOnClickListener{layoutParams.height=if(layoutParams.height<dp(120))dp(200)else dp(58);requestLayout()}};tools.addView(ls,LinearLayout.LayoutParams(-1,dp(58)))
  val da=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;setPadding(0,dp(6),0,0)}
  val copy=Button(this).apply{text="Copy log";setOnClickListener{(getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("Black Cat diagnostics",diagnostics.snapshot()))}};BlackCatStyle.styleButton(this,copy,false,true);da.addView(copy,weight())
  val clear=Button(this).apply{text="Clear";setOnClickListener{diagnostics.clear()}};BlackCatStyle.styleButton(this,clear,false,true);da.addView(clear,weight())
  tools.addView(da);body.addView(tools,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(7)})
  // BEGIN AI-ONLY ENTRY
  AiEntry.attach(this,da){hid}
  // END AI-ONLY ENTRY
  val sendRow=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
  val input=EditText(this).apply{hint="Type text to send"};BlackCatStyle.styleInput(this,input);sendRow.addView(input,LinearLayout.LayoutParams(0,dp(48),1f).apply{rightMargin=dp(6)})
  val send=Button(this).apply{text="Send";setOnClickListener{sendText(input.text.toString());input.text.clear()}};BlackCatStyle.styleButton(this,send,true);sendRow.addView(send,LinearLayout.LayoutParams(dp(82),dp(48)));body.addView(sendRow,LinearLayout.LayoutParams(-1,dp(48)).apply{bottomMargin=dp(7)})
  val pad=TextView(this).apply{text="TOUCHPAD\nDrag to move pointer";gravity=Gravity.CENTER;textSize=15f;setTextColor(BlackCatStyle.COMMAND_TEXT);typeface=android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);background=BlackCatStyle.round(this@MainActivity,BlackCatStyle.COMMAND_BG,16)};var x=0f;var y=0f;pad.setOnTouchListener{_,e->when(e.actionMasked){MotionEvent.ACTION_DOWN->{x=e.x;y=e.y;true};MotionEvent.ACTION_MOVE->{hid.sendMouse(0,(e.x-x).toInt(),(e.y-y).toInt());x=e.x;y=e.y;true};else->true}};body.addView(pad,LinearLayout.LayoutParams(-1,0,1f).apply{bottomMargin=dp(7)})
  val clicks=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
  fun mb(label:String,mask:Int):Button {
   return Button(this).apply {
    text=label
    BlackCatStyle.styleButton(this@MainActivity,this,false,true)
    setOnClickListener { hid.sendMouse(mask,0,0); hid.sendMouse(0,0,0) }
   }
  }
  clicks.addView(mb("Left",HidReports.LEFT),weight());clicks.addView(mb("Middle",HidReports.MIDDLE),weight());clicks.addView(mb("Right",HidReports.RIGHT),weight());body.addView(clicks)
  val kb=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;visibility=View.GONE;setPadding(0,dp(5),0,0)}
  val keyboardToggle=Button(this).apply{text="Full keyboard";setOnClickListener{val show=kb.visibility==View.GONE;kb.visibility=if(show)View.VISIBLE else View.GONE;pad.visibility=if(show)View.GONE else View.VISIBLE;text=if(show)"Touchpad" else "Full keyboard"}};BlackCatStyle.styleButton(this,keyboardToggle,false);body.addView(keyboardToggle,LinearLayout.LayoutParams(-1,dp(46)).apply{topMargin=dp(5)})
  fun k(label:String,key:Int,mod:Int=0):Button {
   return Button(this).apply{text=label;BlackCatStyle.styleButton(this@MainActivity,this,false,true);setOnClickListener{hid.sendKeyboard(mod,key)}}
  }
  fun row(vararg buttons:Button):LinearLayout {
   return LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;buttons.forEach{addView(it,weight())}}
  }
  kb.addView(row(k("ESC",41),k("TAB",43),k("BKSP",42),k("DEL",76),k("ENTER",40)));kb.addView(row(k("CTRL",0,HidReports.CTRL),k("SHIFT",0,HidReports.SHIFT),k("ALT",0,HidReports.ALT),k("GUI",0,HidReports.GUI)));kb.addView(row(k("INS",73),k("HOME",74),k("END",77),k("PGUP",75),k("PGDN",78)));kb.addView(row(k("←",80),k("↑",82),k("↓",81),k("→",79)));kb.addView(row(k("F1",58),k("F2",59),k("F3",60),k("F4",61),k("F5",62),k("F6",63)));kb.addView(row(k("F7",64),k("F8",65),k("F9",66),k("F10",67),k("F11",68),k("F12",69)));body.addView(kb);setContentView(root)
 }
 private fun sendText(v:String){scope.launch{var unsupported=0;for(c in v){val p=HidReports.char(c);if(p!=null){hid.sendKeyboard(p.second,p.first);delay(12)}else unsupported++};event("PASS text submitted chars="+v.length+" unsupported="+unsupported+" content-not-logged")}}
 private fun event(s:String){diagnostics.add(s);runOnUiThread{if(s.contains("HID_REGISTERED"))status.text="READY TO PAIR";if(s.contains("HID_CONNECTED"))status.text="READY";if(s.contains("Bluetooth off"))status.text="BLUETOOTH OFF"}}
 private fun weight()=LinearLayout.LayoutParams(0,dp(46),1f);private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
 override fun onDestroy(){scope.cancel();hid.close();super.onDestroy()}
}