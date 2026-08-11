package com.partssystem.app;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final int REQ_SCAN = 101;
    private static final int REQ_CAMERA = 102;
    private static final int BG = 0xFFF8FAFC;
    private static final int CARD = 0xFFFFFFFF;
    private static final int LINE = 0xFFD9E0EA;
    private static final int TEXT = 0xFF101827;
    private static final int MUTED = 0xFF64748B;
    private static final int BLUE = 0xFF1456C8;
    private static final int SOFT = 0xFFEDF2F7;
    private static final int GREEN = 0xFF0F9F6E;

    private PartsDatabase database;
    private final List<VehicleModel> models = new ArrayList<>();
    private String language = "zh";
    private LinearLayout root;
    private Spinner languageSpinner;
    private Spinner vehicleSpinner;
    private Button partsTabButton;
    private Button scanTabButton;
    private LinearLayout resultList;
    private EditText keywordInput;
    private EditText scanInput;
    private TextView pageTitle;
    private TextView vehicleDetailText;
    private View scannerPanel;
    private View scanFieldLabel;
    private View scanSearchRow;
    private boolean lastScanFromCamera = false;
    private int currentPage = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        database = new PartsDatabase(this);
        database.open();
        models.addAll(database.vehicleModels());
        showMain();
    }

    @Override
    protected void onDestroy() {
        database.close();
        super.onDestroy();
    }

    private void showMain() {
        root = vertical();
        root.setBackgroundColor(BG);
        setContentView(root);

        LinearLayout top = horizontal();
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(18), dp(12), dp(18), dp(14));
        top.setBackgroundColor(CARD);
        LinearLayout titleBlock = vertical();
        pageTitle = label(text("appTitle"), 22, true);
        TextView subtitle = label("Parts Query", 13, false);
        subtitle.setTextColor(MUTED);
        titleBlock.addView(pageTitle);
        titleBlock.addView(subtitle);
        top.addView(titleBlock, new LinearLayout.LayoutParams(0, -2, 1));
        languageSpinner = new Spinner(this);
        ArrayAdapter<String> langAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,
                new String[]{"\u4e2d\u6587", "English", "Francais"});
        langAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        languageSpinner.setAdapter(langAdapter);
        languageSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                language = position == 1 ? "en" : position == 2 ? "fr" : "zh";
                renderPage();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        languageSpinner.setBackground(controlBg());
        top.addView(languageSpinner, new LinearLayout.LayoutParams(dp(126), dp(42)));
        root.addView(top);

        LinearLayout controls = horizontal();
        controls.setPadding(dp(14), dp(10), dp(14), dp(10));
        controls.setBackgroundColor(CARD);
        partsTabButton = button(text("partsTab"));
        partsTabButton.setOnClickListener(v -> {
            currentPage = 0;
            renderPage();
        });
        scanTabButton = button(text("scanTab"));
        scanTabButton.setOnClickListener(v -> {
            currentPage = 1;
            renderPage();
        });
        LinearLayout.LayoutParams tabLp = new LinearLayout.LayoutParams(0, dp(40), 1);
        controls.addView(partsTabButton, tabLp);
        LinearLayout.LayoutParams tabLp2 = new LinearLayout.LayoutParams(0, dp(40), 1);
        tabLp2.setMargins(dp(8), 0, 0, 0);
        controls.addView(scanTabButton, tabLp2);
        root.addView(controls);

        renderPage();
    }

    private void renderPage() {
        if (root == null || root.getChildCount() < 2) return;
        while (root.getChildCount() > 2) {
            root.removeViewAt(2);
        }
        pageTitle.setText(text("appTitle"));
        if (partsTabButton != null) partsTabButton.setText(text("partsTab"));
        if (scanTabButton != null) scanTabButton.setText(text("scanTab"));
        styleTab(partsTabButton, currentPage == 0);
        styleTab(scanTabButton, currentPage == 1);
        if (currentPage == 0) {
            renderPartsPage();
        } else {
            renderScanPage();
        }
    }

    private void renderPartsPage() {
        LinearLayout content = content();
        content.addView(fieldLabel(text("vehicleLabel")));
        vehicleSpinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, modelLabels());
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        vehicleSpinner.setAdapter(adapter);
        vehicleSpinner.setBackground(controlBg());
        vehicleSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateSelectedVehicleDetail();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        content.addView(vehicleSpinner, new LinearLayout.LayoutParams(-1, dp(46)));

        vehicleDetailText = label("", 14, false);
        vehicleDetailText.setBackground(cardBg());
        vehicleDetailText.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams detailLp = new LinearLayout.LayoutParams(-1, -2);
        detailLp.setMargins(0, dp(9), 0, dp(12));
        content.addView(vehicleDetailText, detailLp);
        updateSelectedVehicleDetail();

        LinearLayout searchRow = horizontal();
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        keywordInput = input(text("keywordHint"));
        searchRow.addView(keywordInput, new LinearLayout.LayoutParams(0, dp(46), 1));
        Button search = button(text("search"));
        stylePrimaryButton(search);
        search.setOnClickListener(v -> searchParts());
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(dp(112), dp(46));
        searchLp.setMargins(dp(8), 0, 0, 0);
        searchRow.addView(search, searchLp);
        content.addView(searchRow);

        LinearLayout resultHead = horizontal();
        resultHead.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = label(text("resultTitle"), 18, true);
        resultHead.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView count = label("0", 13, false);
        count.setTextColor(MUTED);
        resultHead.addView(count);
        content.addView(resultHead);

        resultList = vertical();
        ScrollView scroll = new ScrollView(this);
        scroll.addView(resultList);
        content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
    }

    private void renderScanPage() {
        LinearLayout content = content();
        scannerPanel = scannerPreview();
        content.addView(scannerPanel);
        scanFieldLabel = fieldLabel(text("scanHint"));
        content.addView(scanFieldLabel);
        LinearLayout row = horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        scanInput = input(text("scanHint"));
        scanInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                lastScanFromCamera = false;
                setScanCompact(false);
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        Button query = button(text("query"));
        stylePrimaryButton(query);
        query.setOnClickListener(v -> {
            lastScanFromCamera = false;
            queryScan(scanInput.getText().toString(), false, false);
        });
        row.addView(scanInput, new LinearLayout.LayoutParams(0, dp(46), 1));
        LinearLayout.LayoutParams queryLp = new LinearLayout.LayoutParams(dp(112), dp(46));
        queryLp.setMargins(dp(8), 0, 0, 0);
        row.addView(query, queryLp);
        scanSearchRow = row;
        content.addView(scanSearchRow);

        Button scan = button(text("scan"));
        styleGhostButton(scan);
        scan.setOnClickListener(v -> startScan());
        LinearLayout.LayoutParams scanLp = new LinearLayout.LayoutParams(-1, dp(40));
        scanLp.setMargins(0, dp(10), 0, dp(12));
        content.addView(scan, scanLp);

        resultList = vertical();
        ScrollView scroll = new ScrollView(this);
        scroll.addView(resultList);
        content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
    }

    private void updateSelectedVehicleDetail() {
        if (vehicleDetailText == null || models.isEmpty() || vehicleSpinner == null) return;
        vehicleDetailText.setText(vehicleDetail(models.get(Math.max(0, vehicleSpinner.getSelectedItemPosition()))));
    }

    private void searchParts() {
        resultList.removeAllViews();
        if (models.isEmpty()) {
            resultList.addView(label(text("noData"), 16, false));
            return;
        }
        VehicleModel model = models.get(Math.max(0, vehicleSpinner.getSelectedItemPosition()));
        String keyword = keywordInput.getText().toString().trim();
        if (keyword.isEmpty()) {
            Toast.makeText(this, text("enterKeyword"), Toast.LENGTH_SHORT).show();
            return;
        }
        List<PartItem> parts = database.searchParts(language, model.assemblyCode, keyword, 200);
        resultList.addView(label(String.format(text("resultCount"), parts.size()), 15, true));
        for (PartItem item : parts) {
            resultList.addView(partCard(item, model));
        }
    }

    private void startScan() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
            return;
        }
        startActivityForResult(new Intent(this, ScanActivity.class), REQ_SCAN);
    }

    private void queryScan(String raw) {
        queryScan(raw, false, false);
    }

    private void queryScan(String raw, boolean fuzzy) {
        queryScan(raw, fuzzy, lastScanFromCamera);
    }

    private void queryScan(String raw, boolean fuzzy, boolean fromScanner) {
        resultList.removeAllViews();
        String code = raw == null ? "" : raw.trim();
        if (code.isEmpty()) {
            setScanCompact(false);
            Toast.makeText(this, text("enterScan"), Toast.LENGTH_SHORT).show();
            return;
        }
        List<VehicleInfo> infos = database.findVehiclesByScannedPartNo(language, code, fuzzy, fromScanner);
        if (infos.isEmpty()) {
            setScanCompact(false);
            resultList.addView(label(fuzzy ? text("fuzzyNotFound") : text("exactNotFound"), 16, false));
            if (!fuzzy) {
                Button fuzzyButton = button(text("fuzzySearch"));
                styleGhostButton(fuzzyButton);
                fuzzyButton.setOnClickListener(v -> queryScan(code, true, fromScanner));
                resultList.addView(fuzzyButton);
            }
            return;
        }
        setScanCompact(true);
        resultList.addView(label(fuzzy ? text("fuzzyResults") : text("exactResults"), 15, true));
        for (VehicleInfo info : infos) {
            resultList.addView(vehicleCard(info));
            for (PartItem item : info.parts) {
                resultList.addView(partCard(item, info.model));
            }
        }
    }

    private void setScanCompact(boolean compact) {
        if (scannerPanel != null) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(compact ? 96 : 270));
            lp.setMargins(0, 0, 0, dp(compact ? 10 : 14));
            scannerPanel.setLayoutParams(lp);
        }
        if (scanFieldLabel != null) scanFieldLabel.setVisibility(compact ? View.GONE : View.VISIBLE);
        if (scanSearchRow != null) scanSearchRow.setVisibility(compact ? View.GONE : View.VISIBLE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_SCAN && resultCode == RESULT_OK && data != null) {
            String code = data.getStringExtra("code");
            scanInput.setText(code);
            lastScanFromCamera = true;
            queryScan(code, false, true);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CAMERA && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startScan();
        }
    }

    private View vehicleCard(VehicleInfo info) {
        LinearLayout card = card();
        card.addView(label(text("vehicleInfo"), 18, true));
        card.addView(infoRow(text("model"), info.model.modelName(language)));
        card.addView(infoRow(text("shortName"), info.model.shortName(language)));
        card.addView(infoRow(text("assembly"), info.model.assemblyCode));
        card.addView(infoRow(text("partCode"), info.scanLookupCode));
        card.addView(label(String.format(text("resultCount"), info.parts.size()), 14, true));
        return card;
    }

    private View partCard(PartItem item, VehicleModel model) {
        LinearLayout card = card();
        card.addView(label(item.partNo + "  " + item.name, 15, true));
        card.addView(infoRow(text("drawingNo"), item.partNo));
        card.addView(infoRow(text("qty"), item.quantity));
        card.addView(infoRow(text("note"), item.note));
        card.addView(infoRow(text("assembly"), model.assemblyCode));
        card.addView(label(item.group1 + " / " + item.group2 + (item.group3.isEmpty() ? "" : " / " + item.group3), 13, false));
        Button copy = button(text("copy"));
        styleGhostButton(copy);
        copy.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("part", copyText(item, model)));
            Toast.makeText(this, text("copied"), Toast.LENGTH_SHORT).show();
        });
        card.addView(copy);
        return card;
    }

    private View infoRow(String name, String value) {
        LinearLayout row = horizontal();
        row.setGravity(Gravity.TOP);
        TextView key = label(name, 13, false);
        key.setTextColor(MUTED);
        TextView val = label(value == null ? "" : value, 14, true);
        row.addView(key, new LinearLayout.LayoutParams(dp(92), -2));
        row.addView(val, new LinearLayout.LayoutParams(0, -2, 1));
        return row;
    }

    private String vehicleDetail(VehicleModel model) {
        return text("model") + ": " + model.modelName(language) + "\n" +
                text("shortName") + ": " + model.shortName(language) + "\n" +
                text("assembly") + ": " + model.assemblyCode;
    }

    private String copyText(PartItem item, VehicleModel model) {
        return text("model") + ": " + model.modelName(language) + "\n" +
                text("shortName") + ": " + model.shortName(language) + "\n" +
                text("assembly") + ": " + model.assemblyCode + "\n" +
                text("drawingNo") + ": " + item.partNo + "\n" +
                text("partName") + ": " + item.name + "\n" +
                text("qty") + ": " + item.quantity + "\n" +
                text("note") + ": " + item.note + "\n" +
                text("group") + ": " + item.group1 + " / " + item.group2 + (item.group3.isEmpty() ? "" : " / " + item.group3);
    }

    private List<String> modelLabels() {
        List<String> labels = new ArrayList<>();
        for (VehicleModel model : models) labels.add(model.display(language));
        return labels;
    }

    private LinearLayout vertical() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout horizontal() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        return layout;
    }

    private LinearLayout content() {
        LinearLayout layout = vertical();
        layout.setPadding(dp(14), dp(14), dp(14), 0);
        return layout;
    }

    private LinearLayout card() {
        LinearLayout card = vertical();
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setBackground(cardBg());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(10));
        card.setLayoutParams(lp);
        return card;
    }

    private TextView label(String value, int sp, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(value);
        tv.setTextSize(sp);
        tv.setTextColor(TEXT);
        tv.setPadding(0, dp(4), 0, dp(4));
        tv.setSingleLine(false);
        tv.setEllipsize(null);
        tv.setBreakStrategy(android.text.Layout.BREAK_STRATEGY_HIGH_QUALITY);
        tv.setHyphenationFrequency(android.text.Layout.HYPHENATION_FREQUENCY_NORMAL);
        if (bold) tv.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return tv;
    }

    private TextView sectionTitle(String value) {
        TextView tv = label(value, 18, true);
        tv.setPadding(0, dp(16), 0, dp(8));
        return tv;
    }

    private TextView fieldLabel(String value) {
        TextView tv = label(value, 13, true);
        tv.setTextColor(MUTED);
        tv.setPadding(0, dp(4), 0, dp(7));
        return tv;
    }

    private EditText input(String hint) {
        EditText edit = new EditText(this);
        edit.setHint(hint);
        edit.setSingleLine(true);
        edit.setEllipsize(TextUtils.TruncateAt.END);
        edit.setTextSize(15);
        edit.setTextColor(TEXT);
        edit.setHintTextColor(MUTED);
        edit.setPadding(dp(11), 0, dp(11), 0);
        edit.setBackground(controlBg());
        return edit;
    }

    private Button button(String value) {
        Button btn = new Button(this);
        btn.setText(value);
        btn.setAllCaps(false);
        btn.setMinWidth(0);
        btn.setMinimumWidth(0);
        btn.setTextSize(14);
        btn.setSingleLine(false);
        btn.setPadding(dp(8), 0, dp(8), 0);
        return btn;
    }

    private View scannerPreview() {
        LinearLayout scanner = vertical();
        scanner.setGravity(Gravity.CENTER);
        scanner.setPadding(dp(28), dp(32), dp(28), dp(32));
        scanner.setBackground(roundBg(0xFFA9825C, 10, 0));
        LinearLayout.LayoutParams scannerLp = new LinearLayout.LayoutParams(-1, dp(270));
        scannerLp.setMargins(0, 0, 0, dp(14));
        scanner.setLayoutParams(scannerLp);

        LinearLayout frame = vertical();
        frame.setGravity(Gravity.CENTER);
        frame.setPadding(dp(18), dp(18), dp(18), dp(18));
        frame.setBackground(roundStrokeBg(Color.TRANSPARENT, 8, 0xFF2D8CFF, 3));

        LinearLayout box = vertical();
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(16), dp(14), dp(16), dp(14));
        box.setBackground(roundBg(CARD, 5, 0));
        TextView code = label("VBV3JBBXTY0", 20, true);
        code.setGravity(Gravity.CENTER);
        TextView qr = label("QR / BARCODE", 14, false);
        qr.setGravity(Gravity.CENTER);
        TextView made = label("PARTS MADE IN CHINA", 13, false);
        made.setTextColor(MUTED);
        made.setGravity(Gravity.CENTER);
        box.addView(code);
        box.addView(qr);
        box.addView(made);
        frame.addView(box, new LinearLayout.LayoutParams(-1, -2));
        scanner.addView(frame, new LinearLayout.LayoutParams(-1, -1));
        return scanner;
    }

    private void styleTab(Button button, boolean active) {
        if (button == null) return;
        button.setTextColor(active ? Color.WHITE : 0xFF334155);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackground(roundBg(active ? BLUE : SOFT, 6, 0));
    }

    private void stylePrimaryButton(Button button) {
        button.setTextColor(Color.WHITE);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackground(roundBg(BLUE, 6, 0));
    }

    private void styleGhostButton(Button button) {
        button.setTextColor(BLUE);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackground(roundStrokeBg(CARD, 6, BLUE, 1));
    }

    private GradientDrawable cardBg() {
        return roundStrokeBg(CARD, 8, LINE, 1);
    }

    private GradientDrawable controlBg() {
        return roundStrokeBg(CARD, 6, LINE, 1);
    }

    private GradientDrawable roundBg(int color, int radiusDp, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        if (strokeColor != 0) drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private GradientDrawable roundStrokeBg(int color, int radiusDp, int strokeColor, int strokeDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(strokeDp), strokeColor);
        return drawable;
    }

    private String text(String key) {
        switch (language) {
            case "en":
                switch (key) {
                    case "appTitle": return "Parts Query";
                    case "partsTab": return "Parts";
                    case "scanTab": return "Scan";
                    case "vehicleLabel": return "Vehicle model";
                    case "keywordHint": return "Enter part name or drawing number";
                    case "search": return "Search";
                    case "resultTitle": return "Search results";
                    case "scanHint": return "Scan or enter box code";
                    case "scan": return "Scan";
                    case "query": return "Query";
                    case "copy": return "Copy";
                    case "copied": return "Copied";
                    case "qty": return "Qty";
                    case "note": return "Note";
                    case "resultCount": return "%d results";
                    case "enterKeyword": return "Please enter a keyword";
                    case "enterScan": return "Please scan or enter a code";
                    case "notFound": return "No vehicle found";
                    case "exactNotFound": return "No exact drawing number match";
                    case "fuzzySearch": return "Fuzzy search";
                    case "fuzzyNotFound": return "No similar drawing number found";
                    case "exactResults": return "Exact drawing number match";
                    case "fuzzyResults": return "Similar drawing number results";
                    case "noData": return "No data";
                    case "vehicleInfo": return "Vehicle information";
                    case "model": return "Model";
                    case "shortName": return "Short name";
                    case "assembly": return "Assembly";
                    case "scanKey": return "Scan key";
                    case "partCode": return "Part code";
                    case "drawingNo": return "Drawing No";
                    case "partName": return "Part name";
                    case "group": return "Group";
                }
                break;
            case "fr":
                switch (key) {
                    case "appTitle": return "Recherche de pieces";
                    case "partsTab": return "Pieces";
                    case "scanTab": return "Scanner";
                    case "vehicleLabel": return "Modele";
                    case "keywordHint": return "Nom de piece ou numero de dessin";
                    case "search": return "Rechercher";
                    case "resultTitle": return "Resultats";
                    case "scanHint": return "Scanner ou saisir le code";
                    case "scan": return "Scanner";
                    case "query": return "Rechercher";
                    case "copy": return "Copier";
                    case "copied": return "Copie";
                    case "qty": return "Qte";
                    case "note": return "Note";
                    case "resultCount": return "%d resultats";
                    case "enterKeyword": return "Veuillez saisir un mot-cle";
                    case "enterScan": return "Veuillez scanner ou saisir un code";
                    case "notFound": return "Vehicule introuvable";
                    case "exactNotFound": return "Aucune correspondance exacte du numero de dessin";
                    case "fuzzySearch": return "Recherche approximative";
                    case "fuzzyNotFound": return "Aucun numero de dessin similaire";
                    case "exactResults": return "Correspondance exacte du numero de dessin";
                    case "fuzzyResults": return "Numeros de dessin similaires";
                    case "noData": return "Aucune donnee";
                    case "vehicleInfo": return "Informations vehicule";
                    case "model": return "Modele";
                    case "shortName": return "Nom court";
                    case "assembly": return "Assemblage";
                    case "scanKey": return "Cle de scan";
                    case "partCode": return "Code piece";
                    case "drawingNo": return "Numero de dessin";
                    case "partName": return "Nom de piece";
                    case "group": return "Groupe";
                }
                break;
            default:
                switch (key) {
                    case "appTitle": return "\u914d\u4ef6\u67e5\u8be2";
                    case "partsTab": return "\u914d\u4ef6\u67e5\u8be2";
                    case "scanTab": return "\u626b\u7801\u67e5\u8be2";
                    case "vehicleLabel": return "\u9009\u62e9\u8f66\u578b";
                    case "keywordHint": return "\u8f93\u5165\u914d\u4ef6\u540d\u79f0\u6216\u56fe\u53f7";
                    case "search": return "\u641c\u7d22";
                    case "resultTitle": return "\u641c\u7d22\u7ed3\u679c";
                    case "scanHint": return "\u626b\u63cf\u6216\u8f93\u5165\u7bb1\u7801";
                    case "scan": return "\u626b\u7801";
                    case "query": return "\u67e5\u8be2";
                    case "copy": return "\u590d\u5236";
                    case "copied": return "\u5df2\u590d\u5236";
                    case "qty": return "\u6570\u91cf";
                    case "note": return "\u8bf4\u660e";
                    case "resultCount": return "\u5171 %d \u6761";
                    case "enterKeyword": return "\u8bf7\u8f93\u5165\u5173\u952e\u8bcd";
                    case "enterScan": return "\u8bf7\u626b\u63cf\u6216\u8f93\u5165\u7f16\u7801";
                    case "notFound": return "\u672a\u627e\u5230\u8f66\u578b";
                    case "exactNotFound": return "\u672a\u627e\u5230\u7cbe\u786e\u56fe\u53f7\u5339\u914d";
                    case "fuzzySearch": return "\u6a21\u7cca\u67e5\u627e";
                    case "fuzzyNotFound": return "\u672a\u627e\u5230\u76f8\u4f3c\u56fe\u53f7";
                    case "exactResults": return "\u7cbe\u786e\u56fe\u53f7\u5339\u914d";
                    case "fuzzyResults": return "\u76f8\u4f3c\u56fe\u53f7\u7ed3\u679c";
                    case "noData": return "\u65e0\u6570\u636e";
                    case "vehicleInfo": return "\u8f66\u578b\u4fe1\u606f";
                    case "model": return "\u8f66\u578b";
                    case "shortName": return "\u7b80\u79f0";
                    case "assembly": return "\u6574\u7f16";
                    case "scanKey": return "\u626b\u7801\u67e5\u627e\u7801";
                    case "partCode": return "\u622a\u53d6\u540e\u56fe\u53f7";
                    case "drawingNo": return "\u56fe\u53f7";
                    case "partName": return "\u914d\u4ef6\u540d\u79f0";
                    case "group": return "\u5206\u7ec4";
                }
        }
        return key;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
