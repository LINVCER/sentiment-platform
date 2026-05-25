<template>
  <div class="alerts-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <span>预警中心</span>
            <el-badge :value="unreadCount" :hidden="unreadCount === 0" style="margin-left:8px" />
          </div>
          <div class="header-right">
            <el-radio-group v-model="severityFilter" size="small" @change="loadAlerts">
              <el-radio-button label="">全部</el-radio-button>
              <el-radio-button label="critical">严重</el-radio-button>
              <el-radio-button label="warning">警告</el-radio-button>
              <el-radio-button label="info">提示</el-radio-button>
            </el-radio-group>
            <el-button size="small" @click="markAllRead" :disabled="unreadCount === 0">全部已读</el-button>
          </div>
        </div>
      </template>
      <el-table :data="alerts" stripe>
        <el-table-column prop="severity" label="级别" width="80">
          <template #default="{ row }">
            <el-tag :type="row.severity === 'critical' ? 'danger' : row.severity === 'warning' ? 'warning' : 'info'" effect="dark" size="small">
              {{ row.severity === 'critical' ? '严重' : row.severity === 'warning' ? '警告' : '提示' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" />
        <el-table-column prop="description" label="描述" show-overflow-tooltip />
        <el-table-column prop="alertType" label="类型" width="120">
          <template #default="{ row }">
            {{ row.alertType === 'negative_surge' ? '负面突增' : row.alertType === 'keyword_trigger' ? '敏感词' : '话题增长' }}
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="时间" width="160" />
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="markRead(row)" v-if="!row.isRead">已读</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card style="margin-top:16px">
      <template #header>
        <div class="card-header">
          <span>预警规则配置</span>
          <el-button size="small" type="primary" @click="showCreateDialog = true">新增规则</el-button>
        </div>
      </template>
      <el-table :data="rules" stripe>
        <el-table-column prop="ruleName" label="规则名称" />
        <el-table-column prop="ruleType" label="类型" width="120">
          <template #default="{ row }">
            {{ row.ruleType === 'negative_surge' ? '负面突增' : row.ruleType === 'keyword_trigger' ? '敏感词' : '话题增长' }}
          </template>
        </el-table-column>
        <el-table-column label="配置" show-overflow-tooltip>
          <template #default="{ row }">{{ row.config }}</template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-switch v-model="row.enabled" @change="updateRule(row)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-button size="small" text type="danger" @click="deleteRule(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- Create Rule Dialog -->
    <el-dialog v-model="showCreateDialog" title="新增预警规则" width="500px">
      <el-form :model="newRule" label-width="100px">
        <el-form-item label="规则名称">
          <el-input v-model="newRule.ruleName" />
        </el-form-item>
        <el-form-item label="规则类型">
          <el-select v-model="newRule.ruleType" style="width:100%">
            <el-option label="负面突增" value="negative_surge" />
            <el-option label="敏感词触发" value="keyword_trigger" />
            <el-option label="话题增长" value="topic_growth" />
          </el-select>
        </el-form-item>
        <el-form-item label="配置JSON">
          <el-input v-model="newRule.config" type="textarea" :rows="4" placeholder='{"negativeRatioThreshold":0.6,"minPostCount":20,"windowHours":1,"silenceHours":24}' />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="newRule.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" @click="createRule">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElNotification } from 'element-plus'
import axios from 'axios'

const alerts = ref([])
const rules = ref([])
const severityFilter = ref('')
const unreadCount = ref(0)
const showCreateDialog = ref(false)
const newRule = ref({ ruleName: '', ruleType: 'negative_surge', config: '{}', enabled: true })
let stompClient = null

async function loadAlerts() {
  const params = severityFilter.value ? `?severity=${severityFilter.value}` : ''
  const { data } = await axios.get(`/api/alerts${params}`)
  alerts.value = data.records || []
}

async function loadUnreadCount() {
  const { data } = await axios.get('/api/alerts/unread-count')
  unreadCount.value = data.count || 0
}

async function loadRules() {
  const { data } = await axios.get('/api/alerts/rules')
  rules.value = data || []
}

async function markRead(alert) {
  await axios.put(`/api/alerts/${alert.id}/read`)
  alert.isRead = true
  unreadCount.value = Math.max(0, unreadCount.value - 1)
}

async function markAllRead() {
  await axios.put('/api/alerts/read-all')
  alerts.value.forEach(a => a.isRead = true)
  unreadCount.value = 0
}

async function updateRule(rule) {
  await axios.put(`/api/alerts/rules/${rule.id}`, rule)
}

async function deleteRule(rule) {
  await axios.delete(`/api/alerts/rules/${rule.id}`)
  rules.value = rules.value.filter(r => r.id !== rule.id)
}

async function createRule() {
  try {
    await axios.post('/api/alerts/rules', newRule.value)
    showCreateDialog.value = false
    newRule.value = { ruleName: '', ruleType: 'negative_surge', config: '{}', enabled: true }
    await loadRules()
    ElMessage.success('规则创建成功')
  } catch (e) {
    ElMessage.error('创建失败')
  }
}

function connectWebSocket() {
  import('sockjs-client').then(({ default: SockJS }) => {
    import('@stomp/stompjs').then(({ Client }) => {
      const socket = new SockJS('/ws')
      stompClient = new Client({
        webSocketFactory: () => socket,
        onConnect: () => {
          stompClient.subscribe('/topic/alert', (msg) => {
            const alert = JSON.parse(msg.body)
            alerts.value.unshift(alert)
            unreadCount.value++
            ElNotification({
              title: `[${alert.severity === 'critical' ? '严重' : '警告'}] ${alert.title}`,
              message: alert.description,
              type: alert.severity === 'critical' ? 'error' : 'warning',
              duration: 8000
            })
          })
        }
      })
      stompClient.activate()
    })
  })
}

onMounted(() => {
  loadAlerts()
  loadUnreadCount()
  loadRules()
  connectWebSocket()
})

onUnmounted(() => {
  if (stompClient) stompClient.deactivate()
})
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
.header-left { display: flex; align-items: center; }
.header-right { display: flex; gap: 8px; align-items: center; }
</style>
