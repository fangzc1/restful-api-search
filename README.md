# RESTful API Search

一款用于 IntelliJ IDEA 的插件，可快速搜索并跳转到 Spring 项目中的 REST API 端点。

## ✨ 功能特性

- **一键呼出**：通过快捷键 `Ctrl+\`（macOS 为 `Cmd+\`）打开搜索弹窗，或从 **Navigate** 菜单选择 *Search REST API Endpoints*
- **实时搜索**：输入 URL 路径、HTTP 方法名或类/方法名，列表即时过滤
- **连续分段匹配**：支持多关键词（空格分隔）AND 逻辑过滤；单词内支持连续子串分段匹配（如 `usrstop` 可匹配 `user` + `stop`）
- **HTTP Method 过滤**：点击工具栏过滤按钮，可多选 GET / POST / PUT / DELETE / PATCH / HEAD / OPTIONS
- **彩色 Method 标签**：每种 HTTP 方法以不同颜色区分，一眼识别
- **一键跳转**：按 `Enter` 或双击列表项，直接跳转到对应 Controller 方法的源码位置
- **固定弹窗**：点击 Pin 按钮，弹窗不随点击外部区域而关闭，方便多次查询
- **搜索历史**：通过 `Alt+↑ / Alt+↓` 快速切换历史搜索词
- **支持注解**：自动识别 `@RestController`、`@Controller` 及所有 Mapping 注解

## 🖥️ 支持的注解

| 注解 | HTTP 方法 |
|------|-----------|
| `@GetMapping` | GET |
| `@PostMapping` | POST |
| `@PutMapping` | PUT |
| `@DeleteMapping` | DELETE |
| `@PatchMapping` | PATCH |
| `@RequestMapping` | 根据 `method` 属性解析（未指定则显示为 ALL）|

## ⌨️ 快捷键

| 操作 | 快捷键 |
|------|--------|
| 打开搜索弹窗 | `Ctrl+\` / `Cmd+\` |
| 向上移动选中项 | `↑` |
| 向下移动选中项 | `↓` |
| 跳转到源码 | `Enter` / 双击 |
| 切换历史记录 | `Alt+↑` / `Alt+↓` |
| 关闭弹窗 | `Esc` |

## 🚀 快速开始

### 环境要求

- **JDK**：21+
- **IntelliJ IDEA**：2024.3（Build 243）及以上

### 本地构建与运行

```bash
# 克隆仓库
git clone <repository-url>
cd restfule-api-search

# 启动沙箱 IDE 验证插件
./gradlew runIde

# 构建插件发行包
./gradlew buildPlugin
```

构建产物位于 `build/distributions/` 目录下，为 `.zip` 压缩包。

### 手动安装

1. 执行 `./gradlew buildPlugin` 生成插件包
2. 打开 IntelliJ IDEA → **Settings → Plugins → ⚙️ → Install Plugin from Disk...**
3. 选择 `build/distributions/RESTful API Search-*.zip`，重启 IDE 即可

## 🗂️ 项目结构

```
src/main/kotlin/.../restfulapisearch/
├── action/
│   └── SearchApiEndpointAction.kt   # 触发搜索的 AnAction
├── model/
│   ├── ApiEndpoint.kt               # API 端点数据模型
│   └── HttpMethod.kt                # HTTP 方法枚举（含颜色）
├── scanner/
│   └── SpringApiScanner.kt          # PSI 扫描器，解析 Spring 注解
├── ui/
│   ├── ApiEndpointPopup.kt          # 搜索弹窗（含过滤/Pin/导航）
│   └── ApiEndpointCellRenderer.kt   # 列表行渲染器（含高亮）
└── util/
    ├── PathUtils.kt                  # 路径拼接与提取工具
    └── SearchMatcher.kt             # 搜索匹配算法（支持分段匹配）
```

## 🔧 技术栈

| 组件 | 版本 |
|------|------|
| Kotlin | 2.1.0 |
| IntelliJ Platform Plugin Gradle Plugin | 2.2.1 |
| Java Toolchain | 21 |
| 目标平台 | IntelliJ IDEA Community 2024.3 |

## 📄 License

本项目遵循 [Apache License 2.0](LICENSE)。
