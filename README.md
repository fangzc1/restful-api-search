# RESTful API Search

一款 IntelliJ IDEA 插件，可快速搜索并跳转到 Spring 项目中的 REST API 端点。

An IntelliJ IDEA plugin for quickly searching and navigating to REST API endpoints in Spring projects.

---

## ✨ 功能特性 / Features

- **一键呼出 / Quick Launch**：`Ctrl+\`（macOS: `Cmd+\`）或 **Navigate → Search REST API Endpoints**
- **实时搜索 / Real-time Search**：输入 URL 路径、HTTP 方法名或类/方法名，列表即时过滤
- **分段匹配 / Segment Matching**：空格分隔多关键词 AND 逻辑；单词内支持连续子串匹配（`usrstop` → `user` + `stop`）
- **HTTP Method 过滤 / Method Filter**：工具栏按钮多选 GET / POST / PUT / DELETE / PATCH / HEAD / OPTIONS
- **彩色 Method 标签 / Color-coded Methods**：每种 HTTP 方法不同颜色，一眼识别
- **一键跳转 / Navigate to Source**：`Enter` 或双击直接跳转到 Controller 方法源码
- **固定弹窗 / Pin Popup**：Pin 按钮固定弹窗，方便多次查询
- **搜索历史 / Search History**：`Alt+↑` / `Alt+↓` 快速切换历史搜索词
- **注解支持 / Annotation Support**：自动识别 `@RestController`、`@Controller` 及所有 Mapping 注解

---

## 🖥️ 支持的注解 / Supported Annotations

| 注解 / Annotation | HTTP Method |
|---|---|
| `@GetMapping` | GET |
| `@PostMapping` | POST |
| `@PutMapping` | PUT |
| `@DeleteMapping` | DELETE |
| `@PatchMapping` | PATCH |
| `@RequestMapping` | 根据 `method` 属性解析 / Parsed from `method` attribute (未指定则显示 ALL) |

---

## ⌨️ 快捷键 / Shortcuts

| 操作 / Action | 快捷键 / Shortcut |
|---|---|
| 打开搜索 / Open search | `Ctrl+\` / `Cmd+\` |
| 上移 / Move up | `↑` |
| 下移 / Move down | `↓` |
| 跳转源码 / Go to source | `Enter` / Double-click |
| 切换历史 / Cycle history | `Alt+↑` / `Alt+↓` |
| 关闭弹窗 / Close popup | `Esc` |

---

## 🚀 快速开始 / Getting Started

### 环境要求 / Prerequisites

- **JDK** 21+
- **IntelliJ IDEA** 2024.3+ (Build 243+)

### 本地构建 / Build

```bash
git clone <repository-url>
cd restfule-api-search

# 启动沙箱 IDE 验证 / Launch sandbox IDE
./gradlew runIde

# 构建插件包 / Build plugin distribution
./gradlew buildPlugin
```

构建产物位于 `build/distributions/` 目录。

### 手动安装 / Manual Install

1. `./gradlew buildPlugin`
2. **Settings → Plugins → ⚙️ → Install Plugin from Disk...**
3. 选择 `build/distributions/RESTful API Search-*.zip`，重启 IDE

---

## 🗂️ 项目结构 / Project Structure

```
src/main/kotlin/.../restfulapisearch/
├── action/
│   └── SearchApiEndpointAction.kt      # 触发搜索的 AnAction
├── model/
│   ├── ApiEndpoint.kt                  # API 端点数据模型
│   └── HttpMethod.kt                   # HTTP 方法枚举（含颜色）
├── scanner/
│   └── SpringApiScanner.kt             # PSI 扫描器，解析 Spring 注解
├── ui/
│   ├── ApiEndpointPopup.kt             # 搜索弹窗（过滤/Pin/导航）
│   └── ApiEndpointCellRenderer.kt      # 列表行渲染器（含高亮）
└── util/
    ├── PathUtils.kt                    # 路径拼接与提取
    └── SearchMatcher.kt                # 搜索匹配（分段匹配）
```

---

## 🔧 技术栈 / Tech Stack

| 组件 / Component | 版本 / Version |
|---|---|
| Kotlin | 2.1.0 |
| IntelliJ Platform Plugin (Gradle) | 2.2.1 |
| Java Toolchain | 21 |
| 目标平台 / Target Platform | IntelliJ IDEA Community 2024.3 |

---

## 📄 License

[Apache License 2.0](LICENSE)
