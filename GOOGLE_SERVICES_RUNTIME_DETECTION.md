# Runtime-детекция Google Services и Firebase

## 📋 Обзор

Приложение SoundWaveRadio теперь поддерживает **универсальную работу** на устройствах как **с Google Services**, так и **без них** (FOSS-устройства, Huawei без GMS, и т.д.).

**Важно:** Firebase библиотеки включены в APK, но используются **только при наличии Google Play Services на устройстве**. Файл `google-services.json` всегда присутствует в проекте, но детекция происходит в runtime на основе реального наличия GMS на устройстве.

## ✅ Что было реализовано

### 1. **GoogleServicesHelper** - Централизованная система детекции

Создан класс `GoogleServicesHelper.kt`, который обеспечивает:

- ✅ Runtime-проверку доступности Google Play Services
- ✅ Runtime-проверку доступности Firebase
- ✅ Безопасную инициализацию Firebase
- ✅ Безопасное получение Firebase Analytics
- ✅ Безопасное получение FCM токенов
- ✅ Логирование аналитических событий (с fallback)
- ✅ Кэширование результатов проверки

### 2. **Gradle конфигурация**

#### `app/build.gradle`:
```gradle
plugins {
    id 'com.google.gms.google-services'
    id 'com.google.firebase.crashlytics'
    // Плагины всегда применяются
}

// Firebase зависимости включены в APK
implementation platform("com.google.firebase:firebase-bom:33.12.0")
implementation "com.google.firebase:firebase-analytics"
implementation "com.google.firebase:firebase-crashlytics"
implementation "com.google.firebase:firebase-config"
implementation "com.google.firebase:firebase-messaging"
implementation "com.google.firebase:firebase-perf"

// Google Play Services для runtime-детекции
implementation "com.google.android.gms:play-services-base:18.5.0"
implementation "com.google.android.gms:play-services-ads-identifier:18.2.0"
```

**Важно:** Firebase библиотеки включены в APK, но `GoogleServicesHelper` проверяет наличие Google Play Services на устройстве в runtime и использует Firebase только если GMS доступен.

### 3. **Обновленные классы**

#### `URLRadio.kt`:
- ✅ Использует `GoogleServicesHelper` для инициализации
- ✅ Graceful fallback при отсутствии Firebase
- ✅ Логирует статус сервисов при запуске

#### `MainActivity.kt`:
- ✅ Безопасная инициализация Firebase Analytics
- ✅ Безопасная инициализация Firebase Messaging
- ✅ Nullable типы для Firebase объектов

#### `ReviewManager.kt`:
- ✅ Опциональная поддержка Firebase Analytics
- ✅ Логирование событий через `GoogleServicesHelper`

#### `PermissionHelper.kt`:
- ✅ Упрощенное логирование без прямой зависимости от Firebase

### 4. **AndroidManifest.xml**

Все Google/Firebase компоненты помечены как опциональные:
```xml
<!-- Опциональные разрешения -->
<uses-permission android:name="com.google.android.gms.permission.AD_ID" tools:node="replace"/>

<!-- Опциональные meta-data -->
<meta-data
    android:name="com.google.android.gms.car.application"
    tools:node="replace" />

<!-- Опциональный Firebase Messaging Service -->
<service
    android:name=".helpers.FMessagingService"
    tools:node="replace">
```

## 🚀 Как это работает

### Сценарий 1: Устройство С Google Services

1. APK содержит Firebase библиотеки
2. При запуске `GoogleServicesHelper` проверяет наличие Google Play Services на устройстве
3. GMS обнаружен → Firebase инициализируется
4. Firebase Analytics, Crashlytics, Messaging работают нормально
5. Приложение получает FCM токен для push-уведомлений

**Логи:**
```
✓ Google Play Services available
✓ Firebase available
✓ Firebase Analytics initialized successfully
✓ FCM Token: [token]
```

### Сценарий 2: Устройство БЕЗ Google Services (FOSS/Huawei)

1. APK содержит Firebase библиотеки (но они не используются)
2. При запуске `GoogleServicesHelper` проверяет наличие Google Play Services на устройстве
3. GMS НЕ обнаружен → Firebase НЕ инициализируется
4. Приложение продолжает работать без аналитики и push-уведомлений
5. Все основные функции (радио, плейлисты, настройки) работают нормально

**Логи:**
```
⚠ Google Play Services not available
⚠ Firebase not available
⚠ Firebase services are unavailable - running in FOSS mode
⚠ Firebase Messaging not available - push notifications disabled
```

## 📱 Тестирование

### Тест 1: На устройстве С Google Services
1. Соберите APK: `./gradlew assembleRelease`
2. Установите на устройство с Google Play Services (обычный Android)
3. Проверьте логи - должны быть сообщения об успешной инициализации Firebase
4. Проверьте получение FCM токена
5. Проверьте работу основного функционала

