import os
import io
import shutil
from flask import Flask, request, jsonify
from flask_cors import CORS
from ultralytics import YOLO
from PIL import Image

app = Flask(__name__)
CORS(app)

MODEL_PATH = "best.pt"
BACKUP_DIR = "model_backups"

# 加载 YOLOv8s 分类模型（启动时加载一次）
model = YOLO(MODEL_PATH)


@app.route("/reload", methods=["POST"])
def reload_model():
    """热重载模型，替换后无需重启服务。"""
    global model
    try:
        model = YOLO(MODEL_PATH)
        return jsonify({"code": 200, "message": "模型重载成功"})
    except Exception as e:
        return jsonify({"code": 500, "message": f"重载失败: {str(e)}"}), 500


@app.route("/upload_model", methods=["POST"])
def upload_model():
    """接收新模型文件，备份旧模型，替换并热重载。"""
    global model
    if "file" not in request.files:
        return jsonify({"code": 400, "message": "未提供模型文件"}), 400

    file = request.files["file"]
    if not file.filename.endswith(".pt"):
        return jsonify({"code": 400, "message": "仅支持 .pt 格式模型文件"}), 400

    try:
        # 备份旧模型
        if os.path.exists(MODEL_PATH):
            os.makedirs(BACKUP_DIR, exist_ok=True)
            from datetime import datetime
            backup_name = f"best_{datetime.now().strftime('%Y%m%d_%H%M%S')}.pt"
            shutil.copy2(MODEL_PATH, os.path.join(BACKUP_DIR, backup_name))

        # 保存新模型
        file.save(MODEL_PATH)

        # 热重载
        model = YOLO(MODEL_PATH)

        return jsonify({
            "code": 200,
            "message": "模型上传并重载成功",
            "fileSize": os.path.getsize(MODEL_PATH)
        })
    except Exception as e:
        return jsonify({"code": 500, "message": f"上传失败: {str(e)}"}), 500


# 英文类名 → 中文翻译
NAME_ZH = {
    "Maize leaf spot": "玉米叶斑病",
    "Maize leaf blight": "玉米叶枯病",
    "Maize streak virus": "玉米条纹病毒病",
    "Maize grasshoper": "玉米蝗虫",
    "Maize leaf beetle": "玉米叶甲虫",
    "Maize fall armyworm": "玉米草地贪夜蛾",
    "Maize healthy": "玉米（健康）",
    "Tomato septoria leaf spot": "番茄壳针孢叶斑病",
    "Tomato leaf blight": "番茄叶枯病",
    "Cashew anthracnose": "腰果炭疽病",
    "Cashew leaf miner": "腰果潜叶蛾",
}


def to_chinese(name):
    """将英文类名翻译为中文，未收录则原样返回。"""
    return NAME_ZH.get(name, name)


@app.route("/predict", methods=["POST"])
def predict():
    """接收图片文件，使用 YOLOv8s 进行病虫害识别，返回 JSON 结果。"""
    if "file" not in request.files:
        return jsonify({"error": "No file provided"}), 400

    file = request.files["file"]
    if file.filename == "":
        return jsonify({"error": "Empty filename"}), 400

    try:
        # 读取图片
        image_bytes = file.read()
        image = Image.open(io.BytesIO(image_bytes)).convert("RGB")

        # YOLOv8 推理
        results = model(image)

        if results and len(results) > 0:
            result = results[0]

            # ===== 分类模型 (result.probs) =====
            if result.probs is not None:
                top1_idx = result.probs.top1
                top1_conf = float(result.probs.top1conf.cpu().numpy())
                pest_name = result.names[top1_idx]

                return jsonify({
                    "pest_name": to_chinese(pest_name),
                    "confidence": round(top1_conf, 4)
                })

            # ===== 检测模型 (result.boxes) =====
            if result.boxes is not None and len(result.boxes) > 0:
                confidences = result.boxes.conf.cpu().numpy()
                class_ids = result.boxes.cls.cpu().numpy().astype(int)
                best_idx = confidences.argmax()

                pest_name = result.names[class_ids[best_idx]]
                confidence = float(confidences[best_idx])

                return jsonify({
                    "pest_name": to_chinese(pest_name),
                    "confidence": round(confidence, 4)
                })

        # 未检测到任何目标
        return jsonify({
            "pest_name": "未识别",
            "confidence": 0.0
        })

    except Exception as e:
        return jsonify({"error": str(e)}), 500


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000, debug=True)