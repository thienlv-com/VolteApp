package vn.volte.enabler;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;

import java.text.DateFormat;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.muntashirakon.adb.android.AdbMdns;

/**
 * Dịch vụ tiền cảnh làm mọi việc cần ADB: ghép đôi (nhập mã qua thông báo), bật/khôi phục VoLTE,
 * và tự bật VoLTE sau khi khởi động máy.
 */
public class WorkService extends Service {

    public static final String ACTION_PAIR_START = "vn.volte.enabler.PAIR_START";
    public static final String ACTION_PAIR_CODE = "vn.volte.enabler.PAIR_CODE";
    public static final String ACTION_APPLY = "vn.volte.enabler.APPLY";
    public static final String EXTRA_ENABLE = "enable";
    public static final String EXTRA_BOOT = "boot";
    private static final String EXTRA_PORT = "port";
    private static final String KEY_CODE = "code";

    private static final String CH_WORK = "work";
    private static final String CH_PAIR = "pair";
    private static final String CH_RESULT = "result";
    private static final int NOTIF_WORK = 1;
    private static final int NOTIF_RESULT = 2;

    private static final long PAIR_TIMEOUT_MS = 5 * 60_000;
    private static final long BOOT_WIFI_WAIT_MS = 10 * 60_000;
    private static final long BOOT_SETTLE_MS = 20_000;

    public interface Listener {
        void onUpdate(String message);
    }

    static volatile Listener listener;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private NotificationManager nm;
    private AdbMdns pairingMdns;
    private int pendingJobs;

    private final Runnable pairTimeout = () -> {
        stopPairingDiscovery();
        postResult("Hết thời gian ghép nối", "Mở app và bấm \"Ghép nối\" để thử lại.");
        maybeStop();
    };

