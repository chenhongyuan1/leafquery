-- 创建数据库 (如果不存在)
-- CREATE DATABASE IF NOT EXISTS leafquery CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- USE leafquery_db;

-- ============================
-- 1. 用户表 (统一 user_id 格式)
-- ============================
DROP TABLE IF EXISTS qna_comment;
DROP TABLE IF EXISTS qna_post;
DROP TABLE IF EXISTS knowledge;
DROP TABLE IF EXISTS plant;
DROP TABLE IF EXISTS news;
DROP TABLE IF EXISTS identification_record;
DROP TABLE IF EXISTS user_crop;
DROP TABLE IF EXISTS user_favorite;
DROP TABLE IF EXISTS USER;

CREATE TABLE USER (
    user_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '用户主键ID (自增)',
    username VARCHAR(64) NOT NULL UNIQUE COMMENT '管理员用户名',
    PASSWORD VARCHAR(255) NOT NULL COMMENT '用户密码',
    phone_number VARCHAR(20) UNIQUE COMMENT '手机号',
    email VARCHAR(128) UNIQUE COMMENT '邮箱',
    avatar_url VARCHAR(512) DEFAULT '' COMMENT '用户头像URL',
    ROLE VARCHAR(32) DEFAULT 'user' COMMENT '角色: super_admin / admin',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    INDEX idx_username (username)
) ENGINE=INNODB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户信息表';

-- ============================
-- 2. 资讯/推荐表
-- ============================
CREATE TABLE news (
    news_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '资讯主键',
    title VARCHAR(255) NOT NULL COMMENT '收藏标题/快照',
    tag VARCHAR(64) DEFAULT '' COMMENT '标签, 如: 热门技术, 政策解读',
    content TEXT COMMENT '公告正文',
    views INT DEFAULT 0 COMMENT '阅读量',
    author_id BIGINT COMMENT '作者用户ID',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    FOREIGN KEY (author_id) REFERENCES user(user_id) ON DELETE SET NULL
) ENGINE=INNODB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资讯推荐表';

-- ============================
-- 3. 植物表
-- ============================
CREATE TABLE plant (
    plant_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '所属植物ID',
    name VARCHAR(64) NOT NULL UNIQUE COMMENT '中文名',
    en_name VARCHAR(64) DEFAULT '' COMMENT '英文名',
    tag VARCHAR(64) DEFAULT '' COMMENT '标签, 如: 热门技术, 政策解读',
    description TEXT COMMENT '收藏摘要内容',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间'
) ENGINE=INNODB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='植物信息表';

-- ============================
-- 4. 知识库条目表 (病虫害/健康 图鉴)
-- ============================
CREATE TABLE knowledge (
    knowledge_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '知识条目主键',
    plant_id BIGINT NOT NULL COMMENT '所属植物ID',
    condition_type VARCHAR(64) NOT NULL COMMENT '类型: 健康/真菌病害/病毒病害/细菌病害/缺素/虫害',
    title VARCHAR(255) NOT NULL COMMENT '收藏标题/快照',
    tag VARCHAR(64) DEFAULT '' COMMENT '标签, 如: 热门技术, 政策解读',
    image_url VARCHAR(512) DEFAULT '' COMMENT '用户传入分析的图片',
    description TEXT COMMENT '收藏摘要内容',
    prevention TEXT COMMENT '防治方法 (JSON数组格式存储)',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    FOREIGN KEY (plant_id) REFERENCES plant(plant_id) ON DELETE CASCADE,
    INDEX idx_plant (plant_id)
) ENGINE=INNODB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='病虫害知识库表';

