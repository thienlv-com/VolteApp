import android.os.IBinder;
import android.os.PersistableBundle;
import java.lang.reflect.Method;

/**
 * Chạy bằng: CLASSPATH=/data/local/tmp/volte.dex app_process / VolteEnabler <subId> [<subId>...]
 * Ghi đè carrier config (dưới uid shell, có MODIFY_PHONE_STATE) để bật VoLTE,
 * rồi bật công tắc "Enhanced 4G LTE / VoLTE" cho từng subId.
 * Truyền "clear" làm tham số đầu để xoá ghi đè: ... VolteEnabler clear <subId>...
 */
public class VolteEnabler {
    public static void main(String[] args) throws Exception {
        boolean clear = args.length > 0 && args[0].equals("clear");
        Class<?> sm = Class.forName("android.os.ServiceManager");
        IBinder b = (IBinder) sm.getMethod("getService", String.class).invoke(null, "carrier_config");
        Object loader = Class.forName("com.android.internal.telephony.ICarrierConfigLoader$Stub")
                .getMethod("asInterface", IBinder.class).invoke(null, b);

        Method override = null;
        for (Method m : loader.getClass().getMethods()) {
            if (m.getName().equals("overrideConfig")) override = m;
        }

        for (int i = clear ? 1 : 0; i < args.length; i++) {
            int subId = Integer.parseInt(args[i]);
            PersistableBundle p = null;
            if (!clear) {
                p = new PersistableBundle();
                p.putBoolean("carrier_volte_available_bool", true);
                p.putBoolean("carrier_volte_provisioning_required_bool", false);
                p.putBoolean("carrier_volte_provisioned_bool", true);
                p.putBoolean("editable_enhanced_4g_lte_bool", true);
                p.putBoolean("hide_enhanced_4g_lte_bool", false);
                p.putBoolean("enhanced_4g_lte_on_by_default_bool", true);
                p.putBoolean("carrier_ims_gba_required_bool", false);
                p.putBoolean("show_4g_for_lte_data_icon_bool", true);
            }
            if (override.getParameterCount() == 3) {
                override.invoke(loader, subId, p, true);
            } else {
                override.invoke(loader, subId, p);
            }
            System.out.println((clear ? "Cleared" : "Overrode") + " carrier config for subId " + subId);

            if (!clear) {
                try {
                    Class<?> mm = Class.forName("android.telephony.ims.ImsMmTelManager");
                    Object mgr = mm.getMethod("createForSubscriptionId", int.class).invoke(null, subId);
                    mm.getMethod("setAdvancedCallingSettingEnabled", boolean.class).invoke(mgr, true);
                    System.out.println("  VoLTE (Enhanced 4G) switch ON for subId " + subId);
                } catch (Throwable t) {
                    System.out.println("  Could not toggle VoLTE switch: " + t);
                }
            }
        }
    }
}
