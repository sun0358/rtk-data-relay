# 部署配置说明

## 🔧 首次部署配置

本项目已经初始化了Git仓库，并且已经过滤了包含敏感信息的文件。在进行首次部署前，需要进行以下配置：

### 1. 复制部署脚本模板

```bash
# 复制模板文件为实际使用的部署脚本
cp deploy/build-and-deploy.sh.template deploy/build-and-deploy.sh
```

### 2. 配置服务器信息

编辑 `deploy/build-and-deploy.sh` 文件，修改以下配置：

```bash
# 配置变量 - 请修改为您的实际服务器信息
UBUNTU_SERVER="192.168.5.15"     # 替换为您的Ubuntu服务器IP
UBUNTU_USER="sun"                 # 替换为您的SSH用户名
UBUNTU_SSH_KEY=""                 # 如果使用SSH密钥，请填写密钥路径
```

### 3. 确保部署脚本不被提交

`.gitignore` 文件已经配置为忽略 `deploy/build-and-deploy.sh`，确保包含真实服务器信息的部署脚本不会被提交到代码仓库。

### 4. 执行部署

配置完成后，可以使用以下命令进行部署：

```bash
# 自动部署（使用脚本中配置的服务器信息）
./deploy/build-and-deploy.sh

# 或者通过命令行参数指定服务器信息
./deploy/build-and-deploy.sh <服务器IP> <用户名> [SSH密钥路径]
```

## 📁 Git仓库文件说明

### 已提交的文件（35个文件）

✅ **源代码文件**：所有Java源代码和配置文件
✅ **部署模板**：`deploy/build-and-deploy.sh.template`（不含敏感信息）
✅ **安装脚本**：`deploy/install.sh`（通用安装脚本）
✅ **系统服务配置**：`deploy/rtk-data-relay.service`
✅ **项目文档**：README.md、部署指南、使用手册等
✅ **构建配置**：pom.xml、quick-start.sh等

### 被忽略的文件（不会提交）

❌ **敏感部署脚本**：`deploy/build-and-deploy.sh`（包含实际服务器IP）
❌ **构建文件**：`target/` 目录
❌ **日志文件**：`logs/` 目录
❌ **临时文件**：各种临时和缓存文件
❌ **IDE配置**：`.idea/`、`.vscode/` 等

## 🔐 安全注意事项

1. **永远不要提交包含真实服务器IP、用户名、密码的文件**
2. **使用模板文件的方式管理部署配置**
3. **定期检查 `.gitignore` 文件确保敏感文件被正确忽略**
4. **在团队协作时，每个人都应该有自己的部署配置副本**

## 🚀 团队协作流程

1. **克隆仓库**：`git clone <repository-url>`
2. **复制模板**：`cp deploy/build-and-deploy.sh.template deploy/build-and-deploy.sh`
3. **配置服务器**：编辑 `deploy/build-and-deploy.sh` 中的服务器信息
4. **执行部署**：`./deploy/build-and-deploy.sh`

这样既保证了代码的完整性，又保护了敏感信息的安全性。
