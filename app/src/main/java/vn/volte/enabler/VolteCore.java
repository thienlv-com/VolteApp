package vn.volte.enabler;

import android.os.IBinder;
import android.os.PersistableBundle;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Chạy dưới uid shell qua ADB:
 *   CLASSPATH=<apk> app_process /system/bin vn.volte.enabler.VolteCore <enable|enable-status|clear|status>
 * uid shell có MODIFY_PHONE_STATE nên được ghi đè carrier config để bật VoLTE.
 */
public final class VolteCore {

    public static final String STATUS_MARK = "---STATUS---";
    public static final String NO_SIM = "NO_SIM";

    private static final String SHELL_PKG = "com.android.shell";
    private static final int MAX_SLOTS = 4;
    /** Thời gian chờ IMS đăng ký lại trước khi đọc trạng thái. */
    private static final long IMS_SETTLE_MS = 20000;

    public static void main(String[] args) throws Exception {
        String cmd = args.length > 0 ? args[0] : "status";
        switch (cmd) {
            case "enable":
                System.out.println(apply(true));
                break;
            case "enable-status":
                System.out.println(apply(true));
                System.out.flush();
                Thread.sleep(IMS_SETTLE_MS);
                System.out.println(STATUS_MARK);
                System.out.print(status());
                break;
            case "clear":
                System.out.println(apply(false));
                break;
            default:
                System.out.print(status());
        }
        System.out.flush();
    }

    static String apply(boolean enable) {
        StringBuilder log = new StringBuilder();
        try {
            List<int[]> subs = activeSubs();
            if (subs.isEmpty()) return NO_SIM;
            Object loader = carrierConfigLoader();
            Method override = findMethod(loader, "overrideConfig", -1);

            for (int[] s : subs) {
                int slot = s[0], subId = s[1];
                PersistableBundle p = enable ? volteBundle() : null;
                // Có tham số persistent (Android 12+, một số ROM 11): luôn dùng bản lưu vĩnh viễn để
                // ghi đè giữ được sau khi khởi động lại, và khi khôi phục thì xoá đúng bản đã lưu
                if (override.getParameterCount() == 3) {
                    override.invoke(loader, subId, p, true);
                } else {
                    override.invoke(loader, subId, p);
                }
                log.append("SIM ").append(slot + 1)
                        .append(enable ? ": đã bật cấu hình VoLTE\n" : ": đã khôi phục cấu hình gốc\n");

                if (enable) {
                    try {
                        invoke(telephony(), "setAdvancedCallingSettingEnabled", subId, true);
                    } catch (Throwable ignored) {
                        // "cmd phone ims enable" bên dưới vẫn bật IMS
                    }
                    exec("cmd phone ims enable -s " + slot);
                }
            }
        } catch (Throwable t) {
            log.append("Lỗi: ").append(rootCause(t));
        }
        return log.toString().trim();
    }

    /** Mỗi dòng: slot|subId|tên nhà mạng|volteConfig|imsRegistered|volteAvailable */
    static String status() {
        StringBuilder sb = new StringBuilder();
        String[] names = exec("getprop gsm.sim.operator.alpha").trim().split(",", -1);
        Object loader = null, tel = null;
        try {
            loader = carrierConfigLoader();
        } catch (Throwable ignored) {
        }
        try {
            tel = telephony();
        } catch (Throwable ignored) {
        }

        for (int[] s : activeSubs()) {
            int slot = s[0], subId = s[1];
            String name = slot < names.length && !names[slot].isEmpty() ? names[slot] : "SIM " + (slot + 1);
            Boolean cfg = null, reg = null, avail = null;
            try {
                cfg = getConfig(loader, subId).getBoolean("carrier_volte_available_bool");
            } catch (Throwable ignored) {
            }
            try {
                reg = (Boolean) invoke(tel, "isImsRegistered", subId);
            } catch (Throwable ignored) {
            }
            try {
                avail = (Boolean) invoke(tel, "isVolteAvailable", subId);
            } catch (Throwable ignored) {
            }
            sb.append(slot).append('|').append(subId).append('|').append(name.replace('|', ' '))
                    .append('|').append(cfg).append('|').append(reg).append('|').append(avail).append('\n');
        }
        return sb.toString();
    }

