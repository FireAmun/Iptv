package com.fireamun.iptv.prototype;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.os.CancellationSignal;
import android.os.SystemClock;
import android.content.res.AssetFileDescriptor;
import android.os.Bundle;
import java.io.File;
import java.io.FileNotFoundException;

/** Test APK only. Uses platform classes because it runs separately from the target APK. */
public final class FixtureProvider extends ContentProvider {
    private static volatile boolean slowOpening;
    @Override public boolean onCreate() { return true; }
    @Override public AssetFileDescriptor openTypedAssetFile(Uri uri, String mime, Bundle options, CancellationSignal signal) throws FileNotFoundException {
        return openAssetFile(uri, "r", signal);
    }
    @Override public AssetFileDescriptor openAssetFile(Uri uri, String mode, CancellationSignal signal) throws FileNotFoundException {
        if ("/slow".equals(uri.getPath())) {
            slowOpening = true;
            try {
                long deadline = SystemClock.elapsedRealtime() + 5000;
                while (SystemClock.elapsedRealtime() < deadline) {
                    if (signal != null) signal.throwIfCanceled();
                    SystemClock.sleep(10);
                }
                throw new FileNotFoundException("Synthetic provider timeout");
            } finally {
                slowOpening = false;
            }
        }
        return super.openAssetFile(uri, mode, signal);
    }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"/playlist".equals(uri.getPath())) throw new FileNotFoundException("Unknown fixture");
        int flags;
        switch (mode) {
            case "r": flags = ParcelFileDescriptor.MODE_READ_ONLY; break;
            case "w": case "wt":
                flags = ParcelFileDescriptor.MODE_WRITE_ONLY | ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE;
                break;
            default: throw new FileNotFoundException("Unsupported fixture operation");
        }
        return ParcelFileDescriptor.open(new File(getContext().getCacheDir(), "playlist.m3u"), flags);
    }
    @Override public String getType(Uri uri) { return "audio/x-mpegurl"; }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) {
        if (!"/slow-state".equals(uri.getPath())) return null;
        MatrixCursor result = new MatrixCursor(new String[] {"opening"});
        result.addRow(new Object[] {slowOpening ? 1 : 0});
        return result;
    }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { return 0; }
    @Override public int delete(Uri uri, String selection, String[] args) { return 0; }
}
