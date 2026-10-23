package com.blackcat.remote;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileNotFoundException;

/** Test APK only. Java avoids dependencies omitted from this standalone provider process. */
public final class UpdateExportFixtureProvider extends ContentProvider {
    public static final String URI = "content://com.blackcat.remote.test.update-export/checked.apk";

    private static void require(boolean value) {
        if (!value) throw new SecurityException("Restricted disposable export fixture");
    }
    private static boolean emulator() {
        return "ranchu".equals(Build.HARDWARE) || "goldfish".equals(Build.HARDWARE);
    }
    @Override public boolean onCreate() { require(emulator()); return true; }

    private File file(Uri uri) {
        Context c = getContext();
        require(c != null && emulator() && URI.equals(uri.toString()));
        int caller = Binder.getCallingUid();
        String[] packages = c.getPackageManager().getPackagesForUid(caller);
        boolean allowed = false;
        if (packages != null) {
            for (String name : packages) {
                if ("com.blackcat.remote".equals(name) || "com.blackcat.remote.test".equals(name)) allowed = true;
            }
        }
        require(allowed && c.getPackageManager().checkSignatures(caller, android.os.Process.myUid()) == PackageManager.SIGNATURE_MATCH);
        return new File(c.getCacheDir(), "explicit-fixture-export.apk");
    }

    @Override public Bundle call(String method, String arg, Bundle extras) {
        int caller = Binder.getCallingUid();
        require(emulator() && (caller == 0 || caller == 2000) && "grant-fixture-destination".equals(method));
        long token = Binder.clearCallingIdentity();
        try {
            Context c = getContext();
            require(c != null && c.getPackageManager().checkSignatures("com.blackcat.remote", c.getPackageName()) == PackageManager.SIGNATURE_MATCH);
            c.grantUriPermission("com.blackcat.remote", Uri.parse(URI), Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            Bundle result = new Bundle(); result.putBoolean("granted", true); return result;
        } finally { Binder.restoreCallingIdentity(token); }
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        int flags;
        if ("r".equals(mode)) flags = ParcelFileDescriptor.MODE_READ_ONLY;
        else if ("w".equals(mode) || "wt".equals(mode)) flags = ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE | ParcelFileDescriptor.MODE_WRITE_ONLY;
        else throw new FileNotFoundException("Unsupported fixture mode");
        return ParcelFileDescriptor.open(file(uri), flags);
    }
    @Override public int delete(Uri uri, String selection, String[] args) { return file(uri).delete() ? 1 : 0; }
    @Override public String getType(Uri uri) { file(uri); return "application/vnd.android.package-archive"; }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) { file(uri); return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
