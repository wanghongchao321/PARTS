package com.partssystem.app;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

    List<PartItem> searchParts(String language, String assemblyCode, String keyword, String group1, String group2, int limit) {
        String view = viewName(language);
        String like = "%" + safe(keyword) + "%";
        List<String> args = new ArrayList<>();
        args.add(assemblyCode);
        StringBuilder where = new StringBuilder(" WHERE assembly_code=?");
        if (!safe(keyword).isEmpty()) {
            where.append(" AND (name LIKE ? OR part_no LIKE ? OR group1 LIKE ? OR group2 LIKE ? OR group3 LIKE ?)");
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
        }
        if (!safe(group1).isEmpty()) {
            where.append(" AND group1=?");
            args.add(group1);
        }
        if (!safe(group2).isEmpty()) {
            where.append(" AND group2=?");
            args.add(group2);
        }
        args.add(String.valueOf(limit));
        String sql = "SELECT assembly_code, assembly_year_code, vin, scan_lookup_code, part_no, name, quantity, note, group1, group2, group3 " +
                "FROM " + view + where +
                " ORDER BY CASE WHEN " + standardPartCondition() + " THEN 1 ELSE 0 END, group1, group2, name LIMIT ?";
        return queryParts(sql, args.toArray(new String[0]));
    }

    List<String> group1Options(String language, String assemblyCode) {
        return distinctValues(viewName(language), "group1", "assembly_code=? AND group1<>''", new String[]{assemblyCode});
    }

    List<String> group2Options(String language, String assemblyCode, String group1) {
        List<String> args = new ArrayList<>();
        args.add(assemblyCode);
        String where = "assembly_code=? AND group2<>''";
        if (!safe(group1).isEmpty()) {
            where += " AND group1=?";
            args.add(group1);
        }
        return distinctValues(viewName(language), "group2", where, args.toArray(new String[0]));
    }

    private List<String> distinctValues(String view, String column, String where, String[] args) {
        List<String> values = new ArrayList<>();
        Cursor c = db.rawQuery(
                "SELECT DISTINCT " + column + " FROM " + view + " WHERE " + where + " ORDER BY " + column,
                args
        );
        try {
            while (c.moveToNext()) {
                String value = safe(c.getString(0));
                if (!value.isEmpty()) values.add(value);
            }
        } finally {
            c.close();
        }
        return values;
    }

    List<VehicleInfo> findVehiclesByScannedPartNo(String language, String scannedCode, boolean fuzzy, boolean fromScanner) {
        String raw = safe(scannedCode).trim();
        if (raw.isEmpty()) {
            return new ArrayList<>();
        }
        List<VehicleInfo> infos = fuzzy ? fuzzyFindByPartNo(language, raw, 50) : exactFindByPartNo(language, raw, 100);
        String trimmedBoxCode = fromScanner ? normalizeScan(raw) : raw;
        if (!infos.isEmpty() || trimmedBoxCode.equals(raw)) {
            return infos;
        }
        return fuzzy ? fuzzyFindByPartNo(language, trimmedBoxCode, 50) : exactFindByPartNo(language, trimmedBoxCode, 100);
    }

    private List<VehicleInfo> exactFindByPartNo(String language, String code, int limit) {
        Cursor c = db.rawQuery(
                joinedPartSql(viewName(language)) + " WHERE UPPER(p.part_no)=? ORDER BY p.assembly_code, p.part_no LIMIT ?",
                new String[]{normalizePartCodeForSql(code), String.valueOf(limit)}
        );
        try {
            List<VehicleInfo> infos = groupVehicleInfos(c, code);
            return infos.isEmpty() ? normalizedExactFindByPartNo(language, code, limit) : infos;
        } finally {
            c.close();
        }
    }

    private List<VehicleInfo> normalizedExactFindByPartNo(String language, String code, int limit) {
        String needle = normalizePartCode(code);
        List<VehicleInfo> infos = new ArrayList<>();
        Map<String, VehicleInfo> byAssembly = new HashMap<>();
        Cursor c = db.rawQuery(joinedPartSql(viewName(language)) + " ORDER BY p.part_no", null);
        try {
            while (c.moveToNext()) {
                PartItem part = partFromCursor(c);
                if (!normalizePartCode(part.partNo).equals(needle)) continue;
                VehicleInfo info = byAssembly.get(part.assemblyCode);
                if (info == null) {
                    if (infos.size() >= limit) break;
                    info = new VehicleInfo(part.assemblyCode, part.assemblyYearCode, part.vin, code, vehicleFromCursor(c));
                    byAssembly.put(part.assemblyCode, info);
                    infos.add(info);
                }
                info.parts.add(part);
            }
        } finally {
            c.close();
        }
        return infos;
    }

    private List<VehicleInfo> fuzzyFindByPartNo(String language, String code, int limit) {
        String needle = normalizePartCode(code);
        if (needle.isEmpty()) {
            return new ArrayList<>();
        }
        List<ScoredPart> scored = new ArrayList<>();
        Cursor c = db.rawQuery(joinedPartSql(viewName(language)) + " ORDER BY p.part_no", null);
        try {
            while (c.moveToNext()) {
                PartItem part = partFromCursor(c);
                String haystack = normalizePartCode(part.partNo);
                if (haystack.isEmpty()) continue;
                int distance = fuzzyDistance(needle, haystack);
                int threshold = Math.max(2, Math.min(6, needle.length() / 4));
                if (distance <= threshold || haystack.contains(needle) || needle.contains(haystack)) {
                    scored.add(new ScoredPart(part, vehicleFromCursor(c), distance, Math.abs(haystack.length() - needle.length())));
                }
            }
        } finally {
            c.close();
        }
        scored.sort((a, b) -> {
            if (a.distance != b.distance) return a.distance - b.distance;
            if (a.lengthDelta != b.lengthDelta) return a.lengthDelta - b.lengthDelta;
            return a.part.partNo.compareTo(b.part.partNo);
        });

        List<VehicleInfo> infos = new ArrayList<>();
        Map<String, VehicleInfo> byAssembly = new HashMap<>();
        for (ScoredPart candidate : scored) {
            if (infos.size() >= limit) break;
            String assembly = candidate.part.assemblyCode;
            VehicleInfo info = byAssembly.get(assembly);
            if (info == null) {
                info = new VehicleInfo(assembly, candidate.part.assemblyYearCode, candidate.part.vin, code, candidate.model);
                byAssembly.put(assembly, info);
                infos.add(info);
            }
            info.parts.add(candidate.part);
        }
        return infos;
    }

    List<PartItem> partsByAssembly(String language, String assemblyCode, int limit) {
        String sql = "SELECT assembly_code, assembly_year_code, vin, scan_lookup_code, part_no, name, quantity, note, group1, group2, group3 " +
                "FROM " + viewName(language) + " WHERE assembly_code=? ORDER BY group1, group2, name LIMIT ?";
        return queryParts(sql, new String[]{assemblyCode, String.valueOf(limit)});
    }

    static String normalizeScan(String raw) {
        String code = safe(raw).trim();
        if (isBoxCodeWithSerialSuffix(code)) {
            return code.substring(1, code.length() - 5);
        }
        return code;
    }

    private static boolean isBoxCodeWithSerialSuffix(String code) {
        if (code.length() <= 6) return false;
        int suffixStart = code.length() - 5;
        if (!Character.isLetter(code.charAt(suffixStart))) return false;
        for (int i = suffixStart + 1; i < code.length(); i++) {
            if (!Character.isDigit(code.charAt(i))) return false;
        }
        return true;
    }

    private String joinedPartSql(String view) {
        return "SELECT p.assembly_code, p.assembly_year_code, p.vin, p.scan_lookup_code, " +
                "p.part_no, p.name, p.quantity, p.note, p.group1, p.group2, p.group3, " +
                "vm.short_name_zh, vm.model_name_zh, vm.short_name_en, vm.model_name_en, vm.short_name_fr, vm.model_name_fr " +
                "FROM " + view + " p LEFT JOIN vehicle_models vm ON vm.assembly_code=p.assembly_code";
    }

    private List<VehicleInfo> groupVehicleInfos(Cursor c, String code) {
        List<VehicleInfo> infos = new ArrayList<>();
        Map<String, VehicleInfo> byAssembly = new HashMap<>();
        while (c.moveToNext()) {
            PartItem part = partFromCursor(c);
            VehicleInfo info = byAssembly.get(part.assemblyCode);
            if (info == null) {
                info = new VehicleInfo(part.assemblyCode, part.assemblyYearCode, part.vin, code, vehicleFromCursor(c));
                byAssembly.put(part.assemblyCode, info);
                infos.add(info);
            }
            info.parts.add(part);
        }
        return infos;
    }

    private PartItem partFromCursor(Cursor c) {
        return new PartItem(
                c.getString(0), c.getString(1), c.getString(2), c.getString(3),
                c.getString(4), c.getString(5), c.getString(6), c.getString(7),
                c.getString(8), c.getString(9), c.getString(10)
        );
    }

    private VehicleModel vehicleFromCursor(Cursor c) {
        return new VehicleModel(
                c.getString(0),
                c.getString(11), c.getString(12),
                c.getString(13), c.getString(14),
                c.getString(15), c.getString(16)
        );
    }

    private static String normalizePartCodeForSql(String value) {
        return safe(value).trim().toUpperCase(Locale.US);
    }

    private static String normalizePartCode(String value) {
        return safe(value).trim().toUpperCase(Locale.US).replaceAll("[^A-Z0-9]", "");
    }

    private static int fuzzyDistance(String query, String target) {
        if (target.contains(query) || query.contains(target)) return 0;
        if (query.length() <= 63) return myersDistance(query, target);
        return levenshteinDistance(query, target);
    }

    private static int myersDistance(String pattern, String text) {
        int m = pattern.length();
        if (m == 0) return text.length();
        long[] masks = new long[128];
        for (int i = 0; i < m; i++) {
            char ch = pattern.charAt(i);
            if (ch < masks.length) {
                masks[ch] |= 1L << i;
            }
        }
        long vp = ~0L;
        long vn = 0L;
        int score = m;
        long topBit = 1L << (m - 1);
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            long pm = ch < masks.length ? masks[ch] : 0L;
            long x = pm | vn;
            long d0 = (((x & vp) + vp) ^ vp) | x;
            long hp = vn | ~(d0 | vp);
            long hn = vp & d0;
            if ((hp & topBit) != 0) score++;
            if ((hn & topBit) != 0) score--;
            hp = (hp << 1) | 1L;
            hn <<= 1;
            vp = hn | ~(d0 | hp);
            vn = hp & d0;
        }
        return score;
    }

    private static int levenshteinDistance(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[b.length()];
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

    private static String standardPartCondition() {
        return "name LIKE '%螺栓%' OR name LIKE '%螺母%' OR name LIKE '%垫片%' " +
                "OR lower(name) LIKE '%bolt%' OR lower(name) LIKE '%nut%' OR lower(name) LIKE '%washer%' " +
                "OR lower(name) LIKE '%boulon%' OR lower(name) LIKE '%ecrou%' OR lower(name) LIKE '%écrou%' OR lower(name) LIKE '%rondelle%'";
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static final class ScoredPart {
        final PartItem part;
        final VehicleModel model;
        final int distance;
        final int lengthDelta;

        ScoredPart(PartItem part, VehicleModel model, int distance, int lengthDelta) {
            this.part = part;
            this.model = model;
            this.distance = distance;
            this.lengthDelta = lengthDelta;
        }
    }
}
