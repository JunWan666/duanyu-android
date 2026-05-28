# Git 提交规则

本仓库使用“流水号 + 类型 + 中文说明”的提交格式，便于在 GitHub 文件列表中快速看出每次变更的顺序和目的。

## 1. 提交格式

```text
NNN:类型:提交说明
```

示例：

```text
001:新增:初始化端语项目文档与规范
002:文档:完善 README 顶部横幅
003:修复:统一 UI 概念图底部导航
004:调整:选定端语 APK 图标
```

字段说明：

- `NNN`：三位流水号，从 `001` 开始递增。
- `类型`：中文变更类型。
- `提交说明`：中文短句，说明本次提交做了什么。

## 2. 流水号规则

1. 每次提交前先查看最近一次提交：

   ```bash
   git log --oneline -1
   ```

2. 如果最近一次提交是：

   ```text
   018:修复:简化 Bash 颜色配置生效方式
   ```

   下一次提交号应为：

   ```text
   019
   ```

3. 流水号只递增，不复用，不按分支重置。

4. 如果一次提交失败但没有写入 git 历史，可以继续使用原流水号。

## 3. 类型列表

推荐使用以下类型：

```text
新增
修复
文档
调整
重构
样式
测试
构建
发布
清理
```

## 4. 提交说明要求

提交说明必须：

- 使用中文。
- 简洁说明本次变更。
- 不超过 30 个中文字符为宜。
- 不使用句号结尾。
- 不写“更新一下”“修改文件”这类含糊描述。

推荐：

```text
012:文档:添加 README 顶部横幅
018:修复:简化 Bash 颜色配置生效方式
021:样式:统一底部导航文字标签
```

不推荐：

```text
012:update readme
013:修改
014:fix
015:新增:改了一些东西
```

## 5. 提交前检查

每次提交前必须做以下检查。

### 5.1 查看工作区

```bash
git status --short
```

确认没有误加入以下内容：

- APK、AAB、ZIP、JDK、临时下载文件。
- `build/`、 `.gradle/`、 `.idea/` 等本地构建或 IDE 文件。
- 本地密钥、Token、签名文件。
- 重复导出的图片或临时截图。

### 5.2 检查 README 是否需要同步

如果本次变更影响以下内容，必须检查并同步 `README.md`：

- 功能范围变化。
- 项目名称变化。
- 目录结构变化。
- API 路径变化。
- 打包、运行、测试方式变化。
- UI 预览图变化。
- 品牌素材变化。
- 版本路线变化。

如果 README 不需要修改，应在提交前确认原因，例如本次只是内部实现细节变更。

### 5.3 检查 PRD 是否需要同步

如果本次变更影响产品需求、UI 方向、API 设计、i18n、权限、安全策略，应同步：

```text
product-docs/DuanYu-PRD.md
```

### 5.4 检查图片引用

如果新增或替换图片，检查 Markdown 引用是否存在：

```bash
rg -n "!\[.*\]\(.*\)" README.md product-docs brand
```

### 5.5 检查大文件

提交前检查大文件：

```bash
git status --short
git diff --stat
```

本仓库不建议提交：

- `*.apk`
- `*.aab`
- `*.zip`
- `*.jar`
- `*.keystore`
- `*.jks`
- JDK 或 SDK 目录

如确实需要保留测试 APK，请放在 `build-output/`，并保持它不进入版本控制。

## 6. 提交流程

标准流程：

```bash
git status --short
git diff --stat
git add README.md GIT_COMMIT_RULES.md product-docs brand
git commit -m "001:新增:初始化端语项目文档与规范"
```

## 7. 分支建议

推荐分支命名：

```text
main
docs/NNN-description
feature/NNN-description
fix/NNN-description
```

示例：

```text
docs/002-api-design
feature/006-openai-gateway
fix/009-audio-permission
```