-- ============================
-- 5. 问答帖子表
-- ============================
CREATE TABLE qna_post (
    post_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '所属帖子ID',
    user_id BIGINT NOT NULL COMMENT '用户主键ID (自增)',
    content TEXT NOT NULL COMMENT '公告正文',
    images VARCHAR(1024) DEFAULT '' COMMENT '图片URL列表 (JSON数组格式)',
    expert_id BIGINT COMMENT '回答专家用户ID',
    expert_reply TEXT COMMENT '专家回复内容',
    likes INT DEFAULT 0 COMMENT '点赞数',
    status TINYINT DEFAULT 0 COMMENT '审核状态: 0=待审核, 1=已通过, 2=已拒绝',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    FOREIGN KEY (user_id) REFERENCES user(user_id) ON DELETE CASCADE,
    FOREIGN KEY (expert_id) REFERENCES user(user_id) ON DELETE SET NULL,
    INDEX idx_user (user_id)
) ENGINE=INNODB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='问答帖子表';

-- ============================
-- 6. 问答评论表
-- ============================
CREATE TABLE qna_comment (
    comment_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '评论主键',
    post_id BIGINT NOT NULL COMMENT '所属帖子ID',
    user_id BIGINT NOT NULL COMMENT '用户主键ID (自增)',
    content TEXT NOT NULL COMMENT '公告正文',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    FOREIGN KEY (post_id) REFERENCES qna_post(post_id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES user(user_id) ON DELETE CASCADE,
    INDEX idx_post (post_id)
) ENGINE=INNODB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='问答评论表';

-- ============================
-- 初始数据: 用户
-- ============================
INSERT INTO user VALUES (1,'admin','123456','18000000000','admin@leafquery.com','https://api.dicebear.com/7.x/avataaars/svg?seed=admin','admin','2026-04-10 14:50:21'),(2,'test','123456','18031989075','1920991993@qq.com','https://api.dicebear.com/7.x/avataaars/svg?seed=testuser','user','2026-04-10 14:50:21'),(3,'果农老李','123456','13800001111','laoli@example.com','','user','2026-04-10 14:50:21'),(4,'张三的菜园','123456','13800002222','zhangsan@example.com','','user','2026-04-10 14:50:21'),(5,'新手种花','123456','13800003333','flower@example.com','','user','2026-04-10 14:50:21'),(6,'王教授','123456','13900001111','wangjiaoshou@example.com','','expert','2026-04-10 14:50:21'),(7,'多肉达人','123456','13900002222','duorou@example.com','','expert','2026-04-10 14:50:21'),(8,'种植大户老王','123456','13700001111','laowang@example.com','','user','2026-04-10 14:50:21'),(9,'农业小助手','123456','13700002222','helper@example.com','','user','2026-04-10 14:50:21'),(10,'热心网友','123456','13700003333','rexin@example.com','','user','2026-04-10 14:50:21'),(11,'多肉爱好者','123456','13700004444','duorou2@example.com','','user','2026-04-10 14:50:21'),(12,'园艺小能手','123456','13700005555','yuanyi@example.com','','user','2026-04-10 14:50:21'),(14,'15832934531','123456','15832934531',NULL,'https://api.dicebear.com/7.x/avataaars/svg?seed=15832934531',NULL,'2026-04-10 15:03:49');

-- ============================
-- 初始数据: 资讯推荐
-- ============================
INSERT INTO news VALUES (1,'夏季西红柿常见病害防治指南','热门技术','夏季高温高湿，是西红柿病害的高发期。常见的病害包括晚疫病、早疫病和灰霉病等。\n\n针对晚疫病，种植户应注意控制田间湿度，及时排涝，发病初期可喷施氟菌霜霉威等药剂。早疫病则多由高温干旱引起，需注意合理灌溉。\n\n此外，及时摘除下部老叶黄叶，保持通风透光，也是预防各类真菌性病害的重要农业措施。建议在新一轮降雨前喷施保护性杀菌剂，如代森锰锌或百菌清，做到预防为主，综合防治。',1240,1,'2026-04-10 14:50:21'),(2,'2026年农业补贴新政策解读','政策解读','为进一步激发农业生产活力，2026年最新出台的农业补贴政策在诸多方面进行了调整优化。\n\n首先是加大了对绿色生态农业的直接补贴力度，使用有机肥、实施测土配方施肥的种植大户将获得额外的高额度补贴。其次是提高了农业机械购置补贴的上限，特别是针对智能农机具、植保无人机等高效设备。\n\n对于水稻、小麦、玉米等主粮作物的种植者，基础补贴标准上调了15%。广大农户应密切关注当地农业农村局发布的具体申报时间与流程，以免错过补贴申请的窗口期。',3500,1,'2026-04-10 14:50:21'),(3,'新型有机肥使用效果对比','技术前沿','近期，农技推广中心在多个试验田开展了新型发酵有机肥与传统化肥的对比试验。结果显示，虽然前期有机肥见效相对较慢，但其在改良土壤结构、提高农产品品质方面具有显著优势。\n\n施用新型有机肥的地块，土壤孔隙度明显增加，持水保肥能力提升，作物根系更加发达。从果蔬产品来看，糖度平均提升了1-2个百分点，且耐储藏性也有所增强。\n\n专家建议，在实际生产中可以采用\"有机肥为主，化肥为辅\"的施肥策略，在满足作物养分需求的同时，逐步培养健壮的土壤生态环境，实现农业的可持续发展。',890,1,'2026-04-10 14:50:21');

-- ============================
-- 初始数据: 植物
-- ============================
INSERT INTO plant VALUES (1,'番茄','Tomato','tomato','一年生或多年生草本植物，广泛种植于各地，果实营养丰富，常见于日常蔬菜生产。','2026-04-10 14:50:21'),(2,'芒果','Mango','mango','热带及亚热带果树，果实香甜多汁，对生长环境和气候条件要求较高。','2026-04-10 14:50:21'),(3,'甘蔗','Sugarcane','sugar','多年生高秆禾本科植物，是重要的糖料作物，广泛种植于热带和亚热带地区。','2026-04-10 14:50:21'),(4,'玉米','Maize','maize','玉米是重要粮饲作物，常见问题包括真菌病、病毒病、虫害与缺素症。','2026-04-10 14:50:21'),(5,'黄瓜','Cucumber','cucumber','一年生蔓生或攀援草本，喜温暖，不耐寒冷，是主要的温室和大棚栽培蔬菜之一。','2026-04-10 14:50:21'),(6,'草莓','Strawberry','strawberry','多年生草本植物，果实鲜红多汁，喜温凉气候，易受白粉病、灰霉病及红蜘蛛影响。','2026-04-10 14:50:21'),(7,'水稻','Rice','rice','重要的粮食作物，生长期间需水量大，常见病害包括稻瘟病、纹枯病和白叶枯病。','2026-04-10 14:50:21');

-- ============================
-- 初始数据: 知识库条目
-- ============================
INSERT INTO knowledge VALUES (1,1,'健康','番茄 (健康状态)','生长正常','/images/library/tomato_healthy.png','植株长势良好，叶片翠绿平展，无扭曲或病斑，茎秆健壮，根系发达。正常浇水施肥即可。','[\"保持适宜的温湿度，注意通风透光。\", \"合理水肥管理，避免偏施氮肥。\"]','2026-04-10 14:50:21'),(2,1,'真菌病害','番茄晚疫病','真菌病害','/images/library/tomato_late_blight.png','主要危害叶片、叶柄和果实。叶片染病，初见暗绿色水浸状斑点，高湿条件下病斑边缘产生白色霉层。','[\"1. 选用抗病品种，如抗疫1号等。\", \"2. 加强田间管理，注意通风透光，降低湿度。\", \"3. 发病初期喷洒 72% 霜脲·锰锌可湿性粉剂或 68.75% 氟菌·霜霉威悬浮剂。\"]','2026-04-10 14:50:21'),(3,1,'病毒病害','番茄黄化曲叶病毒病','病毒','/images/library/tomato_yellow_leaf_curl_virus.png','叶片边缘上卷，叶脉间变黄，植株矮化，严重影响产量。主要由粉虱传播。','[\"1. 选用抗病毒品种。\", \"2. 在棚室通风口设置防虫网，悬挂黄板诱杀粉虱。\", \"3. 及时拔除并销毁病株。\"]','2026-04-10 14:50:21'),(4,1,'缺素/生理障碍','番茄脐腐病','生理障碍','/images/library/tomato_blossom_end_rot.png','果实顶部（脐部）出现水浸状暗绿色斑点，后逐渐扩大变黑凹陷。主要是由于缺钙或水分失调引起。','[\"1. 避免土壤过干过湿，保持水分均衡。\", \"2. 结果期叶面喷施含有氯化钙或硝酸钙的叶面肥。\"]','2026-04-10 14:50:21'),(5,5,'健康','黄瓜 (健康状态)','生长正常','/images/library/cucumber_healthy.png','叶片宽大舒展，颜色浓绿，无退绿斑点，藤蔓生长健壮。','[\"科学搭架引蔓，及时打顶摘心。\", \"均衡施肥，注意补充微量元素。\"]','2026-04-10 14:50:21'),(6,5,'真菌病害','黄瓜霜霉病','真菌病害','/images/library/cucumber_downy_mildew.png','叶片染病，初产生水浸状褪绿斑，受叶脉限制呈多角形黄色斑块。潮湿时叶背面长出紫褐色霉层。','[\"1. 选用抗病品种，合理密植。\", \"2. 采用滴灌或膜下暗灌，降低棚内湿度。\", \"3. 发病初期喷洒 58% 甲霜灵·锰锌或 75% 百菌清可湿性粉剂。\"]','2026-04-10 14:50:21'),(7,5,'细菌病害','黄瓜角斑病','细菌','/images/library/cucumber_angular_leaf_spot.png','叶片受害初期为鲜绿色水浸状斑，后变淡褐色，病斑受叶脉限制呈多角形，潮湿时叶背溢出乳白色菌脓。','[\"1. 种子消毒，温水浸种。\", \"2. 发病初期喷施 72% 农用链霉素可溶性粉剂或中生菌素。\"]','2026-04-10 14:50:21'),(8,5,'虫害','蚜虫','刺吸式','/images/library/aphid.png','成虫和若虫聚集在植物嫩叶、嫩茎等部位吸食汁液，造成叶片卷曲、皱缩，甚至枯萎。','[\"1. 悬挂黄板诱杀有翅蚜。\", \"2. 保护和利用天敌，如瓢虫、草蛉等。\", \"3. 发生严重时喷洒 10% 吡虫啉或 3% 啶虫脒微乳剂。\"]','2026-04-10 14:50:21'),(9,6,'健康','草莓 (健康状态)','生长正常','/images/library/strawberry_healthy.png','叶片厚实挺拔，边缘锯齿清晰，表面无白粉或霉层。','[\"适时覆膜保温，控制土壤水分。\", \"花果期注意疏花疏果。\"]','2026-04-10 14:50:21'),(10,6,'真菌病害','草莓白粉病','真菌病害','/images/library/strawberry_powdery_mildew.png','主要危害叶片、叶柄、花、果实。叶片染病，背面产生白色粉状物，后期叶缘萎缩、枯焦。','[\"1. 栽植前清洁草莓园，烧毁病叶。\", \"2. 合理施肥，避免偏施氮肥，增施磷钾肥。\", \"3. 发病初期喷洒 25% 嘧菌酯或 10% 苯醚甲环唑水分散粒剂。\"]','2026-04-10 14:50:21'),(11,6,'真菌病害','草莓灰霉病','真菌病害','/images/library/strawberry_gray_mold.png','果实染病初呈水浸状小斑，后迅速扩大致使果实腐烂，表面密生灰色霉层。','[\"1. 及时摘除老叶、病叶和病果。\", \"2. 降低棚室湿度，增加通风。\", \"3. 开花前和发病初期喷施 50% 腐霉利或异菌脲。\"]','2026-04-10 14:50:21'),(12,6,'虫害','红蜘蛛','螨类','/images/library/red_spider_mite.png','主要危害叶片，受害部位正面出现黄白色小点，严重时全叶枯黄、脱落，并有蛛丝结网。','[\"1. 清除田间及四周杂草，减少越冬虫源。\", \"2. 干旱时注意灌水，增加环境湿度。\", \"3. 药剂防治可选用 1.8% 阿维菌素或 15% 哒螨灵乳油。\"]','2026-04-10 14:50:21'),(13,4,'健康','玉米 (健康状态)','生长正常','/images/library/corn_healthy.png','叶片狭长呈带状，叶脉平行，颜色深绿，茎秆粗壮直立。','[\"深耕改土，合理密植。\", \"根据生育期科学追肥，尤其是拔节期和大喇叭口期。\"]','2026-04-10 14:50:21'),(14,4,'真菌病害','玉米大斑病','真菌病害','/images/library/corn_northern_leaf_blight.png','主要危害叶片，病斑初期为水浸状，逐渐扩展为长梭形大斑，后期病斑干枯，严重时全株枯死。','[\"1. 种植抗病品种。\", \"2. 适期早播，合理密植。\", \"3. 发病初期喷施 25% 嘧菌酯悬浮剂或吡唑醚菌酯。\"]','2026-04-10 14:50:21'),(15,4,'虫害','玉米螟','钻蛀性','/images/library/corn_borer.png','幼虫初在心叶内啃食叶肉，后蛀食茎秆和果穗，造成折茎、瘪粒和减产。','[\"1. 处理越冬寄主秸秆，消灭越冬幼虫。\", \"2. 大盛期释放赤眼蜂进行生物防治。\", \"3. 心叶末期选用Bt或 5% 氯虫苯甲酰胺滴心或喷雾。\"]','2026-04-10 14:50:21'),(16,4,'虫害','斜纹夜蛾','食叶性','/images/library/armyworm.png','幼虫食量大，暴食性。初孵幼虫群集叶背啃食，大龄幼虫可将叶片吃光仅剩叶脉。','[\"1. 利用黑光灯或性诱剂诱杀成虫。\", \"2. 结合农事操作摘除卵块和初孵幼虫群。\", \"3. 在幼虫3龄前喷洒 20% 氯虫苯甲酰胺或甲维盐。\"]','2026-04-10 14:50:21'),(17,7,'真菌病害','水稻稻瘟病','真菌病害','/images/library/rice_blast.png','水稻全生育期均可发生。根据受害部位分为苗瘟、叶瘟、节瘟等。病斑多呈梭形，常有黄褐色晕圈。','[\"1. 因地制宜选用抗病品种。\", \"2. 科学用水，浅水勤灌，适时露田。\", \"3. 破口期和齐穗期是施药关键期，可使用三环唑、枯草芽孢杆菌等。\"]','2026-04-10 14:50:21'),(18,7,'细菌病害','水稻白叶枯病','细菌','/images/library/rice_blast.png','主要危害叶片。病斑由叶尖或叶缘向下沿叶脉扩展，呈苍白色或黄白色条斑，早晨有黄色菌脓溢出。','[\"1. 杜绝病种，培育无病壮秧。\", \"2. 防止淹水浸灌和串灌。\", \"3. 发病初期使用 20% 噻菌铜悬浮剂或氯溴异氰尿酸。\"]','2026-04-10 14:50:21'),(19,7,'虫害','稻飞虱','刺吸式','/images/library/rice_blast.png','群集于水稻茎基部刺吸汁液，导致水稻黄化、枯死，严重时会导致倒伏。','[\"1. 选用抗性品种。\", \"2. 保护自然天敌。\", \"3. 达到防治指标时，喷施噻嗪酮或吡蚜酮。\"]','2026-04-10 14:50:21');

-- ============================
-- 初始数据: 问答帖子 (user_id 对应上面插入的用户)
-- ============================
INSERT INTO qna_post VALUES (1,3,'请问各位老师，苹果树叶子边缘发黄干枯是怎么回事？','[\"/images/qna/apple.png\"]',6,'从图片来看，很可能是缺钾或者是早期的褐斑病。建议先取样做一下土壤检测，同时叶面喷施磷酸二氢钾进行补充。',2,0,'2026-04-10 14:50:21'),(2,4,'大棚里的这批黄瓜为什么光长叶子不结瓜？','[\"/images/qna/cucumber.png\"]',NULL,NULL,1,0,'2026-04-10 14:50:21'),(3,5,'刚买的多肉，根部变黑了，还能救吗？','[\"/images/qna/succulent.png\"]',7,'这是一典型的黑腐病，需要马上把黑腐的部分切掉，伤口涂抹多菌灵粉末晾干后重新上盆。',1,0,'2026-04-10 14:50:21');

-- ============================
-- 初始数据: 问答评论
-- ============================
INSERT INTO qna_comment VALUES (1,1,8,'感谢专家指点，这就去试试！','2026-04-10 14:50:21'),(2,1,9,'注意用药安全哦','2026-04-10 14:50:21'),(3,2,10,'可能是授粉不足，可以尝试人工授粉。','2026-04-10 14:50:21'),(4,3,11,'切完记得消毒伤口！','2026-04-10 14:50:21'),(5,3,12,'通风也很重要哦','2026-04-10 14:50:21');

-- ============================
-- 7. 识别记录表 (Identification Records)
-- ============================
DROP TABLE IF EXISTS identification_record;

CREATE TABLE identification_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '日志主键',
    user_id BIGINT NOT NULL COMMENT '用户主键ID (自增)',
    pest_name VARCHAR(128) NOT NULL COMMENT '识别出的病虫害名称',
    confidence DOUBLE NOT NULL COMMENT '置信度/匹配率',
    image_url LONGTEXT COMMENT '用户传入分析的图片',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '识别时间',
    INDEX idx_user_id (user_id),
    CONSTRAINT fk_record_user FOREIGN KEY (user_id) REFERENCES user(user_id) ON DELETE CASCADE
) COMMENT '用户病虫害识别记录表';

