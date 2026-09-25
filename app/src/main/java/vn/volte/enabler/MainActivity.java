package vn.volte.enabler;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.provider.Settings;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private static final int REQ_PERMS = 1;

    private TextView simText, pairText, progressText, lastText, stepsText;
    private Button pairBtn, enableBtn, restoreBtn;
    private CheckBox autoBox;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildLayout();
        requestNeededPermissions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        WorkService.listener = msg -> {
            progressText.setText(msg != null ? msg : "");
            refresh();
        };
        refresh();
    }

    @Override
    protected void onPause() {
        WorkService.listener = null;
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        refresh();
    }

    private void requestNeededPermissions() {
        List<String> perms = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            perms.add(Manifest.permission.READ_PHONE_STATE);
        }
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (!perms.isEmpty()) requestPermissions(perms.toArray(new String[0]), REQ_PERMS);
    }

    private void refresh() {
        simText.setText(simStatus());

        boolean paired = VolteAdb.isPaired(this);
        boolean auto = VolteAdb.canToggleWirelessDebugging(this);
        if (!paired) {
            pairText.setText("⚠️ Chưa ghép nối — làm 1 lần theo hướng dẫn bên dưới");
        } else if (auto) {
            pairText.setText("✅ Đã ghép nối — VoLTE được giữ sau khi khởi động lại máy");
        } else {
            pairText.setText("✅ Đã ghép nối — cần bật Gỡ lỗi không dây trước khi bấm Bật VoLTE");
        }
        pairBtn.setText(paired ? "Ghép nối lại" : "Ghép nối (làm 1 lần)");
        enableBtn.setEnabled(paired);
        restoreBtn.setEnabled(paired);
        stepsText.setVisibility(paired ? View.GONE : View.VISIBLE);
        lastText.setText(VolteAdb.prefs(this).getString(VolteAdb.KEY_LAST, ""));
    }

    /** Trạng thái cấu hình VoLTE hiện tại của từng SIM, đọc bằng API công khai. */
    private String simStatus() {
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            return "Cấp quyền \"Điện thoại\" để xem trạng thái SIM.";
        }
        SubscriptionManager sm = getSystemService(SubscriptionManager.class);
        CarrierConfigManager ccm = getSystemService(CarrierConfigManager.class);
        List<SubscriptionInfo> subs = sm.getActiveSubscriptionInfoList();
        if (subs == null || subs.isEmpty()) return "Không có SIM nào đang hoạt động.";

        StringBuilder sb = new StringBuilder();
        for (SubscriptionInfo si : subs) {
            PersistableBundle cfg = ccm.getConfigForSubId(si.getSubscriptionId());
            boolean volte = cfg != null && cfg.getBoolean(CarrierConfigManager.KEY_CARRIER_VOLTE_AVAILABLE_BOOL);
            if (sb.length() > 0) sb.append("\n\n");
            sb.append("SIM ").append(si.getSimSlotIndex() + 1).append(" · ").append(si.getDisplayName())
                    .append('\n').append(volte ? "✅ VoLTE đã bật" : "❌ VoLTE chưa bật");
        }
        return sb.toString();
    }

    private void startPairing() {
        WorkService.start(this, new Intent(WorkService.ACTION_PAIR_START));
        Intent dev = new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                .putExtra(":settings:fragment_args_key", "toggle_adb_wireless");
        try {
            startActivity(dev);
        } catch (Exception e) {
            progressText.setText("Mở Cài đặt → Hệ thống → Tuỳ chọn nhà phát triển → Gỡ lỗi không dây.");
        }
    }

    private void apply(boolean enable) {
        progressText.setText(enable ? "Đang bật VoLTE…" : "Đang khôi phục…");
        WorkService.start(this, new Intent(WorkService.ACTION_APPLY).putExtra(WorkService.EXTRA_ENABLE, enable));
    }

    // ---- Giao diện dựng bằng code ----

    private void buildLayout() {
        int pad = dp(20);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        root.addView(text("VoLTE VN", 26, true));
        TextView sub = text("Bật gọi thoại 4G (VoLTE) cho SIM Việt Nam trên máy không hỗ trợ sẵn.", 14, false);
        sub.setAlpha(0.7f);
        root.addView(sub, margins(0, dp(4), 0, dp(20)));

        simText = text("—", 17, false);
        simText.setLineSpacing(0, 1.2f);
        root.addView(card(simText), margins(0, 0, 0, dp(12)));

        pairText = text("", 15, false);
        root.addView(card(pairText), margins(0, 0, 0, dp(16)));

        enableBtn = button("Bật VoLTE ngay", v -> apply(true));
        root.addView(enableBtn, margins(0, 0, 0, dp(8)));
        restoreBtn = button("Khôi phục mặc định", v -> apply(false));
        root.addView(restoreBtn, margins(0, 0, 0, dp(8)));
        pairBtn = button("", v -> startPairing());
        root.addView(pairBtn, margins(0, 0, 0, dp(8)));

        autoBox = new CheckBox(this);
        autoBox.setText("Kiểm tra và bật lại VoLTE khi khởi động (dự phòng)");
        autoBox.setChecked(VolteAdb.prefs(this).getBoolean(VolteAdb.KEY_AUTO, true));
        autoBox.setOnCheckedChangeListener((b, c) ->
                VolteAdb.prefs(this).edit().putBoolean(VolteAdb.KEY_AUTO, c).apply());
        root.addView(autoBox, margins(0, dp(4), 0, dp(12)));

        progressText = text("", 14, false);
        root.addView(progressText, margins(0, 0, 0, dp(4)));
        lastText = text("", 14, false);
        lastText.setAlpha(0.8f);
        root.addView(lastText, margins(0, 0, 0, dp(20)));

        stepsText = text("Ghép nối (chỉ làm 1 lần, cần Wi-Fi):\n"
                + "1. Bấm \"Ghép nối\" — app mở Tuỳ chọn nhà phát triển.\n"
                + "2. Bật \"Gỡ lỗi không dây\" (chọn Luôn cho phép trên mạng này).\n"
                + "3. Chạm vào \"Gỡ lỗi không dây\" → \"Ghép nối thiết bị bằng mã ghép nối\".\n"
                + "4. Kéo thanh thông báo xuống, bấm \"Nhập mã\" của VoLTE VN và gõ mã 6 số.\n"
                + "Xong! VoLTE được bật ngay và giữ nguyên sau khi khởi động lại.", 13, false);
        stepsText.setAlpha(0.75f);
        stepsText.setLineSpacing(0, 1.2f);
        root.addView(stepsText);

        ScrollView scroll = new ScrollView(this);
        scroll.setFitsSystemWindows(true);
        scroll.addView(root);
        setContentView(scroll);
    }

    private Button button(String label, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(label);
        b.setOnClickListener(l);
        return b;
    }

    private TextView text(String s, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private View card(View content) {
        LinearLayout box = new LinearLayout(this);
        box.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(14));
        bg.setColor(0x221565C0);
        box.setBackground(bg);
        box.addView(content, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return box;
    }

    private LinearLayout.LayoutParams margins(int l, int t, int r, int b) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(l, t, r, b);
        return lp;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
