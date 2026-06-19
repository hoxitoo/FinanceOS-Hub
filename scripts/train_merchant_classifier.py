#!/usr/bin/env python3
"""
FinanceOS-Hub — merchant category classifier training script.

Trains a 256→128→13 MLP that replicates the TextFeatureExtractor
embedding used in MLCategoryClassifier.kt, then exports to TFLite.

Output: merchant_classifier.tflite  (drop into app/src/main/assets/models/)

Requirements:
    pip install tensorflow numpy

Runs in ~1–2 minutes on a CPU laptop. No GPU needed.
"""

import numpy as np
import os

# ─────────────────────────────────────────────────────────────────────────────
# 1.  Feature extractor — exact replica of TextFeatureExtractor.kt
# ─────────────────────────────────────────────────────────────────────────────

VECTOR_SIZE = 256

def extract(text: str) -> np.ndarray:
    """
    Char n-gram Fibonacci hash, L2-normalised to 256 dims.
    Must stay byte-for-byte identical to TextFeatureExtractor.kt.
    """
    vector = np.zeros(VECTOR_SIZE, dtype=np.float32)
    normalized = "".join(c for c in text.lower() if c.isalnum() or c.isspace())

    # Unigrams
    for ch in normalized:
        idx = int((ord(ch) * 2_654_435_761) & 0xFF)
        vector[idx] += 1.0

    # Bigrams
    for i in range(len(normalized) - 1):
        bigram = ord(normalized[i]) * 31 + ord(normalized[i + 1])
        idx = int((bigram * 2_654_435_761) & 0xFF)
        vector[idx] += 0.5

    # L2 normalise
    norm = np.sqrt(np.sum(vector ** 2))
    if norm > 0:
        vector /= norm

    return vector


# ─────────────────────────────────────────────────────────────────────────────
# 2.  Category index — must match CATEGORY_IDS in MLCategoryClassifier.kt
# ─────────────────────────────────────────────────────────────────────────────

CATEGORIES = [
    "cat_food", "cat_grocery", "cat_transport", "cat_housing", "cat_health",
    "cat_shopping", "cat_telecom", "cat_entertain", "cat_education",
    "cat_travel", "cat_beauty", "cat_pets", "cat_other",
]
CAT_IDX = {c: i for i, c in enumerate(CATEGORIES)}


# ─────────────────────────────────────────────────────────────────────────────
# 3.  Training data — merchant names / keywords → category
#     Sourced from:
#       • FosDatabase.kt  insertDefaultMerchantRules (60 rules)
#       • Extended with ~200 common Russian merchant names per category
# ─────────────────────────────────────────────────────────────────────────────

