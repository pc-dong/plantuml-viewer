# PlantUML Viewer

一款基于 Web 的交互式 PlantUML 查看器，支持实时编辑、元素级可见性控制、演示模式和多格式导出。

[English](README.md) | **中文**

## 技术栈

| 层级 | 技术 |
|------|------|
| 前端 | React 18 + TypeScript + Vite + Ant Design 5 |
| 状态管理 | Zustand + localStorage 持久化 |
| 代码编辑器 | Monaco Editor（PlantUML 语法高亮） |
| 后端 | Spring Boot 3.x + Java 17 + PlantUML |
| 部署 | Docker（多阶段构建，Nginx + supervisord） |

## 功能特性

### 实时代码编辑器
- 基于 Monaco Editor，支持 PlantUML 语法高亮
- 代码修改后自动解析（500ms 防抖）
- 手动触发解析：`Ctrl+Enter`

### 交互式图表视图
- SVG 渲染，支持缩放和平移（`Ctrl/Cmd+滚轮` 缩放，拖拽平移）
- 点击元素高亮显示
- 右键菜单操作元素
- 图表加载时自动适应容器大小

### 元素可见性控制
- 树形面板展示所有图表元素，按类型分组
- 通过复选框显示/隐藏单个元素
- 隐藏元素时，关联的连线自动隐藏
- 折叠/展开容器元素（包、类等）
- 按名称搜索元素

### 紧凑模式
- 一键折叠所有类/接口/枚举的详细信息，适用于大型图表
- 可逐个展开特定元素的详情

### 演示模式
- 全屏分步展示图表
- 使用方向键导航，`Escape` 退出
- 根据图表结构自动生成演示步骤

### 多格式导出
- **PNG** — 2 倍分辨率光栅化，遵循当前可见性状态
- **SVG** — 矢量格式，保留可见性状态
- **PlantUML 源码**（`.puml`）— 导出当前编辑器内容
- **JSON 状态** — 保存/恢复完整视图状态（含可见性信息）

### 支持的图表类型
- 类图（继承、接口、包）
- 时序图（参与者、生命线、消息）
- 用例图

## 快速开始

### 本地开发

```bash
# 安装依赖
pnpm install

# 启动后端（端口 8080）
cd packages/backend && mvn spring-boot:run &

# 启动前端开发服务器（端口 5173）
cd packages/frontend && pnpm dev
```

在浏览器中打开 http://localhost:5173

### Docker 部署

```bash
# 构建镜像
docker build -t plantuml-viewer .

# 运行容器
docker run -p 8080:80 plantuml-viewer
```

在浏览器中打开 http://localhost:8080

## API 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/parse` | 解析 PlantUML 源码为结构化 JSON |
| POST | `/api/render` | 将 PlantUML 源码渲染为 SVG |
| GET | `/api/health` | 健康检查 |

## 项目结构

```
.
├── packages/
│   ├── frontend/          # React + Vite 前端
│   │   └── src/
│   │       ├── components/
│   │       │   ├── EditorPanel/     # Monaco 编辑器，支持 PlantUML 语法
│   │       │   ├── DiagramView/     # SVG 渲染，缩放/平移/交互层
│   │       │   ├── ControlTree/     # 元素可见性控制树
│   │       │   ├── Toolbar/         # 导出、新建图表、紧凑模式
│   │       │   └── ContextMenu/     # 右键元素操作菜单
│   │       ├── store/               # Zustand 状态管理
│   │       ├── services/            # API 客户端
│   │       ├── types/               # TypeScript 类型定义
│   │       └── utils/               # 可见性计算逻辑、示例图表
│   └── backend/           # Spring Boot 后端
│       └── src/main/java/com/plantuml/viewer/
│           ├── controller/          # REST API 接口
│           ├── service/             # PlantUML 解析与 SVG 生成
│           ├── model/               # 请求/响应数据模型
│           └── config/              # CORS 跨域配置
├── Dockerfile               # Docker 多阶段构建
├── nginx.conf               # Nginx 配置（前端静态文件 + API 反向代理）
└── docker/
    └── supervisord.conf     # 进程管理（同时运行 Nginx 和 Java）
```

## 键盘快捷键

| 快捷键 | 功能 |
|--------|------|
| `Ctrl+Enter` | 手动触发解析 |
| `Ctrl/Cmd+滚轮` | 缩放图表 |
| `F2` | 导出 PNG |
| `F5` | 导出 SVG |
| `F6` | 导出 PlantUML 源码 |
| `F7` | 导出 JSON 状态 |
| `F8` | 导入 JSON 状态 |
| `F9` | 加载 `.puml` 文件 |
| `F10` | 切换紧凑模式 |
| `F11` | 进入演示模式 |
| `← →` | 演示模式中切换步骤 |
| `Escape` | 退出演示模式 |
