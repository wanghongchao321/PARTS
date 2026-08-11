# PartsSystem Android App

This Android project implements the requirements in `技术说明文档.docx`.

## Features

- Parts query page:
  - Select vehicle / assembly.
  - Show the selected vehicle model, short name, and assembly code below the selector.
  - Search controls and result cards use wrapping layout to avoid long French text, part numbers, and assembly codes overflowing.
  - Search by part name, drawing number, or group.
  - Show drawing number, part name, quantity, note, and group.
  - Copy one part record with one tap, including model, short name, and assembly code.
- Scan query page:
  - Camera barcode / QR scanning through CameraX and ML Kit.
  - Manual code entry is also supported.
  - The scanned lookup key follows the document rule: second character through the sixth character from the end.
  - Fuzzy lookup is supported against scan key and assembly code. VIN field lookup is intentionally not used.
  - Scan results show the matched vehicle model, short name, and assembly code.
- Language switch:
  - Chinese, English, and French.
  - App queries `v_parts_zh`, `v_parts_en`, and `v_parts_fr` from the bundled SQLite database.

## Project Layout

- `app/src/main/assets/parts_android.db`: Android SQLite database.
- `app/src/main/java/com/partssystem/app/MainActivity.java`: Query UI.
- `app/src/main/java/com/partssystem/app/ScanActivity.java`: Camera scanner.
- `app/src/main/java/com/partssystem/app/PartsDatabase.java`: SQLite access.

## Build

Open this folder in Android Studio:

`C:\Users\wangh\Desktop\partssystem\android_app`

Then run:

1. Let Android Studio sync Gradle dependencies.
2. Connect an Android device or start an emulator.
3. Click Run.

This machine currently has no Java, Gradle, Android SDK, or adb command available in PATH, so local APK compilation was not performed here.

## Updating Data

After rebuilding:

`C:\Users\wangh\Desktop\partssystem\basic data\parts_android.db`

copy it to:

`C:\Users\wangh\Desktop\partssystem\android_app\app\src\main\assets\parts_android.db`

The app refreshes the bundled database on startup.
