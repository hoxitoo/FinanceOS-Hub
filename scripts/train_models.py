"""
Train two TFLite models for FinanceOS-Hub:
1. spending_predictor.tflite  — 30-day sequence → multiplier
2. behavioral_cluster.tflite  — 7 behavioral features → 5-archetype softmax
"""

import os
import numpy as np
import tensorflow as tf

print(f"TensorFlow version: {tf.__version__}")

OUTPUT_DIR = "/home/user/FinanceOS-Hub/app/src/main/assets/models"
os.makedirs(OUTPUT_DIR, exist_ok=True)

np.random.seed(42)
tf.random.set_seed(42)

# ─────────────────────────────────────────────────────────────────────────────
# Model 1: spending_predictor
# ─────────────────────────────────────────────────────────────────────────────
print("\n=== Training spending_predictor ===")

def generate_spending_sequences(n=20000, seq_len=30):
    """
    Generate synthetic 30-day spending sequences with:
    - Random walk baseline
    - Weekend spikes (index 4,5 mod 7)
    - End-of-month dip (last 3 days)
    Target: average of last 7 days (clipped 0..1)
    """
    X = np.zeros((n, seq_len), dtype=np.float32)
    y = np.zeros((n, 1), dtype=np.float32)

    for i in range(n):
        # Random walk starting value
        base = np.random.uniform(0.2, 0.8)
        seq = []
        val = base
        for d in range(seq_len):
            # Weekend spike (days 4,5 of each 7-day cycle)
            day_of_week = d % 7
            if day_of_week in (4, 5):
                spike = np.random.uniform(0.1, 0.4)
            else:
                spike = 0.0

            # End-of-month dip (last 3 days)
            if d >= seq_len - 3:
                dip = np.random.uniform(0.1, 0.3)
            else:
                dip = 0.0

            # Random walk step
            step = np.random.normal(0, 0.05)
            val = float(np.clip(val + step + spike - dip, 0.0, 1.0))
            seq.append(val)

        seq = np.array(seq, dtype=np.float32)

        # Normalize to 0..1
        mn, mx = seq.min(), seq.max()
        if mx > mn:
            seq_norm = (seq - mn) / (mx - mn)
        else:
            seq_norm = seq

        X[i] = seq_norm
        # Target: average of last 7 days, clipped 0..1
        target = float(np.clip(seq_norm[-7:].mean(), 0.0, 1.0))
        y[i, 0] = target

    return X, y

X_spend, y_spend = generate_spending_sequences(20000)
print(f"Spending data: X={X_spend.shape}, y={y_spend.shape}")
print(f"  y range: [{y_spend.min():.3f}, {y_spend.max():.3f}]")

# Build model: Dense(30→16, relu) → Dense(16→8, relu) → Dense(8→1, linear)
spend_model = tf.keras.Sequential([
    tf.keras.layers.Input(shape=(30,), dtype=tf.float32),
    tf.keras.layers.Dense(16, activation='relu'),
    tf.keras.layers.Dense(8, activation='relu'),
    tf.keras.layers.Dense(1, activation='linear'),
], name="spending_predictor")

spend_model.compile(
    optimizer=tf.keras.optimizers.Adam(learning_rate=1e-3),
    loss='mse',
    metrics=['mae']
)
spend_model.summary()

# Train
spend_model.fit(
    X_spend, y_spend,
    epochs=20,
    batch_size=256,
    validation_split=0.1,
    verbose=1
)

# Convert to TFLite (float32)
converter = tf.lite.TFLiteConverter.from_keras_model(spend_model)
converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS]
converter.inference_input_type = tf.float32
converter.inference_output_type = tf.float32
spend_tflite = converter.convert()

spend_path = os.path.join(OUTPUT_DIR, "spending_predictor.tflite")
with open(spend_path, "wb") as f:
    f.write(spend_tflite)

size_kb = len(spend_tflite) / 1024
print(f"\nspending_predictor.tflite saved: {size_kb:.1f} KB")

# Quick sanity check
interp = tf.lite.Interpreter(model_content=spend_tflite)
interp.allocate_tensors()
inp_idx = interp.get_input_details()[0]['index']
out_idx = interp.get_output_details()[0]['index']
test_input = np.ones((1, 30), dtype=np.float32) * 0.5
interp.set_tensor(inp_idx, test_input)
interp.invoke()
out_val = interp.get_tensor(out_idx)
print(f"Sanity check (all-0.5 input) → output: {out_val[0][0]:.4f}")


# ─────────────────────────────────────────────────────────────────────────────
# Model 2: behavioral_cluster
# ─────────────────────────────────────────────────────────────────────────────
print("\n=== Training behavioral_cluster ===")

# Archetype index map
# 0=Плановик, 1=Импульсивный, 2=Гурман, 3=Экономный, 4=Путешественник

