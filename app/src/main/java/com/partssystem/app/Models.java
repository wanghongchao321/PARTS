package com.partssystem.app;

import java.util.ArrayList;
import java.util.List;

final class VehicleModel {
    final String assemblyCode;
    final String shortNameZh;
    final String modelNameZh;
    final String shortNameEn;
    final String modelNameEn;
    final String shortNameFr;
    final String modelNameFr;

    VehicleModel(String assemblyCode, String shortNameZh, String modelNameZh,
                 String shortNameEn, String modelNameEn, String shortNameFr, String modelNameFr) {
        this.assemblyCode = value(assemblyCode);
        this.shortNameZh = value(shortNameZh);
        this.modelNameZh = value(modelNameZh);
        this.shortNameEn = value(shortNameEn);
        this.modelNameEn = value(modelNameEn);
        this.shortNameFr = value(shortNameFr);
        this.modelNameFr = value(modelNameFr);
    }

    String display(String lang) {
        String model = modelName(lang);
        String shortName = shortName(lang);
        String title = join(" / ", model, shortName);
        return title.isEmpty() ? assemblyCode : title + " (" + assemblyCode + ")";
    }

    String modelName(String lang) {
        if ("en".equals(lang)) return modelNameEn.isEmpty() ? modelNameZh : modelNameEn;
        if ("fr".equals(lang)) return modelNameFr.isEmpty() ? modelNameZh : modelNameFr;
        return modelNameZh;
    }

    String shortName(String lang) {
        if ("en".equals(lang)) return shortNameEn.isEmpty() ? shortNameZh : shortNameEn;
        if ("fr".equals(lang)) return shortNameFr.isEmpty() ? shortNameZh : shortNameFr;
        return shortNameZh;
    }

    private static String join(String sep, String a, String b) {
        if (!a.isEmpty() && !b.isEmpty()) return a + sep + b;
        return !a.isEmpty() ? a : b;
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }
}

final class PartItem {
    final String assemblyCode;
    final String assemblyYearCode;
    final String vin;
    final String scanLookupCode;
    final String partNo;
    final String name;
    final String quantity;
    final String note;
    final String group1;
    final String group2;
    final String group3;

    PartItem(String assemblyCode, String assemblyYearCode, String vin, String scanLookupCode,
             String partNo, String name, String quantity, String note,
             String group1, String group2, String group3) {
        this.assemblyCode = value(assemblyCode);
        this.assemblyYearCode = value(assemblyYearCode);
        this.vin = value(vin);
        this.scanLookupCode = value(scanLookupCode);
        this.partNo = value(partNo);
        this.name = value(name);
        this.quantity = value(quantity);
        this.note = value(note);
        this.group1 = value(group1);
        this.group2 = value(group2);
        this.group3 = value(group3);
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }
}

final class VehicleInfo {
    final String assemblyCode;
    final String assemblyYearCode;
    final String vin;
    final String scanLookupCode;
    final VehicleModel model;
    final List<PartItem> parts = new ArrayList<>();

    VehicleInfo(String assemblyCode, String assemblyYearCode, String vin, String scanLookupCode, VehicleModel model) {
        this.assemblyCode = assemblyCode == null ? "" : assemblyCode;
        this.assemblyYearCode = assemblyYearCode == null ? "" : assemblyYearCode;
        this.vin = vin == null ? "" : vin;
        this.scanLookupCode = scanLookupCode == null ? "" : scanLookupCode;
        this.model = model;
    }
}
