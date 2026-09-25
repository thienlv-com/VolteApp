package vn.volte.enabler;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.provider.Settings;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import io.github.muntashirakon.adb.AdbPairingRequiredException;
import io.github.muntashirakon.adb.AdbStream;

/** Chạy VolteCore dưới quyền shell thông qua kết nối Gỡ lỗi không dây tới chính điện thoại. */
public final class VolteAdb {

    public static final String PREFS = "settings";
    public static final String KEY_PAIRED = "paired";
    public static final String KEY_AUTO = "auto";
    public static final String KEY_LAST = "last";

    private static final String ADB_WIFI = "adb_wifi_enabled";

    private VolteAdb() {
    }

    public static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean isPaired(Context c) {
        return prefs(c).getBoolean(KEY_PAIRED, false);
    }

    public static boolean canToggleWirelessDebugging(Context c) {
        return c.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean isWifiConnected(Context c) {
        ConnectivityManager cm = c.getSystemService(ConnectivityManager.class);
        for (Network n : cm.getAllNetworks()) {
            NetworkCapabilities nc = cm.getNetworkCapabilities(n);
            if (nc != null && nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return true;
        }
        return false;
    }

    public static boolean pair(Context c, int port, String code) throws Exception {
        boolean ok = AdbManager.get(c).pair("127.0.0.1", port, code);
        if (ok) prefs(c).edit().putBoolean(KEY_PAIRED, true).apply();
        return ok;
    }

    /**
     * Kết nối ADB (tự bật Gỡ lỗi không dây nếu app đã có WRITE_SECURE_SETTINGS rồi tắt lại sau khi xong),
     * áp các thiết lập một lần, rồi chạy VolteCore với tham số {@code coreArg}.
     */
    public static synchronized String runCore(Context c, String coreArg) throws Exception {
        ContentResolver cr = c.getContentResolver();
        boolean wasOn = Settings.Global.getInt(cr, ADB_WIFI, 0) == 1;
        boolean toggled = false;
        AdbManager adb = AdbManager.get(c);
        try {
            if (!wasOn) {
                if (!canToggleWirelessDebugging(c)) {
                    throw new IllegalStateException("Hãy bật Gỡ lỗi không dây trong Tuỳ chọn nhà phát triển.");
                }
                Settings.Global.putInt(cr, ADB_WIFI, 1);
                toggled = true;
            }
            connect(c, adb);

            String pkg = c.getPackageName();
            String apk = c.getApplicationInfo().sourceDir;
            String cmd = "pm grant " + pkg + " android.permission.WRITE_SECURE_SETTINGS >/dev/null 2>&1; "
                    // Không để Android thu hồi khoá ghép đôi sau 7 ngày không dùng
                    + "settings put global adb_allowed_connection_time 0; "
                    // Cho phép chạy lúc khởi động dù đang tối ưu pin
                    + "dumpsys deviceidle whitelist +" + pkg + " >/dev/null 2>&1; "
                    + "CLASSPATH='" + apk + "' app_process /system/bin " + VolteCore.class.getName() + " " + coreArg;
            return shell(adb, cmd);
        } finally {
            try {
                adb.disconnect();
            } catch (Exception ignored) {
            }
            if (toggled) Settings.Global.putInt(cr, ADB_WIFI, 0);
        }
    }

    private static void connect(Context c, AdbManager adb) throws Exception {
        if (adb.isConnected()) return;
        Exception last = null;
        // adbd cần vài giây để khởi động và quảng bá cổng qua mDNS sau khi bật
        for (int i = 0; i < 8; i++) {
            try {
                if (adb.connectTls(c, 8000)) return;
            } catch (AdbPairingRequiredException e) {
                prefs(c).edit().putBoolean(KEY_PAIRED, false).apply();
                throw new IllegalStateException("Khoá ghép đôi không còn hợp lệ, hãy ghép nối lại.");
            } catch (Exception e) {
                last = e;
            }
            Thread.sleep(2000);
        }
        throw new IllegalStateException("Không kết nối được Gỡ lỗi không dây. Kiểm tra Wi-Fi và chọn "
                + "\"Luôn cho phép trên mạng này\" nếu được hỏi." + (last != null ? " (" + last.getMessage() + ")" : ""));
    }

    private static String shell(AdbManager adb, String cmd) throws Exception {
        try (AdbStream stream = adb.openStream("shell:" + cmd);
             InputStream in = stream.openInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            try {
                while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            } catch (IOException e) {
                // libadb ném "Stream closed." khi adbd đóng luồng sau khi lệnh kết thúc: coi là hết dữ liệu
                if (!"Stream closed.".equals(e.getMessage())) throw e;
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }

    /** Chuyển kết quả của VolteCore thành văn bản dễ đọc. */
    public static String summarize(String raw, boolean enable) {
        if (raw == null) return "";
        String text = raw.replace("\r", "").trim();
        if (text.contains(VolteCore.NO_SIM)) return "Không tìm thấy SIM nào đang hoạt động.";
        int mark = text.indexOf(VolteCore.STATUS_MARK);
        if (mark < 0) return text;

        String head = text.substring(0, mark).trim();
        StringBuilder sb = new StringBuilder();
        if (head.contains("Lỗi")) sb.append(head).append("\n\n");
        for (String line : text.substring(mark + VolteCore.STATUS_MARK.length()).trim().split("\n")) {
            String[] f = line.split("\\|", -1);
            if (f.length < 6) continue;
            boolean cfg = "true".equals(f[3]), reg = "true".equals(f[4]), avail = "true".equals(f[5]);
            String state;
            if (avail || (cfg && reg)) state = "✅ VoLTE đang hoạt động";
            else if (cfg) state = "⏳ Đã bật, đang chờ mạng đăng ký IMS";
            else state = "❌ Chưa bật VoLTE";
            if (sb.length() > 0) sb.append('\n');
            sb.append("SIM ").append(Integer.parseInt(f[0]) + 1).append(" · ").append(f[2]).append(": ").append(state);
        }
        return sb.length() > 0 ? sb.toString() : (enable ? head : text);
    }
}
