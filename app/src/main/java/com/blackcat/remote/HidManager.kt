/* Pairing lifecycle adapted from GhostBoard by ToxicOrca (MIT). See GHOSTBOARD_LICENSE. */
package com.blackcat.remote

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import java.util.concurrent.Executors

class HidManager(private val context: Context, private val events:(String)->Unit) {
    private val adapter=(context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    private val executor=Executors.newSingleThreadExecutor()
    private var hid:BluetoothHidDevice?=null
    var registered=false; private set
    var connected:BluetoothDevice?=null; private set

    fun init():Boolean {
        if(adapter==null){events("FAIL Bluetooth unavailable");return false}
        if(!adapter.isEnabled){events("FAIL Bluetooth off");return false}
        if(!canConnect()){events("FAIL Bluetooth permission missing");return false}
        return try { events("INFO requesting HID_DEVICE profile"); adapter.getProfileProxy(context,listener,BluetoothProfile.HID_DEVICE) }
        catch(t:Throwable){events("FAIL HID profile: "+(t.message?:"unknown"));false}
    }

    fun discoverableIntent(seconds:Int=300)=Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply{putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION,seconds)}

    @SuppressLint("MissingPermission")
    fun disconnect(){if(!canConnect())return;val d=connected?:return;try{hid?.disconnect(d);events("INFO HID disconnect requested")}catch(t:Throwable){events("FAIL disconnect: "+(t.message?:"unknown"))}}

    @SuppressLint("MissingPermission")
    fun sendKeyboard(modifier:Int,key:Int):Boolean {
        if(!canConnect())return false;val d=connected?:return false;val h=hid?:return false
        return try{val down=h.sendReport(d,HidDescriptor.KEYBOARD_ID,byteArrayOf(modifier.toByte(),0,key.toByte(),0,0,0,0,0));val up=h.sendReport(d,HidDescriptor.KEYBOARD_ID,ByteArray(8));down&&up}catch(_:Throwable){false}
    }

    @SuppressLint("MissingPermission")
    fun sendMouse(buttons:Int,dx:Int,dy:Int,wheel:Int=0):Boolean {
        if(!canConnect())return false;val d=connected?:return false
        return try{hid?.sendReport(d,HidDescriptor.MOUSE_ID,byteArrayOf(buttons.toByte(),dx.coerceIn(-127,127).toByte(),dy.coerceIn(-127,127).toByte(),wheel.coerceIn(-127,127).toByte()))==true}catch(_:Throwable){false}
    }

    @SuppressLint("MissingPermission")
    private fun register(h:BluetoothHidDevice){
        if(!canConnect())return
        val sdp=BluetoothHidDeviceAppSdpSettings("Black Cat","Phone as Keyboard & Mouse","Black Cat",BluetoothHidDevice.SUBCLASS1_COMBO,HidDescriptor.COMBO)
        val qos=BluetoothHidDeviceAppQosSettings(BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT,800,9,0,11250,BluetoothHidDeviceAppQosSettings.MAX)
        try{val accepted=h.registerApp(sdp,null,qos,executor,callback);events(if(accepted)"PASS HID registerApp accepted" else "FAIL HID registerApp rejected")}catch(t:Throwable){events("FAIL HID registerApp: "+(t.message?:"unknown"))}
    }

    @SuppressLint("MissingPermission")
    private val listener=object:BluetoothProfile.ServiceListener{
        override fun onServiceConnected(profile:Int,proxy:BluetoothProfile){if(profile!=BluetoothProfile.HID_DEVICE)return;hid=proxy as BluetoothHidDevice;events("PASS HID_DEVICE profile acquired");register(hid!!)}
        override fun onServiceDisconnected(profile:Int){if(profile==BluetoothProfile.HID_DEVICE){registered=false;connected=null;hid=null;events("WARN HID_DEVICE profile disconnected")}}
    }

    @SuppressLint("MissingPermission")
    private val callback=object:BluetoothHidDevice.Callback(){
        override fun onAppStatusChanged(device:BluetoothDevice?,ok:Boolean){registered=ok;events(if(ok)"PASS HID_REGISTERED — ready for computer-side pairing" else "FAIL HID unregistered")}
        override fun onConnectionStateChanged(device:BluetoothDevice,state:Int){when(state){BluetoothProfile.STATE_CONNECTED->{connected=device;events("PASS HID_CONNECTED — READY")};BluetoothProfile.STATE_CONNECTING->events("INFO HID_CONNECTING");BluetoothProfile.STATE_DISCONNECTED->{if(connected==device)connected=null;events("INFO HID_DISCONNECTED")};BluetoothProfile.STATE_DISCONNECTING->events("INFO HID_DISCONNECTING")}}
        override fun onGetReport(device:BluetoothDevice?,type:Byte,id:Byte,size:Int){val report=when(id.toInt()){HidDescriptor.KEYBOARD_ID->ByteArray(8);HidDescriptor.MOUSE_ID->ByteArray(4);else->null};try{if(report!=null)hid?.replyReport(device,type,id,report)else hid?.reportError(device,BluetoothHidDevice.ERROR_RSP_INVALID_RPT_ID)}catch(_:Throwable){}}
        override fun onSetReport(device:BluetoothDevice?,type:Byte,id:Byte,data:ByteArray?){try{hid?.reportError(device,BluetoothHidDevice.ERROR_RSP_SUCCESS)}catch(_:Throwable){}}
    }

    @SuppressLint("MissingPermission")
    fun close(){try{if(canConnect())hid?.unregisterApp()}catch(_:Throwable){};try{adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE,hid)}catch(_:Throwable){};executor.shutdownNow();hid=null;registered=false;connected=null}
    private fun canConnect()=Build.VERSION.SDK_INT<31||context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED
}