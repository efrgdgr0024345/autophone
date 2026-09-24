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
  val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(16,16,16,16)}
  root.addView(TextView(this).apply{text="BLACK CAT REMOTE";textSize=22f});status=TextView(this).apply{text="STARTING";textSize=17f};root.addView(status)
  root.addView(Button(this).apply{text="PAIR AS KEYBOARD/MOUSE";setOnClickListener{pair()}})
  root.addView(TextView(this).apply{text="Computer: Bluetooth → Add device → select this phone → Pair"})
  log=TextView(this).apply{textSize=10f};val ls=ScrollView(this).apply{addView(log);setOnClickListener{layoutParams.height=if(layoutParams.height<500)dp(320)else dp(90);requestLayout()}};root.addView(ls,LinearLayout.LayoutParams(-1,dp(90)))
  val da=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL};da.addView(Button(this).apply{text="COPY LOG";setOnClickListener{(getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("Black Cat diagnostics",diagnostics.snapshot()))}},weight());da.addView(Button(this).apply{text="CLEAR";setOnClickListener{diagnostics.clear()}},weight());root.addView(da)
  val input=EditText(this).apply{hint="Type text to send"};root.addView(input);root.addView(Button(this).apply{text="SEND";setOnClickListener{sendText(input.text.toString());input.text.clear()}})
  val pad=TextView(this).apply{text="TOUCHPAD";gravity=Gravity.CENTER;setBackgroundColor(0xffdddddd.toInt())};var x=0f;var y=0f;pad.setOnTouchListener{_,e->when(e.actionMasked){MotionEvent.ACTION_DOWN->{x=e.x;y=e.y;true};MotionEvent.ACTION_MOVE->{hid.sendMouse(0,(e.x-x).toInt(),(e.y-y).toInt());x=e.x;y=e.y;true};else->true}};root.addView(pad,LinearLayout.LayoutParams(-1,0,1f))
  val clicks=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL};fun mb(t:String,m:Int)=Button(this).apply{text=t;setOnClickListener{hid.sendMouse(m,0,0);hid.sendMouse(0,0,0)}};clicks.addView(mb("LEFT",HidReports.LEFT),weight());clicks.addView(mb("MIDDLE",HidReports.MIDDLE),weight());clicks.addView(mb("RIGHT",HidReports.RIGHT),weight());root.addView(clicks)
  val kb=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;visibility=View.GONE};root.addView(Button(this).apply{text="FULL KEYBOARD";setOnClickListener{kb.visibility=if(kb.visibility==View.GONE)View.VISIBLE else View.GONE}});fun k(t:String,key:Int,mod:Int=0)=Button(this).apply{text=t;setOnClickListener{hid.sendKeyboard(mod,key)}};fun row(vararg b:Button)=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;b.forEach{addView(it,weight())}}
  kb.addView(row(k("ESC",41),k("TAB",43),k("BKSP",42),k("DEL",76),k("ENTER",40)));kb.addView(row(k("CTRL",0,HidReports.CTRL),k("SHIFT",0,HidReports.SHIFT),k("ALT",0,HidReports.ALT),k("GUI",0,HidReports.GUI)));kb.addView(row(k("INS",73),k("HOME",74),k("END",77),k("PGUP",75),k("PGDN",78)));kb.addView(row(k("←",80),k("↑",82),k("↓",81),k("→",79)));kb.addView(row(k("F1",58),k("F2",59),k("F3",60),k("F4",61),k("F5",62),k("F6",63)));kb.addView(row(k("F7",64),k("F8",65),k("F9",66),k("F10",67),k("F11",68),k("F12",69)));root.addView(kb);setContentView(root)
 }
 private fun sendText(v:String){scope.launch{var unsupported=0;for(c in v){val p=HidReports.char(c);if(p!=null){hid.sendKeyboard(p.second,p.first);delay(12)}else unsupported++};event("PASS text submitted chars="+v.length+" unsupported="+unsupported+" content-not-logged")}}
 private fun event(s:String){diagnostics.add(s);runOnUiThread{if(s.contains("HID_REGISTERED"))status.text="READY TO PAIR";if(s.contains("HID_CONNECTED"))status.text="READY";if(s.contains("Bluetooth off"))status.text="BLUETOOTH OFF"}}
 private fun weight()=LinearLayout.LayoutParams(0,dp(46),1f);private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
 override fun onDestroy(){scope.cancel();hid.close();super.onDestroy()}
}