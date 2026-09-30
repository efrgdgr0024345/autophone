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

  val hero=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER_HORIZONTAL;setPadding(dp(12),dp(6),dp(12),0);setBackgroundColor(BlackCatStyle.PAPER)}
  val cat=ImageView(this).apply{setImageResource(R.drawable.black_cat_full);scaleType=ImageView.ScaleType.CENTER_CROP;contentDescription="Black Cat"}
  hero.addView(cat,LinearLayout.LayoutParams(-1,dp(190)))
  hero.addView(BlackCatStyle.label(this,"Black Cat AI Remote",20f,BlackCatStyle.INK,true).apply{gravity=Gravity.CENTER})
  hero.addView(BlackCatStyle.label(this,"Control · Automate · Assist",11f,BlackCatStyle.MUTED).apply{gravity=Gravity.CENTER;setPadding(0,dp(2),0,dp(7))})
  root.addView(hero)

  val statusRow=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(12),dp(7),dp(12),dp(7));setBackgroundColor(BlackCatStyle.PAPER)}
  status=TextView(this).apply{text="STARTING"};BlackCatStyle.styleStatus(this,status);statusRow.addView(status)
  statusRow.addView(BlackCatStyle.label(this,"Standard Bluetooth HID",11f,BlackCatStyle.MUTED).apply{setPadding(dp(10),0,0,0)},LinearLayout.LayoutParams(0,-2,1f))
  root.addView(statusRow)

  val pairButton=Button(this).apply{text="Make Discoverable";setOnClickListener{pair()}}
  BlackCatStyle.styleButton(this,pairButton,true)
  root.addView(pairButton,LinearLayout.LayoutParams(-1,dp(52)).apply{leftMargin=dp(12);rightMargin=dp(12);bottomMargin=dp(8)})

  val scroll=ScrollView(this).apply{isFillViewport=true;overScrollMode=View.OVER_SCROLL_IF_CONTENT_SCROLLS}
  val body=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(10),0,dp(10),dp(14))}
  scroll.addView(body,ViewGroup.LayoutParams(-1,-2))
  root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))

  val sendPanel=BlackCatStyle.card(this).apply{visibility=View.GONE}
  val input=EditText(this).apply{hint="Text to type on the computer"};BlackCatStyle.styleInput(this,input)
  val send=Button(this).apply{text="Send text";setOnClickListener{sendText(input.text.toString());input.text.clear()}};BlackCatStyle.styleButton(this,send,true)
  sendPanel.addView(input,LinearLayout.LayoutParams(-1,dp(50)).apply{bottomMargin=dp(7)})
  sendPanel.addView(send,LinearLayout.LayoutParams(-1,dp(48)))

  val pointerPanel=BlackCatStyle.card(this).apply{visibility=View.GONE}
  val pad=TextView(this).apply{text="TOUCHPAD\nDrag to move pointer";gravity=Gravity.CENTER;textSize=15f;setTextColor(BlackCatStyle.COMMAND_TEXT);typeface=android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);background=BlackCatStyle.round(this@MainActivity,BlackCatStyle.COMMAND_BG,16)}
  var x=0f;var y=0f
  pad.setOnTouchListener{_,e->when(e.actionMasked){MotionEvent.ACTION_DOWN->{x=e.x;y=e.y;true};MotionEvent.ACTION_MOVE->{hid.sendMouse(0,(e.x-x).toInt(),(e.y-y).toInt());x=e.x;y=e.y;true};else->true}}
  pointerPanel.addView(pad,LinearLayout.LayoutParams(-1,dp(230)).apply{bottomMargin=dp(7)})
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

  val keyboardPanel=BlackCatStyle.card(this).apply{visibility=View.GONE}
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

  fun hideRemotePanels(){
   sendPanel.visibility=View.GONE
   pointerPanel.visibility=View.GONE
   keyboardPanel.visibility=View.GONE
  }

  val tiles=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
  fun tile(text:String,sub:String,click:()->Unit):Button{
   return Button(this).apply{
    this.text="$text\n$sub"
    isAllCaps=false
    textSize=14f
    gravity=Gravity.CENTER
    setTextColor(BlackCatStyle.COMMAND_TEXT)
    typeface=android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD)
    background=BlackCatStyle.round(this@MainActivity,BlackCatStyle.COMMAND_BG,16)
    setPadding(dp(8),dp(8),dp(8),dp(8))
    setOnClickListener{click()}
   }
  }
  val topTiles=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
  val assistantTileRow=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
  // BEGIN AI-ONLY ENTRY
  AiEntry.attach(this,assistantTileRow){hid}
  // END AI-ONLY ENTRY
  topTiles.addView(assistantTileRow,LinearLayout.LayoutParams(0,dp(96),1f).apply{rightMargin=dp(4)})
  topTiles.addView(tile("Send Text","Type on computer"){hideRemotePanels();sendPanel.visibility=View.VISIBLE},LinearLayout.LayoutParams(0,dp(96),1f).apply{leftMargin=dp(4)})
  tiles.addView(topTiles)
  val bottomTiles=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;setPadding(0,dp(8),0,0)}
  bottomTiles.addView(tile("Touchpad","Move and click"){hideRemotePanels();pointerPanel.visibility=View.VISIBLE},LinearLayout.LayoutParams(0,dp(96),1f).apply{rightMargin=dp(4)})
  bottomTiles.addView(tile("Keyboard","Full HID keys"){hideRemotePanels();keyboardPanel.visibility=View.VISIBLE},LinearLayout.LayoutParams(0,dp(96),1f).apply{leftMargin=dp(4)})
  tiles.addView(bottomTiles)
  body.addView(tiles,sectionLp())
  body.addView(sendPanel,sectionLp())
  body.addView(pointerPanel,sectionLp())
  body.addView(keyboardPanel,sectionLp())

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