def sample_planner(n):
    """Плановик: disciplined, morning spender, good savings, medium diversity"""
    data = np.zeros((n, 7), dtype=np.float32)
    # [0] avg_hour (normalized 0..1): morning → 0.3-0.5
    data[:, 0] = np.random.uniform(0.3, 0.5, n)
    # [1] weekend_ratio: balanced → 0.3-0.6
    data[:, 1] = np.random.uniform(0.3, 0.6, n)
    # [2] avg_amount_rub (norm): moderate → 0.1-0.25
    data[:, 2] = np.random.uniform(0.1, 0.25, n)
    # [3] impulse_share (night): low → 0.0-0.1
    data[:, 3] = np.random.uniform(0.0, 0.1, n)
    # [4] category_diversity: medium → 0.3-0.6
    data[:, 4] = np.random.uniform(0.3, 0.6, n)
    # [5] income_expense_gap (savings): high → 0.3-0.5
    data[:, 5] = np.random.uniform(0.3, 0.5, n)
    # [6] txn_frequency: medium → 0.3-0.6
    data[:, 6] = np.random.uniform(0.3, 0.6, n)
    return data

def sample_impulsive(n):
    """Импульсивный: night shopping, high impulse, low savings"""
    data = np.zeros((n, 7), dtype=np.float32)
    # avg_hour: night → 0.7-1.0
    data[:, 0] = np.random.uniform(0.7, 1.0, n)
    # weekend_ratio: high (binge weekends) → 0.6-1.0
    data[:, 1] = np.random.uniform(0.6, 1.0, n)
    # avg_amount: medium-high → 0.15-0.4
    data[:, 2] = np.random.uniform(0.15, 0.4, n)
    # impulse_share: high → 0.4-0.8
    data[:, 3] = np.random.uniform(0.4, 0.8, n)
    # category_diversity: medium → 0.2-0.5
    data[:, 4] = np.random.uniform(0.2, 0.5, n)
    # savings: very low → 0.0-0.05
    data[:, 5] = np.random.uniform(0.0, 0.05, n)
    # txn_frequency: high → 0.5-1.0
    data[:, 6] = np.random.uniform(0.5, 1.0, n)
    return data

def sample_foodie(n):
    """Гурман: high diversity, frequent, medium savings"""
    data = np.zeros((n, 7), dtype=np.float32)
    # avg_hour: mixed → 0.4-0.7 (lunch/dinner)
    data[:, 0] = np.random.uniform(0.4, 0.7, n)
    # weekend_ratio: medium → 0.4-0.7
    data[:, 1] = np.random.uniform(0.4, 0.7, n)
    # avg_amount: medium → 0.1-0.3
    data[:, 2] = np.random.uniform(0.1, 0.3, n)
    # impulse_share: low-medium → 0.1-0.3
    data[:, 3] = np.random.uniform(0.1, 0.3, n)
    # category_diversity: high → 0.6-1.0
    data[:, 4] = np.random.uniform(0.6, 1.0, n)
    # savings: medium → 0.1-0.3
    data[:, 5] = np.random.uniform(0.1, 0.3, n)
    # txn_frequency: medium-high → 0.4-0.8
    data[:, 6] = np.random.uniform(0.4, 0.8, n)
    return data

def sample_saver(n):
    """Экономный: low amounts, high savings, low diversity, low frequency"""
    data = np.zeros((n, 7), dtype=np.float32)
    # avg_hour: any → 0.2-0.6
    data[:, 0] = np.random.uniform(0.2, 0.6, n)
    # weekend_ratio: low (shops rarely on weekends) → 0.2-0.5
    data[:, 1] = np.random.uniform(0.2, 0.5, n)
    # avg_amount: very low → 0.0-0.1
    data[:, 2] = np.random.uniform(0.0, 0.1, n)
    # impulse_share: very low → 0.0-0.05
    data[:, 3] = np.random.uniform(0.0, 0.05, n)
    # category_diversity: low → 0.1-0.3
    data[:, 4] = np.random.uniform(0.1, 0.3, n)
    # savings: high → 0.4-0.7
    data[:, 5] = np.random.uniform(0.4, 0.7, n)
    # txn_frequency: low → 0.0-0.3
    data[:, 6] = np.random.uniform(0.0, 0.3, n)
    return data

def sample_traveler(n):
    """Путешественник: very high amounts, high diversity, low frequency"""
    data = np.zeros((n, 7), dtype=np.float32)
    # avg_hour: daytime → 0.3-0.7
    data[:, 0] = np.random.uniform(0.3, 0.7, n)
    # weekend_ratio: high (trips on weekends) → 0.5-1.0
    data[:, 1] = np.random.uniform(0.5, 1.0, n)
    # avg_amount: very high → 0.3-1.0
    data[:, 2] = np.random.uniform(0.3, 1.0, n)
    # impulse_share: low (planned trips) → 0.0-0.2
    data[:, 3] = np.random.uniform(0.0, 0.2, n)
    # category_diversity: high → 0.5-1.0
    data[:, 4] = np.random.uniform(0.5, 1.0, n)
    # savings: mixed → 0.05-0.4
    data[:, 5] = np.random.uniform(0.05, 0.4, n)
    # txn_frequency: low (but large) → 0.0-0.3
    data[:, 6] = np.random.uniform(0.0, 0.3, n)
    return data

