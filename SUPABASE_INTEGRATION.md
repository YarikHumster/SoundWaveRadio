# Интеграция Supabase - Руководство

## Обзор изменений

Проект был переработан для использования Supabase вместо текущего API для получения данных о радиостанциях и категориях.

## Структура базы данных Supabase

### Таблица `Category`
```sql
CREATE TABLE Category (
    id INT PRIMARY KEY,
    category_name TEXT NOT NULL,
    category_image TEXT,
    category_status TEXT,
    featured BOOLEAN,
    last_update TIMESTAMP
);
```

Поля:
- `id` - ID категории (целое число)
- `category_name` - Название категории
- `category_image` - URL изображения категории (опционально)
- `category_status` - Статус категории (опционально)
- `featured` - Избранная категория (опционально)
- `last_update` - Дата последнего обновления (опционально)

### Таблица `Radio`
```sql
CREATE TABLE Radio (
    id INT PRIMARY KEY,
    category_id INT,
    radio_name TEXT NOT NULL,
    radio_image TEXT,
    radio_url TEXT NOT NULL,
    radio_status TEXT,
    view_count INT DEFAULT 0,
    featured BOOLEAN,
    type TEXT,
    last_update TIMESTAMP,
    FOREIGN KEY (category_id) REFERENCES Category(id)
);
```

Поля:
- `id` - ID радиостанции (целое число)
- `category_id` - ID категории (внешний ключ)
- `radio_name` - Название радиостанции
- `radio_image` - URL изображения (опционально)
- `radio_url` - URL потока радиостанции
- `radio_status` - Статус радиостанции (опционально)
- `view_count` - Количество просмотров
- `featured` - Избранная станция (опционально)
- `type` - Тип радиостанции (опционально)
- `last_update` - Дата последнего обновления (опционально)

## Конфигурация подключения

**URL:** `https://emxgttrptqqaywszojxu.supabase.co`
**API Key:** `sb_publishable_J9s-q-GiOq2g3v5EHmdYrA_IzVzFLVe`

Эти параметры находятся в файле `SupabaseClient.kt`.

## Добавленные файлы

### 1. `app/src/main/java/com/yaros/RadioUrl/core/supabase/SupabaseClient.kt`
Конфигурация клиента Supabase с установленными модулями Postgrest и Realtime.

### 2. `app/src/main/java/com/yaros/RadioUrl/core/supabase/SupabaseRepository.kt`
Основной репозиторий для работы с Supabase. Содержит методы:
- `getCategories()` - получить все категории
- `getAllStations()` - получить все радиостанции
- `getStationsByCategory(categoryName)` - получить станции по категории
- `searchStations(query)` - поиск радиостанций
- `getStationById(stationId)` - получить станцию по ID
- `updateStationViews(stationId, newViews)` - обновить просмотры
- `getCategoryById(categoryId)` - получить категорию по ID

### 3. `app/src/main/java/com/yaros/RadioUrl/core/supabase/SupabaseApiRepository.kt`
Адаптер для совместимости с существующим `ApiRepository`. Преобразует данные из Supabase в формат, используемый в приложении.

Методы:
- `getHomeData()` - данные для главного экрана
- `getCategories()` - список категорий
- `getRecentRadio()` - последние радиостанции
- `getCategoryDetail(categoryId, filter)` - станции категории
- `search(query)` - поиск станций
- `searchRTL(query)` - RTL поиск
- `updateStationViews(stationId)` - обновить просмотры

## Изменённые файлы

### 1. `app/build.gradle`
Добавлены зависимости:
```gradle
// Supabase
implementation platform('io.github.jan-tennert.supabase:bom:3.0.3')
implementation 'io.github.jan-tennert.supabase:postgrest-kt'
implementation 'io.github.jan-tennert.supabase:realtime-kt'
implementation 'io.ktor:ktor-client-android:3.0.3'
implementation 'org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3'
```

Добавлен плагин:
```gradle
id 'org.jetbrains.kotlin.plugin.serialization' version '2.1.10'
```

### 2. Fragment файлы
Обновлены для использования `SupabaseApiRepository`:
- `HomeFragment.kt`
- `CategoriesFragment.kt`
- `CategoryStationsFragment.kt`
- `CatalogFragment.kt`