-- ============================
-- 8. 用户收藏表 (User Favorites)
-- ============================
CREATE TABLE user_favorite (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '日志主键',
    user_id BIGINT NOT NULL COMMENT '用户主键ID (自增)',
    item_type VARCHAR(32) NOT NULL COMMENT '收藏类型: news, library, qna',
    item_id VARCHAR(64) NOT NULL COMMENT '外部主键UUID或ID',
    title VARCHAR(255) COMMENT '收藏标题/快照',
    image_url LONGTEXT COMMENT '用户传入分析的图片',
    description TEXT COMMENT '收藏摘要内容',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '识别时间',
    INDEX idx_fav_user_id (user_id),
    UNIQUE INDEX idx_user_item (user_id, item_type, item_id),
    CONSTRAINT fk_favorite_user FOREIGN KEY (user_id) REFERENCES user(user_id) ON DELETE CASCADE
) COMMENT '用户收藏记录表';

-- ============================
-- 9. 管理员用户表
-- ============================
CREATE TABLE user_crop (
    crop_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '关联作物档案ID快照',
    user_id BIGINT NOT NULL COMMENT '用户主键ID (自增)',
    crop_name VARCHAR(64) NOT NULL COMMENT '识别时的作物名称快照',
    stage VARCHAR(64) DEFAULT '' COMMENT '当前生效物候期',
    province VARCHAR(64) DEFAULT '' COMMENT '省份',
    city VARCHAR(64) DEFAULT '' COMMENT '识别时的城市快照',
    region VARCHAR(64) DEFAULT '' COMMENT '识别时的农业生态区快照',
    location_id VARCHAR(32) DEFAULT '' COMMENT '识别时的和风天气地区ID快照',
    sowing_date DATE NULL COMMENT '播种日期',
    transplant_date DATE NULL COMMENT '移栽日期',
    stage_mode VARCHAR(16) DEFAULT 'MANUAL' COMMENT '物候期模式：AUTO自动判断，MANUAL手动设置',
    estimated_stage VARCHAR(64) DEFAULT '' COMMENT '系统估算物候期',
    stage_confidence DECIMAL(4,2) DEFAULT 0 COMMENT '系统估算置信度',
    stage_reason VARCHAR(255) DEFAULT '' COMMENT '系统估算依据或提示',
    stage_evaluated_at TIMESTAMP NULL COMMENT '最近一次物候期估算时间',
    is_active TINYINT(1) DEFAULT 0 COMMENT '是否为当前激活作物',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_crop_user_id (user_id),
    INDEX idx_crop_user_active (user_id, is_active),
    CONSTRAINT fk_crop_user FOREIGN KEY (user_id) REFERENCES user(user_id) ON DELETE CASCADE
) ENGINE=INNODB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户作物档案表';

