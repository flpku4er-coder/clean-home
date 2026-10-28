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

Пока «Чистый экран» — обычное приложение, кнопка Home ещё ведёт в Google TV.

## 3. Сделать его главным экраном (1 мин)

Посмотреть, какой лаунчер стоит сейчас:
```
adb shell cmd package resolve-activity -a android.intent.action.MAIN -c android.intent.category.HOME
```

Для Google TV отключить штатный лаунчер и его «помощника»:
```
adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
adb shell pm disable-user --user 0 com.google.android.tungsten.setupwraith
```
Если первая команда показала другой пакет (например, оболочку Xiaomi) — отключить его тем же `pm disable-user`.

Нажать Home на пульте → выбрать «Чистый экран» → «Всегда».

### Вернуть всё как было
```
adb shell pm enable com.google.android.apps.tv.launcherx
adb shell pm enable com.google.android.tungsten.setupwraith
adb uninstall com.serhii.cleanhome
```

Ничего не удаляется, только выключается. Обновления ТВ иногда включают штатный лаунчер обратно — тогда повторить шаг 3.

---

## Дальше (этап 2)
- AI-ассистент (Claude API + TMDB: «что посмотреть сегодня»)
- Пульт и записки с телефона (веб-страница на Vercel + realtime)
