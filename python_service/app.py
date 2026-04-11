import io
import json
import os
import shutil

# Must be configured before importing ultralytics, otherwise config writes may fail.
os.environ["YOLO_CONFIG_DIR"] = os.path.join(
    os.path.dirname(os.path.abspath(__file__)), ".ultralytics"
)

from flask import Flask, jsonify, request
from flask_cors import CORS
from PIL import Image
from ultralytics import YOLO

from class_name_translation_clean import translate_class_name, get_target_metadata

app = Flask(__name__)
CORS(app)

MODEL_PATH = "best.pt"
BACKUP_DIR = "model_backups"
DETECTION_BOX_THRESHOLD = float(os.environ.get("YOLO_DETECTION_BOX_THRESHOLD", "0.35"))
PRIMARY_CONFIDENCE_THRESHOLD = float(
    os.environ.get("YOLO_DETECTION_PRIMARY_THRESHOLD", "0.60")
)
UNKNOWN_LABEL = "\u672a\u8bc6\u522b"

# ── 兜底值：JSON 加载失败时使用 ──
_FALLBACK_YOLO_SUPPORTED = {"水稻", "玉米", "小麦", "虫害"}


def _load_category_index():
    """加载 detection_target_metadata_clean.json，构建：
    1. 类名 → 作物和类型的索引（用于 is_class_allowed 筛查）
    2. yoloSupportedCategories（与 Java 端共享的用户类别集）
    """
    # 优先读取系统环境变量（生产环境通过环境变量绝对路径挂载防丢）
    catalog_path = os.environ.get("DETECTION_METADATA_PATH")
    if not (catalog_path and os.path.exists(catalog_path)):
        catalog_path = os.path.join(
            os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
            "docs",
            "detection_target_metadata_clean.json",
        )
    supported = _FALLBACK_YOLO_SUPPORTED
    try:
        with open(catalog_path, "r", encoding="utf-8") as f:
            payload = json.load(f)
    except FileNotFoundError:
        print(f"[WARN] Metadata file not found: {catalog_path}, using fallback categories")
        return {}, supported

    # ── 读取 yoloSupportedCategories ──
    raw_cats = payload.get("yoloSupportedCategories", [])
    if raw_cats:
        loaded = {c.strip() for c in raw_cats if isinstance(c, str) and c.strip()}
        if loaded:
            supported = loaded
            print(f"[INFO] Loaded yoloSupportedCategories from {catalog_path}: {supported}")
        else:
            print(f"[WARN] yoloSupportedCategories empty in {catalog_path}, using fallback")
    else:
        print(f"[WARN] yoloSupportedCategories not found in {catalog_path}, using fallback")

    # ── 构建类名索引 ──
    targets = payload.get("targets", [])
    index = {}
    for item in targets:
        class_name = str(item.get("className", "")).strip().replace("-", "_").lower()
        if class_name:
            index[class_name] = {
                "crop_names": [c.strip() for c in item.get("cropNames", [])],
                "target_type": item.get("targetType", "unknown"),
            }
    return index, supported


_loaded = _load_category_index()
CATEGORY_INDEX = _loaded[0]
YOLO_SUPPORTED_CATEGORIES = _loaded[1]

model = YOLO(MODEL_PATH)


def is_class_allowed(class_name, categories):
    """判断一个 YOLO 类名是否被用户选择的类别所允许。

    规则：
    - 用户选了 "水稻"：允许 cropNames 包含 "水稻" 的所有类（病害+虫害+健康）
    - 用户选了 "虫害"：允许 targetType == "pest" 的所有类（不限作物）
    - 用户选了 "其他"：该函数不会被调用（其他不走YOLO）
    - 多选取并集
    """
    normalized = class_name.strip().replace("-", "_").lower()
    meta = CATEGORY_INDEX.get(normalized)
    if meta is None:
        # 未知类名（不在元数据中），保守保留
        return True

    for cat in categories:
        if cat == "虫害":
            if meta["target_type"] == "pest":
                return True
        elif cat in ("水稻", "玉米", "小麦"):
            if cat in meta["crop_names"]:
                return True
    return False


@app.route("/reload", methods=["POST"])
def reload_model():
    """Hot reload the model after replacing best.pt."""
    global model
    try:
        model = YOLO(MODEL_PATH)
        return jsonify({"code": 200, "message": "model reloaded"})
    except Exception as exc:
        return jsonify({"code": 500, "message": f"reload failed: {exc}"}), 500


@app.route("/upload_model", methods=["POST"])
def upload_model():
    """Upload a new .pt model, back up the old one, and hot reload."""
    global model
    if "file" not in request.files:
        return jsonify({"code": 400, "message": "missing model file"}), 400

    file = request.files["file"]
    if not file.filename.endswith(".pt"):
        return jsonify({"code": 400, "message": "only .pt model files are supported"}), 400

    try:
        if os.path.exists(MODEL_PATH):
            os.makedirs(BACKUP_DIR, exist_ok=True)
            from datetime import datetime

            backup_name = f"best_{datetime.now().strftime('%Y%m%d_%H%M%S')}.pt"
            shutil.copy2(MODEL_PATH, os.path.join(BACKUP_DIR, backup_name))

        file.save(MODEL_PATH)
        model = YOLO(MODEL_PATH)

        return jsonify(
            {
                "code": 200,
                "message": "model uploaded and reloaded",
                "fileSize": os.path.getsize(MODEL_PATH),
            }
        )
    except Exception as exc:
        return jsonify({"code": 500, "message": f"upload failed: {exc}"}), 500