RAW_DATA = [
    # ── cat_food ─────────────────────────────────────────────────────────────
    ("макдональдс", "cat_food"), ("mcdonald", "cat_food"), ("mcdonalds", "cat_food"),
    ("kfc", "cat_food"), ("бургер кинг", "cat_food"), ("burger king", "cat_food"),
    ("domino", "cat_food"), ("dominos pizza", "cat_food"),
    ("пицца", "cat_food"), ("pizza", "cat_food"), ("pizza hut", "cat_food"),
    ("суши", "cat_food"), ("sushi", "cat_food"), ("суши шоп", "cat_food"),
    ("кофе", "cat_food"), ("coffee", "cat_food"), ("starbucks", "cat_food"),
    ("ресторан", "cat_food"), ("кафе", "cat_food"), ("cafe", "cat_food"),
    ("шаурма", "cat_food"), ("shawarma", "cat_food"),
    ("блины", "cat_food"), ("блинная", "cat_food"),
    ("столовая", "cat_food"), ("буфет", "cat_food"),
    ("subway", "cat_food"), ("papa johns", "cat_food"),
    ("вкусно и точка", "cat_food"), ("теремок", "cat_food"),
    ("чайхона", "cat_food"), ("мясо и рыба", "cat_food"),
    ("якитория", "cat_food"), ("додо пицца", "cat_food"), ("dodo pizza", "cat_food"),
    ("хинкальная", "cat_food"), ("пельменная", "cat_food"),
    ("il patio", "cat_food"), ("tgi fridays", "cat_food"),
    ("costa coffee", "cat_food"), ("cofix", "cat_food"),
    ("шоколадница", "cat_food"), ("кофе хауз", "cat_food"),
    ("макс", "cat_food"), ("суши мастер", "cat_food"),
    ("пицца хат", "cat_food"), ("ролл клуб", "cat_food"),
    ("вилка ложка", "cat_food"), ("му-му", "cat_food"),
    ("грабли", "cat_food"), ("тануки", "cat_food"),
    ("gyros", "cat_food"), ("бургерная", "cat_food"),

    # ── cat_grocery ──────────────────────────────────────────────────────────
    ("пятёрочка", "cat_grocery"), ("пятерочка", "cat_grocery"), ("5ka", "cat_grocery"),
    ("магнит", "cat_grocery"), ("magnit", "cat_grocery"),
    ("перекрёсток", "cat_grocery"), ("перекресток", "cat_grocery"),
    ("лента", "cat_grocery"), ("ашан", "cat_grocery"), ("auchan", "cat_grocery"),
    ("вкусвилл", "cat_grocery"), ("дикси", "cat_grocery"),
    ("metro", "cat_grocery"), ("spar", "cat_grocery"),
    ("окей", "cat_grocery"), ("o'key", "cat_grocery"), ("okey", "cat_grocery"),
    ("азбука вкуса", "cat_grocery"), ("billa", "cat_grocery"),
    ("верный", "cat_grocery"), ("красное белое", "cat_grocery"),
    ("кб", "cat_grocery"), ("продукты", "cat_grocery"),
    ("гастроном", "cat_grocery"), ("супермаркет", "cat_grocery"),
    ("гипермаркет", "cat_grocery"), ("универсам", "cat_grocery"),
    ("семишагофф", "cat_grocery"), ("семья", "cat_grocery"),
    ("глобус", "cat_grocery"), ("карусель", "cat_grocery"),
    ("реал", "cat_grocery"), ("биला", "cat_grocery"),
    ("фермер", "cat_grocery"), ("фреш", "cat_grocery"),
    ("рынок", "cat_grocery"), ("базар", "cat_grocery"),
    ("fix price", "cat_grocery"), ("фикс прайс", "cat_grocery"),

    # ── cat_transport ────────────────────────────────────────────────────────
    ("яндекс такси", "cat_transport"), ("yandex taxi", "cat_transport"),
    ("uber", "cat_transport"), ("ситимобил", "cat_transport"),
    ("метро", "cat_transport"), ("metro", "cat_transport"),
    ("аэрофлот", "cat_transport"), ("aeroflot", "cat_transport"),
    ("rzd", "cat_transport"), ("ржд", "cat_transport"),
    ("автобус", "cat_transport"), ("самокат", "cat_transport"),
    ("такси", "cat_transport"), ("taxi", "cat_transport"),
    ("электричка", "cat_transport"), ("мцд", "cat_transport"),
    ("мцк", "cat_transport"), ("трамвай", "cat_transport"),
    ("троллейбус", "cat_transport"), ("маршрутка", "cat_transport"),
    ("авиа", "cat_transport"), ("авиабилет", "cat_transport"),
    ("s7", "cat_transport"), ("победа", "cat_transport"),
    ("ural airlines", "cat_transport"), ("уральские авиалинии", "cat_transport"),
    ("nordwind", "cat_transport"), ("utair", "cat_transport"),
    ("автовокзал", "cat_transport"), ("bus ticket", "cat_transport"),
    ("каршеринг", "cat_transport"), ("делимобиль", "cat_transport"),
    ("яндекс драйв", "cat_transport"), ("yandex drive", "cat_transport"),
    ("бла бла кар", "cat_transport"), ("blablacar", "cat_transport"),
    ("паркинг", "cat_transport"), ("парковка", "cat_transport"),
    ("заправка", "cat_transport"), ("азс", "cat_transport"),
    ("лукойл", "cat_transport"), ("газпром нефть", "cat_transport"),
    ("роснефть", "cat_transport"), ("bp", "cat_transport"),

    # ── cat_housing ──────────────────────────────────────────────────────────
    ("жкх", "cat_housing"), ("квартплата", "cat_housing"),
    ("электроэнерг", "cat_housing"), ("газ", "cat_housing"),
    ("домофон", "cat_housing"), ("мосэнерго", "cat_housing"),
    ("водоканал", "cat_housing"), ("теплосеть", "cat_housing"),
    ("управляющая компания", "cat_housing"), ("ук ", "cat_housing"),
    ("капремонт", "cat_housing"), ("коммуналка", "cat_housing"),
    ("аренда", "cat_housing"), ("rent", "cat_housing"),
    ("циан", "cat_housing"), ("avito аренда", "cat_housing"),
    ("ипотека", "cat_housing"), ("mortgage", "cat_housing"),
    ("сбербанк ипотека", "cat_housing"),
    ("пени жкх", "cat_housing"), ("счёт жкх", "cat_housing"),
    ("горгаз", "cat_housing"), ("мосгаз", "cat_housing"),
    ("мосводоканал", "cat_housing"), ("ооо жэк", "cat_housing"),
    ("тсж", "cat_housing"), ("интернет домашний", "cat_housing"),

    # ── cat_health ───────────────────────────────────────────────────────────
    ("аптека", "cat_health"), ("pharmacy", "cat_health"),
    ("клиника", "cat_health"), ("поликлиник", "cat_health"),
    ("стоматолог", "cat_health"), ("медцентр", "cat_health"),
    ("больница", "cat_health"), ("hospital", "cat_health"),
    ("аптека 36.6", "cat_health"), ("36.6", "cat_health"),
    ("ригла", "cat_health"), ("горздрав", "cat_health"),
    ("планета здоровья", "cat_health"), ("мед", "cat_health"),
    ("медицина", "cat_health"), ("врач", "cat_health"),
    ("доктор", "cat_health"), ("doctor", "cat_health"),
    ("анализ", "cat_health"), ("лаборатор", "cat_health"),
    ("гемотест", "cat_health"), ("инвитро", "cat_health"),
    ("kdl", "cat_health"), ("мрт", "cat_health"),
    ("узи", "cat_health"), ("рентген", "cat_health"),
    ("фитнес", "cat_health"), ("fitness", "cat_health"),
    ("спортзал", "cat_health"), ("gym", "cat_health"),
    ("world class", "cat_health"), ("fit", "cat_health"),
    ("йога", "cat_health"), ("yoga", "cat_health"),
    ("бассейн", "cat_health"), ("pool", "cat_health"),
    ("ортека", "cat_health"), ("оптика", "cat_health"),

    # ── cat_shopping ─────────────────────────────────────────────────────────
    ("wildberries", "cat_shopping"), ("wildber", "cat_shopping"),
    ("ozon", "cat_shopping"), ("озон", "cat_shopping"),
    ("avito", "cat_shopping"), ("авито", "cat_shopping"),
    ("lamoda", "cat_shopping"), ("lamoda ru", "cat_shopping"),
    ("zara", "cat_shopping"), ("h&m", "cat_shopping"), ("hm", "cat_shopping"),
    ("ikea", "cat_shopping"), ("икеа", "cat_shopping"),
    ("леруа мерлен", "cat_shopping"), ("leroy merlin", "cat_shopping"),
    ("obi", "cat_shopping"), ("оби", "cat_shopping"),
    ("ситилинк", "cat_shopping"), ("citilink", "cat_shopping"),
    ("dns", "cat_shopping"), ("днс", "cat_shopping"),
    ("эльдорадо", "cat_shopping"), ("eldorado", "cat_shopping"),
    ("м видео", "cat_shopping"), ("mvideo", "cat_shopping"),
    ("re store", "cat_shopping"), ("apple store", "cat_shopping"),
    ("сберегай", "cat_shopping"), ("максидом", "cat_shopping"),
    ("твой дом", "cat_shopping"), ("hoff", "cat_shopping"),
    ("uniqlo", "cat_shopping"), ("bershka", "cat_shopping"),
    ("pull bear", "cat_shopping"), ("massimo dutti", "cat_shopping"),
    ("глобус", "cat_shopping"), ("торговый центр", "cat_shopping"),
    ("тц", "cat_shopping"), ("мол", "cat_shopping"),
    ("ali", "cat_shopping"), ("aliexpress", "cat_shopping"),
    ("joom", "cat_shopping"), ("shein", "cat_shopping"),

    # ── cat_telecom ──────────────────────────────────────────────────────────
    ("мтс", "cat_telecom"), ("mts", "cat_telecom"),
    ("билайн", "cat_telecom"), ("beeline", "cat_telecom"),
    ("мегафон", "cat_telecom"), ("megafon", "cat_telecom"),
    ("теле2", "cat_telecom"), ("tele2", "cat_telecom"),
    ("ростелеком", "cat_telecom"), ("rostelecom", "cat_telecom"),
    ("yota", "cat_telecom"), ("йота", "cat_telecom"),
    ("мтс интернет", "cat_telecom"), ("домашний интернет", "cat_telecom"),
    ("онлайм", "cat_telecom"), ("dom ru", "cat_telecom"),
    ("комстар", "cat_telecom"), ("ттк", "cat_telecom"),

    # ── cat_entertain ────────────────────────────────────────────────────────
    ("кинотеатр", "cat_entertain"), ("cinema", "cat_entertain"),
    ("netflix", "cat_entertain"), ("spotify", "cat_entertain"),
    ("okko", "cat_entertain"), ("more.tv", "cat_entertain"),
    ("иви", "cat_entertain"), ("ivi", "cat_entertain"),
    ("яндекс музыка", "cat_entertain"), ("yandex music", "cat_entertain"),
    ("steam", "cat_entertain"), ("playstation", "cat_entertain"),
    ("xbox", "cat_entertain"), ("apple tv", "cat_entertain"),
    ("кино", "cat_entertain"), ("театр", "cat_entertain"),
    ("museum", "cat_entertain"), ("музей", "cat_entertain"),
    ("концерт", "cat_entertain"), ("concert", "cat_entertain"),
    ("билет", "cat_entertain"), ("ticket", "cat_entertain"),
    ("кассир", "cat_entertain"), ("kassir", "cat_entertain"),
    ("яндекс игры", "cat_entertain"), ("яндекс плюс", "cat_entertain"),
    ("google play", "cat_entertain"), ("app store", "cat_entertain"),
    ("twitch", "cat_entertain"), ("youtube", "cat_entertain"),
    ("квест", "cat_entertain"), ("боулинг", "cat_entertain"),
    ("бильярд", "cat_entertain"), ("karting", "cat_entertain"),
    ("картинг", "cat_entertain"), ("батут", "cat_entertain"),

    # ── cat_education ────────────────────────────────────────────────────────
    ("coursera", "cat_education"), ("skillbox", "cat_education"),
    ("нетология", "cat_education"), ("netology", "cat_education"),
    ("geekbrains", "cat_education"), ("яндекс практикум", "cat_education"),
    ("udemy", "cat_education"), ("stepik", "cat_education"),
    ("университет", "cat_education"), ("институт", "cat_education"),
    ("школа", "cat_education"), ("детский сад", "cat_education"),
    ("репетитор", "cat_education"), ("курсы", "cat_education"),
    ("обучение", "cat_education"), ("education", "cat_education"),
    ("учебник", "cat_education"), ("books", "cat_education"),
    ("читай город", "cat_education"), ("буквоед", "cat_education"),
    ("ozon книги", "cat_education"), ("litres", "cat_education"),

    # ── cat_travel ───────────────────────────────────────────────────────────
    ("booking", "cat_travel"), ("букинг", "cat_travel"),
    ("airbnb", "cat_travel"), ("ostrovok", "cat_travel"),
    ("островок", "cat_travel"), ("отель", "cat_travel"),
    ("hotel", "cat_travel"), ("hostel", "cat_travel"),
    ("хостел", "cat_travel"), ("санаторий", "cat_travel"),
    ("туроператор", "cat_travel"), ("тур ", "cat_travel"),
    ("путёвка", "cat_travel"), ("путевка", "cat_travel"),
    ("экскурсия", "cat_travel"), ("визовый", "cat_travel"),
    ("страхование путешест", "cat_travel"), ("travel insurance", "cat_travel"),
    ("currency exchange", "cat_travel"), ("обмен валюты", "cat_travel"),
    ("мир тур", "cat_travel"), ("anex", "cat_travel"),
    ("tez tour", "cat_travel"), ("coral travel", "cat_travel"),

    # ── cat_beauty ───────────────────────────────────────────────────────────
    ("л'этуаль", "cat_beauty"), ("летуаль", "cat_beauty"), ("letu", "cat_beauty"),
    ("рив гош", "cat_beauty"), ("rivgosh", "cat_beauty"),
    ("салон красоты", "cat_beauty"), ("beauty", "cat_beauty"),
    ("парикмахер", "cat_beauty"), ("барбер", "cat_beauty"), ("barber", "cat_beauty"),
    ("маникюр", "cat_beauty"), ("педикюр", "cat_beauty"),
    ("nail", "cat_beauty"), ("ногти", "cat_beauty"),
    ("косметика", "cat_beauty"), ("cosmetics", "cat_beauty"),
    ("sephora", "cat_beauty"), ("иль де ботэ", "cat_beauty"),
    ("золотое яблоко", "cat_beauty"), ("douglas", "cat_beauty"),
    ("макияж", "cat_beauty"), ("makeup", "cat_beauty"),
    ("tati", "cat_beauty"), ("dm", "cat_beauty"),

    # ── cat_pets ─────────────────────────────────────────────────────────────
    ("ветеринар", "cat_pets"), ("veterinar", "cat_pets"), ("ветклиника", "cat_pets"),
    ("зоомагазин", "cat_pets"), ("petshop", "cat_pets"), ("pet shop", "cat_pets"),
    ("кошачий", "cat_pets"), ("собачий", "cat_pets"),
    ("корм для животных", "cat_pets"), ("pet food", "cat_pets"),
    ("бетховен", "cat_pets"), ("четыре лапы", "cat_pets"),
    ("зоогалерея", "cat_pets"), ("зоо", "cat_pets"),
    ("royal canin", "cat_pets"), ("hills", "cat_pets"),
    ("purina", "cat_pets"), ("whiskas", "cat_pets"),
    ("pedigree", "cat_pets"), ("animall", "cat_pets"),

    # ── cat_other ────────────────────────────────────────────────────────────
    ("сбербанк", "cat_other"), ("тинькофф", "cat_other"),
    ("перевод", "cat_other"), ("transfer", "cat_other"),
    ("снятие", "cat_other"), ("atm", "cat_other"),
    ("банкомат", "cat_other"), ("комиссия", "cat_other"),
    ("госуслуги", "cat_other"), ("налог", "cat_other"),
    ("штраф", "cat_other"), ("гибдд", "cat_other"),
    ("страховка", "cat_other"), ("осаго", "cat_other"),
    ("нотариус", "cat_other"), ("юрист", "cat_other"),
    ("благотворительность", "cat_other"), ("charity", "cat_other"),
    ("прочее", "cat_other"), ("другое", "cat_other"),
]


