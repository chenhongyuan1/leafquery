package com.example.leafquery.service;

import java.util.regex.Pattern;

/** Report eligibility follows the diagnosis value, not words in the explanation. */
public final class ReviewConclusionPolicy {
    public static final String REPORT_BLOCK_REASON = "复核确诊为“无意义”，不能生成诊断报告。请重新上传清晰的作物或病虫害图片。";
    private static final Pattern DIAGNOSIS = Pattern.compile(
        "(?:【\\s*确诊结果\\s*】\\s*[:：]?|(?:^|[\\r\\n])\\s*确诊结果\\s*[:：])\\s*([^\\r\\n]+)",
        Pattern.UNICODE_CHARACTER_CLASS);

    private ReviewConclusionPolicy() {}

    public static boolean isMeaningless(String reviewResult) {
        if (reviewResult == null || reviewResult.isBlank()) return false;
        String text = reviewResult.replaceAll("(?is)<think>.*?</think>", "")
            .replaceAll("[*`_#>]", "").strip();
        var diagnosis = DIAGNOSIS.matcher(text);
        String value = diagnosis.find() ? diagnosis.group(1) : text;
        value = value.replaceAll("^[\\s\\p{P}\\p{Z}]+|[\\s\\p{P}\\p{Z}]+$", "");
        return "无意义".equals(value);
    }
}
