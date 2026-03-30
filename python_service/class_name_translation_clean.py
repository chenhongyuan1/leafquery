"""Helpers for translating detector class names into user-facing labels."""

from __future__ import annotations

import json
import os
from functools import lru_cache


CROP_NAME_ZH = {
    "alfalfa": "苜蓿",
    "beet": "甜菜",
    "cabbage": "甘蓝",
    "cashew": "腰果",
    "corn": "玉米",
    "maize": "玉米",
    "mango": "芒果",
    "paddy": "水稻",
    "peach": "桃",
    "rice": "水稻",
    "tomato": "番茄",
    "wheat": "小麦",
}


def _catalog_path() -> str:
    return os.path.join(
        os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
        "detection_target_metadata_clean.json",
    )


@lru_cache(maxsize=1)
def _catalog_index() -> dict[str, dict]:
    try:
        with open(_catalog_path(), "r", encoding="utf-8") as file:
            payload = json.load(file)
    except FileNotFoundError:
        return {}

    targets = payload.get("targets", payload if isinstance(payload, list) else [])
    index: dict[str, dict] = {}
    for item in targets:
        class_name = str(item.get("className", "")).strip().replace("-", "_").lower()
        if class_name:
            index[class_name] = item
    return index


def _humanize_name(name: str) -> str:
    words = [segment for segment in name.replace("-", "_").split("_") if segment]
    return " ".join(word.capitalize() for word in words) or name


def get_target_metadata(name: str) -> dict | None:
    if not name:
        return None

    normalized = name.strip().replace("-", "_").lower()
    return _catalog_index().get(normalized)


def translate_class_name(name: str) -> str:
    if not name:
        return name

    metadata = get_target_metadata(name)
    if metadata and metadata.get("labelZh"):
        return metadata["labelZh"]

    normalized = name.strip().replace("-", "_").lower()
    if "healthy" in normalized.split("_"):
        crop_name = ""
        for token in normalized.split("_"):
            if token in CROP_NAME_ZH:
                crop_name = CROP_NAME_ZH[token]
                break
        if crop_name:
            return f"{crop_name}（健康）"

    return _humanize_name(name)
