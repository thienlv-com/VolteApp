package vn.volte.enabler;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Android 11 xoá cấu hình ghi đè khi khởi động lại, nên bật lại VoLTE mỗi lần khởi động. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        if (!VolteAdb.isPaired(context) || !VolteAdb.prefs(context).getBoolean(VolteAdb.KEY_AUTO, true)) return;
        WorkService.start(context, new Intent(WorkService.ACTION_APPLY)
                .putExtra(WorkService.EXTRA_ENABLE, true)
                .putExtra(WorkService.EXTRA_BOOT, true));
    }
}
