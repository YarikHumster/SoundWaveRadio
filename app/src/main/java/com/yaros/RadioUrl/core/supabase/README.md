# Supabase Integration

## Описание
Этот модуль обеспечивает интеграцию с Supabase для получения данных о радиостанциях и категориях.

## Структура базы данных

### Таблица `Category`
- `id` (Int) - ID категории
- `category_name` (String) - Название категории
- `category_image` (String?) - URL изображения категории
- `category_status` (String?) - Статус категории
- `featured` (Boolean?) - Избранная категория
- `last_update` (String?) - Дата последнего обновления

### Таблица `Radio`
- `id` (Int) - ID радиостанции
- `category_id` (Int?) - ID категории (внешний ключ)
- `radio_name` (String) - Название радиостанции
- `radio_image` (String?) - URL изображения
- `radio_url` (String) - URL потока
- `radio_status` (String?) - Статус радиостанции
- `view_count` (Int) - Количество просмотров
- `featured` (Boolean?) - Избранная станция
- `type` (String?) - Тип радиостанции
- `last_update` (String?) - Дата последнего обновления

## Использование

### Базовое использование SupabaseRepository

```kotlin
val repository = SupabaseRepository()

// Получить все категории
val categories = repository.getCategories()

// Получить все радиостанции
val stations = repository.getAllStations()

// Получить радиостанции по ID категории
val stationsByCategory = repository.getStationsByCategory(1)

// Поиск радиостанций
val searchResults = repository.searchStations("BBC")

// Обновить просмотры
repository.updateStationViews("station_id", 100)
```

### Использование SupabaseApiRepository (совместимость с ApiRepository)

```kotlin
val apiRepository = SupabaseApiRepository()

// Получить категории
when (val result = apiRepository.getCategories()) {
    is SupabaseApiRepository.NetworkResult.Success -> {
        val categories = result.data
        // Обработка данных
    }
    is SupabaseApiRepository.NetworkResult.Error -> {
        val error = result.message
        // Обработка ошибки
    }
}
```

## Замена существующего ApiRepository

Чтобы использовать Supabase вместо текущего API:

1. Замените создание `ApiRepository` на `SupabaseApiRepository`:
```kotlin
// Было:
val repository = ApiRepository(ApiClient.apiService)

// Стало:
val repository = SupabaseApiRepository()
```

2. Методы остаются теми же:
- `getCategories()`
- `getRecentRadio()`
- `getCategoryDetail(categoryId, filter)`
- `search(query)`
- `searchRTL(query)`
- `updateStationViews(stationId)`

## Конфигурация

Параметры подключения к Supabase находятся в `SupabaseClient.kt`:
- URL: https://emxgttrptqqaywszojxu.supabase.co
- Key: sb_publishable_J9s-q-GiOq2g3v5EHmdYrA_IzVzFLVe

## Зависимости

Добавлены в `build.gradle`:
```gradle
implementation platform('io.github.jan-tennert.supabase:bom:3.0.3')
implementation 'io.github.jan-tennert.supabase:postgrest-kt'
implementation 'io.github.jan-tennert.supabase:realtime-kt'
implementation 'io.ktor:ktor-client-android:3.0.3'
implementation 'org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3'
```

Также добавлен плагин сериализации:
```gradle
id 'org.jetbrains.kotlin.plugin.serialization' version '2.1.10'
```