ALTER TABLE identification_record
    ADD COLUMN crop_id BIGINT NULL COMMENT '关联作物档案ID快照' AFTER user_id,
    ADD COLUMN crop_name VARCHAR(64) DEFAULT '' COMMENT '识别时的作物名称快照' AFTER crop_id,
    ADD COLUMN location_id VARCHAR(32) DEFAULT '' COMMENT '识别时的和风天气地区ID快照' AFTER confidence,
    ADD COLUMN city VARCHAR(64) DEFAULT '' COMMENT '识别时的城市快照' AFTER location_id,
    ADD COLUMN region VARCHAR(64) DEFAULT '' COMMENT '识别时的农业生态区快照' AFTER city,
    ADD INDEX idx_record_crop_id (crop_id),
    ADD CONSTRAINT fk_record_crop FOREIGN KEY (crop_id) REFERENCES user_crop(crop_id) ON DELETE SET NULL;

DROP TABLE IF EXISTS admin_log;
DROP TABLE IF EXISTS announcement;
DROP TABLE IF EXISTS model_config;
DROP TABLE IF EXISTS admin_user;

CREATE TABLE admin_user (
    admin_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '操作管理员ID',
    username VARCHAR(64) NOT NULL UNIQUE COMMENT '管理员用户名',
    password VARCHAR(255) NOT NULL COMMENT '用户密码',
    nickname VARCHAR(64) DEFAULT '' COMMENT '昵称',
    avatar_url VARCHAR(512) DEFAULT '' COMMENT '用户头像URL',
    role VARCHAR(32) DEFAULT 'admin' COMMENT '角色: super_admin / admin',
    status TINYINT DEFAULT 1 COMMENT '审核状态: 0=待审核, 1=已通过, 2=已拒绝',
    last_login_at TIMESTAMP NULL COMMENT '最后登录时间',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间'
) ENGINE=INNODB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='管理员用户表';

