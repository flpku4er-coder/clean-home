# Чистый экран — свой лаунчер для Xiaomi TV (Google TV)

Часы, дата, погода (El Campello, Open-Meteo) и ряд избранных приложений. Без рекламы, без рекомендаций.

- **OK** на плитке — открыть приложение
- **Долгое OK** на главном — убрать плитку
- **Все приложения** → долгое OK — добавить на главный (★)
- Первый запуск: на главный сами попадают YouTube, Netflix, Prime, Disney+, Max, Spotify и всё с «rezka» в названии, если установлено

Координаты погоды — в начале `MainActivity.java` (`LAT`, `LON`).

---

## Установка (без компьютера)

APK собирается автоматически на GitHub после каждого изменения.

1. На телевизоре установить **Downloader** (AFTVnews) из Google Play.
2. Открыть Downloader и ввести адрес:
   `github.com/flpku4er-coder/clean-home/releases/download/latest/clean-home.apk`
3. Разрешить Downloader установку приложений, если ТВ спросит → «Установить» → «Открыть».

## Обновление (с мака, ADB)

```bash
cd ~/platform-tools
./adb connect 192.168.1.137
curl -LO https://github.com/flpku4er-coder/clean-home/releases/download/latest/clean-home.apk && ./adb install -r clean-home.apk
```

## Фоновое видео

Ссылка на исходный mp4 — в `videos/bg.txt`. После изменения GitHub сам пережимает его в 720p/24fps
и выкладывает в `releases/download/video/bg.mp4`. Поставить на ТВ (на маке, в `~/platform-tools`):
```bash
curl -L -o bg.mp4 https://github.com/flpku4er-coder/clean-home/releases/download/video/bg.mp4 && ./adb push bg.mp4 /sdcard/Android/data/com.serhii.cleanhome/files/bg.mp4
```
Убрать видео: `./adb shell rm /sdcard/Android/data/com.serhii.cleanhome/files/bg.mp4`

## Как настроен телевизор (Xiaomi, Android TV)

Отключён старый лаунчер Google, Home открывает «Чистый экран»:
```bash
./adb shell pm disable-user --user 0 com.google.android.tvlauncher
```

PatchWall (`com.mitv.tvhome.atv`) **не отключать**: в нём живёт окно выбора HDMI.

Кнопка входов на пульте шлёт `KEY_RECORD`; её перехватывает служба `KeyService` и открывает выбор HDMI:
```bash
./adb shell settings put secure enabled_accessibility_services com.serhii.cleanhome/com.serhii.cleanhome.KeyService
./adb shell settings put secure accessibility_enabled 1
```

### Вернуть всё как было
```bash
./adb shell pm enable com.google.android.tvlauncher
./adb shell settings put secure enabled_accessibility_services ""
./adb uninstall com.serhii.cleanhome
```

---

## Дальше
- Домашний дашборд — см. `IDEAS.md`