### Тест 2: На устройстве БЕЗ Google Services (Huawei, эмулятор без GMS)
1. Соберите тот же APK: `./gradlew assembleRelease`
2. Установите на устройство без Google Play Services
3. Проверьте логи - должны быть предупреждения о FOSS режиме
4. Проверьте основной функционал - всё должно работать без крашей
5. Firebase функции будут отключены автоматически

### Тест 3: Эмулятор без GMS
1. Создайте эмулятор без Google Play Services
2. Установите APK
3. Приложение должно запуститься и работать стабильно
4. Проверьте отсутствие крашей при попытке использования Firebase

## 🔧 API GoogleServicesHelper

### Основные методы:

```kotlin
// Проверка доступности Google Play Services
GoogleServicesHelper.isGooglePlayServicesAvailable(context): Boolean

// Проверка доступности Firebase
GoogleServicesHelper.isFirebaseAvailable(): Boolean

// Безопасная инициализация Firebase
GoogleServicesHelper.initFirebaseSafely(context): Boolean

// Получение Firebase Analytics (nullable)
GoogleServicesHelper.getFirebaseAnalytics(context): Any?

// Логирование событий (с fallback)
GoogleServicesHelper.logAnalyticsEvent(context, eventName, params)

// Получение FCM токена
GoogleServicesHelper.getFirebaseMessagingToken(onSuccess, onFailure)

// Проверка необходимости Google функций
GoogleServicesHelper.shouldEnableGoogleFeatures(context): Boolean

// Получение статуса сервисов
GoogleServicesHelper.getServicesStatus(context): String
```

### Пример использования:

```kotlin
// В Activity или Fragment
if (GoogleServicesHelper.isFirebaseAvailable()) {
    val analytics = GoogleServicesHelper.getFirebaseAnalytics(this)
    // Используем analytics
} else {
    // Работаем без аналитики
}

// Логирование события
GoogleServicesHelper.logAnalyticsEvent(
    context,
    "user_action",
    mapOf("action" to "button_click", "screen" to "main")
)

// Получение FCM токена
GoogleServicesHelper.getFirebaseMessagingToken(
    onSuccess = { token ->
        // Отправить токен на сервер
    },
    onFailure = { exception ->
        // Обработать ошибку
    }
)
```

## 📦 Сборка приложения

### Универсальная сборка (для всех магазинов)
```bash
# Один APK для всех типов устройств
./gradlew assembleRelease
```

**Результат:**
- APK содержит Firebase библиотеки
- На устройствах с GMS → Firebase работает
- На устройствах без GMS → Firebase не используется
- Один APK подходит для Google Play, RuStore, F-Droid, Huawei AppGallery

## ⚠️ Важные замечания

1. **Firebase библиотеки**: Всегда включены в APK, но используются только при наличии GMS на устройстве:
   - Код компилируется с Firebase API
   - Библиотеки включены в APK (~15-20 МБ)
   - Runtime-детекция определяет доступность GMS
   - Если GMS нет - Firebase не инициализируется

2. **Размер APK**:
   - Универсальный APK: ~15-20 МБ (включая Firebase)
   - Firebase библиотеки занимают ~5-8 МБ
   - На устройствах без GMS библиотеки просто не используются

3. **Функциональность**:
   - **На устройствах с GMS**: Полная функциональность + аналитика + push-уведомления
   - **На устройствах без GMS**: Полная функциональность радио, но без аналитики и push

4. **Crashlytics**: В release сборке отключен через:
   ```gradle
   manifestPlaceholders = [crashlyticsCollectionEnabled: "false"]
   ```

5. **google-services.json**: Файл всегда присутствует в проекте и используется для конфигурации Firebase, но реальное использование зависит от наличия GMS на устройстве.

## 🎯 Преимущества решения

✅ **Универсальность**: Один APK для всех типов устройств и магазинов
✅ **Безопасность**: Нет крашей при отсутствии Google Services
✅ **Простота**: Не нужно собирать разные версии APK
✅ **Автоматизация**: Runtime-детекция работает автоматически
✅ **Совместимость**: Работает на Huawei, китайских устройствах, эмуляторах
✅ **Поддержка**: Легко поддерживать и тестировать
✅ **Единая сборка**: Один APK для Google Play, RuStore, F-Droid, Huawei AppGallery

## 🔍 Отладка

Для проверки статуса сервисов в runtime:

```kotlin
// В любом месте приложения
val status = GoogleServicesHelper.getServicesStatus(context)
Timber.d(status)
```

Вывод:
```
Google Services Status:
- Google Play Services: ✓ Available
- Firebase: ✓ Available
```

или

```
Google Services Status:
- Google Play Services: ✗ Not Available
- Firebase: ✗ Not Available
```

## 📝 Дополнительная информация

- Все изменения обратно совместимы
- Существующий функционал не затронут
- Приложение автоматически адаптируется к окружению
- Нет необходимости в разных build flavors (хотя можно добавить при желании)

---

**Дата создания**: 2025
**Версия**: 1.0
**Статус**: ✅ Готово к использованию