-- ============================
-- 10. 系统公告表
-- ============================
CREATE TABLE announcement (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '日志主键',
    title VARCHAR(255) NOT NULL COMMENT '收藏标题/快照',
    content TEXT COMMENT '公告正文',
    type VARCHAR(32) DEFAULT 'info' COMMENT '类型: info / warning / urgent',
    status TINYINT DEFAULT 1 COMMENT '审核状态: 0=待审核, 1=已通过, 2=已拒绝',
    display_mode VARCHAR(20) DEFAULT 'normal' COMMENT '展示方式: normal=普通通知, popup=弹窗通知',
    admin_id BIGINT COMMENT '操作管理员ID',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    FOREIGN KEY (admin_id) REFERENCES admin_user(admin_id) ON DELETE SET NULL
) ENGINE=INNODB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统公告表';

-- ============================
-- 10.1 用户公告已读表
-- ============================
CREATE TABLE user_announcement_read (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '读取用户ID',
    announcement_id BIGINT NOT NULL COMMENT '已读公告ID',
    read_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '阅读时间',
    UNIQUE INDEX idx_user_announcement (user_id, announcement_id),
    FOREIGN KEY (user_id) REFERENCES user(user_id) ON DELETE CASCADE,
    FOREIGN KEY (announcement_id) REFERENCES announcement(id) ON DELETE CASCADE
) ENGINE=INNODB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户公告已读表';

