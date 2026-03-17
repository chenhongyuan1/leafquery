package com.example.leafquery.controller;

import com.example.leafquery.service.DoubaoAiService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * AI 分析相关接口。
 */
@RestController
@RequestMapping("/api/ai")
@CrossOrigin(origins = "*")
public class AiController {

    private final DoubaoAiService doubaoAiService;

    public AiController(DoubaoAiService doubaoAiService) {
        this.doubaoAiService = doubaoAiService;
    }

    /**
     * 根据病虫害名称获取 AI 详细分析。
     *
     * @param pestName 病虫害名称
     * @return AI 生成的 Markdown 分析文本
     */
    @PostMapping("/analyze")
    public ResponseEntity<?> analyzePest(@RequestBody Map<String, String> body) {
        String pestName = body.get("pestName");
        if (pestName == null || pestName.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "请提供病虫害名称"));
        }

        String analysis = doubaoAiService.analyzePest(pestName);
        return ResponseEntity.ok(Map.of("analysis", analysis));
    }

    /**
     * 多轮对话接口，基于之前的病虫害上下文继续交流。
     *
     * @param request 包含病虫害名称和聊天历史记录
     * @return AI 生成的最新回复文本
     */
    @PostMapping("/chat")
    public ResponseEntity<?> chatWithAi(@RequestBody com.example.leafquery.dto.ChatRequest request) {
        if (request.getPestName() == null || request.getPestName().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "缺少病虫害上下文名称"));
        }
        if (request.getMessages() == null || request.getMessages().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "聊天记录不能为空"));
        }

        String reply = doubaoAiService.chatWithDoubao(request.getPestName(), request.getMessages());
        return ResponseEntity.ok(Map.of("reply", reply));
    }

    /**
     * 语音转文字后端中转接口。
     * 提供安全的多端 (App/Web/H5) 音频转录文字能力。
     *
     * @param audio 前端录音返回的多段视频流数据 (如 .webm 格式 Blob)
     * @return 解析得出的对应文字
     */
    @PostMapping("/speech-to-text")
    public ResponseEntity<?> speechToText(
            @RequestParam("audio") org.springframework.web.multipart.MultipartFile audio,
            @RequestParam(value = "dialect", required = false, defaultValue = "zh-CN") String dialect) {
        if (audio == null || audio.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "The uploaded audio file is missing or empty."));
        }

        String transcription = doubaoAiService.speechToText(audio, dialect);
        return ResponseEntity.ok(Map.of("text", transcription));
    }

    /**
     * 文字转语音接口。
     * 接收文本，返回 MP3 音频流。
     *
     * @param body 包含 text 字段的 JSON
     * @return MP3 音频字节流
     */
    @PostMapping("/text-to-speech")
    public ResponseEntity<?> textToSpeech(@RequestBody Map<String, String> body) {
        String text = body.get("text");
        if (text == null || text.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "请提供要朗读的文本"));
        }

        try {
            byte[] audioData = doubaoAiService.textToSpeech(text);
            if (audioData == null || audioData.length == 0) {
                return ResponseEntity.status(500).body(Map.of("error", "语音合成失败，未返回音频数据"));
            }

            return ResponseEntity.ok()
                    .header("Content-Type", "audio/mpeg")
                    .header("Content-Disposition", "inline; filename=\"tts.mp3\"")
                    .body(audioData);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "TTS Error: " + e.getMessage()));
        }
    }
}
