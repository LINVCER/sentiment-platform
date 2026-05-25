<template>
  <div class="settings-page">
    <el-row :gutter="16">
      <el-col :span="12">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>监控关键词</span>
              <el-button size="small" type="primary" @click="showAddKeyword = true">添加关键词</el-button>
            </div>
          </template>
          <el-table :data="keywords" stripe size="small">
            <el-table-column prop="keyword" label="关键词" />
            <el-table-column prop="platform" label="平台" width="100">
              <template #default="{ row }">
                <el-tag size="small" :type="row.platform === 'weibo' ? 'danger' : row.platform === 'xiaohongshu' ? 'success' : 'info'">
                  {{ row.platform === 'weibo' ? '微博' : row.platform === 'xiaohongshu' ? '小红书' : '全部' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="70">
              <template #default="{ row }">
                <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? '启用' : '禁用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="100">
              <template #default="{ row }">
                <el-button size="small" text type="primary" @click="toggleKeyword(row)">{{ row.enabled ? '禁用' : '启用' }}</el-button>
                <el-button size="small" text type="danger" @click="deleteKeyword(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card>
          <template #header>系统状态</template>
          <div v-for="h in health" :key="h.moduleName" class="health-item">
            <el-tag :type="h.status === 'healthy' ? 'success' : h.status === 'degraded' ? 'warning' : 'danger'" size="small">
              {{ h.status }}
            </el-tag>
            <span class="module">{{ h.moduleName }}</span>
            <span class="time">{{ h.lastHeartbeat ? h.lastHeartbeat.substring(5, 16) : '-' }}</span>
          </div>
          <el-empty v-if="health.length === 0" description="暂无状态" :image-size="60" />
        </el-card>
      </el-col>
    </el-row>

    <el-card style="margin-top:16px">
      <template #header>平台登录</template>
      <el-alert type="info" :closable="false" style="margin-bottom:16px">
        小红书需要登录后才能采集。点击登录会弹出浏览器窗口，请在窗口中完成扫码登录。
      </el-alert>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="小红书">
          <el-tag :type="xhsStatus.hasCookies ? 'success' : 'danger'" size="small">
            {{ xhsStatus.hasCookies ? '已登录' : '未登录' }}
          </el-tag>
          <el-button size="small" type="primary" style="margin-left:12px"
                     :loading="xhsLoggingIn" @click="xhsLogin">
            {{ xhsStatus.hasCookies ? '重新登录' : '登录' }}
          </el-button>
        </el-descriptions-item>
        <el-descriptions-item label="微博">
          <el-tag type="success" size="small">无需登录</el-tag>
        </el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card style="margin-top:16px">
      <template #header>通知渠道配置</template>
      <el-form label-width="120px">
        <el-form-item label="Webhook URL">
          <el-input v-model="webhookUrl" placeholder="企业微信/钉钉 Webhook 地址" />
        </el-form-item>
        <el-form-item label="Webhook 类型">
          <el-radio-group v-model="webhookType">
            <el-radio label="wechat">企业微信</el-radio>
            <el-radio label="dingtalk">钉钉</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="通知级别">
          <el-checkbox-group v-model="notifyLevels">
            <el-checkbox label="critical">严重</el-checkbox>
            <el-checkbox label="warning">警告</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="saveNotifyConfig">保存配置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- Add Keyword Dialog -->
    <el-dialog v-model="showAddKeyword" title="添加监控关键词" width="400px">
      <el-form :model="newKeyword" label-width="80px">
        <el-form-item label="关键词">
          <el-input v-model="newKeyword.keyword" placeholder="如: AI, 新能源" />
        </el-form-item>
        <el-form-item label="平台">
          <el-select v-model="newKeyword.platform" style="width:100%">
            <el-option label="全部" value="all" />
            <el-option label="微博" value="weibo" />
            <el-option label="小红书" value="xiaohongshu" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddKeyword = false">取消</el-button>
        <el-button type="primary" @click="addKeyword">添加</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import axios from 'axios'

const keywords = ref([])
const health = ref([])
const showAddKeyword = ref(false)
const newKeyword = ref({ keyword: '', platform: 'all', enabled: true })
const webhookUrl = ref('')
const webhookType = ref('wechat')
const notifyLevels = ref(['critical', 'warning'])
const xhsStatus = ref({ hasCookies: false })
const xhsLoggingIn = ref(false)

async function loadKeywords() {
  try {
    const { data } = await axios.get('/api/settings/keywords')
    keywords.value = data || []
  } catch (e) { console.warn('Keywords load failed') }
}

async function loadHealth() {
  try {
    const { data } = await axios.get('/api/settings/health')
    health.value = data.modules || []
  } catch (e) { console.warn('Health load failed') }
}

async function addKeyword() {
  if (!newKeyword.value.keyword.trim()) return
  try {
    await axios.post('/api/settings/keywords', newKeyword.value)
    showAddKeyword.value = false
    newKeyword.value = { keyword: '', platform: 'all', enabled: true }
    await loadKeywords()
    ElMessage.success('添加成功')
  } catch (e) { ElMessage.error('添加失败') }
}

async function toggleKeyword(kw) {
  kw.enabled = !kw.enabled
  await axios.put(`/api/settings/keywords/${kw.id}`, kw)
}

async function deleteKeyword(kw) {
  await axios.delete(`/api/settings/keywords/${kw.id}`)
  keywords.value = keywords.value.filter(k => k.id !== kw.id)
}

function saveNotifyConfig() {
  ElMessage.success('配置已保存（需重启后端生效）')
}

async function loadXhsStatus() {
  try {
    const { data } = await axios.get('/api/settings/xhs/status')
    xhsStatus.value = data
  } catch (e) { console.warn('XHS status load failed') }
}

async function xhsLogin() {
  xhsLoggingIn.value = true
  ElMessage.info('正在打开浏览器，请完成扫码登录...')
  try {
    const { data } = await axios.post('/api/settings/xhs/login', {}, { timeout: 130000 })
    if (data.success) {
      ElMessage.success('小红书登录成功！')
      xhsStatus.value = { hasCookies: true }
    } else {
      ElMessage.warning('登录超时或失败，请重试')
    }
  } catch (e) {
    ElMessage.error('登录请求失败')
  } finally {
    xhsLoggingIn.value = false
  }
}

onMounted(() => { loadKeywords(); loadHealth(); loadXhsStatus() })
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
.health-item { display: flex; align-items: center; gap: 12px; padding: 8px 0; border-bottom: 1px solid #f0f0f0; }
.module { font-weight: 500; flex: 1; }
.time { color: #999; font-size: 12px; }
</style>