Изменения:
```kotlin
// Было:
import com.yaros.RadioUrl.core.APIInterface.ApiClient
import com.yaros.RadioUrl.core.APIInterface.ApiRepository
private val apiRepository by lazy { ApiRepository(ApiClient.apiService) }

// Стало:
import com.yaros.RadioUrl.core.supabase.SupabaseApiRepository
private val apiRepository by lazy { SupabaseApiRepository() }
```

И обновлены типы результатов:
```kotlin
// Было:
is ApiRepository.NetworkResult.Success -> { ... }
is ApiRepository.NetworkResult.Error -> { ... }

// Стало:
is SupabaseApiRepository.NetworkResult.Success -> { ... }
is SupabaseApiRepository.NetworkResult.Error -> { ... }
```

## Следующие шаги

### 1. Синхронизация Gradle
После внесения изменений необходимо синхронизировать проект с Gradle:
- В Android Studio: File → Sync Project with Gradle Files
- Или нажмите кнопку "Sync Now" в верхней части экрана

### 2. Заполнение базы данных
Необходимо заполнить таблицы `category` и `radio` в Supabase данными.

Пример для категорий:
```sql
INSERT INTO Category (id, category_name, category_image, category_status, featured, last_update) VALUES
(1, 'Rock', 'https://example.com/rock.jpg', 'active', true, NOW()),
(2, 'Pop', 'https://example.com/pop.jpg', 'active', true, NOW()),
(3, 'Jazz', 'https://example.com/jazz.jpg', 'active', false, NOW());
```

Пример для радиостанций:
```sql
INSERT INTO Radio (id, category_id, radio_name, radio_image, radio_url, radio_status, view_count, featured, type, last_update) VALUES
(1, 1, 'Rock FM', 'https://example.com/rockfm.jpg', 'http://stream.rockfm.com/live', 'active', 0, true, 'live', NOW()),
(2, 2, 'Pop Radio', 'https://example.com/popradio.jpg', 'http://stream.popradio.com/live', 'active', 0, false, 'live', NOW());
```

### 3. Настройка прав доступа в Supabase
Убедитесь, что в Supabase настроены правильные политики доступа (RLS - Row Level Security):

```sql
-- Разрешить чтение всем
CREATE POLICY "Allow public read access" ON Category FOR SELECT USING (true);
CREATE POLICY "Allow public read access" ON Radio FOR SELECT USING (true);

-- Разрешить обновление просмотров
CREATE POLICY "Allow update views" ON Radio FOR UPDATE USING (true);
```

### 4. Тестирование
После синхронизации и заполнения базы данных:
1. Запустите приложение
2. Проверьте загрузку категорий на главном экране
3. Проверьте загрузку радиостанций
4. Проверьте поиск
5. Проверьте переход в категории

## Отладка

Если возникают проблемы:

1. **Проверьте логи**: Все ошибки логируются с тегами:
   - `SupabaseRepository`
   - `SupabaseApiRepository`
   - `HomeFragment`
   - `CategoriesFragment`
   - `CategoryStationsFragment`
   - `CatalogFragment`

2. **Проверьте подключение к Supabase**:
   - URL и API Key корректны
   - Таблицы существуют
   - Политики доступа настроены

3. **Проверьте структуру данных**:
   - Поля в таблицах соответствуют DTO классам
   - Данные в правильном формате

## Возврат к старому API (если нужно)

Если нужно вернуться к старому API, просто измените импорты обратно:

```kotlin
// В каждом Fragment:
import com.yaros.RadioUrl.core.APIInterface.ApiClient
import com.yaros.RadioUrl.core.APIInterface.ApiRepository
private val apiRepository by lazy { ApiRepository(ApiClient.apiService) }

// И верните типы результатов:
is ApiRepository.NetworkResult.Success -> { ... }
is ApiRepository.NetworkResult.Error -> { ... }
```

## Преимущества Supabase

1. **Реальное время**: Возможность подписки на изменения данных
2. **Простота**: Не нужен отдельный backend сервер
3. **Масштабируемость**: Автоматическое масштабирование
4. **Безопасность**: Встроенная аутентификация и авторизация
5. **Бесплатный план**: Достаточно для разработки и небольших проектов

## Контакты и поддержка

При возникновении вопросов обращайтесь к документации:
- [Supabase Documentation](https://supabase.com/docs)
- [Supabase Kotlin Client](https://github.com/supabase-community/supabase-kt)
