package com.serhii.cleanhome;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/**
 * Все приложения сеткой.
 * OK — открыть, долгое нажатие OK — добавить на главный / убрать (★).
 */
public class AllAppsActivity extends Activity {

    private List<Apps.AppInfo> apps;
    private BaseAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        apps = Apps.loadAll(this);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackground(Ui.background());
        int padH = Ui.dp(this, 64), padV = Ui.dp(this, 40);
        col.setPadding(padH, padV, padH, 0);

        TextView title = new TextView(this);
        title.setText("Все приложения");
        Ui.text(title, 32, Color.WHITE, true);
        TextView hint = new TextView(this);
        hint.setText("Долгое нажатие OK — добавить на главный экран или убрать (★)");
        Ui.text(hint, 16, 0x99FFFFFF, false);
        hint.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 24));
        col.addView(title);
        col.addView(hint);

        GridView grid = new GridView(this);
        grid.setNumColumns(GridView.AUTO_FIT);
        grid.setColumnWidth(Ui.dp(this, 240));
        grid.setStretchMode(GridView.SPACING_WIDTH_UNIFORM);
        grid.setVerticalSpacing(Ui.dp(this, 24));
        grid.setHorizontalSpacing(Ui.dp(this, 24));
        grid.setClipToPadding(false);
        grid.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 40));
        grid.setDrawSelectorOnTop(true);
        GradientDrawable sel = new GradientDrawable();
        sel.setCornerRadius(Ui.dp(this, 18));
        sel.setColor(Color.TRANSPARENT);
        sel.setStroke(Ui.dp(this, 3), Color.WHITE);
        grid.setSelector(sel);

        adapter = new BaseAdapter() {
            @Override public int getCount() { return apps.size(); }
            @Override public Object getItem(int i) { return apps.get(i); }
            @Override public long getItemId(int i) { return i; }
            @Override public View getView(int i, View convert, ViewGroup parent) {
                Apps.AppInfo a = apps.get(i);
                boolean banner = a.banner != null;
                String label = (Apps.isFav(AllAppsActivity.this, a.pkg) ? "★ " : "") + a.label;
                return Ui.tile(AllAppsActivity.this, banner ? a.banner : a.icon, null,
                        label, banner, false);
            }
        };
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((p, v, i, id) -> Apps.launch(this, apps.get(i).pkg));
        grid.setOnItemLongClickListener((p, v, i, id) -> {
            Apps.AppInfo a = apps.get(i);
            boolean added = Apps.toggleFav(this, a.pkg);
            Toast.makeText(this, (added ? "На главном: " : "Убрано: ") + a.label,
                    Toast.LENGTH_SHORT).show();
            adapter.notifyDataSetChanged();
            return true;
        });

        col.addView(grid, new LinearLayout.LayoutParams(Ui.MATCH, 0, 1f));
        setContentView(col);
        grid.requestFocus();
    }
}
