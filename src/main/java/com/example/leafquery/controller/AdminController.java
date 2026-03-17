package com.example.leafquery.controller;

import com.example.leafquery.entity.*;
import com.example.leafquery.mapper.*;
import com.example.leafquery.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 后台管理系统统一控制器
 * 所有接口统一挂载在 /api/admin/** 路径下
 */
@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "*")
public class AdminController {

    @Autowired
    private AdminAuthService adminAuthService;
    @Autowired
    private AnnouncementService announcementService;
    @Autowired
    private ModelConfigService modelConfigService;
    @Autowired
    private AdminLogService adminLogService;
    @Autowired
    private DiscoveryService discoveryService;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private NewsMapper newsMapper;
    @Autowired
    private KnowledgeMapper knowledgeMapper;
    @Autowired
    private QnaMapper qnaMapper;
    @Autowired
    private RecordMapper recordMapper;

    // ==================== 登录 ====================

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> params,
            HttpServletRequest request) {
        Map<String, Object> res = new HashMap<>();
        String username = params.get("username");
        String password = params.get("password");

        if (username == null || password == null) {
            res.put("code", 400);
            res.put("message", "用户名或密码不能为空");
            return ResponseEntity.badRequest().body(res);
        }

        AdminUser admin = adminAuthService.login(username, password);
        if (admin != null) {
            admin.setPassword(null); // 不返回密码
            adminLogService.log(admin.getAdminId(), "登录", "管理系统",
                    "管理员 " + admin.getUsername() + " 登录系统", getClientIp(request));
            res.put("code", 200);
            res.put("message", "登录成功");
            res.put("data", admin);
            return ResponseEntity.ok(res);
        }

        res.put("code", 401);
        res.put("message", "用户名或密码错误，或账号已禁用");
        return ResponseEntity.status(401).body(res);
    }

    // ==================== 仪表盘统计 ====================

    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> dashboard() {
        Map<String, Object> res = new HashMap<>();
        Map<String, Object> stats = new HashMap<>();
        stats.put("userCount", userMapper.selectAll().size());
        stats.put("recordCount", recordMapper.countAll());
        stats.put("newsCount", newsMapper.countAll());
        stats.put("knowledgeCount", knowledgeMapper.countAll());
        stats.put("qnaCount", qnaMapper.countPosts());
        stats.put("announcementCount", announcementService.countAll());
        stats.put("adminCount", adminAuthService.countAll());
        stats.put("logCount", adminLogService.countAll());
        res.put("code", 200);
        res.put("data", stats);
        return ResponseEntity.ok(res);
    }

    // ==================== 公告管理 ====================

    @GetMapping("/announcements")
    public ResponseEntity<Map<String, Object>> getAnnouncements() {
        Map<String, Object> res = new HashMap<>();
        res.put("code", 200);
        res.put("data", announcementService.getAll());
        return ResponseEntity.ok(res);
    }

    @PostMapping("/announcements")
    public ResponseEntity<Map<String, Object>> createAnnouncement(@RequestBody Announcement announcement,
            HttpServletRequest request) {
        Map<String, Object> res = new HashMap<>();
        Announcement created = announcementService.create(announcement);
        adminLogService.log(announcement.getAdminId(), "新增公告", announcement.getTitle(),
                "创建公告: " + announcement.getTitle(), getClientIp(request));
        res.put("code", 200);
        res.put("data", created);
        return ResponseEntity.ok(res);
    }

    @PutMapping("/announcements/{id}")
    public ResponseEntity<Map<String, Object>> updateAnnouncement(@PathVariable Long id,
            @RequestBody Announcement announcement,
            HttpServletRequest request) {
        Map<String, Object> res = new HashMap<>();
        announcement.setId(id);
        String result = announcementService.update(announcement);
        if ("success".equals(result)) {
            adminLogService.log(announcement.getAdminId(), "更新公告", announcement.getTitle(),
                    "更新公告ID=" + id, getClientIp(request));
            res.put("code", 200);
            res.put("message", "更新成功");
        } else {
            res.put("code", 400);
            res.put("message", result);
        }
        return ResponseEntity.ok(res);
    }

    @DeleteMapping("/announcements/{id}")
    public ResponseEntity<Map<String, Object>> deleteAnnouncement(@PathVariable Long id,
            @RequestParam(required = false) Long adminId,
            HttpServletRequest request) {
        Map<String, Object> res = new HashMap<>();
        String result = announcementService.delete(id);
        if ("success".equals(result)) {
            adminLogService.log(adminId, "删除公告", "公告ID=" + id,
                    "删除公告ID=" + id, getClientIp(request));
            res.put("code", 200);
            res.put("message", "删除成功");
        } else {
            res.put("code", 400);
            res.put("message", result);
        }
        return ResponseEntity.ok(res);
    }

    // ==================== 用户管理 ====================

    @GetMapping("/users")
    public ResponseEntity<Map<String, Object>> getUsers() {
        Map<String, Object> res = new HashMap<>();
        List<User> users = userMapper.selectAll();
        // 不返回密码
        for (User u : users) {
            u.setPassword(null);
        }
        res.put("code", 200);
        res.put("data", users);
        return ResponseEntity.ok(res);
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<Map<String, Object>> getUserDetail(@PathVariable Long userId) {
        Map<String, Object> res = new HashMap<>();
        User user = userMapper.selectByUserId(userId);
        if (user != null) {
            user.setPassword(null);
            res.put("code", 200);
            res.put("data", user);
        } else {
            res.put("code", 404);
            res.put("message", "用户不存在");
        }
        return ResponseEntity.ok(res);
    }

    @PostMapping("/users")
    public ResponseEntity<Map<String, Object>> createUser(@RequestBody User user,
            HttpServletRequest request) {
        Map<String, Object> res = new HashMap<>();
        // 检查用户名是否已存在
        if (userMapper.selectByUsername(user.getUsername()) != null) {
            res.put("code", 400);
            res.put("message", "用户名已被注册");
            return ResponseEntity.badRequest().body(res);
        }
        userMapper.insertUser(user);
        adminLogService.log(null, "新增前台用户", user.getUsername(),
                "创建前台用户: " + user.getUsername(), getClientIp(request));
        res.put("code", 200);
        res.put("data", user);
        return ResponseEntity.ok(res);
    }

    @PutMapping("/users/{userId}")
    public ResponseEntity<Map<String, Object>> updateUser(@PathVariable Long userId,
            @RequestBody User user,
            HttpServletRequest request) {
        Map<String, Object> res = new HashMap<>();
        // 检查用户名是否被其他用户占用
        if (user.getUsername() != null) {
            User existing = userMapper.selectByUsername(user.getUsername());
            if (existing != null && !existing.getUserId().equals(userId)) {
                res.put("code", 400);
                res.put("message", "用户名已被其他用户使用");
                return ResponseEntity.badRequest().body(res);
            }
        }
        user.setUserId(userId);
        userMapper.updateUser(user);
        adminLogService.log(null, "更新前台用户", user.getUsername(),
                "更新前台用户ID=" + userId, getClientIp(request));
        res.put("code", 200);
        res.put("message", "更新成功");
        return ResponseEntity.ok(res);
    }

    @DeleteMapping("/users/{userId}")
    public ResponseEntity<Map<String, Object>> deleteUser(@PathVariable Long userId,
            HttpServletRequest request) {
        Map<String, Object> res = new HashMap<>();
        userMapper.deleteByUserId(userId);
        adminLogService.log(null, "删除前台用户", "用户ID=" + userId,
                "删除前台用户ID=" + userId, getClientIp(request));
        res.put("code", 200);
        res.put("message", "删除成功");
        return ResponseEntity.ok(res);
    }

    // ==================== 发现管理 (资讯/知识库/问答) ====================

    // --- 资讯 ---
    @GetMapping("/discovery/news")
    public ResponseEntity<Map<String, Object>> getAdminNews() {
        Map<String, Object> res = new HashMap<>();
        res.put("code", 200);
        res.put("data", discoveryService.getAllNews());
        return ResponseEntity.ok(res);
    }

    @PostMapping("/discovery/news")
    public ResponseEntity<Map<String, Object>> createNews(@RequestBody News news,
            HttpServletRequest request) {
        Map<String, Object> res = new HashMap<>();
        newsMapper.insertNews(news);
        adminLogService.log(null, "新增资讯", news.getTitle(),
                "创建资讯: " + news.getTitle(), getClientIp(request));
        res.put("code", 200);
        res.put("data", news);
        return ResponseEntity.ok(res);
    }

    @PutMapping("/discovery/news/{newsId}")
    public ResponseEntity<Map<String, Object>> updateNews(@PathVariable Long newsId,
            @RequestBody News news,
            HttpServletRequest request) {
        Map<String, Object> res = new HashMap<>();
        news.setNewsId(newsId);
        newsMapper.updateNews(news);
        adminLogService.log(null, "更新资讯", news.getTitle(),
                "更新资讯ID=" + newsId, getClientIp(request));
        res.put("code", 200);
        res.put("message", "更新成功");
        return ResponseEntity.ok(res);
    }

    @DeleteMapping("/discovery/news/{newsId}")
    public ResponseEntity<Map<String, Object>> deleteNews(@PathVariable Long newsId,
            HttpServletRequest request) {
        Map<String, Object> res = new HashMap<>();
        newsMapper.deleteByNewsId(newsId);
        adminLogService.log(null, "删除资讯", "资讯ID=" + newsId,
                "删除资讯ID=" + newsId, getClientIp(request));
        res.put("code", 200);
        res.put("message", "删除成功");
        return ResponseEntity.ok(res);
    }

    // --- 知识库 ---
    @GetMapping("/discovery/knowledge")
    public ResponseEntity<Map<String, Object>> getAdminKnowledge() {
        Map<String, Object> res = new HashMap<>();
        res.put("code", 200);
        res.put("data", discoveryService.getAllKnowledge());
        return ResponseEntity.ok(res);
    }

    @PostMapping("/discovery/knowledge")
    public ResponseEntity<Map<String, Object>> createKnowledge(@RequestBody Knowledge knowledge,
            HttpServletRequest request) {
        Map<String, Object> res = new HashMap<>();
        knowledgeMapper.insertKnowledge(knowledge);
        adminLogService.log(null, "新增知识条目", knowledge.getTitle(),
                "创建知识条目: " + knowledge.getTitle(), getClientIp(request));
        res.put("code", 200);
        res.put("data", knowledge);
        return ResponseEntity.ok(res);
    }

    @PutMapping("/discovery/knowledge/{knowledgeId}")
    public ResponseEntity<Map<String, Object>> updateKnowledge(@PathVariable Long knowledgeId,
            @RequestBody Knowledge knowledge,
            HttpServletRequest request) {
        Map<String, Object> res = new HashMap<>();
        knowledge.setKnowledgeId(knowledgeId);
        knowledgeMapper.updateKnowledge(knowledge);
        adminLogService.log(null, "更新知识条目", knowledge.getTitle(),
                "更新知识条目ID=" + knowledgeId, getClientIp(request));
        res.put("code", 200);
        res.put("message", "更新成功");
        return ResponseEntity.ok(res);
    }

    @DeleteMapping("/discovery/knowledge/{knowledgeId}")
    public ResponseEntity<Map<String, Object>> deleteKnowledge(@PathVariable Long knowledgeId,
            HttpServletRequest request) {
        Map<String, Object> res = new HashMap<>();
        knowledgeMapper.deleteByKnowledgeId(knowledgeId);
        adminLogService.log(null, "删除知识条目", "ID=" + knowledgeId,
                "删除知识条目ID=" + knowledgeId, getClientIp(request));
        res.put("code", 200);
        res.put("message", "删除成功");
        return ResponseEntity.ok(res);
    }

    // --- 问答 ---
    @GetMapping("/discovery/qna")
    public ResponseEntity<Map<String, Object>> getAdminQna() {
        Map<String, Object> res = new HashMap<>();
        res.put("code", 200);
        res.put("data", discoveryService.getAllPosts());
        return ResponseEntity.ok(res);
    }

    @DeleteMapping("/discovery/qna/{postId}")
    public ResponseEntity<Map<String, Object>> deleteQnaPost(@PathVariable Long postId,
            HttpServletRequest request) {
        Map<String, Object> res = new HashMap<>();
        qnaMapper.deletePostById(postId);
        adminLogService.log(null, "删除问答帖子", "帖子ID=" + postId,
                "删除问答帖子ID=" + postId, getClientIp(request));
        res.put("code", 200);
        res.put("message", "删除成功");
        return ResponseEntity.ok(res);
    }

    @PutMapping("/discovery/qna/{postId}/review")
    public ResponseEntity<Map<String, Object>> reviewQnaPost(@PathVariable Long postId,
            @RequestBody Map<String, Integer> body,
            HttpServletRequest request) {
        Map<String, Object> res = new HashMap<>();
        Integer status = body.get("status");
        qnaMapper.updateStatus(postId, status);
        String action = status == 1 ? "通过" : "拒绝";
        adminLogService.log(null, "审核问答帖子", "帖子ID=" + postId,
                action + "问答帖子ID=" + postId, getClientIp(request));
        res.put("code", 200);
        res.put("message", "审核" + action);
        return ResponseEntity.ok(res);
    }

    // ==================== 系统管理 (管理员账号) ====================

    @GetMapping("/system/admins")
    public ResponseEntity<Map<String, Object>> getAdmins() {
        Map<String, Object> res = new HashMap<>();
        List<AdminUser> admins = adminAuthService.getAllAdmins();
        // 不返回密码
        for (AdminUser a : admins) {
            a.setPassword(null);
        }
        res.put("code", 200);
        res.put("data", admins);
        return ResponseEntity.ok(res);
    }

    @PostMapping("/system/admins")
    public ResponseEntity<Map<String, Object>> createAdmin(@RequestBody AdminUser admin,
            @RequestHeader(value = "X-Admin-Role", required = false) String callerRole,
            HttpServletRequest request) {
        Map<String, Object> res = new HashMap<>();
        // 仅 super_admin 可以创建管理员
        if (!"super_admin".equals(callerRole)) {
            res.put("code", 403);
            res.put("message", "权限不足，仅系统管理员可操作");
            return ResponseEntity.status(403).body(res);
        }
        String result = adminAuthService.createAdmin(admin);
        if ("success".equals(result)) {
            adminLogService.log(null, "新增管理员", admin.getUsername(),
                    "创建管理员: " + admin.getUsername(), getClientIp(request));
            admin.setPassword(null);
            res.put("code", 200);
            res.put("data", admin);
        } else {
            res.put("code", 400);
            res.put("message", result);
        }
        return ResponseEntity.ok(res);
    }

    @PutMapping("/system/admins/{adminId}")
    public ResponseEntity<Map<String, Object>> updateAdmin(@PathVariable Long adminId,
            @RequestBody AdminUser admin,
            @RequestHeader(value = "X-Admin-Role", required = false) String callerRole,
            HttpServletRequest request) {
        Map<String, Object> res = new HashMap<>();
        if (!"super_admin".equals(callerRole)) {
            res.put("code", 403);
            res.put("message", "权限不足，仅系统管理员可操作");
            return ResponseEntity.status(403).body(res);
        }
        admin.setAdminId(adminId);
        String result = adminAuthService.updateAdmin(admin);
        if ("success".equals(result)) {
            adminLogService.log(null, "更新管理员", admin.getUsername(),
                    "更新管理员ID=" + adminId, getClientIp(request));
            res.put("code", 200);
            res.put("message", "更新成功");
        } else {
            res.put("code", 400);
            res.put("message", result);
        }
        return ResponseEntity.ok(res);
    }

    @DeleteMapping("/system/admins/{adminId}")
    public ResponseEntity<Map<String, Object>> deleteAdmin(@PathVariable Long adminId,
            @RequestHeader(value = "X-Admin-Role", required = false) String callerRole,
            HttpServletRequest request) {
        Map<String, Object> res = new HashMap<>();
        if (!"super_admin".equals(callerRole)) {
            res.put("code", 403);
            res.put("message", "权限不足，仅系统管理员可操作");
            return ResponseEntity.status(403).body(res);
        }
        String result = adminAuthService.deleteAdmin(adminId);
        if ("success".equals(result)) {
            adminLogService.log(null, "删除管理员", "管理员ID=" + adminId,
                    "删除管理员ID=" + adminId, getClientIp(request));
            res.put("code", 200);
            res.put("message", "删除成功");
        } else {
            res.put("code", 400);
            res.put("message", result);
        }
        return ResponseEntity.ok(res);
    }

    // ==================== 模型管理 ====================

    @GetMapping("/models")
    public ResponseEntity<Map<String, Object>> getModelConfigs(@RequestParam(required = false) String category) {
        Map<String, Object> res = new HashMap<>();
        res.put("code", 200);
        if (category != null && !category.isEmpty()) {
            res.put("data", modelConfigService.getByCategory(category));
        } else {
            res.put("data", modelConfigService.getAll());
        }
        return ResponseEntity.ok(res);
    }

    @PutMapping("/models/{id}")
    public ResponseEntity<Map<String, Object>> updateModelConfig(@PathVariable Long id,
            @RequestBody ModelConfig config,
            HttpServletRequest request) {
        Map<String, Object> res = new HashMap<>();
        config.setId(id);
        String result = modelConfigService.update(config);
        if ("success".equals(result)) {
            adminLogService.log(config.getUpdatedBy(), "更新模型配置", config.getConfigKey(),
                    "更新配置: " + config.getConfigKey() + " = " + config.getConfigValue(), getClientIp(request));
            res.put("code", 200);
            res.put("message", "更新成功");
        } else {
            res.put("code", 400);
            res.put("message", result);
        }
        return ResponseEntity.ok(res);
    }

    @PostMapping("/models")
    public ResponseEntity<Map<String, Object>> createModelConfig(@RequestBody ModelConfig config,
            HttpServletRequest request) {
        Map<String, Object> res = new HashMap<>();
        ModelConfig created = modelConfigService.create(config);
        adminLogService.log(config.getUpdatedBy(), "新增模型配置", config.getConfigKey(),
                "新增配置: " + config.getConfigKey(), getClientIp(request));
        res.put("code", 200);
        res.put("data", created);
        return ResponseEntity.ok(res);
    }

    // ==================== 操作日志 ====================

    @GetMapping("/logs")
    public ResponseEntity<Map<String, Object>> getLogs(@RequestParam(required = false) Long adminId) {
        Map<String, Object> res = new HashMap<>();
        res.put("code", 200);
        if (adminId != null) {
            res.put("data", adminLogService.getLogsByAdmin(adminId));
        } else {
            res.put("data", adminLogService.getAllLogs());
        }
        return ResponseEntity.ok(res);
    }

    // ==================== 模型上传 ====================

    @PostMapping("/models/upload")
    public ResponseEntity<Map<String, Object>> uploadModel(
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            HttpServletRequest request) {
        Map<String, Object> res = new HashMap<>();
        if (file.isEmpty() || !file.getOriginalFilename().endsWith(".pt")) {
            res.put("code", 400);
            res.put("message", "请上传 .pt 格式模型文件");
            return ResponseEntity.badRequest().body(res);
        }
        try {
            // 转发到 Python 服务
            String pythonUrl = "http://localhost:5000/upload_model";
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.MULTIPART_FORM_DATA);

            org.springframework.util.LinkedMultiValueMap<String, Object> body = new org.springframework.util.LinkedMultiValueMap<>();
            body.add("file", new org.springframework.core.io.ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            });

            org.springframework.http.HttpEntity<org.springframework.util.MultiValueMap<String, Object>> reqEntity = new org.springframework.http.HttpEntity<>(
                    body, headers);

            org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();
            // 1. 使用 Map<String, Object> 替换裸类型 Map
            // 2. 检查 Python 服务的返回状态码 pyRes.getStatusCode().is2xxSuccessful()
            ResponseEntity<Map<String, Object>> pyRes = restTemplate.exchange(
                    pythonUrl,
                    org.springframework.http.HttpMethod.POST,
                    reqEntity,
                    new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {
                    });

            // 判断如果 Python 服务处理失败，直接抛出异常或返回错误给前端
            if (!pyRes.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("Python 服务处理模型失败");
            }

            adminLogService.log(null, "上传YOLO模型", file.getOriginalFilename(),
                    "上传模型文件: " + file.getOriginalFilename() + " (" + file.getSize() + " bytes)", getClientIp(request));

            res.put("code", 200);
            res.put("message", "模型上传并重载成功");
            res.put("fileName", file.getOriginalFilename());
            res.put("fileSize", file.getSize());
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            res.put("code", 500);
            res.put("message", "上传失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(res);
        }
    }

    // ==================== 工具方法 ====================

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}
