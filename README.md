# 移动工牌管理（一期）

仓库：<https://github.com/betterbettermanman/mobile-badge-mgmt>  
默认分支：`main`

单租户闭环的第一期切片：组织与账号（US-01）已可登录验收；采集来源、会话、授权、画像/需求草案与已确认、导出和 Webhook 有可见的类型与接口边界。手机录音的音频字节、云 ASR、云 LLM、Webhook 出站都是可替换适配器上的 Mock / stub，不接真实厂商，也不做 SOP、真工牌固件、经营看板、CRM 双向或 SaaS 多租户。

## 本地启动

需要 JDK 21、Maven 3.8+、Node.js 20+。

```bash
# 后端，端口 8092。开发库是 H2 文件库（PostgreSQL 兼容模式），对象在 ./data/objects
cd backend
mvn spring-boot:run

# 前端，端口 43124，把 /api 代理到 8092。浏览器只访问 43124，Session Cookie 同源
cd frontend
npm install
npm run dev
```

验收：

```bash
cd backend && mvn test
cd frontend && npm run build
```

打开 <http://localhost:43124/login>。

## 默认账号

种子数据会创建组织「体验中心」、门店「总店」，以及：

| 工号 | 密码 | 角色 |
| --- | --- | --- |
| 10001 | Admin123 | 管理员 |
| 10002 | Super123 | 主管 |
| 10003 | Advisor123 | 顾问 |

密码至少 6 位。一期没有自助注册和验证码，账号由管理员在「成员与邀请」直接创建。停用后再次登录仍提示「账号或密码不正确」。

## 联调说明

浏览器走前端 43124，接口前缀 `/api`，凭证是 Cookie `MBSESSION`（HttpOnly、SameSite=Lax）。用 curl 时保存 Cookie：

```bash
curl -c /tmp/mb.cj -H 'Content-Type: application/json' \
  -d '{"account":"10003","password":"Advisor123"}' \
  http://localhost:8092/api/auth/login

curl -b /tmp/mb.cj http://localhost:8092/api/me
curl -b /tmp/mb.cj http://localhost:8092/api/meta/device-sources
```

建议按这条路径点一遍：

1. 顾问登录后进入 `/app/sessions`。管理员登录后进入 `/admin`，可从侧栏或「我的」切到顾问端。
2. 顾问打开「设备」，只能绑定「手机」。工牌是灰的，不能提交。
3. 新建会话选「手机录音」并点「开始录音」。没有有效授权时出现拦截半屏，后端 `POST /api/sessions/{id}/recording/start` 返回 403，`code` 为 `NO_CONSENT` / `CONSENT_EXPIRED` / `CONSENT_REVOKED`。勾选同意并记录授权后，若尚未绑定手机会得到 `DEVICE_UNBOUND`。
4. 绑定本机后再开录，结束上传。转写由 Mock ASR 同步变成「转写成功」。页面写明这不是云端转写，也没有分句角色手动纠正。
5. 在画像里保存草案或「由转写生成草案」。未点「确认入库」前，`/app/confirmed/profiles` 和默认导出都查不到这条。确认后才进入已确认；「解锁再编辑」会退回草案。
6. 「导入文件」只接受 wav/mp3/m4a/aac，上限 200MB，并且要勾选授权（或账号上已有有效授权）。来源标记为「文件」。
7. 顾问访问 `/admin` 会进入全屏「无权访问该页面」，没有管理菜单。主管可以看组织会话和设备，不能创建成员、改授权策略、触发导出或配置 Webhook；这些接口返回 403。
8. 管理员导出 JSON/CSV。响应里的说明是「默认仅导出已确认数据，不包含草案」。即使请求里多带草稿字段，服务端也不导出草案。
9. Webhook 只保存 https 地址和掩码密钥。测试发送和业务事件只写本地投递日志，**不会**请求外部 URL。

未登录访问业务接口返回 401。角色不对或查看他人会话返回 403，文案是「无权访问该页面」。

## 一期边界

- `DeviceSource`：`phone`、`file`、`badge`（JSON 里也接受 `badgeDeviceId`，但一律拒绝绑定和开录）。
- 会话状态：`draft` / `recording` / `uploading` / `upload_failed` / `uploaded` / `cancelled`。转写：`queued` / `processing` / `succeeded` / `failed`。
- 画像与需求：`draft` / `confirmed` / `rejected`。只有所属顾问能确认或解锁。主管和管理员在管理端会话详情里只读。
- 授权策略锁定为「无授权禁止开录」。管理员只能改有效天数（默认 30）。不做「仅提醒」，也不强制每次会话重新确认。
- 对象存储是本地磁盘。音频键的第一段是 `sessionId`，导出文件在 `exports/` 下。
- 适配器：`AsrAdapter`、`LlmAdapter`、`ObjectStorage`。当前实现是 `MockAsrAdapter`、`MockLlmAdapter`、`LocalDiskObjectStorage`。
- 开发库用 H2。实体字段是常规 JPA 类型，后续可换 PostgreSQL。本环境不依赖 Docker。

明确没有入口：SOP 质检、真工牌固件 / OTA、经营看板、CRM 双向同步、租户切换、分句角色手改。

## 已知限制与后续顺序

- 手机开录只推进状态机，不采集真实麦克风字节；文件导入会把文件写入本地磁盘。
- 转写和画像/需求抽取是 Mock，结果必须经人工确认才算正式数据。
- Webhook 不出站。导出在请求里同步生成，状态枚举仍保留排队 / 处理中 / 完成 / 失败。
- 会话删除不对顾问开放；取消只作用于尚未上传完成的录音。
- CSRF 因同源 Cookie + SameSite=Lax 在本期关闭。

建议下一轮按 US-03 真实上传与失败重试、US-04 可替换 ASR、US-05/06 确认与检索打磨、US-08 真实导出任务与受控出站的顺序做，仍然不要提前做工牌固件和 CRM。
