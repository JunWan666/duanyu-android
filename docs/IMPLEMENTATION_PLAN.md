# 端语 DuanYu 实施计划

版本：v0.1  
日期：2026-05-29  
目标：将 Google AI Edge Gallery 技术底座整理为“端语 DuanYu”Android App。

## 1. 总体原则

端语不是继续扩展 Gallery 的演示集合，而是从现有代码中提炼一个更聚焦的产品。

实施顺序必须遵循：

```text
先定范围 -> 再清入口 -> 再搭导航 -> 再做 i18n -> 再落 UI -> 再开放 API
```

不要先做最终 UI，也不要一开始就做 OpenAI 兼容 API。否则会把新能力绑定到旧的 ViewModel、旧导航和旧文案里，后面返工会很重。

## 2. 最终功能范围

### 2.1 必须保留

- AI Chat
- Ask Image
- Audio Scribe / Ask Audio
- Agent Skills
- 通知功能
- 主题与设置
- 精简模型管理
- i18n：中文和英文
- OpenAI 兼容本地 API

### 2.2 第一阶段移除或隐藏

- Benchmark
- Prompt Lab
- Tiny Garden
- Mobile Actions
- Promo 页面
- 示例 Custom Task
- 非目标首页入口
- 与上游 Gallery 传播相关的文案和图标

说明：第一阶段可以先“隐藏入口并断开导航”，再逐步删除代码。直接大规模删除容易牵连 Hilt、路由、资源和构建配置。

## 3. 里程碑规划

## M1：项目瘦身

目标：让 App 从“Gallery 演示集合”变成“端语目标功能集合”。

任务：

- 梳理所有首页 capability/task。
- 隐藏或移除 Benchmark、Prompt Lab、Tiny Garden、Mobile Actions。
- 去掉 Promo 首页逻辑。
- 保留 Chat、Image、Audio、Agent Skills、Notifications、Settings、Model Manager。
- 检查 Hilt `@IntoSet` task 注册，移除非目标任务注册。
- 检查导航路由，移除不可达页面。
- 检查 README 和 PRD 是否同步。

建议提交：

```text
003:清理:移除非目标功能入口
004:清理:精简非目标任务注册
```

验收：

- App 首页只出现目标能力入口。
- 不再能从 UI 进入被移除功能。
- 项目可成功构建 debug APK。

风险：

- Hilt 多绑定任务移除后可能影响 `ModelManagerViewModel` 的任务列表。
- 某些模型 allowlist 仍包含被移除 task type，需要兼容空匹配。
- Tiny Garden 资源较多，删除时要确认没有被其它模块引用。

## M2：主导航和页面壳

目标：建立端语的五入口结构。

主导航：

```text
对话 / 图片 / 音频 / 技能 / 设置
```

任务：

- 新建端语主导航结构。
- 将 Chat 设为默认首页。
- 设置页承载模型中心、API 服务、语言、主题、通知、关于。
- 图片页承载拍照、相册、图片问答。
- 音频页承载录音、音频导入、转写、翻译、摘要。
- 技能页承载 Agent Skills 管理。

建议提交：

```text
004:重构:建立端语主导航结构
005:调整:将对话设为默认首页
```

验收：

- 底部导航固定显示 5 个入口。
- 每个入口都有页面壳和空状态。
- 返回行为符合 Android 常规体验。

风险：

- 现有 `GalleryNavGraph` 承担了模型页和深链逻辑，拆分时不要一次性重写过大。
- 可先保留旧 Model Manager 页面，作为设置页里的二级入口。

## M3：i18n 基础

目标：所有新增 UI 文案资源化，只支持中文和英文。

任务：

- 建立 `values/strings.xml` 英文兜底。
- 建立 `values-zh-rCN/strings.xml` 中文主语言。
- 新增语言设置：跟随系统、中文、English。
- 新增系统提示词中英文资源。
- 将新增页面标题、按钮、权限说明、错误文案资源化。
- 检查硬编码中文和英文。

建议提交：

```text
006:新增:添加中英文资源框架
007:调整:资源化端语主界面文案
```

验收：

- 中文系统下默认中文。
- 英文系统下默认英文。
- 用户可手动切换语言。
- 新 UI 无硬编码展示文案。

风险：

- 上游现有字符串很多，第一阶段不必全部翻译。
- 先覆盖端语新增 UI 和目标功能路径，后续再逐步整理旧文案。

## M4：端语 UI 落地

目标：将 PRD 概念图落成 Compose 页面。

参考：

- `product-docs/assets/duanyu-ui-overview.png`
- `product-docs/assets/duanyu-chat.png`
- `product-docs/assets/duanyu-image.png`
- `product-docs/assets/duanyu-audio.png`
- `product-docs/assets/duanyu-skills.png`
- `product-docs/assets/duanyu-api-settings.png`

任务：

- 定义端语颜色、间距、卡片、底部导航样式。
- 实现对话页顶部模型状态卡。
- 实现图片页拍照/相册入口。
- 实现音频页录音入口和转写状态。
- 实现技能页列表和权限策略说明。
- 实现设置页 API 服务卡片。
- 使用已选 C 方案作为 App 图标。

建议提交：

```text
008:样式:添加端语主题基础样式
009:样式:实现端语五入口页面壳
010:样式:替换端语应用图标
```

验收：

- 主要页面视觉与 PRD 概念图方向一致。
- 底部导航为“图标 + 中文文字”。
- 页面在常见手机尺寸下不重叠、不溢出。

风险：

- 不要在 UI 阶段重写模型推理逻辑。
- 先接现有 ViewModel，再在后续 core-ai 阶段拆分。

