package com.blackcat.remote

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.*

class MainActivity:Activity(){
    private lateinit var hid:HidManager
    private lateinit var status:TextView
    private lateinit var log:TextView
    override fun onCreate(state:Bundle?){super.onCreate(state);buildUi();hid=HidManager(this){event(it)};permissionsOrInit()}
    private fun permissionsOrInit(){
        if(Build.VERSION.SDK_INT>=31){val need=mutableListOf<String>();if(checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)need+=Manifest.permission.BLUETOOTH_CONNECT;if(checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE)!=PackageManager.PERMISSION_GRANTED)need+=Manifest.permission.BLUETOOTH_ADVERTISE;if(need.isNotEmpty()){requestPermissions(need.toTypedArray(),10);return}}
        initHid()
    }
    private fun initHid(){status.text="HID REGISTERING";if(!hid.init())status.text="BLUETOOTH/HID ERROR"}
    override fun onRequestPermissionsResult(r:Int,p:Array<out String>,g:IntArray){super.onRequestPermissionsResult(r,p,g);if(r==10&&g.isNotEmpty()&&g.all{it==PackageManager.PERMISSION_GRANTED})initHid()else event("FAIL Bluetooth permission denied")}
    @Suppress("DEPRECATION") private fun pair(){
        if(!hid.registered){event("WAIT HID must register before pairing");status.text="HID REGISTERING";return}
        event("PASS HID already registered before discoverability")
        status.text="WAITING FOR COMPUTER"
        try{startActivityForResult(hid.discoverableIntent(300),20);event("INFO requesting discoverability") }catch(t:Throwable){event("FAIL discoverability: "+(t.message?:"unknown"))}
    }
    @Deprecated("Activity result retained for broad Android compatibility") override fun onActivityResult(r:Int,result:Int,data:Intent?){super.onActivityResult(r,result,data);if(r==20){if(result>0){status.text="DISCOVERABLE — ADD ON COMPUTER";event("PASS discoverable for "+result+" seconds")}else{status.text="READY TO PAIR";event("WARN discoverability declined")}}}
    private fun buildUi(){
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(24,24,24,24)}
        root.addView(TextView(this).apply{text="BLACK CAT REMOTE";textSize=24f})
        status=TextView(this).apply{text="STARTING";textSize=18f};root.addView(status)
        root.addView(Button(this).apply{text="PAIR AS KEYBOARD/MOUSE";setOnClickListener{pair()}})
        root.addView(TextView(this).apply{text="On computer: Bluetooth → Add device → select this phone → Pair";textSize=14f})
        log=TextView(this).apply{text="DEBUG";textSize=11f};root.addView(ScrollView(this).apply{addView(log)},LinearLayout.LayoutParams(-1,0,1f))
        setContentView(root)
    }
    private fun event(s:String){runOnUiThread{log.append("\n"+s);if(s.contains("HID_REGISTERED"))status.text="READY TO PAIR";if(s.contains("HID_CONNECTED"))status.text="READY"}}
    override fun onDestroy(){hid.close();super.onDestroy()}
}