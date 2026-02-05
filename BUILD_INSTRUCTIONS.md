# Инструкция по сборке проекта после изменений UI

## Шаг 1: Синхронизация проекта

1. Откройте проект в Android Studio
2. Выполните синхронизацию Gradle:
   - **File → Sync Project with Gradle Files**
   - Или нажмите кнопку "Sync Now" если появится уведомление

## Шаг 2: Очистка и пересборка

1. Очистите проект:
   ```
   Build → Clean Project
   ```

2. Пересоберите проект:
   ```
   Build → Rebuild Project
   ```

## Шаг 3: Проверка импортов

Если после синхронизации остаются ошибки импортов:

1. Откройте файлы с ошибками
2. Поставьте курсор на красное подчеркивание
3. Нажмите **Alt + Enter** (Windows/Linux) или **Option + Enter** (Mac)
4. Выберите "Import class"

## Шаг 4: Invalidate Caches (если нужно)

Если проблемы сохраняются:

1. **File → Invalidate Caches / Restart...**
2. Выберите "Invalidate and Restart"
3. Дождитесь перезапуска Android Studio

## Проверка созданных файлов

### Адаптеры (должны быть в `app/src/main/java/com/yaros/RadioUrl/adapters/`)
- ✅ RadioStationAdapter.kt
- ✅ RecommendedStationAdapter.kt
- ✅ CategoryAdapter.kt
- ✅ CategoryGridAdapter.kt
- ✅ NewsAdapter.kt

### Модели (должны быть в `app/src/main/java/com/yaros/RadioUrl/models/`)
- ✅ RadioStation.kt
- ✅ CategoryWithCount.kt

### Layout файлы (должны быть в `app/src/main/res/layout/`)
- ✅ item_radio_station.xml (уже был)
- ✅ item_recommended_station.xml
- ✅ item_category.xml
- ✅ item_category_grid.xml
- ✅ item_news.xml
- ✅ fragment_catalog.xml (обновлен)
- ✅ fragment_category_stations.xml (обновлен)

### Drawable ресурсы (должны быть в `app/src/main/res/drawable/`)
- ✅ gradient_overlay.xml
- ✅ ic_station_placeholder.xml
- ✅ ic_chevron_left_24dp.xml

## Возможные проблемы и решения

### Проблема 1: "Unresolved reference 'adapters'"

**Решение:**
1. Убедитесь, что папка `adapters` создана в правильном месте
2. Выполните Sync Project with Gradle Files
3. Если не помогло - Invalidate Caches

### Проблема 2: "Cannot resolve symbol 'R'"

**Решение:**
1. Build → Clean Project
2. Build → Rebuild Project
3. Проверьте, нет ли ошибок в XML файлах

### Проблема 3: Ошибки в layout файлах

**Решение:**
1. Откройте каждый layout файл
2. Проверьте, что все ID корректны
3. Убедитесь, что используются правильные виджеты

### Проблема 4: "Unresolved reference" для Station

**Причина:** В проекте есть два класса Station:
- `com.yaros.RadioUrl.data.Station` (API модель)
- `com.yaros.RadioUrl.core.Station` (Core модель)

**Решение:** Используйте алиасы импорта:
```kotlin
import com.yaros.RadioUrl.data.Station as ApiStation
import com.yaros.RadioUrl.core.Station
```

## Тестирование

После успешной сборки:

1. Запустите приложение
2. Перейдите в раздел "Каталог"
3. Проверьте загрузку станций из API
4. Попробуйте добавить станцию в коллекцию
5. Проверьте категории

## Дополнительные настройки

### Добавление библиотеки для загрузки изображений

В `app/build.gradle` добавьте одну из библиотек:

**Glide (рекомендуется):**
```gradle
dependencies {
    implementation 'com.github.bumptech.glide:glide:4.16.0'
    annotationProcessor 'com.github.bumptech.glide:compiler:4.16.0'
}
```

**Или Coil:**
```gradle
dependencies {
    implementation 'io.coil-kt:coil:2.5.0'
}
```

### Реализация imageLoader

После добавления библиотеки, обновите imageLoader в фрагментах:

**С Glide:**
```kotlin
imageLoader = { imageView, url ->
    Glide.with(imageView.context)
        .load(url)
        .placeholder(R.drawable.ic_station_placeholder)
        .error(R.drawable.ic_station_placeholder)
        .into(imageView)
}
```

**С Coil:**
```kotlin
imageLoader = { imageView, url ->
    imageView.load(url) {
        placeholder(R.drawable.ic_station_placeholder)
        error(R.drawable.ic_station_placeholder)
    }
}
```

## Контрольный список

- [ ] Синхронизация Gradle выполнена
- [ ] Проект очищен и пересобран
- [ ] Все импорты корректны
- [ ] Нет ошибок компиляции
- [ ] Приложение запускается
- [ ] Каталог загружает станции
- [ ] Станции добавляются в коллекцию
- [ ] Категории отображаются корректно

## Поддержка

Если возникли проблемы:
1. Проверьте файл `UI_CHANGES_SUMMARY.md` для деталей изменений
2. Убедитесь, что все файлы созданы в правильных директориях
3. Проверьте логи Android Studio на наличие специфичных ошибок