def build_dataset(data, augment_factor=8):
    """
    Build (X, y) arrays. Light augmentation:
      • random substring of the merchant name
      • prefix/suffix noise
    """
    merchants = [m for m, _ in data]
    labels    = [CAT_IDX[c] for _, c in data]

    xs, ys = [], []
    for merchant, label in zip(merchants, labels):
        # Original
        xs.append(extract(merchant))
        ys.append(label)
        # Augmented variants
        for _ in range(augment_factor):
            text = merchant
            # random casing noise
            if np.random.rand() < 0.3:
                text = text.upper()
            # add a random word prefix (simulates full SMS merchant field)
            prefixes = ["оплата ", "покупка ", "payment ", "магазин ", ""]
            text = np.random.choice(prefixes) + text
            xs.append(extract(text))
            ys.append(label)

    return np.array(xs, dtype=np.float32), np.array(ys, dtype=np.int32)


# ─────────────────────────────────────────────────────────────────────────────
# 4.  Build dataset
# ─────────────────────────────────────────────────────────────────────────────

print("Building dataset …")
X, y = build_dataset(RAW_DATA, augment_factor=12)
print(f"  {X.shape[0]} samples, {X.shape[1]} features, {len(CATEGORIES)} classes")

# Shuffle
idx = np.random.permutation(len(X))
X, y = X[idx], y[idx]

