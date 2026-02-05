# Структура таблиц Supabase

## Таблица Category

### SQL для создания таблицы
```sql
CREATE TABLE Category (
    id INT PRIMARY KEY,
    category_name TEXT NOT NULL,
    category_image TEXT,
    category_status TEXT,
    featured BOOLEAN DEFAULT false,
    last_update TIMESTAMP DEFAULT NOW()
);
```

### Описание полей
| Поле | Тип | Обязательное | Описание |
|------|-----|--------------|----------|
| id | INT | Да | Уникальный идентификатор категории |
| category_name | TEXT | Да | Название категории |
| category_image | TEXT | Нет | URL изображения категории |
| category_status | TEXT | Нет | Статус категории (active, inactive и т.д.) |
| featured | BOOLEAN | Нет | Флаг избранной категории |
| last_update | TIMESTAMP | Нет | Дата и время последнего обновления |

### Пример данных
```sql
INSERT INTO Category (id, category_name, category_image, category_status, featured, last_update) VALUES
(1, 'Rock', 'https://example.com/images/rock.jpg', 'active', true, NOW()),
(2, 'Pop', 'https://example.com/images/pop.jpg', 'active', true, NOW()),
(3, 'Jazz', 'https://example.com/images/jazz.jpg', 'active', false, NOW()),
(4, 'Classical', 'https://example.com/images/classical.jpg', 'active', false, NOW()),
(5, 'Electronic', 'https://example.com/images/electronic.jpg', 'active', true, NOW());
```

---

## Таблица Radio

### SQL для создания таблицы
```sql
CREATE TABLE Radio (
    id INT PRIMARY KEY,
    category_id INT,
    radio_name TEXT NOT NULL,
    radio_image TEXT,
    radio_url TEXT NOT NULL,
    radio_status TEXT,
    view_count INT DEFAULT 0,
    featured BOOLEAN DEFAULT false,
    type TEXT,
    last_update TIMESTAMP DEFAULT NOW(),
    FOREIGN KEY (category_id) REFERENCES Category(id) ON DELETE SET NULL
);
```

### Описание полей
| Поле | Тип | Обязательное | Описание |
|------|-----|--------------|----------|
| id | INT | Да | Уникальный идентификатор радиостанции |
| category_id | INT | Нет | ID категории (внешний ключ на Category.id) |
| radio_name | TEXT | Да | Название радиостанции |
| radio_image | TEXT | Нет | URL изображения радиостанции |
| radio_url | TEXT | Да | URL потока радиостанции |
| radio_status | TEXT | Нет | Статус радиостанции (active, inactive и т.д.) |
| view_count | INT | Нет | Количество просмотров/прослушиваний |
| featured | BOOLEAN | Нет | Флаг избранной радиостанции |
| type | TEXT | Нет | Тип радиостанции (live, podcast и т.д.) |
| last_update | TIMESTAMP | Нет | Дата и время последнего обновления |

### Пример данных
```sql
INSERT INTO Radio (id, category_id, radio_name, radio_image, radio_url, radio_status, view_count, featured, type, last_update) VALUES
(1, 1, 'Rock FM', 'https://example.com/images/rockfm.jpg', 'http://stream.rockfm.com/live', 'active', 0, true, 'live', NOW()),
(2, 1, 'Classic Rock Radio', 'https://example.com/images/classicrock.jpg', 'http://stream.classicrock.com/live', 'active', 0, false, 'live', NOW()),
(3, 2, 'Pop Hits', 'https://example.com/images/pophits.jpg', 'http://stream.pophits.com/live', 'active', 0, true, 'live', NOW()),
(4, 2, 'Top 40', 'https://example.com/images/top40.jpg', 'http://stream.top40.com/live', 'active', 0, false, 'live', NOW()),
(5, 3, 'Smooth Jazz', 'https://example.com/images/smoothjazz.jpg', 'http://stream.smoothjazz.com/live', 'active', 0, true, 'live', NOW()),
(6, 4, 'Classical Music', 'https://example.com/images/classical.jpg', 'http://stream.classical.com/live', 'active', 0, false, 'live', NOW()),
(7, 5, 'Electronic Beats', 'https://example.com/images/electronic.jpg', 'http://stream.electronic.com/live', 'active', 0, true, 'live', NOW());
```

---

## Индексы для оптимизации

```sql
-- Индекс для быстрого поиска по категории
CREATE INDEX idx_radio_category_id ON Radio(category_id);

-- Индекс для поиска по имени радиостанции
CREATE INDEX idx_radio_name ON Radio(radio_name);

-- Индекс для фильтрации по статусу
CREATE INDEX idx_radio_status ON Radio(radio_status);
CREATE INDEX idx_category_status ON Category(category_status);

-- Индекс для избранных
CREATE INDEX idx_radio_featured ON Radio(featured);
CREATE INDEX idx_category_featured ON Category(featured);
```

---

## Политики безопасности (RLS)

```sql
-- Включить Row Level Security
ALTER TABLE Category ENABLE ROW LEVEL SECURITY;
ALTER TABLE Radio ENABLE ROW LEVEL SECURITY;

-- Разрешить всем читать категории
CREATE POLICY "Allow public read access on Category"
ON Category FOR SELECT
USING (true);

-- Разрешить всем читать радиостанции
CREATE POLICY "Allow public read access on Radio"
ON Radio FOR SELECT
USING (true);

-- Разрешить обновление счетчика просмотров
CREATE POLICY "Allow update view_count on Radio"
ON Radio FOR UPDATE
USING (true)
WITH CHECK (true);
```

---

## Запросы для проверки данных

### Получить все категории
```sql
SELECT * FROM Category ORDER BY category_name;
```

### Получить все радиостанции
```sql
SELECT * FROM Radio ORDER BY radio_name;
```

### Получить радиостанции по категории
```sql
SELECT r.*
FROM Radio r
WHERE r.category_id = 1
ORDER BY r.radio_name;
```

### Получить категории с количеством радиостанций
```sql
SELECT
    c.id,
    c.category_name,
    c.category_image,
    COUNT(r.id) as radio_count
FROM Category c
LEFT JOIN Radio r ON c.id = r.category_id
GROUP BY c.id, c.category_name, c.category_image
ORDER BY c.category_name;
```

### Получить избранные радиостанции
```sql
SELECT * FROM Radio
WHERE featured = true
ORDER BY view_count DESC;
```

### Поиск радиостанций по имени
```sql
SELECT * FROM Radio
WHERE radio_name ILIKE '%rock%'
ORDER BY radio_name;
```

---

## Миграция данных (если есть старая база)

Если у вас есть данные в старом формате, используйте следующие запросы для миграции:

### Из старой таблицы category
```sql
INSERT INTO Category (id, category_name, category_image, category_status, featured)
SELECT
    cid as id,
    category_name,
    category_image,
    'active' as category_status,
    false as featured
FROM old_category_table;
```

### Из старой таблицы radio
```sql
INSERT INTO Radio (id, category_id, radio_name, radio_image, radio_url, radio_status, view_count, type)
SELECT
    id::INT,
    category::INT as category_id,
    name as radio_name,
    image as radio_image,
    url as radio_url,
    'active' as radio_status,
    views as view_count,
    type
FROM old_radio_table;
```
