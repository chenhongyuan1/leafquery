# LeafQuery 管理后台

LeafQuery 的独立运营管理端，使用 Vue 3、Vite 7、Pinia、Vue Router 和 Axios。它调用 Java 后端的 `/api/admin` 接口，默认开发地址为 `http://localhost:5174`。

完整环境准备、数据库配置及服务器部署见 [应用仓库 README](../README.md)。

## 页面与功能

| 页面 | 路由 | 用途 |
| --- | --- | --- |
| 登录 | `/login` | 使用管理员账号登录 |
| 仪表盘 | `/` | 查看用户、识别记录、内容等统计 |
| 公告管理 | `/announcements` | 发布、编辑和删除公告 |
| 用户管理 | `/users` | 管理应用用户资料 |
| 内容管理 | `/discovery` | 管理资讯、知识和社区内容 |
| 系统管理 | `/system` | 管理管理员账号，具体权限由后端校验 |
| 模型管理 | `/models` | 管理模型配置和上传权重 |
| 操作日志 | `/logs` | 查询管理员操作记录 |

管理员账号来自后端的 `admin_user` 表，与普通应用用户的 `user` 表分开。账号和密码由项目管理员提供，前端配置文件不保存登录密码。

## 本地开发

前置条件：Node.js 20.19+ 或 22.12+，Java 后端已启动且可连接数据库。

### 使用一键启动器

在应用仓库根目录执行以下命令，启动器会加载根目录 `.env`，启动后端、Python 和两个前端，并给管理端设置正确的后端地址：

```powershell
.\start-all.cmd
.\start-all.cmd status
.\start-all.cmd restart-backend  # 修改后端 .env 后使用
.\start-all.cmd stop
```

### 单独启动管理端

在 `admin-frontend/` 目录创建 `.env.development.local`，填写开发后端地址：

```dotenv
VITE_API_BASE_URL=http://localhost:8080
```

这个个人配置文件已由管理端 `.gitignore` 中的 `*.local` 忽略，Vite 会在开发模式下加载它。然后执行：

```powershell
npm ci
npm run dev -- --port 5174 --strictPort
```

打开 `http://localhost:5174`。`--strictPort` 会在端口被占用时明确报错，避免自动切换端口。

## 后端地址与环境文件

`VITE_API_BASE_URL` 填后端站点地址，不带末尾 `/` 或 `/api`。例如填写 `http://localhost:8080`，`src/api.js` 会自动拼接成 `http://localhost:8080/api/admin`。

| 文件 | 作用 |
| --- | --- |
| 仓库根目录 `.env` | 后端数据库和接口凭据，由一键启动器加载 |
| 本目录 `.env.development.local` | 管理端个人开发地址，由 Vite 开发模式加载 |
| 本目录 `.env.production` | 管理端正式打包使用的公开后端地址 |

前端不会自行读取仓库根目录的后端 `.env`。`VITE_` 参数会进入浏览器代码，因此只放公开网址等参数；数据库密码、API 密钥保留在后端私有配置中。

修改 Vite 环境文件后需重启开发服务器；修改生产参数后需重新打包。终端中已有同名环境变量时，它会优先于 Vite 环境文件，打包前应确认没有残留本机开发地址。

## 正式打包与部署

在本目录 `.env.production` 中设置实际部署地址。例如管理端与 API 使用同一站点时：

```dotenv
VITE_API_BASE_URL=https://admin.example.com
```

在 `admin-frontend/` 目录执行：

```powershell
npm ci
npm run build
```

产物位于 `dist/`，由 Nginx 等静态服务器提供访问。站点需要把 `/api/` 转发到 Java 后端，并配置 Vue Router 的页面刷新回退到 `index.html`；上传图片访问和完整 Nginx 示例见 [服务器部署说明](../README.md#服务器部署linux-示例)。

```powershell
npm run preview -- --port 4174 --strictPort
```

`preview` 用于本地预览构建产物，正式站点按仓库部署说明使用静态服务器。

## 源码目录

```text
src/
├── api.js          # /api/admin 请求客户端和请求头处理
├── main.js         # Vue、Pinia、路由和 Axios 初始化
├── App.vue         # 后台布局和导航
├── router/         # 页面路由与登录状态检查
├── stores/         # 管理员登录状态
├── views/          # 登录、仪表盘及各管理页面
└── style.css       # 公共样式
```

登录状态存放在 Pinia 和浏览器 `localStorage` 中。排查接口异常时，先检查浏览器 Network 面板里的请求地址和响应，再查看 Java 后端日志；修改账号或权限后可退出并重新登录。