## M5：核心能力整理

目标：为 UI 和 API 共用同一套 AI 调用边界做准备。

任务：

- 梳理 Chat/Image/Audio 当前依赖的 ViewModel 和 helper。
- 抽象 `core-ai` 风格接口。
- 将模型初始化、清理、推理、停止生成从 UI 层剥离。
- 将模型下载和模型状态整理成可复用服务。
- 将 Skills 调用权限统一收口。

建议提交：

```text
011:重构:抽象端语 AI 调用接口
012:重构:整理模型初始化状态机
```

验收：

- UI 不直接关心底层 runtime 类型。
- Chat 和 API 后续可复用同一套调用接口。
- 模型切换和清理行为可预测。

风险：

- `ModelManagerViewModel` 当前职责较重，拆分要小步提交。
- 不能在没有测试兜底的情况下大规模改动模型生命周期。

## M6：OpenAI 兼容 API

目标：在手机本地提供 OpenAI 兼容接口。

第一阶段接口：

```text
GET  /health
GET  /v1/models
POST /v1/chat/completions
POST /v1/audio/transcriptions
GET  /v1/skills
POST /v1/skills/{name}/run
```

任务：

- 新增 Foreground Service。
- 新增本地 HTTP Server，推荐 Ktor Server CIO。
- 新增 Bearer Token 管理。
- 默认仅监听 `127.0.0.1`。
- 支持 `/v1/models`。
- 支持非流式 `/v1/chat/completions`。
- 支持 SSE 流式响应。
- API 设置页展示地址、Token、监听范围和安全策略。

建议提交：

```text
013:新增:添加本地 API 服务骨架
014:新增:实现模型列表 API
015:新增:实现聊天补全 API
016:新增:支持聊天流式响应
```

验收：

- API 默认关闭。
- 开启 API 后有前台通知。
- 请求无 Token 时返回 401。
- 正确响应 `/v1/models`。
- OpenAI SDK 可通过 base URL 调用聊天接口。

风险：

- Android 后台限制要求 API 服务使用 Foreground Service。
- 并发请求第一版建议只允许 1 个推理任务。
- 工具调用必须默认关闭或逐次确认。

## 4. 当前进度

已完成：

- 003：隐藏非目标功能入口。
- 004：精简非目标任务注册。
- 005：更新端语应用标识与主界面。
- 006：集中忽略本地构建产物。
- 007：建立端语中英文资源框架。
- 008：完善端语设置页、语言切换和首次启动协议体验。
- 009：重构设置页为二级菜单结构。
- 010：忽略本地 NAS 上传规则。
- 011：优化设置页排版与 Android 手势返回逻辑。
- 012：统一核心功能页中英文文案。
- 013：完善二级面板中文文案。
- 014：完善模型配置与 Agent 进度文案。
- 015：收口端语目标功能范围，隐藏非目标导入能力与深链入口。
- 016：重构模型管理为在线安装和已安装双页签，并加入搜索与分类筛选。
- 017：为已安装模型加入导出入口。
- 018：为已安装模型补充详情弹窗和文件路径复制。
- 019：新增端语 core-ai 调用边界和模型目录骨架。
- 020：将文本聊天推理接入 core-ai 服务，支持 UI 与后续 API 复用同一调用边界。
- 021：新增本地 API 前台服务骨架，支持设置页启动停止、前台通知、健康检查和模型列表路由。

当前状态：

- 首页已收敛为 5 个底部入口：对话、图片、音频、技能、设置。
- 设置页已承载模型中心、API 服务占位、通知、语言、主题、协议、开源许可和关于。
- 新增 UI 文案已覆盖中英文资源。
- 首次启动协议已替换为端语自己的中英文说明。
- 用户可在设置页手动切换“跟随系统 / 中文 / English”。
- 模型导入只暴露端语目标能力：Chat、Ask Image、Ask Audio、Agent Skills。
- 非目标能力的模型绑定和精确深链入口已做过滤，避免绕过首页进入旧实验功能。
- 模型管理已拆分为“在线安装 / 已安装”，支持按全部、对话、图片、音频、技能分类筛选，在线模型支持搜索。
- 已安装模型菜单支持导出模型文件，便于测试包之间复用本地模型。
- 已安装模型菜单支持查看详情，包括来源、运行时、文件名、大小、路径、输入能力和加速器。
- 新增 core-ai 服务接口和模型目录注册表，后续 API 可复用同一套模型列表。
- core-ai 已能通过模型目录查找真实运行时模型，并执行非流式或流式文本聊天推理。
- API 服务页已可启动和停止本地前台服务，默认监听 `127.0.0.1:8765`，当前骨架提供 `/health` 和 `/v1/models`。

下一步优先级：

1. 接入 `/v1/chat/completions`，复用当前 core-ai 聊天推理服务。
2. 为 API 服务补充 Bearer Token 管理和请求鉴权。
3. 补充 OpenAI 兼容响应结构、错误结构和流式 SSE。
4. 继续完善模型管理的批量管理、多个变体导出和更细的安装状态。

## 5. 提交节奏

每次提交只做一个清晰主题。

推荐粒度：

- 一个提交只移除一类入口。
- 一个提交只新增一组 i18n 资源。
- 一个提交只完成一个页面壳。
- 一个提交只新增一个 API endpoint。

提交前必须执行：

```bash
git status --short
git diff --stat
rg -n "<旧品牌关键词>" .
```

如果改动 Android 代码，还需要至少执行：

```bash
cd Android/src
./gradlew :app:assembleDebug
```

Windows 可使用：

```powershell
cd Android\src
.\gradlew.bat :app:assembleDebug
```