-- ============================
-- 11. 模型配置表
-- ============================
CREATE TABLE model_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '日志主键',
    config_key VARCHAR(128) NOT NULL UNIQUE COMMENT '配置键',
    config_value TEXT COMMENT '配置值',
    description VARCHAR(255) DEFAULT '' COMMENT '收藏摘要内容',
    category VARCHAR(64) DEFAULT 'llm' COMMENT '分类: yolo / llm / asr',
    updated_by BIGINT COMMENT '最后修改人',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=INNODB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模型配置表';

-- ============================
-- 12. 操作日志表
-- ============================
CREATE TABLE admin_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '日志主键',
    admin_id BIGINT COMMENT '操作管理员ID',
    action VARCHAR(128) NOT NULL COMMENT '操作动作',
    target VARCHAR(255) DEFAULT '' COMMENT '操作对象',
    detail TEXT COMMENT '详细信息',
    ip VARCHAR(64) DEFAULT '' COMMENT 'IP地址',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    INDEX idx_admin (admin_id)
) ENGINE=INNODB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='管理员操作日志表';

-- ============================
-- 初始数据: 默认系统管理员
-- ============================
INSERT INTO admin_user VALUES (1,'super_admin','admin123','系统管理员','','super_admin',1,NULL,'2026-04-10 14:50:21'),(2,'admin','admin123','普通管理员','','admin',1,NULL,'2026-04-10 14:50:21');

