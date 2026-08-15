import json
import os
import numpy as np
import pandas as pd
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import classification_report, accuracy_score
from sklearn.model_selection import train_test_split, cross_val_score

os.makedirs('app/src/main/assets/models', exist_ok=True)

# ==============================================================================
# 1. TRAIN REMINDER CLASSIFIER (Binary: 0 = NON_REMINDER, 1 = REMINDER)
# ==============================================================================
print("=" * 60)
print("1. Training Reminder Sentence Classifier from datasets/reminder_dataset.xlsx")
print("=" * 60)

rem_df = pd.read_excel('datasets/reminder_dataset.xlsx')
rem_texts = rem_df['sentence'].astype(str).values
rem_labels = rem_df['label'].astype(int).values

print(f"Loaded {len(rem_texts)} sentences. Class distribution: {np.bincount(rem_labels)}")

# Split for validation
X_train_r, X_test_r, y_train_r, y_test_r = train_test_split(
    rem_texts, rem_labels, test_size=0.15, random_state=42, stratify=rem_labels
)

# Vectorizer matching Kotlin implementation
rem_vec = TfidfVectorizer(
    ngram_range=(1, 2),
    token_pattern=r'(?u)\b\w\w+\b',
    sublinear_tf=True,
    min_df=2
)
X_train_r_vec = rem_vec.fit_transform(X_train_r)
X_test_r_vec = rem_vec.transform(X_test_r)

# Logistic Regression
rem_model = LogisticRegression(C=2.0, max_iter=1000, random_state=42)
rem_model.fit(X_train_r_vec, y_train_r)

# Validation Metrics
y_pred_r = rem_model.predict(X_test_r_vec)
val_acc_r = accuracy_score(y_test_r, y_pred_r)
print(f"Validation Accuracy: {val_acc_r * 100:.2f}%")
print(classification_report(y_test_r, y_pred_r, target_names=['NON_REMINDER (0)', 'REMINDER (1)']))

# Retrain on full dataset for maximum performance and coverage
full_rem_vec = TfidfVectorizer(
    ngram_range=(1, 2),
    token_pattern=r'(?u)\b\w\w+\b',
    sublinear_tf=True,
    min_df=2
)
X_full_r = full_rem_vec.fit_transform(rem_texts)
full_rem_model = LogisticRegression(C=2.0, max_iter=1000, random_state=42)
full_rem_model.fit(X_full_r, rem_labels)

cv_scores_r = cross_val_score(full_rem_model, X_full_r, rem_labels, cv=5)
print(f"Full 5-Fold Cross-Validation Accuracy: {cv_scores_r.mean() * 100:.2f}% (+/- {cv_scores_r.std() * 100:.2f}%)")

# Export to JSON format for Kotlin LocalTextClassifier
rem_export = {
    "type": "binary_logistic",
    "sublinear_tf": True,
    "vocabulary": {term: int(idx) for term, idx in full_rem_vec.vocabulary_.items()},
    "idf": full_rem_vec.idf_.tolist(),
    "coef": full_rem_model.coef_[0].tolist(),
    "intercept": float(full_rem_model.intercept_[0])
}

rem_path = 'app/src/main/assets/models/reminder_classifier.json'
with open(rem_path, 'w', encoding='utf-8') as f:
    json.dump(rem_export, f)

print(f"Successfully saved reminder model to {rem_path} ({os.path.getsize(rem_path)} bytes, vocab={len(full_rem_vec.vocabulary_)})")


# ==============================================================================
# 2. TRAIN INTENT CLASSIFIER (Multiclass: 0 = ASKING, 1 = TELLING, 2 = MIXED)
# ==============================================================================
print("\n" + "=" * 60)
print("2. Training Intent Classifier from datasets/intent_dataset.xlsx")
print("=" * 60)

int_df = pd.read_excel('datasets/intent_dataset.xlsx')
int_texts = int_df['sentence'].astype(str).values
int_labels = int_df['label'].astype(int).values

print(f"Loaded {len(int_texts)} sentences. Class distribution: {np.bincount(int_labels)}")

# Split for validation
X_train_i, X_test_i, y_train_i, y_test_i = train_test_split(
    int_texts, int_labels, test_size=0.15, random_state=42, stratify=int_labels
)

# Vectorizer matching Kotlin implementation
int_vec = TfidfVectorizer(
    ngram_range=(1, 2),
    token_pattern=r'(?u)\b\w\w+\b',
    sublinear_tf=True,
    min_df=2
)
X_train_i_vec = int_vec.fit_transform(X_train_i)
X_test_i_vec = int_vec.transform(X_test_i)

# Multinomial Logistic Regression
int_model = LogisticRegression(C=2.0, max_iter=1000, multi_class='multinomial', random_state=42)
int_model.fit(X_train_i_vec, y_train_i)

# Validation Metrics
y_pred_i = int_model.predict(X_test_i_vec)
val_acc_i = accuracy_score(y_test_i, y_pred_i)
print(f"Validation Accuracy: {val_acc_i * 100:.2f}%")
print(classification_report(y_test_i, y_pred_i, target_names=['ASKING (0)', 'TELLING (1)', 'MIXED (2)']))

# Retrain on full dataset for maximum coverage
full_int_vec = TfidfVectorizer(
    ngram_range=(1, 2),
    token_pattern=r'(?u)\b\w\w+\b',
    sublinear_tf=True,
    min_df=2
)
X_full_i = full_int_vec.fit_transform(int_texts)
full_int_model = LogisticRegression(C=2.0, max_iter=1000, multi_class='multinomial', random_state=42)
full_int_model.fit(X_full_i, int_labels)

cv_scores_i = cross_val_score(full_int_model, X_full_i, int_labels, cv=5)
print(f"Full 5-Fold Cross-Validation Accuracy: {cv_scores_i.mean() * 100:.2f}% (+/- {cv_scores_i.std() * 100:.2f}%)")

# Export to JSON format for Kotlin LocalTextClassifier
int_export = {
    "type": "multiclass_logistic",
    "sublinear_tf": True,
    "classes": full_int_model.classes_.tolist(),
    "vocabulary": {term: int(idx) for term, idx in full_int_vec.vocabulary_.items()},
    "idf": full_int_vec.idf_.tolist(),
    "coef": full_int_model.coef_.tolist(),
    "intercepts": full_int_model.intercept_.tolist()
}

int_path = 'app/src/main/assets/models/intent_classifier.json'
with open(int_path, 'w', encoding='utf-8') as f:
    json.dump(int_export, f)

print(f"Successfully saved intent model to {int_path} ({os.path.getsize(int_path)} bytes, vocab={len(full_int_vec.vocabulary_)})")
print("\n>>> TRAINING COMPLETED SUCCESSFULLY! <<<")