    private static PersistableBundle volteBundle() {
        PersistableBundle p = new PersistableBundle();
        p.putBoolean("carrier_volte_available_bool", true);
        p.putBoolean("carrier_volte_provisioning_required_bool", false);
        p.putBoolean("carrier_volte_provisioned_bool", true);
        p.putBoolean("editable_enhanced_4g_lte_bool", true);
        p.putBoolean("hide_enhanced_4g_lte_bool", false);
        p.putBoolean("enhanced_4g_lte_on_by_default_bool", true);
        p.putBoolean("carrier_ims_gba_required_bool", false);
        p.putBoolean("show_4g_for_lte_data_icon_bool", true);
        return p;
    }

    /**
     * Danh sách {slot, subId} của các SIM đang hoạt động. Dùng ISub trực tiếp vì trong app_process
     * không có TelephonyFrameworkInitializer nên API SubscriptionManager tĩnh không dùng được.
     */
    private static List<int[]> activeSubs() {
        List<int[]> out = new ArrayList<>();
        Object isub;
        try {
            isub = asInterface("com.android.internal.telephony.ISub", getService("isub"));
        } catch (Throwable t) {
            return out;
        }
        for (int slot = 0; slot < MAX_SLOTS; slot++) {
            int subId = -1;
            try {
                Object r = invoke(isub, "getSubId", slot);
                if (r instanceof int[] && ((int[]) r).length > 0) subId = ((int[]) r)[0];
                else if (r instanceof Integer) subId = (Integer) r;
            } catch (Throwable ignored) {
            }
            if (subId > 0) out.add(new int[]{slot, subId});
        }
        return out;
    }

    private static PersistableBundle getConfig(Object loader, int subId) throws Exception {
        try {
            return (PersistableBundle) invoke(loader, "getConfigForSubIdWithFeature", subId, SHELL_PKG, null);
        } catch (NoSuchMethodException e) {
            return (PersistableBundle) invoke(loader, "getConfigForSubId", subId, SHELL_PKG);
        }
    }

    private static Object carrierConfigLoader() throws Exception {
        return asInterface("com.android.internal.telephony.ICarrierConfigLoader", getService("carrier_config"));
    }

    private static Object telephony() throws Exception {
        return asInterface("com.android.internal.telephony.ITelephony", getService("phone"));
    }

    private static IBinder getService(String name) throws Exception {
        return (IBinder) Class.forName("android.os.ServiceManager")
                .getMethod("getService", String.class).invoke(null, name);
    }

    private static Object asInterface(String aidl, IBinder binder) throws Exception {
        return Class.forName(aidl + "$Stub").getMethod("asInterface", IBinder.class).invoke(null, binder);
    }

    private static Method findMethod(Object target, String name, int paramCount) throws NoSuchMethodException {
        for (Method m : target.getClass().getMethods()) {
            if (m.getName().equals(name) && (paramCount < 0 || m.getParameterCount() == paramCount)) return m;
        }
        throw new NoSuchMethodException(name);
    }

    private static Object invoke(Object target, String name, Object... args) throws Exception {
        if (target == null) throw new IllegalStateException("service unavailable");
        try {
            return findMethod(target, name, args.length).invoke(target, args);
        } catch (InvocationTargetException e) {
            Throwable c = e.getCause();
            throw c instanceof Exception ? (Exception) c : e;
        }
    }

    private static String exec(String cmd) {
        StringBuilder sb = new StringBuilder();
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c", cmd});
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line).append('\n');
            }
            p.waitFor();
        } catch (Exception ignored) {
        }
        return sb.toString();
    }

    private static String rootCause(Throwable t) {
        while (t.getCause() != null) t = t.getCause();
        return t.getClass().getSimpleName() + ": " + t.getMessage();
    }
}
