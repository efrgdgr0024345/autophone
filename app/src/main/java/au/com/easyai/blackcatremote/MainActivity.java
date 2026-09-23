package au.com.easyai.blackcatremote;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.*;
import android.content.pm.PackageManager;
import android.content.*;
import android.text.method.ScrollingMovementMethod;
import java.io.*;
import java.text.SimpleDateFormat;
import android.os.*;
import android.view.*;
import android.widget.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private BluetoothAdapter adapter;
    private BluetoothHidDevice hid;
    private BluetoothDevice target;
    private TextView status;
    private Spinner devices;
    private EditText text;
    private TextView logView;
    private final StringBuilder logBuffer = new StringBuilder();
    private static final String LOG_FILE = "blackcat_remote.log";
    private final ArrayList<BluetoothDevice> bonded = new ArrayList<>();

    private static final int REPORT_KEYBOARD = 1;
    private static final int REPORT_MOUSE = 2;

    private static final byte[] DESCRIPTOR = new byte[] {
        0x05,0x01, 0x09,0x06, (byte)0xA1,0x01, (byte)0x85,0x01,
        0x05,0x07, 0x19,(byte)0xE0, 0x29,(byte)0xE7, 0x15,0x00, 0x25,0x01,
        0x75,0x01, (byte)0x95,0x08, (byte)0x81,0x02,
        (byte)0x95,0x01, 0x75,0x08, (byte)0x81,0x01,
        (byte)0x95,0x06, 0x75,0x08, 0x15,0x00, 0x25,0x65, 0x05,0x07,
        0x19,0x00, 0x29,0x65, (byte)0x81,0x00, (byte)0xC0,

        0x05,0x01, 0x09,0x02, (byte)0xA1,0x01, (byte)0x85,0x02,
        0x09,0x01, (byte)0xA1,0x00,
        0x05,0x09, 0x19,0x01, 0x29,0x03, 0x15,0x00, 0x25,0x01,
        (byte)0x95,0x03, 0x75,0x01, (byte)0x81,0x02,
        (byte)0x95,0x01, 0x75,0x05, (byte)0x81,0x01,
        0x05,0x01, 0x09,0x30, 0x09,0x31, 0x09,0x38,
        0x15,(byte)0x81, 0x25,0x7F, 0x75,0x08, (byte)0x95,0x03, (byte)0x81,0x06,
        (byte)0xC0, (byte)0xC0
    };

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        buildUi();
        loadOldLog();
        log("APP START Android " + Build.VERSION.RELEASE + " API " + Build.VERSION.SDK_INT + " / " + Build.MANUFACTURER + " " + Build.MODEL);
        requestBtAndInit();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(18), dp(16), dp(16));
        root.setBackgroundColor(Color.rgb(245,245,245));

        TextView title = tv("Black Cat Remote", 26, true);
        root.addView(title);

        status = tv("Bluetooth: starting…", 14, false);
        root.addView(status);

        devices = new Spinner(this);
        root.addView(devices, new LinearLayout.LayoutParams(-1, dp(52)));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button refresh = btn("Refresh");
        Button connect = btn("Connect");
        Button disconnect = btn("Disconnect");
        row.addView(refresh, weight());
        row.addView(connect, weight());
        row.addView(disconnect, weight());
        root.addView(row);

        text = new EditText(this);
        text.setHint("Type text to send");
        text.setSingleLine(true);
        text.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(text, new LinearLayout.LayoutParams(-1, dp(52)));

        Button send = btn("SEND TEXT");
        root.addView(send, new LinearLayout.LayoutParams(-1, dp(48)));

        TextView label = tv("TOUCHPAD — drag to move • tap to click", 15, true);
        label.setPadding(0, dp(8), 0, dp(6));
        root.addView(label);

        TouchpadView pad = new TouchpadView();
        root.addView(pad, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout clicks = new LinearLayout(this);
        clicks.setOrientation(LinearLayout.HORIZONTAL);
        Button left = btn("Left click");
        Button right = btn("Right click");
        clicks.addView(left, weight());
        clicks.addView(right, weight());
        root.addView(clicks);

        TextView diagTitle = tv("DIAGNOSTICS", 14, true);
        diagTitle.setPadding(0, dp(8), 0, dp(4));
        root.addView(diagTitle);

        logView = tv("", 11, false);
        logView.setBackgroundColor(Color.WHITE);
        logView.setPadding(dp(8),dp(8),dp(8),dp(8));
        logView.setMovementMethod(new ScrollingMovementMethod());
        root.addView(logView, new LinearLayout.LayoutParams(-1, dp(150)));

        LinearLayout logButtons = new LinearLayout(this);
        logButtons.setOrientation(LinearLayout.HORIZONTAL);
        Button copyLog = btn("COPY LOG");
        Button shareLog = btn("SHARE LOG");
        Button clearLog = btn("CLEAR");
        logButtons.addView(copyLog, weight());
        logButtons.addView(shareLog, weight());
        logButtons.addView(clearLog, weight());
        root.addView(logButtons);

        setContentView(root);

        refresh.setOnClickListener(v -> populateDevices());
        connect.setOnClickListener(v -> connectSelected());
        disconnect.setOnClickListener(v -> {
            if (hid != null && target != null) {
                try { hid.disconnect(target); } catch (SecurityException ignored) {}
            }
        });
        send.setOnClickListener(v -> sendText(text.getText().toString()));
        left.setOnClickListener(v -> mouseClick(1));
        right.setOnClickListener(v -> mouseClick(2));
        copyLog.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("Black Cat Remote diagnostics", logBuffer.toString()));
            Toast.makeText(this, "Log copied", Toast.LENGTH_SHORT).show();
        });
        shareLog.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_SUBJECT, "Black Cat Remote diagnostics");
            i.putExtra(Intent.EXTRA_TEXT, logBuffer.toString());
            startActivity(Intent.createChooser(i, "Share diagnostics"));
        });
        clearLog.setOnClickListener(v -> {
            logBuffer.setLength(0);
            if (logView != null) logView.setText("");
            deleteFile(LOG_FILE);
            log("LOG CLEARED");
        });
    }

    private LinearLayout.LayoutParams weight() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(52), 1f);
        p.setMargins(dp(2),dp(4),dp(2),dp(4));
        return p;
    }

    private TextView tv(String s, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(Color.BLACK);
        if (bold) t.setTypeface(null, 1);
        return t;
    }

    private Button btn(String s) {
        Button b = new Button(this);
        b.setText(s);
        return b;
    }

    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }

    private synchronized void log(String m) {
        String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
        String line = ts + "  " + m + "\n";
        logBuffer.append(line);
        try (FileOutputStream fos = openFileOutput(LOG_FILE, MODE_APPEND)) {
            fos.write(line.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception ignored) {}
        runOnUiThread(() -> {
            if (logView != null) {
                logView.setText(logBuffer.toString());
                final int scroll = logView.getLayout() == null ? 0 :
                    logView.getLayout().getLineTop(logView.getLineCount()) - logView.getHeight();
                if (scroll > 0) logView.scrollTo(0, scroll);
            }
        });
    }

    private void loadOldLog() {
        try (FileInputStream fis = openFileInput(LOG_FILE)) {
            byte[] b = new byte[(int)new File(getFilesDir(), LOG_FILE).length()];
            int n = fis.read(b);
            if (n > 0) logBuffer.append(new String(b,0,n,java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception ignored) {}
        if (logView != null) logView.setText(logBuffer.toString());
    }

    private String bondStateName(int s) {
        if (s == BluetoothDevice.BOND_BONDED) return "BONDED";
        if (s == BluetoothDevice.BOND_BONDING) return "BONDING";
        return "NONE";
    }

    private String connStateName(int s) {
        if (s == BluetoothProfile.STATE_CONNECTED) return "CONNECTED";
        if (s == BluetoothProfile.STATE_CONNECTING) return "CONNECTING";
        if (s == BluetoothProfile.STATE_DISCONNECTING) return "DISCONNECTING";
        return "DISCONNECTED";
    }

    private boolean hasBtPermission() {
        return Build.VERSION.SDK_INT < 31 ||
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestBtAndInit() {
        log("Permission BLUETOOTH_CONNECT=" + hasBtPermission());
        if (Build.VERSION.SDK_INT >= 31 && !hasBtPermission()) {
            log("Requesting Bluetooth runtime permissions");
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN}, 7);
        } else initBt();
    }

    @Override public void onRequestPermissionsResult(int r, String[] p, int[] g) {
        super.onRequestPermissionsResult(r,p,g);
        log("Permission result request=" + r + " granted=" + hasBtPermission());
        if (r == 7 && hasBtPermission()) initBt();
        else { status.setText("Bluetooth permission denied"); log("ERROR Bluetooth permission denied"); }
    }

    private void initBt() {
        log("initBt()");
        BluetoothManager bm = getSystemService(BluetoothManager.class);
        adapter = bm == null ? null : bm.getAdapter();
        if (adapter == null) { status.setText("No Bluetooth adapter"); log("ERROR adapter=null"); return; }
        log("Adapter present enabled=" + adapter.isEnabled());
        if (!adapter.isEnabled()) { status.setText("Turn Bluetooth on, then tap Refresh"); log("WARN Bluetooth disabled"); }
        populateDevices();

        adapter.getProfileProxy(this, new BluetoothProfile.ServiceListener() {
            @Override public void onServiceConnected(int profile, BluetoothProfile proxy) {
                log("Profile proxy connected profile=" + profile + " expected=" + BluetoothProfile.HID_DEVICE);
                if (profile != BluetoothProfile.HID_DEVICE) return;
                hid = (BluetoothHidDevice) proxy;

                BluetoothHidDeviceAppSdpSettings sdp =
                    new BluetoothHidDeviceAppSdpSettings(
                        "Black Cat Remote",
                        "Bluetooth keyboard and mouse",
                        "Black Cat",
                        BluetoothHidDevice.SUBCLASS1_COMBO,
                        DESCRIPTOR
                    );

                boolean registerStarted = hid.registerApp(sdp, null, null, Executors.newSingleThreadExecutor(),
                    new BluetoothHidDevice.Callback() {
                        @Override public void onAppStatusChanged(BluetoothDevice d, boolean registered) {
                            log("HID onAppStatusChanged registered=" + registered + " device=" + safeName(d));
                            runOnUiThread(() -> status.setText(registered ? "Ready — choose paired computer" : "HID registration failed"));
                        }
                        @Override public void onConnectionStateChanged(BluetoothDevice d, int state) {
                            log("HID connection state=" + connStateName(state) + " device=" + safeName(d));
                            if (state == BluetoothProfile.STATE_CONNECTED) target = d;
                            runOnUiThread(() -> status.setText(state == BluetoothProfile.STATE_CONNECTED ?
                                    "Connected: " + safeName(d) :
                                    state == BluetoothProfile.STATE_CONNECTING ? "Connecting…" : "Disconnected"));
                        }
                    });
                log("registerApp() returned=" + registerStarted);
            }
            @Override public void onServiceDisconnected(int profile) {
                log("Profile proxy disconnected profile=" + profile);
                hid = null;
                status.setText("Bluetooth HID unavailable");
            }
        }, BluetoothProfile.HID_DEVICE);
        log("getProfileProxy(HID_DEVICE) requested");
    }

    private String safeName(BluetoothDevice d) {
        try {
            String n = d == null ? null : d.getName();
            return n == null ? "(unnamed device)" : n;
        } catch (SecurityException e) { return "(device)"; }
    }

    private void populateDevices() {
        if (adapter == null || !hasBtPermission()) return;
        bonded.clear();
        ArrayList<String> names = new ArrayList<>();
        try {
            for (BluetoothDevice d : adapter.getBondedDevices()) {
                bonded.add(d);
                names.add(safeName(d) + "  •  " + d.getAddress());
                log("Bonded device: " + safeName(d) + " bond=" + bondStateName(d.getBondState()));
            }
        } catch (SecurityException e) {
            log("ERROR populateDevices SecurityException: " + e);
            status.setText("Bluetooth permission required");
        }
        devices.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names));
        log("Bonded device count=" + names.size());
        if (names.isEmpty()) status.setText("Pair the computer in Android Bluetooth settings first");
    }

    private void connectSelected() {
        if (hid == null) { status.setText("HID service not ready"); return; }
        int i = devices.getSelectedItemPosition();
        if (i < 0 || i >= bonded.size()) { status.setText("Choose a paired computer"); return; }
        target = bonded.get(i);
        try {
            status.setText("Connecting…");
            log("connect() target=" + safeName(target) + " bond=" + bondStateName(target.getBondState()));
            boolean ok = hid.connect(target);
            log("connect() returned=" + ok);
        } catch (SecurityException e) {
            log("ERROR connect SecurityException: " + e);
            status.setText("Bluetooth permission required");
        } catch (Throwable t) {
            log("ERROR connect Throwable: " + t);
            status.setText("Connect failed — see log");
        }
    }

    private void sendText(String s) {
        if (!ready()) return;
        log("sendText length=" + s.length());
        new Thread(() -> {
            for (char c : s.toCharArray()) {
                Key k = keyFor(c);
                if (k == null) { log("WARN unsupported char U+" + Integer.toHexString(c)); continue; }
                sendKey(k.mod, k.code);
                try { Thread.sleep(18); } catch (InterruptedException ignored) {}
            }
        }).start();
    }

    private boolean ready() {
        if (hid == null || target == null) {
            runOnUiThread(() -> status.setText("Not connected"));
            return false;
        }
        return true;
    }

    private void sendKey(byte mod, byte code) {
        try {
            boolean down = hid.sendReport(target, REPORT_KEYBOARD, new byte[]{mod,0,code,0,0,0,0,0});
            boolean up = hid.sendReport(target, REPORT_KEYBOARD, new byte[]{0,0,0,0,0,0,0,0});
            if (!down || !up) log("ERROR keyboard sendReport down=" + down + " up=" + up + " code=" + (code & 0xff));
        } catch (SecurityException e) { log("ERROR keyboard SecurityException: " + e); }
          catch (Throwable t) { log("ERROR keyboard Throwable: " + t); }
    }

    private void mouseMove(int dx, int dy) {
        if (!ready()) return;
        dx = Math.max(-127, Math.min(127, dx));
        dy = Math.max(-127, Math.min(127, dy));
        try { hid.sendReport(target, REPORT_MOUSE, new byte[]{0,(byte)dx,(byte)dy,0}); }
        catch (SecurityException ignored) {}
    }

    private void mouseClick(int button) {
        if (!ready()) return;
        try {
            hid.sendReport(target, REPORT_MOUSE, new byte[]{(byte)button,0,0,0});
            hid.sendReport(target, REPORT_MOUSE, new byte[]{0,0,0,0});
        } catch (SecurityException ignored) {}
    }

    static class Key {
        final byte mod, code;
        Key(int m, int c) { mod=(byte)m; code=(byte)c; }
    }

    private Key keyFor(char c) {
        if (c >= 'a' && c <= 'z') return new Key(0, 4 + c-'a');
        if (c >= 'A' && c <= 'Z') return new Key(2, 4 + c-'A');
        if (c >= '1' && c <= '9') return new Key(0, 30 + c-'1');
        if (c == '0') return new Key(0,39);
        switch(c) {
            case ' ': return new Key(0,44);
            case '\n': return new Key(0,40);
            case '\t': return new Key(0,43);
            case '.': return new Key(0,55);
            case ',': return new Key(0,54);
            case '-': return new Key(0,45);
            case '_': return new Key(2,45);
            case '=': return new Key(0,46);
            case '+': return new Key(2,46);
            case '/': return new Key(0,56);
            case '?': return new Key(2,56);
            case ':': return new Key(2,51);
            case ';': return new Key(0,51);
            case '!': return new Key(2,30);
            case '@': return new Key(2,31);
            case '#': return new Key(2,32);
            case '$': return new Key(2,33);
            case '%': return new Key(2,34);
            case '^': return new Key(2,35);
            case '&': return new Key(2,36);
            case '*': return new Key(2,37);
            case '(': return new Key(2,38);
            case ')': return new Key(2,39);
            default: return null;
        }
    }

    private class TouchpadView extends View {
        float lastX,lastY,downX,downY;
        long downAt;
        TouchpadView() {
            super(MainActivity.this);
            GradientDrawable g = new GradientDrawable();
            g.setColor(Color.WHITE);
            g.setStroke(dp(2), Color.DKGRAY);
            g.setCornerRadius(dp(16));
            setBackground(g);
        }
        @Override public boolean onTouchEvent(android.view.MotionEvent e) {
            switch(e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    lastX=downX=e.getX(); lastY=downY=e.getY(); downAt=System.currentTimeMillis(); return true;
                case MotionEvent.ACTION_MOVE:
                    float dx=e.getX()-lastX, dy=e.getY()-lastY;
                    lastX=e.getX(); lastY=e.getY();
                    float accel=1.6f;
                    mouseMove(Math.round(dx*accel), Math.round(dy*accel));
                    return true;
                case MotionEvent.ACTION_UP:
                    float dist=(float)Math.hypot(e.getX()-downX,e.getY()-downY);
                    if (System.currentTimeMillis()-downAt < 250 && dist < dp(12)) mouseClick(1);
                    return true;
            }
            return true;
        }
    }
}
