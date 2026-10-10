/* Copyright (C) 2026 The ArkUI Project; SPDX-License-Identifier: Apache-2.0 */
package org.arkui.adskip.fixture;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

/** A local fixture with real accessible buttons; no downloaded app or live ad required. */
public final class AdActivity extends Activity {
    volatile boolean clicked;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.WHITE);
        setContentView(root);
        TextView content = new TextView(this);
        content.setText("Opening screen fixture");
        content.setTextColor(Color.BLACK);
        root.addView(content, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            TextView label = new TextView(this);
            label.setText(getIntent().getStringExtra("label"));
            label.setTextColor(Color.BLACK);
            label.setGravity(Gravity.CENTER);
            label.setBackgroundColor(0xffdddddd);
            int width = Math.round(104 * getResources().getDisplayMetrics().density);
            int height = Math.round(40 * getResources().getDisplayMetrics().density);
            FrameLayout.LayoutParams position = new FrameLayout.LayoutParams(width, height,
                    getIntent().getBooleanExtra("center", false) ? Gravity.CENTER
                            : Gravity.TOP | Gravity.RIGHT);
            position.topMargin = 24;
            position.rightMargin = 24;
            View button = label;
            if (getIntent().getBooleanExtra("parent", false)) {
                FrameLayout parent = new FrameLayout(this);
                parent.addView(label, new FrameLayout.LayoutParams(-1, -1));
                button = parent;
            }
            button.setOnClickListener(view -> {
                clicked = true;
                content.setText("Ad skipped");
                view.setVisibility(View.GONE);
            });
            root.addView(button, position);
        }, getIntent().getLongExtra("delay", 0));
    }
}