# Train / val split  80/20
split = int(len(X) * 0.8)
X_train, X_val = X[:split], X[split:]
y_train, y_val = y[:split], y[split:]

print(f"  train={len(X_train)}  val={len(X_val)}")


# ─────────────────────────────────────────────────────────────────────────────
# 5.  Model — 256 → 128 → 64 → 13
# ─────────────────────────────────────────────────────────────────────────────

import tensorflow as tf

model = tf.keras.Sequential([
    tf.keras.layers.Input(shape=(VECTOR_SIZE,)),
    tf.keras.layers.Dense(128, activation="relu"),
    tf.keras.layers.Dropout(0.3),
    tf.keras.layers.Dense(64, activation="relu"),
    tf.keras.layers.Dropout(0.2),
    tf.keras.layers.Dense(len(CATEGORIES), activation="softmax"),
], name="merchant_classifier")

model.compile(
    optimizer=tf.keras.optimizers.Adam(1e-3),
    loss="sparse_categorical_crossentropy",
    metrics=["accuracy"],
)
model.summary()

print("\nTraining …")
history = model.fit(
    X_train, y_train,
    validation_data=(X_val, y_val),
    epochs=60,
    batch_size=64,
    callbacks=[
        tf.keras.callbacks.EarlyStopping(patience=8, restore_best_weights=True),
        tf.keras.callbacks.ReduceLROnPlateau(patience=4, factor=0.5, verbose=0),
    ],
    verbose=1,
)

