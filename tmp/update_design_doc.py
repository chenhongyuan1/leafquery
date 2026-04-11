# -*- coding: utf-8 -*-
from docx import Document


DOC_PATH = r"D:\LeafQuery\leafquery\tmp\design_template.docx"


def main():
    doc = Document(DOC_PATH)

    replacements = {
        81: "3. 历史气象参考和环境判断实现的预测逻辑",
        84: "4. 农场档案与诊断记录统一状态管理设计",
        85: "为避免系统在“游客试用”和“登录后长期使用”两种场景下出现数据割裂，前端通过 farmCloud 统一管理作物档案、当前激活作物与识别历史。该层并非独立云服务，而是一个 Pinia 状态仓库，对上向识别页、记录页、趋势页提供一致数据，对下按登录态在本地缓存与云端接口之间切换。",
        86: "游客模式下，系统保留两级本地兜底：识别页将最近诊断历史轻量写入 localStorage 的 leafquery_history，便于用户立即回看；farmCloud 则将作物档案、激活作物及 identificationHistory 持久化到本地缓存，用于预测页、农场页等全局业务恢复。由于存储介质为浏览器 localStorage，该部分数据仅在当前设备的当前浏览器中可见，不会跨设备共享。",
        87: "登录模式下，系统初始化优先从云端装载农场与诊断数据：作物档案由 user_crop 表承载，诊断记录由 identification_record 表承载，二者处于同一 MySQL 数据库而非两套独立数据库。identification_record 通过 crop_id 与 user_crop 关联，同时冗余保存 crop_name、city、region、pest_name 等快照字段，使历史记录在作物名称调整或地区信息变更后仍具备可追溯性。",
        88: "在读写策略上，写入时先完成前端本地落点，保证识别结果即时可见；若用户已登录，再调用 /api/record/add 完成后端持久化。读取时优先按 userId 从云端获取记录，若未登录或网络异常则降级读取本地缓存。该“本地可用、登录上云、失败回退”的双轨设计既降低了游客首次使用门槛，也保证了正式用户跨设备访问时的数据连续性。",
    }

    for index, text in replacements.items():
        doc.paragraphs[index].text = text

    doc.save(DOC_PATH)


if __name__ == "__main__":
    main()
