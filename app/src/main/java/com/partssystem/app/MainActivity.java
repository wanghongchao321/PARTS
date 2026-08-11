package com.partssystem.app;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.Size;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends ComponentActivity {
    private static final int REQ_SCAN = 101;
    private static final int REQ_CAMERA = 102;
    private static final int BG = 0xFFF8FAFC;
    private static final int CARD = 0xFFFFFFFF;
    private static final int LINE = 0xFFD9E0EA;
    private static final int TEXT = 0xFF101827;
    private static final int MUTED = 0xFF64748B;
    private static final int BLUE = 0xFF1456C8;
    private static final int SOFT = 0xFFEDF2F7;
    private static final float SCAN_LEFT_RATIO = 0.08f;
    private static final float SCAN_RIGHT_RATIO = 0.92f;
    private static final float SCAN_TOP_RATIO = 0.34f;
    private static final float SCAN_BOTTOM_RATIO = 0.66f;

    private PartsDatabase database;
    private final List<VehicleModel> models = new ArrayList<>();
    private String language = "zh";
    private LinearLayout root;
    private Spinner languageSpinner;
    private Spinner vehicleSpinner;
    private Spinner group1Spinner;
    private Spinner group2Spinner;
    private Button partsTabButton;
    private Button scanTabButton;
    private LinearLayout resultList;
    private EditText keywordInput;
    private EditText scanInput;
    private TextView pageTitle;
    private TextView vehicleDetailText;
    private ScrollView partsPageScroll;
    private ScrollView scanPageScroll;
    private final List<String> group1Values = new ArrayList<>();
    private final List<String> group2Values = new ArrayList<>();
    private boolean updatingGroupFilters = false;
    private FrameLayout scannerPanel;
    private View scanFieldLabel;
    private View scanSearchRow;
    private boolean lastScanFromCamera = false;
    private boolean inlineScanning = false;
    private ExecutorService cameraExecutor;
    private BarcodeScanner barcodeScanner;
    private ProcessCameraProvider cameraProvider;
    private int currentPage = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        cameraExecutor = Executors.newSingleThreadExecutor();
        BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                        Barcode.FORMAT_CODE_128,
                        Barcode.FORMAT_CODE_39,
                        Barcode.FORMAT_CODE_93,
                        Barcode.FORMAT_CODABAR,
                        Barcode.FORMAT_EAN_13,
                        Barcode.FORMAT_EAN_8,
                        Barcode.FORMAT_ITF,
                        Barcode.FORMAT_UPC_A,
                        Barcode.FORMAT_UPC_E
                )
                .build();
        barcodeScanner = BarcodeScanning.getClient(options);
        database = new PartsDatabase(this);
        database.open();
        models.addAll(database.vehicleModels());
        showMain();
    }

    @Override
    protected void onDestroy() {
        stopInlineScan();
        if (barcodeScanner != null) barcodeScanner.close();
        if (cameraExecutor != null) cameraExecutor.shutdown();
        database.close();
        super.onDestroy();
    }

    private void showMain() {
        root = vertical();
        root.setBackgroundColor(BG);
        setContentView(root);

        LinearLayout top = horizontal();
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(18), statusBarHeight() + dp(10), dp(18), dp(10));
        top.setBackgroundColor(CARD);
        LinearLayout titleBlock = vertical();
        TextView logo = label("FOTON", 15, true);
        logo.setTextColor(BLUE);
        pageTitle = label(text("appTitle"), 22, true);
        TextView subtitle = label("Parts Query", 13, false);
        subtitle.setTextColor(MUTED);
        titleBlock.addView(logo);
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
        stopInlineScan();
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
        ScrollView pageScroll = new ScrollView(this);
        pageScroll.setFillViewport(true);
        partsPageScroll = pageScroll;
        LinearLayout content = content();

        LinearLayout filterCard = card();
        filterCard.addView(fieldLabel(text("vehicleLabel")));
        vehicleSpinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, modelLabels());
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        vehicleSpinner.setAdapter(adapter);
        vehicleSpinner.setBackground(controlBg());
        vehicleSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateSelectedVehicleDetail();
                refreshGroupFilters();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        filterCard.addView(vehicleSpinner, new LinearLayout.LayoutParams(-1, dp(46)));

        vehicleDetailText = label("", 14, false);
        vehicleDetailText.setBackground(cardBg());
        vehicleDetailText.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams detailLp = new LinearLayout.LayoutParams(-1, -2);
        detailLp.setMargins(0, dp(8), 0, dp(8));
        filterCard.addView(vehicleDetailText, detailLp);

        LinearLayout groupRow = horizontal();
        groupRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout group1Box = vertical();
        group1Box.addView(fieldLabel(text("group1Label")));
        group1Spinner = new Spinner(this);
        group1Spinner.setBackground(controlBg());
        group1Spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!updatingGroupFilters) refreshGroup2Filter();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        group1Box.addView(group1Spinner, new LinearLayout.LayoutParams(-1, dp(46)));

        LinearLayout group2Box = vertical();
        group2Box.addView(fieldLabel(text("group2Label")));
        group2Spinner = new Spinner(this);
        group2Spinner.setBackground(controlBg());
        group2Box.addView(group2Spinner, new LinearLayout.LayoutParams(-1, dp(46)));

        groupRow.addView(group1Box, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams group2Lp = new LinearLayout.LayoutParams(0, -2, 1);
        group2Lp.setMargins(dp(8), 0, 0, 0);
        groupRow.addView(group2Box, group2Lp);
        filterCard.addView(groupRow);

        content.addView(filterCard);
        updateSelectedVehicleDetail();
        refreshGroupFilters();

        LinearLayout searchRow = horizontal();
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        keywordInput = input(text("keywordHint"));
        keywordInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) scrollInputIntoView(partsPageScroll, searchRow);
        });
        keywordInput.setOnClickListener(v -> scrollInputIntoView(partsPageScroll, searchRow));
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
        content.addView(resultList, new LinearLayout.LayoutParams(-1, -2));
        pageScroll.setClipToPadding(false);
        pageScroll.addView(content);
        root.addView(pageScroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setupKeyboardAwareInputScroll(pageScroll, searchRow, keywordInput, false);
    }

    private void renderScanPage() {
        ScrollView pageScroll = new ScrollView(this);
        pageScroll.setFillViewport(true);
        scanPageScroll = pageScroll;
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
                setScanCompact(true);
                scrollInputIntoView(scanPageScroll, scanSearchRow);
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        scanInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                setScanCompact(true);
                scrollInputIntoView(scanPageScroll, scanSearchRow);
            }
        });
        scanInput.setOnClickListener(v -> scrollInputIntoView(scanPageScroll, scanSearchRow));
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
        content.addView(resultList, new LinearLayout.LayoutParams(-1, -2));
        pageScroll.setClipToPadding(false);
        pageScroll.addView(content);
        root.addView(pageScroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setupKeyboardAwareInputScroll(pageScroll, scanSearchRow, scanInput, true);
    }

    private void updateSelectedVehicleDetail() {
        if (vehicleDetailText == null || models.isEmpty() || vehicleSpinner == null) return;
        vehicleDetailText.setText(vehicleDetail(models.get(Math.max(0, vehicleSpinner.getSelectedItemPosition()))));
    }

    private void refreshGroupFilters() {
        if (group1Spinner == null || group2Spinner == null || models.isEmpty() || vehicleSpinner == null) return;
        updatingGroupFilters = true;
        VehicleModel model = models.get(Math.max(0, vehicleSpinner.getSelectedItemPosition()));
        group1Values.clear();
        group1Values.add("");
        group1Values.addAll(database.group1Options(language, model.assemblyCode));
        ArrayAdapter<String> group1Adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, optionLabels(group1Values, text("allGroup1")));
        group1Adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        group1Spinner.setAdapter(group1Adapter);
        updatingGroupFilters = false;
        refreshGroup2Filter();
    }

    private void refreshGroup2Filter() {
        if (group2Spinner == null || models.isEmpty() || vehicleSpinner == null) return;
        VehicleModel model = models.get(Math.max(0, vehicleSpinner.getSelectedItemPosition()));
        String group1 = selectedGroup1();
        group2Values.clear();
        group2Values.add("");
        group2Values.addAll(database.group2Options(language, model.assemblyCode, group1));
        ArrayAdapter<String> group2Adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, optionLabels(group2Values, text("allGroup2")));
        group2Adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        group2Spinner.setAdapter(group2Adapter);
    }

    private String selectedGroup1() {
        if (group1Spinner == null || group1Values.isEmpty()) return "";
        int position = Math.max(0, group1Spinner.getSelectedItemPosition());
        return position < group1Values.size() ? group1Values.get(position) : "";
    }

    private String selectedGroup2() {
        if (group2Spinner == null || group2Values.isEmpty()) return "";
        int position = Math.max(0, group2Spinner.getSelectedItemPosition());
        return position < group2Values.size() ? group2Values.get(position) : "";
    }

    private List<String> optionLabels(List<String> values, String allText) {
        List<String> labels = new ArrayList<>();
        for (String value : values) {
            labels.add(value.isEmpty() ? allText : value);
        }
        return labels;
    }

    private void searchParts() {
        resultList.removeAllViews();
        if (models.isEmpty()) {
            resultList.addView(label(text("noData"), 16, false));
            return;
        }
        VehicleModel model = models.get(Math.max(0, vehicleSpinner.getSelectedItemPosition()));
        String keyword = keywordInput.getText().toString().trim();
        String group1 = selectedGroup1();
        String group2 = selectedGroup2();
        if (keyword.isEmpty() && group1.isEmpty() && group2.isEmpty()) {
            Toast.makeText(this, text("enterKeyword"), Toast.LENGTH_SHORT).show();
            return;
        }
        List<PartItem> parts = database.searchParts(language, model.assemblyCode, keyword, group1, group2, 200);
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
        setScanCompact(false);
        startInlineScan();
    }

    private void scrollInputIntoView(ScrollView pageScroll, View target) {
        if (pageScroll == null || target == null) return;
        pageScroll.postDelayed(() -> pageScroll.smoothScrollTo(0, Math.max(0, target.getTop() - dp(24))), 260);
    }

    private void setupKeyboardAwareInputScroll(ScrollView pageScroll, View target, EditText input, boolean compactScanOnKeyboard) {
        pageScroll.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            if ((currentPage == 0 && partsPageScroll != pageScroll) || (currentPage == 1 && scanPageScroll != pageScroll)) return;
            Rect visible = new Rect();
            pageScroll.getWindowVisibleDisplayFrame(visible);
            int screenHeight = pageScroll.getRootView().getHeight();
            int keyboardHeight = Math.max(0, screenHeight - visible.bottom);
            boolean keyboardVisible = keyboardHeight > dp(120);
            int bottomPadding = keyboardVisible ? keyboardHeight + dp(18) : 0;
            if (pageScroll.getPaddingBottom() != bottomPadding) {
                pageScroll.setPadding(0, 0, 0, bottomPadding);
            }
            if (keyboardVisible && input != null && input.hasFocus()) {
                if (compactScanOnKeyboard) setScanCompact(true);
                scrollInputIntoView(pageScroll, target);
            }
        });
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

    private void startInlineScan() {
        if (scannerPanel == null || inlineScanning) return;
        inlineScanning = true;
        scannerPanel.removeAllViews();

        PreviewView previewView = new PreviewView(this);
        scannerPanel.addView(previewView, new FrameLayout.LayoutParams(-1, -1));
        scannerPanel.addView(scanOverlay(), new FrameLayout.LayoutParams(-1, -1));

        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                cameraProvider = future.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(1280, 720))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
                analysis.setAnalyzer(cameraExecutor, this::analyzeBarcode);

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis);
            } catch (Exception e) {
                inlineScanning = false;
                scannerPanel.removeAllViews();
                scannerPanel.addView(scannerPlaceholder(), new FrameLayout.LayoutParams(-1, -1));
                Toast.makeText(this, text("cameraError"), Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void stopInlineScan() {
        inlineScanning = false;
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
            cameraProvider = null;
        }
    }

    @ExperimentalGetImage
    private void analyzeBarcode(@NonNull ImageProxy imageProxy) {
        if (!inlineScanning || imageProxy.getImage() == null) {
            imageProxy.close();
            return;
        }
        int rotation = imageProxy.getImageInfo().getRotationDegrees();
        int imageWidth = (rotation == 90 || rotation == 270) ? imageProxy.getHeight() : imageProxy.getWidth();
        int imageHeight = (rotation == 90 || rotation == 270) ? imageProxy.getWidth() : imageProxy.getHeight();
        InputImage image = InputImage.fromMediaImage(imageProxy.getImage(), rotation);
        barcodeScanner.process(image)
                .addOnSuccessListener(barcodes -> handleInlineBarcodes(barcodes, imageWidth, imageHeight))
                .addOnCompleteListener(task -> imageProxy.close());
    }

    private void handleInlineBarcodes(List<Barcode> barcodes, int imageWidth, int imageHeight) {
        if (!inlineScanning || barcodes.isEmpty()) return;
        Barcode barcode = firstBarcodeInsideScanArea(barcodes, imageWidth, imageHeight);
        if (barcode == null) return;
        String value = barcode.getRawValue();
        if (value == null || value.trim().isEmpty()) return;
        stopInlineScan();
        if (scannerPanel != null) {
            scannerPanel.removeAllViews();
            scannerPanel.addView(scannerPlaceholder(), new FrameLayout.LayoutParams(-1, -1));
        }
        String code = value.trim();
        if (scanInput != null) {
            scanInput.setText(code);
        }
        lastScanFromCamera = true;
        queryScan(code, false, true);
    }

    private Barcode firstBarcodeInsideScanArea(List<Barcode> barcodes, int imageWidth, int imageHeight) {
        for (Barcode barcode : barcodes) {
            Rect box = barcode.getBoundingBox();
            if (box == null) continue;
            int centerX = box.centerX();
            int centerY = box.centerY();
            boolean inside = centerX >= imageWidth * SCAN_LEFT_RATIO
                    && centerX <= imageWidth * SCAN_RIGHT_RATIO
                    && centerY >= imageHeight * SCAN_TOP_RATIO
                    && centerY <= imageHeight * SCAN_BOTTOM_RATIO;
            if (inside) return barcode;
        }
        return null;
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

    private FrameLayout scannerPreview() {
        FrameLayout scanner = new FrameLayout(this);
        scanner.setBackground(roundBg(0xFFA9825C, 10, 0));
        LinearLayout.LayoutParams scannerLp = new LinearLayout.LayoutParams(-1, dp(270));
        scannerLp.setMargins(0, 0, 0, dp(14));
        scanner.setLayoutParams(scannerLp);
        scanner.addView(scannerPlaceholder(), new FrameLayout.LayoutParams(-1, -1));
        return scanner;
    }

    private View scannerPlaceholder() {
        LinearLayout wrapper = vertical();
        wrapper.setGravity(Gravity.CENTER);
        wrapper.setPadding(dp(28), dp(32), dp(28), dp(32));
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
        TextView qr = label("BARCODE", 14, false);
        qr.setGravity(Gravity.CENTER);
        TextView made = label("PARTS MADE IN CHINA", 13, false);
        made.setTextColor(MUTED);
        made.setGravity(Gravity.CENTER);
        box.addView(code);
        box.addView(qr);
        box.addView(made);
        frame.addView(box, new LinearLayout.LayoutParams(-1, -2));
        wrapper.addView(frame, new LinearLayout.LayoutParams(-1, -1));
        return wrapper;
    }

    private View scanOverlay() {
        FrameLayout overlay = new FrameLayout(this);
        Button back = button(text("back"));
        back.setTextColor(Color.WHITE);
        back.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        back.setBackground(roundBg(0x99111827, 18, 0));
        back.setOnClickListener(v -> {
            stopInlineScan();
            if (scannerPanel != null) {
                scannerPanel.removeAllViews();
                scannerPanel.addView(scannerPlaceholder(), new FrameLayout.LayoutParams(-1, -1));
            }
        });
        FrameLayout.LayoutParams backLp = new FrameLayout.LayoutParams(dp(78), dp(38), Gravity.TOP | Gravity.LEFT);
        backLp.setMargins(dp(10), dp(10), 0, 0);
        overlay.addView(back, backLp);

        View frame = new View(this);
        frame.setBackground(roundStrokeBg(Color.TRANSPARENT, 8, 0xFF2D8CFF, 2));
        FrameLayout.LayoutParams frameLp = new FrameLayout.LayoutParams(-1, dp(86), Gravity.CENTER);
        frameLp.setMargins(dp(22), 0, dp(22), 0);
        overlay.addView(frame, frameLp);

        View line = new View(this);
        line.setBackgroundColor(0xFF2D8CFF);
        FrameLayout.LayoutParams lineLp = new FrameLayout.LayoutParams(-1, dp(3), Gravity.CENTER);
        lineLp.setMargins(dp(22), 0, dp(22), 0);
        overlay.addView(line, lineLp);
        TextView hint = label(text("barcodeOnly"), 13, true);
        hint.setTextColor(Color.WHITE);
        hint.setGravity(Gravity.CENTER);
        hint.setBackgroundColor(0x66000000);
        FrameLayout.LayoutParams hintLp = new FrameLayout.LayoutParams(-1, dp(42), Gravity.BOTTOM);
        overlay.addView(hint, hintLp);
        return overlay;
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
                    case "group1Label": return "Level-1 subgroup";
                    case "group2Label": return "Level-2 subgroup";
                    case "allGroup1": return "All level-1";
                    case "allGroup2": return "All level-2";
                    case "keywordHint": return "Enter part name or drawing number";
                    case "search": return "Search";
                    case "resultTitle": return "Search results";
                    case "scanHint": return "Scan or enter spare part drawing number";
                    case "scan": return "Scan";
                    case "back": return "Back";
                    case "barcodeOnly": return "Barcode only";
                    case "cameraError": return "Camera failed to start";
                    case "query": return "Query";
                    case "copy": return "Copy";
                    case "copied": return "Copied";
                    case "qty": return "Qty";
                    case "note": return "Note";
                    case "resultCount": return "%d results";
                    case "enterKeyword": return "Please enter a keyword";
                    case "enterScan": return "Please scan or enter a spare part drawing number";
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
                    case "partCode": return "Lookup drawing No";
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
                    case "group1Label": return "Sous-groupe 1";
                    case "group2Label": return "Sous-groupe 2";
                    case "allGroup1": return "Tous niveau 1";
                    case "allGroup2": return "Tous niveau 2";
                    case "keywordHint": return "Nom de piece ou numero de dessin";
                    case "search": return "Rechercher";
                    case "resultTitle": return "Resultats";
                    case "scanHint": return "Scanner ou saisir le numero de dessin de la piece";
                    case "scan": return "Scanner";
                    case "back": return "Retour";
                    case "barcodeOnly": return "Code-barres seulement";
                    case "cameraError": return "Impossible de demarrer la camera";
                    case "query": return "Rechercher";
                    case "copy": return "Copier";
                    case "copied": return "Copie";
                    case "qty": return "Qte";
                    case "note": return "Note";
                    case "resultCount": return "%d resultats";
                    case "enterKeyword": return "Veuillez saisir un mot-cle";
                    case "enterScan": return "Veuillez scanner ou saisir le numero de dessin de la piece";
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
                    case "partCode": return "Numero de dessin recherche";
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
                    case "group1Label": return "\u4e00\u7ea7\u5b50\u7ec4\u540d\u79f0";
                    case "group2Label": return "\u4e8c\u7ea7\u5b50\u7ec4\u540d\u79f0";
                    case "allGroup1": return "\u5168\u90e8\u4e00\u7ea7";
                    case "allGroup2": return "\u5168\u90e8\u4e8c\u7ea7";
                    case "keywordHint": return "\u8f93\u5165\u914d\u4ef6\u540d\u79f0\u6216\u56fe\u53f7";
                    case "search": return "\u641c\u7d22";
                    case "resultTitle": return "\u641c\u7d22\u7ed3\u679c";
                    case "scanHint": return "\u626b\u63cf\u6216\u8f93\u5165\u5907\u4ef6\u56fe\u53f7";
                    case "scan": return "\u626b\u7801";
                    case "back": return "\u8fd4\u56de";
                    case "barcodeOnly": return "\u4ec5\u652f\u6301\u6761\u5f62\u7801";
                    case "cameraError": return "\u76f8\u673a\u542f\u52a8\u5931\u8d25";
                    case "query": return "\u67e5\u8be2";
                    case "copy": return "\u590d\u5236";
                    case "copied": return "\u5df2\u590d\u5236";
                    case "qty": return "\u6570\u91cf";
                    case "note": return "\u8bf4\u660e";
                    case "resultCount": return "\u5171 %d \u6761";
                    case "enterKeyword": return "\u8bf7\u8f93\u5165\u5173\u952e\u8bcd";
                    case "enterScan": return "\u8bf7\u626b\u63cf\u6216\u8f93\u5165\u5907\u4ef6\u56fe\u53f7";
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
                    case "partCode": return "\u67e5\u8be2\u56fe\u53f7";
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

    private int statusBarHeight() {
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : 0;
    }
}
