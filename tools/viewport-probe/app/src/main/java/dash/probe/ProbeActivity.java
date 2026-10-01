package dash.probe;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/** Throwaway viewport probe for roadmap 1.7.1. Driven from adb with intent extras. */
public class ProbeActivity extends Activity {
    static final String T = "DASHPROBE";
    static final List<View> overlays = new ArrayList<>();

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(30, 30, 40));
        TextView tv = new TextView(this);
        tv.setText("DASH PROBE (pretend DASH)");
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(28);
        tv.setGravity(Gravity.CENTER);
        root.addView(tv);
        root.setOnTouchListener((v, e) -> { if (e.getAction() == android.view.MotionEvent.ACTION_DOWN) Log.i(T, "DASH area touched at " + (int) e.getRawX() + "," + (int) e.getRawY()); return true; });
        setContentView(root);
        handle(getIntent());
    }

    @Override protected void onNewIntent(Intent i) { super.onNewIntent(i); handle(i); }

    void handle(Intent i) {
        String act = i.getStringExtra("act");
        if (act == null) return;
        String pkg = i.getStringExtra("pkg");
        Log.i(T, "act=" + act + " pkg=" + pkg + " sdk=" + Build.VERSION.SDK_INT);
        try {
            switch (act) {
                case "bounds": {
                    Rect r = new Rect(i.getIntExtra("l", 0), i.getIntExtra("t", 0), i.getIntExtra("r", 800), i.getIntExtra("b", 800));
                    Intent launch = getPackageManager().getLaunchIntentForPackage(pkg);
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    if (i.getBooleanExtra("multi", false)) launch.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
                    ActivityOptions o = ActivityOptions.makeBasic();
                    o.setLaunchBounds(r);
                    startActivity(launch, o.toBundle());
                    Log.i(T, "bounds launch sent " + r);
                    break;
                }
                case "full": {
                    Intent launch = getPackageManager().getLaunchIntentForPackage(pkg);
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(launch);
                    break;
                }
                case "overlay": {
                    boolean can = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this);
                    Log.i(T, "canDrawOverlays=" + can);
                    if (!can) break;
                    WindowManager wm = (WindowManager) getApplicationContext().getSystemService(Context.WINDOW_SERVICE);
                    // top "system bar" and left "module panel", both semi-transparent to show what is under them
                    addBar(wm, 0, 0, WindowManager.LayoutParams.MATCH_PARENT, i.getIntExtra("top", 200), Color.argb(200, 200, 40, 40), "SYSTEM BAR (overlay)");
                    addBar(wm, 0, i.getIntExtra("top", 200), i.getIntExtra("left", 500), WindowManager.LayoutParams.MATCH_PARENT, Color.argb(200, 40, 120, 200), "PANEL (overlay)");
                    break;
                }
                case "overlay_off": {
                    WindowManager wm = (WindowManager) getApplicationContext().getSystemService(Context.WINDOW_SERVICE);
                    for (View v : overlays) wm.removeView(v);
                    overlays.clear();
                    break;
                }
            }
        } catch (Throwable e) {
            Log.e(T, "failed " + act, e);
        }
    }

    void addBar(WindowManager wm, int x, int y, int w, int h, int colour, String label) {
        TextView v = new TextView(getApplicationContext());
        v.setBackgroundColor(colour);
        v.setText(label);
        v.setTextColor(Color.WHITE);
        v.setTextSize(22);
        v.setGravity(Gravity.CENTER);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(w, h,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        lp.x = x; lp.y = y;
        v.setOnClickListener(c -> Log.i(T, "overlay tapped: " + label));
        wm.addView(v, lp);
        overlays.add(v);
        Log.i(T, "overlay added " + label);
    }
}
