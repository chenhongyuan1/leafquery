package com.example.leafquery.dto;

import java.util.List;

public class ChatRequest {
    private String pestName;
    private List<Message> messages;

    public String getPestName() {
        return pestName;
    }

    public void setPestName(String pestName) {
        this.pestName = pestName;
    }

    public List<Message> getMessages() {
        return messages;
    }

    public void setMessages(List<Message> messages) {
        this.messages = messages;
    }

    public static class Message {
        private String role;
        private String content;
        private String imageBase64; // Base64 编码的图片数据，用于提供视觉理解（多模态）能力

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public String getImageBase64() {
            return imageBase64;
        }

        public void setImageBase64(String imageBase64) {
            this.imageBase64 = imageBase64;
        }
    }
}