# Generate 2000 samples per archetype = 10000 total
n_per_class = 2000
X_parts = [
    sample_planner(n_per_class),     # class 0
    sample_impulsive(n_per_class),   # class 1
    sample_foodie(n_per_class),      # class 2
    sample_saver(n_per_class),       # class 3
    sample_traveler(n_per_class),    # class 4
]
X_cluster = np.concatenate(X_parts, axis=0).astype(np.float32)
y_labels = np.concatenate([
    np.full(n_per_class, i) for i in range(5)
]).astype(np.int32)

# Shuffle
perm = np.random.permutation(len(X_cluster))
X_cluster = X_cluster[perm]
y_labels = y_labels[perm]

# One-hot encode
y_cluster = tf.keras.utils.to_categorical(y_labels, num_classes=5).astype(np.float32)

print(f"Cluster data: X={X_cluster.shape}, y={y_cluster.shape}")
print(f"  Class distribution: {np.bincount(y_labels)}")

# Build model: Dense(7→32, relu) → Dense(32→16, relu) → Dense(16→5, softmax)
cluster_model = tf.keras.Sequential([
    tf.keras.layers.Input(shape=(7,), dtype=tf.float32),
    tf.keras.layers.Dense(32, activation='relu'),
    tf.keras.layers.Dense(16, activation='relu'),
    tf.keras.layers.Dense(5, activation='softmax'),
], name="behavioral_cluster")

cluster_model.compile(
    optimizer=tf.keras.optimizers.Adam(learning_rate=1e-3),
    loss='categorical_crossentropy',
    metrics=['accuracy']
)
cluster_model.summary()

# Train
cluster_model.fit(
    X_cluster, y_cluster,
    epochs=30,
    batch_size=256,
    validation_split=0.1,
    verbose=1
)

# Convert to TFLite (float32)
converter2 = tf.lite.TFLiteConverter.from_keras_model(cluster_model)
converter2.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS]
converter2.inference_input_type = tf.float32
converter2.inference_output_type = tf.float32
cluster_tflite = converter2.convert()

cluster_path = os.path.join(OUTPUT_DIR, "behavioral_cluster.tflite")
with open(cluster_path, "wb") as f:
    f.write(cluster_tflite)

size_kb2 = len(cluster_tflite) / 1024
print(f"\nbehavioral_cluster.tflite saved: {size_kb2:.1f} KB")

# Sanity check per archetype
interp2 = tf.lite.Interpreter(model_content=cluster_tflite)
interp2.allocate_tensors()
inp_idx2 = interp2.get_input_details()[0]['index']
out_idx2 = interp2.get_output_details()[0]['index']

archetype_names = ["Плановик", "Импульсивный", "Гурман", "Экономный", "Путешественник"]
test_vectors = [
    np.array([[0.4, 0.45, 0.15, 0.05, 0.45, 0.4, 0.45]], dtype=np.float32),   # Плановик
    np.array([[0.85, 0.75, 0.25, 0.6, 0.35, 0.02, 0.7]], dtype=np.float32),   # Импульсивный
    np.array([[0.55, 0.55, 0.2, 0.2, 0.8, 0.2, 0.6]], dtype=np.float32),      # Гурман
    np.array([[0.4, 0.35, 0.05, 0.02, 0.2, 0.55, 0.15]], dtype=np.float32),   # Экономный
    np.array([[0.5, 0.7, 0.65, 0.1, 0.75, 0.2, 0.15]], dtype=np.float32),     # Путешественник
]

print("\nSanity checks:")
for i, (vec, name) in enumerate(zip(test_vectors, archetype_names)):
    interp2.set_tensor(inp_idx2, vec)
    interp2.invoke()
    probs = interp2.get_tensor(out_idx2)[0]
    predicted = int(np.argmax(probs))
    status = "OK" if predicted == i else "MISMATCH"
    print(f"  {name}: predicted={archetype_names[predicted]} ({probs[predicted]:.3f}) [{status}]")

# ─────────────────────────────────────────────────────────────────────────────
# Summary
# ─────────────────────────────────────────────────────────────────────────────
print("\n=== DONE ===")
print(f"spending_predictor.tflite  : {spend_path}  ({len(spend_tflite)/1024:.1f} KB)")
print(f"behavioral_cluster.tflite  : {cluster_path}  ({len(cluster_tflite)/1024:.1f} KB)")

import os
for fname in ["spending_predictor.tflite", "behavioral_cluster.tflite", "merchant_classifier.tflite"]:
    full = os.path.join(OUTPUT_DIR, fname)
    if os.path.exists(full):
        print(f"  {fname}: {os.path.getsize(full)} bytes")
    else:
        print(f"  {fname}: MISSING")