    public static void start(Context c, Intent intent) {
        c.startForegroundService(intent.setClass(c, WorkService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CH_WORK, "Đang xử lý", NotificationManager.IMPORTANCE_LOW));
        nm.createNotificationChannel(new NotificationChannel(CH_PAIR, "Ghép nối", NotificationManager.IMPORTANCE_HIGH));
        nm.createNotificationChannel(new NotificationChannel(CH_RESULT, "Kết quả", NotificationManager.IMPORTANCE_DEFAULT));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification n = progress("VoLTE VN đang chạy…");
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_WORK, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIF_WORK, n);
        }

        String action = intent != null ? intent.getAction() : null;
        if (ACTION_PAIR_START.equals(action)) {
            startPairingDiscovery();
        } else if (ACTION_PAIR_CODE.equals(action)) {
            Bundle input = RemoteInput.getResultsFromIntent(intent);
            CharSequence code = input != null ? input.getCharSequence(KEY_CODE) : null;
            int port = intent.getIntExtra(EXTRA_PORT, -1);
            if (code != null && port > 0) {
                stopPairingDiscovery();
                submit(() -> doPair(port, code.toString().trim()));
            }
        } else if (ACTION_APPLY.equals(action)) {
            boolean enable = intent.getBooleanExtra(EXTRA_ENABLE, true);
            boolean boot = intent.getBooleanExtra(EXTRA_BOOT, false);
            submit(() -> doApply(enable, boot));
        } else {
            maybeStop();
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        stopPairingDiscovery();
        worker.shutdownNow();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ---- Ghép đôi ----

    private void startPairingDiscovery() {
        if (pairingMdns != null) return;
        nm.notify(NOTIF_WORK, progress("Đang chờ ghép nối: vào Gỡ lỗi không dây → "
                + "\"Ghép nối thiết bị bằng mã ghép nối\""));
        pairingMdns = new AdbMdns(this, AdbMdns.SERVICE_TYPE_TLS_PAIRING, (host, port) -> main.post(() -> {
            if (port > 0 && pairingMdns != null) showCodeInput(port);
        }));
        pairingMdns.start();
        main.postDelayed(pairTimeout, PAIR_TIMEOUT_MS);
    }

    private void stopPairingDiscovery() {
        main.removeCallbacks(pairTimeout);
        if (pairingMdns != null) {
            pairingMdns.stop();
            pairingMdns = null;
        }
    }

    private void showCodeInput(int port) {
        RemoteInput remote = new RemoteInput.Builder(KEY_CODE).setLabel("Mã ghép nối Wi-Fi (6 số)").build();
        Intent i = new Intent(this, WorkService.class).setAction(ACTION_PAIR_CODE).putExtra(EXTRA_PORT, port);
        PendingIntent pi = PendingIntent.getForegroundService(this, 1, i,
                PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Action action = new Notification.Action.Builder(
                Icon.createWithResource(this, R.drawable.ic_stat), "Nhập mã", pi)
                .addRemoteInput(remote).build();
        Notification n = new Notification.Builder(this, CH_PAIR)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("Đã thấy yêu cầu ghép nối")
                .setContentText("Bấm \"Nhập mã\" và gõ mã 6 số đang hiện trên màn hình.")
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_CALL)
                .addAction(action)
                .build();
        nm.notify(NOTIF_WORK, n);
    }

    private void doPair(int port, String code) {
        progressUpdate("Đang ghép nối…");
        try {
            if (!VolteAdb.pair(this, port, code)) {
                postResult("Ghép nối thất bại", "Mã không đúng. Mở app và bấm \"Ghép nối\" để thử lại.");
                return;
            }
        } catch (Exception e) {
            postResult("Ghép nối thất bại", String.valueOf(e.getMessage()));
            return;
        }
        progressUpdate("Đã ghép nối. Đang bật VoLTE…");
        runAndReport(true);
    }

    // ---- Bật / khôi phục VoLTE ----

    private void doApply(boolean enable, boolean boot) {
        if (!VolteAdb.isPaired(this)) {
            postResult("Chưa ghép nối", "Mở app VoLTE VN và bấm \"Ghép nối\" (chỉ làm 1 lần).");
            return;
        }
        if (boot) SystemClock.sleep(BOOT_SETTLE_MS);

        long deadline = SystemClock.elapsedRealtime() + (boot ? BOOT_WIFI_WAIT_MS : 0);
        while (!VolteAdb.isWifiConnected(this)) {
            if (SystemClock.elapsedRealtime() >= deadline) {
                postResult("Cần Wi-Fi để bật VoLTE", "Kết nối Wi-Fi rồi mở app VoLTE VN.");
                return;
            }
            progressUpdate("Đang chờ kết nối Wi-Fi…");
            SystemClock.sleep(5000);
        }
        progressUpdate(enable ? "Đang bật VoLTE…" : "Đang khôi phục…");
        runAndReport(enable, boot ? 6 : 1);
    }

    private void runAndReport(boolean enable) {
        runAndReport(enable, 1);
    }

    private void runAndReport(boolean enable, int attempts) {
        try {
            String raw = null;
            for (int i = 0; i < attempts; i++) {
                raw = VolteAdb.runCore(this, enable ? "enable-status" : "clear");
                // Ngay sau khởi động SIM có thể chưa sẵn sàng
                if (!raw.contains(VolteCore.NO_SIM)) break;
                SystemClock.sleep(10_000);
            }
            String summary = VolteAdb.summarize(raw, enable);
            postResult(enable ? "VoLTE VN" : "Đã khôi phục mặc định", summary);
        } catch (Exception e) {
            postResult("Không bật được VoLTE", String.valueOf(e.getMessage()));
        }
    }

    // ---- Tiện ích ----

    private void submit(Runnable r) {
        pendingJobs++;
        worker.execute(() -> {
            try {
                r.run();
            } finally {
                main.post(() -> {
                    pendingJobs--;
                    maybeStop();
                });
            }
        });
    }

    private void maybeStop() {
        if (pendingJobs == 0 && pairingMdns == null) {
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        }
    }

    private Notification progress(String text) {
        return new Notification.Builder(this, CH_WORK)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("VoLTE VN")
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setOngoing(true)
                .build();
    }

    private void progressUpdate(String text) {
        nm.notify(NOTIF_WORK, progress(text));
        Listener l = listener;
        if (l != null) main.post(() -> l.onUpdate(text));
    }

    private void postResult(String title, String text) {
        String stamp = DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date());
        VolteAdb.prefs(this).edit().putString(VolteAdb.KEY_LAST, title + " (" + stamp + ")\n" + text).apply();

        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        nm.notify(NOTIF_RESULT, new Notification.Builder(this, CH_RESULT)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build());
        Listener l = listener;
        if (l != null) main.post(() -> l.onUpdate(null));
    }
}