-- ============================
-- 初始数据: 模型配置
-- ============================
INSERT INTO model_config VALUES (1,'yolo.model_path','best.pt','YOLO模型文件路径','yolo',NULL,'2026-04-10 14:50:21'),(2,'yolo.model_type','YOLOv8s-cls','YOLO模型类型','yolo',NULL,'2026-04-10 14:50:21'),(3,'yolo.confidence_threshold','0.5','置信度阈值','yolo',NULL,'2026-04-10 14:50:21'),(4,'yolo.class_count','11','分类数量','yolo',NULL,'2026-04-10 14:50:21'),(5,'doubao.api.url','https://ark.cn-beijing.volces.com/api/v3/responses','豆包API地址','llm',NULL,'2026-04-10 14:50:21'),(6,'doubao.api.key','3a6f0896-a76b-4d55-bbec-6ab3ed1c7d53','豆包API密钥','llm',NULL,'2026-04-10 14:50:21'),(7,'doubao.api.model','doubao-seed-2-0-lite-260215','豆包模型ID','llm',NULL,'2026-04-10 14:50:21'),(8,'asr.app.id','9366438019','语音识别AppID','asr',NULL,'2026-04-10 14:50:21'),(9,'asr.access.token','VhpBZv_l2cc0vce7vf56xqe7puXtdm_1','语音识别Token','asr',NULL,'2026-04-10 14:50:21'),(10,'asr.resource.id','volc.seedasr.sauc.duration','语音识别资源ID','asr',NULL,'2026-04-10 14:50:21'),(11,'tts.app.id','9366438019','语音合成AppID','tts',NULL,'2026-04-10 14:50:21'),(12,'tts.access.token','VhpBZv_l2cc0vce7vf56xqe7puXtdm_1','语音合成Token','tts',NULL,'2026-04-10 14:50:21'),(13,'tts.resource.id','seed-tts-2.0','语音合成资源ID','tts',NULL,'2026-04-10 14:50:21'),(14,'tts.speaker','zh_female_tianmeitaozi_uranus_bigtts','语音合成发音人','tts',NULL,'2026-04-10 14:50:21');

-- ============================
-- 初始数据: 示例公告
-- ============================
INSERT INTO announcement VALUES (1,'系统上线公告','叶查询 LeafQuery 病虫害智能识别系统已正式上线，欢迎广大农户使用！如有问题请通过问答圈反馈。','info',1,'normal',1,'2026-04-10 14:50:21','2026-04-10 14:50:21'),(2,'模型更新通知','YOLO 图像识别模型已升级至 v2.0 版本，新增了草莓和水稻相关病虫害的识别能力，识别准确率提升约15%。','info',1,'normal',1,'2026-04-10 14:50:21','2026-04-10 14:50:21');