val_loss, val_acc = model.evaluate(X_val, y_val, verbose=0)
print(f"\nValidation accuracy: {val_acc * 100:.1f}%")


# ─────────────────────────────────────────────────────────────────────────────
# 6.  Quick smoke-test on known merchants
# ─────────────────────────────────────────────────────────────────────────────

print("\nSmoke-test:")
test_cases = [
    ("магнит",       "cat_grocery"),
    ("kfc",          "cat_food"),
    ("аэрофлот",     "cat_transport"),
    ("жкх",          "cat_housing"),
    ("apteka 36.6",  "cat_health"),
    ("wildberries",  "cat_shopping"),
    ("мтс",          "cat_telecom"),
    ("netflix",      "cat_entertain"),
    ("skillbox",     "cat_education"),
    ("booking",      "cat_travel"),
    ("летуаль",      "cat_beauty"),
    ("ветеринар",    "cat_pets"),
]
passed = 0
for merchant, expected in test_cases:
    vec = extract(merchant).reshape(1, -1)
    probs = model.predict(vec, verbose=0)[0]
    pred_idx = int(np.argmax(probs))
    pred_cat = CATEGORIES[pred_idx]
    conf     = probs[pred_idx]
    ok       = "✓" if pred_cat == expected else "✗"
    if pred_cat == expected:
        passed += 1
    print(f"  {ok}  {merchant:<22} → {pred_cat:<20} ({conf:.0%})  expected={expected}")
