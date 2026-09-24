package com.blackcat.remote

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.*
import android.content.pm.PackageManager
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.*
import kotlinx.coroutines.*

class MainActivity : android.app.Activity() {
    private val scope=CoroutineScope(Dispatchers.Main+SupervisorJob())
    private var service:HidService?=null
    private var bound=false
    private lateinit var status:TextView
    private lateinit var log:TextView
    private lateinit var pairButton:Button

    override fun onRequestPermissionsResult(r:Int,p:Array<out String>,g:IntArray){
        super.onRequestPermissionsResult(r,p,g)
        if(r==7&&g.isNotEmpty()&&g.all{it==PackageManager.PERMISSION_GRANTED}) startCore()
        else write("FAIL Bluetooth permission denied")
    }

    private val connection=object:ServiceConnection{
        override fun onServiceConnected(n:ComponentName?,b:IBinder?){
            service=(b as? HidService.LocalBinder)?.getService()
            bound=service!=null
            write("PASS HID service bound")
            observe()
        }
        override fun onServiceDisconnected(n:ComponentName?){
            service=null;bound=false;write("WARN HID service disconnected")
        }
    }

    override fun onCreate(s:Bundle?){
        super.onCreate(s)
        buildUi()
        ensurePermissions()
    }

    private fun ensurePermissions(){
        if(Build.VERSION.SDK_INT>=31){
            val needed=mutableListOf<String>()
            if(checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED) needed+=Manifest.permission.BLUETOOTH_CONNECT
            if(checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE)!=PackageManager.PERMISSION_GRANTED) needed+=Manifest.permission.BLUETOOTH_ADVERTISE
            if(needed.isNotEmpty()){requestPermissions(needed.toTypedArray(),7);return}
        }
        startCore()
    }

    private fun startCore(){
        val i=Intent(this,HidService::class.java)
        if(Build.VERSION.SDK_INT>=26)startForegroundService(i)else startService(i)
        bindService(i,connection,BIND_AUTO_CREATE)
        write("INFO starting Bluetooth HID keyboard core")
    }

    private fun observe(){
        val s=service?:return
        scope.launch{
            s.appRegistered.collect{registered->
                pairButton.isEnabled=registered
                write(if(registered)"PASS HID keyboard registered — ready for computer pairing" else "INFO waiting for HID registration")
            }
        }
        scope.launch{
            s.connectionState.collect{st->
                status.text=when(st){
                    is ConnectionState.Connected->"READY — CONNECTED"
                    is ConnectionState.Connecting->"CONNECTING"
                    is ConnectionState.Error->"ERROR: "+st.message
                    ConnectionState.Disconnected->"PAIR FROM COMPUTER"
                    ConnectionState.Idle->"PAIR FROM COMPUTER"
                }
                write("STATE "+status.text)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun makePairable(){
        val s=service
        if(s==null||s.appRegistered.value!=true){
            write("WAIT HID keyboard is not registered yet")
            return
        }
        val intent=Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply{
            putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION,300)
        }
        try{
            write("INFO requesting 5-minute Bluetooth discoverability")
            startActivityForResult(intent,88)
        }catch(t:Throwable){
            write("FAIL discoverable request: "+(t.message?:"unknown error"))
        }
    }

    @Deprecated("Deprecated in Android API; retained for broad minSdk compatibility")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode==88){
            if(resultCode>0) write("PASS phone discoverable for $resultCode seconds — on the computer open Bluetooth and select this phone")
            else write("WARN discoverability was not enabled")
        }
    }

    private fun buildUi(){
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(16,16,16,16)}
        root.addView(TextView(this).apply{text="BLACK CAT REMOTE";textSize=22f})
        root.addView(TextView(this).apply{text="Bluetooth HID keyboard — pair from the computer";textSize=14f})

        log=TextView(this).apply{text="DEBUG\n";textSize=10f;setPadding(8,8,8,8)}
        val logScroll=ScrollView(this).apply{
            addView(log)
            setOnClickListener{layoutParams.height=if(layoutParams.height<500)dp(360) else dp(100);requestLayout()}
        }
        root.addView(logScroll,LinearLayout.LayoutParams(-1,dp(100)))

        val debugActions=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        debugActions.addView(Button(this).apply{
            text="COPY LOG"
            setOnClickListener{
                (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                    .setPrimaryClip(android.content.ClipData.newPlainText("Black Cat diagnostics",log.text))
                write("PASS diagnostics copied")
            }
        },weight())
        debugActions.addView(Button(this).apply{text="CLEAR";setOnClickListener{log.text="DEBUG\n"}},weight())
        root.addView(debugActions)

        status=TextView(this).apply{text="STARTING";textSize=18f;setPadding(0,12,0,12)}
        root.addView(status)

        pairButton=Button(this).apply{
            text="PAIR FROM COMPUTER"
            isEnabled=false
            setOnClickListener{makePairable()}
        }
        root.addView(pairButton)
        root.addView(TextView(this).apply{
            text="1. Tap PAIR FROM COMPUTER.\n2. On Ubuntu/Windows/macOS open Bluetooth.\n3. Select this phone as the keyboard device.\n4. Accept the normal pairing request."
            textSize=13f
            setPadding(8,8,8,16)
        })

        val input=EditText(this).apply{hint="Type text to send"}
        root.addView(input)
        root.addView(Button(this).apply{
            text="SEND"
            setOnClickListener{
                val v=input.text.toString()
                scope.launch{service?.reportSender?.sendString(v)}
                input.text.clear()
                write("PASS text submitted (content not logged)")
            }
        })

        val pad=TextView(this).apply{text="TOUCHPAD";gravity=Gravity.CENTER;textSize=18f;setBackgroundColor(0xffdddddd.toInt())}
        var lx=0f;var ly=0f
        pad.setOnTouchListener{_,e->
            when(e.actionMasked){
                MotionEvent.ACTION_DOWN->{lx=e.x;ly=e.y;true}
                MotionEvent.ACTION_MOVE->{val dx=(e.x-lx).toInt();val dy=(e.y-ly).toInt();lx=e.x;ly=e.y;service?.reportSender?.queueMouseMove(dx,dy);true}
                else->true
            }
        }
        root.addView(pad,LinearLayout.LayoutParams(-1,0,1f))

        val clicks=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        fun clickButton(label:String,mask:Int)=Button(this).apply{text=label;setOnClickListener{scope.launch{service?.reportSender?.tapMouseClick(mask)}}}
        clicks.addView(clickButton("LEFT",MouseButtonMask.LEFT.mask),weight())
        clicks.addView(clickButton("MIDDLE",MouseButtonMask.MIDDLE.mask),weight())
        clicks.addView(clickButton("RIGHT",MouseButtonMask.RIGHT.mask),weight())
        root.addView(clicks)

        root.addView(Button(this).apply{text="DISCONNECT";setOnClickListener{service?.disconnectCurrent()}})
        setContentView(root)
    }

    private fun write(s:String){
        log.append("\n"+s)
        if(log.text.length>12000)log.text=log.text.takeLast(9000)
    }
    private fun weight()=LinearLayout.LayoutParams(0,dp(48),1f)
    private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()

    override fun onDestroy(){
        scope.cancel()
        if(bound)try{unbindService(connection)}catch(_:Throwable){}
        super.onDestroy()
    }
}
