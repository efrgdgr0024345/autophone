package au.com.easyai.blackcatremote;

import android.Manifest;
import android.app.*;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HidService extends Service {
    public interface Listener { void onLog(String line); void onState(String state); }
    private static HidService instance;
    private static Listener listener;
    private final ArrayDeque<String> logs = new ArrayDeque<>();
    private static final int MAX_LOGS = 1200;
    private BluetoothAdapter adapter;
    private BluetoothHidDevice hid;
    private BluetoothDevice target;
    private boolean registered;
    private final ExecutorService callbackExecutor = Executors.newSingleThreadExecutor();

    static final int REPORT_KEYBOARD=1, REPORT_MOUSE=2;
    static final byte[] DESCRIPTOR=new byte[]{
        0x05,0x01,0x09,0x06,(byte)0xA1,0x01,(byte)0x85,0x01,0x05,0x07,0x19,(byte)0xE0,0x29,(byte)0xE7,0x15,0x00,0x25,0x01,0x75,0x01,(byte)0x95,0x08,(byte)0x81,0x02,(byte)0x95,0x01,0x75,0x08,(byte)0x81,0x01,(byte)0x95,0x06,0x75,0x08,0x15,0x00,0x25,0x65,0x05,0x07,0x19,0x00,0x29,0x65,(byte)0x81,0x00,(byte)0xC0,
        0x05,0x01,0x09,0x02,(byte)0xA1,0x01,(byte)0x85,0x02,0x09,0x01,(byte)0xA1,0x00,0x05,0x09,0x19,0x01,0x29,0x03,0x15,0x00,0x25,0x01,(byte)0x95,0x03,0x75,0x01,(byte)0x81,0x02,(byte)0x95,0x01,0x75,0x05,(byte)0x81,0x01,0x05,0x01,0x09,0x30,0x09,0x31,0x09,0x38,0x15,(byte)0x81,0x25,0x7F,0x75,0x08,(byte)0x95,0x03,(byte)0x81,0x06,(byte)0xC0,(byte)0xC0
    };

    public static HidService get(){ return instance; }
    public static void setListener(Listener l){ listener=l; if(instance!=null) instance.replay(); }

    @Override public void onCreate(){
        super.onCreate(); instance=this;
        createChannel();
        startForeground(41,new Notification.Builder(this,"hid")
            .setContentTitle("Black Cat Remote")
            .setContentText("Bluetooth keyboard/mouse service active")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth).build());
        log("SERVICE created Android="+Build.VERSION.RELEASE+" API="+Build.VERSION.SDK_INT);
        initBluetooth();
    }
    @Override public int onStartCommand(Intent i,int flags,int id){ log("SERVICE startCommand flags="+flags+" startId="+id); return START_STICKY; }
    @Override public android.os.IBinder onBind(Intent i){ return null; }
    @Override public void onDestroy(){ log("SERVICE destroy"); releaseAll(); closeProfile(); callbackExecutor.shutdownNow(); instance=null; super.onDestroy(); }

    private void createChannel(){ if(Build.VERSION.SDK_INT>=26){ NotificationManager n=getSystemService(NotificationManager.class); n.createNotificationChannel(new NotificationChannel("hid","Bluetooth HID",NotificationManager.IMPORTANCE_LOW)); } }
    private boolean permitted(){ return Build.VERSION.SDK_INT<31 || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED; }

    private void initBluetooth(){
        if(!permitted()){ state("WAITING_PERMISSION"); log("HID init blocked: BLUETOOTH_CONNECT missing"); return; }
        BluetoothManager bm=getSystemService(BluetoothManager.class); adapter=bm==null?null:bm.getAdapter();
        if(adapter==null){ state("ERROR_NO_ADAPTER"); log("ERROR Bluetooth adapter unavailable"); return; }
        if(!adapter.isEnabled()){ state("BLUETOOTH_OFF"); log("Bluetooth adapter disabled"); return; }
        state("ACQUIRING_HID_PROXY");
        boolean requested=adapter.getProfileProxy(this,profileListener,BluetoothProfile.HID_DEVICE);
        log("getProfileProxy requested="+requested);
    }

    private final BluetoothProfile.ServiceListener profileListener=new BluetoothProfile.ServiceListener(){
        @Override public void onServiceConnected(int profile,BluetoothProfile proxy){
            log("HID proxy callback connected profile="+profile);
            if(profile!=BluetoothProfile.HID_DEVICE) return;
            hid=(BluetoothHidDevice)proxy; state("REGISTERING_HID");
            BluetoothHidDeviceAppSdpSettings sdp=new BluetoothHidDeviceAppSdpSettings("Black Cat Remote","Bluetooth keyboard and mouse","Black Cat",BluetoothHidDevice.SUBCLASS1_COMBO,DESCRIPTOR);
            try { boolean accepted=hid.registerApp(sdp,null,null,callbackExecutor,hidCallback); log("registerApp submitted accepted="+accepted+" (callback is authoritative)"); }
            catch(SecurityException e){ fail("registerApp SecurityException",e); }
        }
        @Override public void onServiceDisconnected(int profile){ log("HID proxy disconnected profile="+profile); registered=false; hid=null; target=null; state("HID_PROXY_LOST"); }
    };

    private final BluetoothHidDevice.Callback hidCallback=new BluetoothHidDevice.Callback(){
        @Override public void onAppStatusChanged(BluetoothDevice d,boolean r){ registered=r; log("onAppStatusChanged registered="+r+" host="+name(d)); state(r?"HID_REGISTERED":"HID_UNREGISTERED"); if(!r){ target=null; } }
        @Override public void onConnectionStateChanged(BluetoothDevice d,int s){ log("onConnectionStateChanged state="+conn(s)+" host="+name(d)); if(s==BluetoothProfile.STATE_CONNECTED){ target=d; state("READY"); } else if(s==BluetoothProfile.STATE_CONNECTING) state("CONNECTING"); else { if(target!=null && target.equals(d)) target=null; releaseLocal(); state("WAITING_HOST"); } }
    };

    public boolean connect(BluetoothDevice d){
        if(!registered||hid==null||d==null||!permitted()){ log("CONNECT rejected precondition registered="+registered+" hid="+(hid!=null)+" device="+(d!=null)); return false; }
        releaseAll(); target=d; state("CONNECTING");
        try { boolean accepted=hid.connect(d); log("connect submitted accepted="+accepted+" host="+name(d)+" (callback is authoritative)"); return accepted; }
        catch(SecurityException e){ fail("connect SecurityException",e); target=null; return false; }
    }
    public void disconnect(){ BluetoothDevice d=target; releaseAll(); if(hid!=null&&d!=null&&permitted()) try{ boolean accepted=hid.disconnect(d); log("disconnect submitted accepted="+accepted); }catch(SecurityException e){ fail("disconnect SecurityException",e); } }
    public boolean readyForPairing(){ return registered&&hid!=null; }
    public boolean ready(){ return registered&&hid!=null&&target!=null; }

    public boolean keyboard(byte mod,byte code){
        if(!ready()) return false;
        try{
            boolean down=hid.sendReport(target,REPORT_KEYBOARD,new byte[]{mod,0,code,0,0,0,0,0});
            boolean up=hid.sendReport(target,REPORT_KEYBOARD,new byte[]{0,0,0,0,0,0,0,0});
            log("KEYBOARD report attempted downAccepted="+down+" releaseAccepted="+up);
            return down&&up;
        }catch(SecurityException e){ fail("keyboard SecurityException",e); return false; }
    }
    public boolean mouse(int buttons,int dx,int dy,int wheel){
        if(!ready()) return false;
        dx=Math.max(-127,Math.min(127,dx)); dy=Math.max(-127,Math.min(127,dy)); wheel=Math.max(-127,Math.min(127,wheel));
        try{ boolean ok=hid.sendReport(target,REPORT_MOUSE,new byte[]{(byte)buttons,(byte)dx,(byte)dy,(byte)wheel}); if(!ok) log("MOUSE report rejected buttons="+buttons+" dx="+dx+" dy="+dy); return ok; }
        catch(SecurityException e){ fail("mouse SecurityException",e); return false; }
    }
    public void releaseAll(){
        if(hid!=null&&target!=null&&permitted()) try{
            boolean k=hid.sendReport(target,REPORT_KEYBOARD,new byte[]{0,0,0,0,0,0,0,0});
            boolean m=hid.sendReport(target,REPORT_MOUSE,new byte[]{0,0,0,0});
            log("RELEASE_ALL_INPUT keyboardAccepted="+k+" mouseAccepted="+m);
        }catch(SecurityException e){ fail("releaseAll SecurityException",e); }
        releaseLocal();
    }
    private void releaseLocal(){ /* no durable key/button state by design */ }

    public List<BluetoothDevice> bonded(){
        ArrayList<BluetoothDevice> out=new ArrayList<>(); if(adapter==null||!permitted()) return out;
        try{ out.addAll(adapter.getBondedDevices()); }catch(SecurityException e){ fail("bonded SecurityException",e); }
        return out;
    }
    public String diagnostics(){ synchronized(logs){ StringBuilder b=new StringBuilder(); for(String s:logs)b.append(s).append('\n'); return b.toString(); } }
    public void clearDiagnostics(){ synchronized(logs){ logs.clear(); } log("DIAGNOSTICS cleared"); }

    private void closeProfile(){ if(adapter!=null&&hid!=null) adapter.closeProfileProxy(BluetoothProfile.HID_DEVICE,hid); hid=null; registered=false; target=null; }
    private void state(String s){ log("STATE -> "+s); Listener l=listener; if(l!=null) l.onState(s); }
    private void log(String m){ String line=new SimpleDateFormat("HH:mm:ss.SSS",Locale.US).format(new Date())+"  "+m; synchronized(logs){ while(logs.size()>=MAX_LOGS) logs.removeFirst(); logs.addLast(line); } Listener l=listener; if(l!=null) l.onLog(line); }
    private void replay(){ Listener l=listener; if(l==null)return; for(String s:new ArrayList<>(logs)) l.onLog(s); }
    private void fail(String where,Throwable t){ log("ERROR "+where+": "+t.getClass().getSimpleName()+": "+String.valueOf(t.getMessage())); state("ERROR"); }
    private String name(BluetoothDevice d){ if(d==null)return "(none)"; try{ String n=d.getName(); return n==null?"(unnamed)":n; }catch(SecurityException e){return "(device)";} }
    private String conn(int s){ if(s==BluetoothProfile.STATE_CONNECTED)return"CONNECTED"; if(s==BluetoothProfile.STATE_CONNECTING)return"CONNECTING"; if(s==BluetoothProfile.STATE_DISCONNECTING)return"DISCONNECTING"; return"DISCONNECTED"; }
}