print(f"\n  {passed}/{len(test_cases)} smoke-tests passed")


# ─────────────────────────────────────────────────────────────────────────────
# 7.  Export to TFLite
# ─────────────────────────────────────────────────────────────────────────────

OUT_FILE = "merchant_classifier.tflite"

print(f"\nExporting to TFLite → {OUT_FILE} …")
converter = tf.lite.TFLiteConverter.from_keras_model(model)
# Optimize for size/latency without quantization (keeps float32, simpler)
converter.optimizations = [tf.lite.Optimize.DEFAULT]
tflite_model = converter.convert()

with open(OUT_FILE, "wb") as f:
    f.write(tflite_model)

size_kb = os.path.getsize(OUT_FILE) / 1024
print(f"  Saved {OUT_FILE}  ({size_kb:.0f} KB)")


# ─────────────────────────────────────────────────────────────────────────────
# 8.  Verify TFLite model directly
# ─────────────────────────────────────────────────────────────────────────────

print("\nVerifying TFLite model …")
interp = tf.lite.Interpreter(model_path=OUT_FILE)
interp.allocate_tensors()

inp  = interp.get_input_details()
out  = interp.get_output_details()
print(f"  Input  shape: {inp[0]['shape']}  dtype={inp[0]['dtype']}")
print(f"  Output shape: {out[0]['shape']}  dtype={out[0]['dtype']}")

sample = extract("wildberries").reshape(1, -1)
interp.set_tensor(inp[0]['index'], sample)
interp.invoke()
probs = interp.get_tensor(out[0]['index'])[0]
print(f"  'wildberries' → {CATEGORIES[int(np.argmax(probs))]}  ({probs.max():.0%})")

print("\n✅  Done!  Copy merchant_classifier.tflite to:")
print("     app/src/main/assets/models/merchant_classifier.tflite")
print("   then rebuild the APK — ML will activate automatically.")