def build_empty_prediction(scene_type="empty"):
    return {
        "pest_name": UNKNOWN_LABEL,
        "confidence": 0.0,
        "scene_type": scene_type,
        "primary_target": "",
        "primary_target_zh": UNKNOWN_LABEL,
        "primary_confidence": 0.0,
        "class_count": 0,
        "target_count": 0,
        "class_names_zh": [],
        "detected_summary": [],
    }


def summarize_detections(result, categories=None):
    """汇总检测框，可选按用户类别过滤。"""
    raw_boxes = result.boxes
    if raw_boxes is None or len(raw_boxes) == 0:
        return None, 0

    confidences = raw_boxes.conf.cpu().tolist()
    class_ids = [int(value) for value in raw_boxes.cls.cpu().tolist()]
    raw_target_count = len(class_ids)

    summary_by_name = {}
    filtered_count = 0
    for confidence, class_id in zip(confidences, class_ids):
        confidence = float(confidence)
        if confidence < DETECTION_BOX_THRESHOLD:
            continue

        class_name = result.names[class_id]

        # ===== 用户类别硬性筛查 =====
        if categories and not is_class_allowed(class_name, categories):
            filtered_count += 1
            continue

        item = summary_by_name.setdefault(
            class_name,
            {
                "name": class_name,
                "name_zh": translate_class_name(class_name),
                "count": 0,
                "max_confidence": 0.0,
                "confidence_sum": 0.0,
            },
        )
        item["count"] += 1
        item["max_confidence"] = max(item["max_confidence"], confidence)
        item["confidence_sum"] += confidence

    detected_summary = []
    for item in summary_by_name.values():
        detected_summary.append(
            {
                "name": item["name"],
                "name_zh": item["name_zh"],
                "count": item["count"],
                "max_confidence": round(item["max_confidence"], 4),
                "avg_confidence": round(item["confidence_sum"] / item["count"], 4),
            }
        )

    detected_summary.sort(
        key=lambda entry: (-entry["max_confidence"], -entry["count"], entry["name"])
    )
    return detected_summary, raw_target_count


def build_detection_prediction(result, categories=None):
    detected_summary, raw_target_count = summarize_detections(result, categories)
    if not detected_summary:
        return build_empty_prediction("uncertain" if raw_target_count > 0 else "empty")

    primary = detected_summary[0]
    primary_confidence = float(primary["max_confidence"])
    class_names_zh = [entry["name_zh"] for entry in detected_summary]
    target_count = sum(entry["count"] for entry in detected_summary)

    if primary_confidence < PRIMARY_CONFIDENCE_THRESHOLD:
        scene_type = "uncertain"
    elif len(detected_summary) == 1:
        scene_type = "single"
    else:
        scene_type = "multi"

    return {
        "pest_name": primary["name_zh"],
        "confidence": round(primary_confidence, 4),
        "scene_type": scene_type,
        "primary_target": primary["name"],
        "primary_target_zh": primary["name_zh"],
        "primary_confidence": round(primary_confidence, 4),
        "class_count": len(detected_summary),
        "target_count": target_count,
        "class_names_zh": class_names_zh,
        "detected_summary": detected_summary,
    }


def build_classification_prediction(result):
    top1_idx = result.probs.top1
    top1_conf = float(result.probs.top1conf.cpu().numpy())
    class_name = result.names[top1_idx]
    class_name_zh = translate_class_name(class_name)
    scene_type = "single" if top1_conf >= PRIMARY_CONFIDENCE_THRESHOLD else "uncertain"

    return {
        "pest_name": class_name_zh,
        "confidence": round(top1_conf, 4),
        "scene_type": scene_type,
        "primary_target": class_name,
        "primary_target_zh": class_name_zh,
        "primary_confidence": round(top1_conf, 4),
        "class_count": 1,
        "target_count": 1,
        "class_names_zh": [class_name_zh],
        "detected_summary": [
            {
                "name": class_name,
                "name_zh": class_name_zh,
                "count": 1,
                "max_confidence": round(top1_conf, 4),
                "avg_confidence": round(top1_conf, 4),
            }
        ],
    }


@app.route("/predict", methods=["POST"])
def predict():
    """Run YOLO inference with optional category filtering."""
    if "file" not in request.files:
        return jsonify({"error": "No file provided"}), 400

    file = request.files["file"]
    if file.filename == "":
        return jsonify({"error": "Empty filename"}), 400

    # 解析用户选择的类别（逗号分隔字符串或 JSON 数组）
    categories_raw = request.form.get("categories", "")
    categories = []
    if categories_raw:
        try:
            categories = json.loads(categories_raw)
        except (json.JSONDecodeError, TypeError):
            categories = [c.strip() for c in categories_raw.split(",") if c.strip()]

    try:
        image_bytes = file.read()
        image = Image.open(io.BytesIO(image_bytes)).convert("RGB")
        results = model(image, verbose=False)

        if results:
            result = results[0]

            if result.boxes is not None and len(result.boxes) > 0:
                return jsonify(build_detection_prediction(result, categories or None))

            if result.probs is not None:
                return jsonify(build_classification_prediction(result))

        return jsonify(build_empty_prediction())
    except Exception as exc:
        return jsonify({"error": str(exc)}), 500


if __name__ == "__main__":
    port = int(os.environ.get("PORT", 5000))
    app.run(host="0.0.0.0", port=port, debug=False)
