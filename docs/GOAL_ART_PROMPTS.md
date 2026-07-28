# Пиксель-арт подложки для карточек целей — промпты для Nano Banana

## Как это работает в приложении

Арт **опциональный**: приложение ищет ресурс по имени в рантайме и, если его нет, рисует
процедурную градиентную подложку. То есть можно добавлять картинки по одной — каждая появится
сразу после того, как файл окажется в `app/src/main/res/drawable/`.

| Тема | Имя файла | Когда выбирается |
|---|---|---|
| Отпуск / путешествия | `goal_art_vacation.webp` | эмодзи ✈ 🏖 · слова «отпуск», «путешеств», «поездка», «море», «тревел» |
| Жильё / ремонт | `goal_art_home.webp` | 🏠 🛋 · «квартир», «дом», «ремонт», «ипотек», «мебел» |
| Авто | `goal_art_car.webp` | 🚗 · «авто», «машин», «права» |
| Техника | `goal_art_tech.webp` | 📱 💻 🎸 · «телефон», «ноут», «iphone», «гаджет» |
| Образование | `goal_art_education.webp` | 📚 🎓 · «курс», «учеб», «универ», «язык» |
| Здоровье / спорт | `goal_art_health.webp` | 💊 🏋 · «лечен», «здоров», «зуб», «фитнес» |
| Подарки / события | `goal_art_gift.webp` | 🎁 💍 · «подар», «свадьб», «юбилей» |
| Покупка (по умолчанию) | `goal_art_purchase.webp` | всё остальное |
| Накопления / подушка | `goal_art_savings.webp` | 💰 ⭐ · «подушк», «накопл», «резерв» |

**Формат:** WebP (или PNG), **соотношение примерно 16:5** — рекомендую **1024 × 320 px**.
Карточка широкая и невысокая; картинка обрезается по центру (`ContentScale.Crop`).

**Важно:** слева на карточке лежит текст (название цели, суммы), поэтому левая треть затемняется
скримом. **Основной объект располагай в правой половине**, слева оставляй пустоту/фон.

---

## Базовый промпт (общая часть)

Вставляй это в начало каждого запроса, меняя только строку `SUBJECT`:

```
16-bit pixel art scene, wide horizontal banner, 1024x320, aspect ratio 16:5.
SUBJECT: <подставить из таблицы ниже>
Style: retro SNES/JRPG pixel art, crisp hard pixels, visible pixel grid, limited palette
(max 24 colors), clean dithering for gradients, no anti-aliasing, no blur.
Composition: main subject placed in the RIGHT half of the image; the LEFT third is nearly empty
dark background (it will be covered by a text overlay). Wide cinematic framing.
Mood: calm, cozy, nocturnal. Very dark background (#0A0D12 to #111620), the artwork should feel
dim and atmospheric so light text stays readable on top.
Lighting: soft glow from the subject only, subtle rim light, deep shadows.
Do NOT include: text, letters, numbers, logos, watermarks, UI elements, human faces, borders, frames.
Background must be dark and uncluttered — no bright sky, no white areas.
```

> Примечание: если Nano Banana лучше понимает английский — оставляй промпт как есть, он уже на
> английском; строку SUBJECT тоже подставляй на английском.

---

## SUBJECT по темам

**`goal_art_vacation`** (отпуск)
```
SUBJECT: a tiny tropical island with two palm trees and a small sailboat on calm dark ocean water,
distant moon low on the horizon, gentle reflected moonlight on the waves, teal and deep blue palette
```

**`goal_art_home`** (жильё)
```
SUBJECT: a small cozy house silhouette at night with two warmly lit windows, a chimney with a thin
smoke trail, a few dark pine trees beside it, warm orange window glow against a deep navy background
```

**`goal_art_car`** (авто)
```
SUBJECT: a small retro sedan car in side view on an empty night road, headlights casting a soft cone
of light forward, a couple of distant street lamps, indigo and steel blue palette
```

**`goal_art_tech`** (техника)
```
SUBJECT: a retro desktop computer and a smartphone on a dark desk, screens glowing soft violet,
a small desk lamp, scattered pixel sparkles, violet and deep purple palette
```

**`goal_art_education`** (образование)
```
SUBJECT: a stack of books with a graduation cap resting on top, a small desk globe beside them,
soft blue glow from an open book, deep blue palette
```

**`goal_art_health`** (здоровье / спорт)
```
SUBJECT: a dumbbell and a simple heart-rate line, a small green plant in a pot beside them,
soft green glow, dark background, mint and forest green palette
```

**`goal_art_gift`** (подарки)
```
SUBJECT: two wrapped gift boxes with ribbons and a few floating sparkles above them, soft pink glow,
dark background, pink and magenta palette
```

**`goal_art_purchase`** (покупка — дефолт)
```
SUBJECT: a shopping bag and a small cardboard box with a price tag, a few coins stacked beside them,
warm golden glow, dark background, amber and yellow palette
```

**`goal_art_savings`** (накопления)
```
SUBJECT: a piggy bank with a few coins falling into it and a small stack of coins beside it,
soft mint-green glow, dark background, green and teal palette
```

---

## После генерации

1. Сохрани как `goal_art_<тема>.webp` (WebP, качество ~85, без альфы — фон и так тёмный).
2. Положи в `app/src/main/res/drawable/`.
3. Пересобери — подложка появится автоматически, кода менять не нужно.

Если картинка окажется слишком яркой и мешает читать суммы — уменьши `alpha` в
`GoalArtBackdrop` (`ui/components/GoalArt.kt`, сейчас `0.38f`).
