package au.com.easyai.blackcatremote;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity implements HidService.Listener {
    private TextView status,logView; private Spinner devices; private EditText text;
    private final ArrayList<BluetoothDevice> bonded=new ArrayList<>(); private final ArrayDeque<String> uiLogs=new ArrayDeque<>();
    private boolean expanded=false; private LinearLayout root; private View controls;
    private static final int REQ_BT=7;

    @Override public void onCreate(Bundle b){ super.onCreate(b); buildUi(); onLog("APP Activity created API="+Build.VERSION.SDK_INT+" "+Build.MANUFACTURER+" "+Build.MODEL); ensurePermissionAndService(); }
    @Override protected void onResume(){ super.onResume(); HidService.setListener(this); refreshDevices(); onLog("ACTIVITY onResume"); }
    @Override protected void onPause(){ onLog("ACTIVITY onPause (HID service remains active)"); HidService.setListener(null); super.onPause(); }

    private void ensurePermissionAndService(){
        if(Build.VERSION.SDK_INT>=31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},REQ_BT); return;
        }
        startHidService();
    }
    private void startHidService(){ Intent i=new Intent(this,HidService.class); if(Build.VERSION.SDK_INT>=26) startForegroundService(i); else startService(i); HidService.setListener(this); }
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){ super.onRequestPermissionsResult(r,p,g); if(r==REQ_BT&&g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED) startHidService(); else { status.setText("Bluetooth permission denied"); onLog("ERROR BLUETOOTH_CONNECT denied"); } }

    private void buildUi(){
        root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(14),dp(12),dp(14),dp(12)); root.setBackgroundColor(Color.rgb(245,245,245));
        TextView title=tv("Black Cat Remote",24,true); root.addView(title);
        logView=tv("Tap for full diagnostics",11,false); logView.setBackgroundColor(Color.BLACK); logView.setTextColor(Color.GREEN); logView.setPadding(dp(8),dp(6),dp(8),dp(6)); root.addView(logView,new LinearLayout.LayoutParams(-1,dp(92))); logView.setOnClickListener(v->toggleDiagnostics());
        LinearLayout body=new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); controls=body;
        status=tv("Starting Bluetooth HID…",14,true); body.addView(status);
        devices=new Spinner(this); body.addView(devices,new LinearLayout.LayoutParams(-1,dp(50)));
        LinearLayout row=new LinearLayout(this); Button refresh=btn("Refresh"); Button connect=btn("Connect"); Button disconnect=btn("Disconnect"); row.addView(refresh,weight());row.addView(connect,weight());row.addView(disconnect,weight());body.addView(row);
        text=new EditText(this); text.setHint("Type text to send (US keyboard layout)"); text.setSingleLine(true); body.addView(text,new LinearLayout.LayoutParams(-1,dp(52)));
        Button send=btn("SEND TEXT"); body.addView(send,new LinearLayout.LayoutParams(-1,dp(48)));
        TextView label=tv("TOUCHPAD — drag to move • tap to click",14,true); body.addView(label);
        body.addView(new TouchpadView(),new LinearLayout.LayoutParams(-1,0,1f));
        LinearLayout clicks=new LinearLayout(this); Button left=btn("Left click"),right=btn("Right click"); clicks.addView(left,weight());clicks.addView(right,weight());body.addView(clicks);
        root.addView(body,new LinearLayout.LayoutParams(-1,0,1f)); setContentView(root);
        refresh.setOnClickListener(v->refreshDevices()); connect.setOnClickListener(v->connectSelected()); disconnect.setOnClickListener(v->{HidService s=HidService.get();if(s!=null)s.disconnect();});
        send.setOnClickListener(v->sendText()); left.setOnClickListener(v->click(1)); right.setOnClickListener(v->click(2));
    }

    private void toggleDiagnostics(){
        expanded=!expanded;
        if(expanded){ ((ViewGroup)controls.getParent()).removeView(controls); LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,0,1f); logView.setLayoutParams(p); logView.setTextSize(12); addDiagButtons(); }
        else recreate();
    }
    private void addDiagButtons(){ LinearLayout bar=new LinearLayout(this); bar.setTag("diagbar"); Button copy=btn("COPY"),clear=btn("CLEAR"),back=btn("BACK"); bar.addView(copy,weight());bar.addView(clear,weight());bar.addView(back,weight());root.addView(bar); copy.setOnClickListener(v->{HidService s=HidService.get();String d=s==null?joinedLogs():s.diagnostics();((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Black Cat Remote diagnostics",d));Toast.makeText(this,"Diagnostics copied",Toast.LENGTH_SHORT).show();}); clear.setOnClickListener(v->{uiLogs.clear();HidService s=HidService.get();if(s!=null)s.clearDiagnostics();renderLogs();}); back.setOnClickListener(v->recreate()); }
    private String joinedLogs(){StringBuilder b=new StringBuilder();for(String s:uiLogs)b.append(s).append('\n');return b.toString();}

    private void refreshDevices(){ HidService s=HidService.get(); if(s==null)return; bonded.clear();bonded.addAll(s.bonded());ArrayList<String> names=new ArrayList<>();for(BluetoothDevice d:bonded)names.add(safeName(d));devices.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));onLog("UI bonded host count="+bonded.size()); }
    private void connectSelected(){ HidService s=HidService.get();int i=devices.getSelectedItemPosition();if(s==null||i<0||i>=bonded.size()){status.setText("Choose a paired computer");return;}s.connect(bonded.get(i)); }
    private String safeName(BluetoothDevice d){try{String n=d.getName();return n==null?"(unnamed device)":n;}catch(SecurityException e){return"(device)";}}

    private void sendText(){ HidService s=HidService.get(); if(s==null||!s.ready()){status.setText("Not connected");return;} final String payload=text.getText().toString(); text.setText(""); onLog("TEXT send requested length="+payload.length()+" (content redacted)");
        new Thread(()->{for(int i=0;i<payload.length();i++){HidService h=HidService.get();if(h==null||!h.ready()){onLog("TEXT cancelled: host no longer ready");return;}Key k=keyFor(payload.charAt(i));if(k==null){onLog("TEXT cancelled: unsupported character at index="+i);h.releaseAll();return;}if(!h.keyboard(k.mod,k.code)){onLog("TEXT cancelled: HID report rejected");h.releaseAll();return;}try{Thread.sleep(20);}catch(InterruptedException e){Thread.currentThread().interrupt();h.releaseAll();return;}}onLog("TEXT send sequence complete length="+payload.length());}).start();
    }
    private void move(int dx,int dy){HidService s=HidService.get();if(s!=null&&s.ready()){while(dx!=0||dy!=0){int x=Math.max(-127,Math.min(127,dx)),y=Math.max(-127,Math.min(127,dy));s.mouse(0,x,y,0);dx-=x;dy-=y;}}}
    private void click(int b){HidService s=HidService.get();if(s!=null&&s.ready()){s.mouse(b,0,0,0);s.mouse(0,0,0,0);}}

    @Override public void onLog(String line){runOnUiThread(()->{while(uiLogs.size()>=300)uiLogs.removeFirst();uiLogs.addLast(line);renderLogs();});}
    @Override public void onState(String s){runOnUiThread(()->status.setText("HID: "+s));}
    private void renderLogs(){StringBuilder b=new StringBuilder();for(String s:uiLogs)b.append(s).append('\n');logView.setText(b.toString());logView.post(()->{int y=logView.getLayout()==null?0:logView.getLayout().getLineTop(logView.getLineCount())-logView.getHeight();if(y>0)logView.scrollTo(0,y);});}

    static class Key{final byte mod,code;Key(int m,int c){mod=(byte)m;code=(byte)c;}}
    private Key keyFor(char c){if(c>='a'&&c<='z')return new Key(0,4+c-'a');if(c>='A'&&c<='Z')return new Key(2,4+c-'A');if(c>='1'&&c<='9')return new Key(0,30+c-'1');if(c=='0')return new Key(0,39);switch(c){case' ':return new Key(0,44);case'.':return new Key(0,55);case',':return new Key(0,54);case'-':return new Key(0,45);case'_':return new Key(2,45);case'=':return new Key(0,46);case'+':return new Key(2,46);case'/':return new Key(0,56);case'?':return new Key(2,56);case':':return new Key(2,51);case';':return new Key(0,51);case'!':return new Key(2,30);case'@':return new Key(2,31);case'#':return new Key(2,32);case'$':return new Key(2,33);case'%':return new Key(2,34);case'^':return new Key(2,35);case'&':return new Key(2,36);case'*':return new Key(2,37);case'(':return new Key(2,38);case')':return new Key(2,39);default:return null;}}

    private class TouchpadView extends View{float lx,ly,dx0,dy0;long down;TouchpadView(){super(MainActivity.this);GradientDrawable g=new GradientDrawable();g.setColor(Color.WHITE);g.setStroke(dp(2),Color.DKGRAY);g.setCornerRadius(dp(16));setBackground(g);}@Override public boolean onTouchEvent(MotionEvent e){switch(e.getActionMasked()){case MotionEvent.ACTION_DOWN:lx=dx0=e.getX();ly=dy0=e.getY();down=System.currentTimeMillis();return true;case MotionEvent.ACTION_MOVE:int dx=Math.round((e.getX()-lx)*1.6f),dy=Math.round((e.getY()-ly)*1.6f);lx=e.getX();ly=e.getY();move(dx,dy);return true;case MotionEvent.ACTION_UP:if(System.currentTimeMillis()-down<250&&Math.hypot(e.getX()-dx0,e.getY()-dy0)<dp(12))click(1);return true;case MotionEvent.ACTION_CANCEL:HidService s=HidService.get();if(s!=null)s.releaseAll();onLog("TOUCH cancelled -> RELEASE_ALL_INPUT");return true;}return true;}}

    private TextView tv(String s,int sp,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(Color.BLACK);if(bold)t.setTypeface(null,1);return t;} private Button btn(String s){Button b=new Button(this);b.setText(s);return b;} private LinearLayout.LayoutParams weight(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(50),1);p.setMargins(dp(2),dp(2),dp(2),dp(2));return p;} private int dp(int n){return(int)(n*getResources().getDisplayMetrics().density+.5f);}
}
