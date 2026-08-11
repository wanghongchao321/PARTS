package com.partssystem.app;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

final class PartsDatabase {
    private static final String ASSET_DB = "parts_android.db";
    private static final String DB_NAME = "parts_android.db";

    private final Context context;
    private SQLiteDatabase db;

    PartsDatabase(Context context) {
        this.context = context.getApplicationContext();
    }

    void open() {
        try {
            File target = context.getDatabasePath(DB_NAME);
            File parent = target.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            try (InputStream in = context.getAssets().open(ASSET_DB);
                 FileOutputStream out = new FileOutputStream(target, false)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }
            db = SQLiteDatabase.openDatabase(target.getPath(), null, SQLiteDatabase.OPEN_READONLY);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot open database", e);
        }
    }

    void close() {
        if (db != null) {
            db.close();
            db = null;
        }
    }

    List<VehicleModel> vehicleModels() {
        List<VehicleModel> models = new ArrayList<>();
        Cursor c = db.rawQuery(
                "SELECT assembly_code, short_name_zh, model_name_zh, short_name_en, model_name_en, short_name_fr, model_name_fr " +
                        "FROM vehicle_models ORDER BY assembly_code",
                null
        );
        try {
            while (c.moveToNext()) {
                models.add(new VehicleModel(
                        c.getString(0), c.getString(1), c.getString(2),
                        c.getString(3), c.getString(4), c.getString(5), c.getString(6)
                ));
            }
        } finally {
            c.close();
        }
        return models;
    }

    List<PartItem> searchParts(String language, String assemblyCode, String keyword, int limit) {
        String view = viewName(language);
        String like = "%" + safe(keyword) + "%";
        String sql = "SELECT assembly_code, assembly_year_code, vin, scan_lookup_code, part_no, name, quantity, note, group1, group2, group3 " +
                "FROM " + view + " WHERE assembly_code=? AND (name LIKE ? OR part_no LIKE ? OR group1 LIKE ? OR group2 LIKE ? OR group3 LIKE ?) " +
                "ORDER BY group1, group2, name LIMIT ?";
        return queryParts(sql, new String[]{assemblyCode, like, like, like, like, like, String.valueOf(limit)});
    }

    VehicleInfo findVehicleByScanCode(String language, String scannedCode) {
        String raw = safe(scannedCode).trim();
        String code = normalizeScan(raw);
        String rawLike = "%" + raw + "%";
        String codeLike = "%" + code + "%";
        Cursor c = db.rawQuery(
                "SELECT a.assembly_code, a.assembly_year_code, a.vin, a.scan_lookup_code, " +
                        "vm.short_name_zh, vm.model_name_zh, vm.short_name_en, vm.model_name_en, vm.short_name_fr, vm.model_name_fr " +
                        "FROM assemblies a LEFT JOIN vehicle_models vm ON vm.id=a.vehicle_model_id " +
                        "WHERE a.scan_lookup_code LIKE ? OR a.scan_lookup_code LIKE ? OR a.assembly_code LIKE ? OR a.assembly_code LIKE ? LIMIT 1",
                new String[]{rawLike, codeLike, rawLike, codeLike}
        );
        try {
            if (!c.moveToFirst()) {
                return null;
            }
            VehicleModel model = new VehicleModel(
                    c.getString(0),
                    c.getString(4), c.getString(5),
                    c.getString(6), c.getString(7),
                    c.getString(8), c.getString(9)
            );
            VehicleInfo info = new VehicleInfo(c.getString(0), c.getString(1), c.getString(2), c.getString(3), model);
            info.parts.addAll(partsByAssembly(language, info.assemblyCode, 300));
            return info;
        } finally {
            c.close();
        }
    }

    List<PartItem> partsByAssembly(String language, String assemblyCode, int limit) {
        String sql = "SELECT assembly_code, assembly_year_code, vin, scan_lookup_code, part_no, name, quantity, note, group1, group2, group3 " +
                "FROM " + viewName(language) + " WHERE assembly_code=? ORDER BY group1, group2, name LIMIT ?";
        return queryParts(sql, new String[]{assemblyCode, String.valueOf(limit)});
    }

    static String normalizeScan(String raw) {
        String code = safe(raw).trim();
        if (code.length() > 7) {
            return code.substring(1, code.length() - 5);
        }
        return code;
    }

    private List<PartItem> queryParts(String sql, String[] args) {
        List<PartItem> items = new ArrayList<>();
        Cursor c = db.rawQuery(sql, args);
        try {
            while (c.moveToNext()) {
                items.add(new PartItem(
                        c.getString(0), c.getString(1), c.getString(2), c.getString(3),
                        c.getString(4), c.getString(5), c.getString(6), c.getString(7),
                        c.getString(8), c.getString(9), c.getString(10)
                ));
            }
        } finally {
            c.close();
        }
        return items;
    }

    private static String viewName(String language) {
        if ("en".equals(language)) return "v_parts_en";
        if ("fr".equals(language)) return "v_parts_fr";
        return "v_parts_zh";
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
