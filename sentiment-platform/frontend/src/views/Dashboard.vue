<template>
  <div class="dashboard">
    <el-row :gutter="16" class="stat-cards">
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-item">
            <div class="stat-value">{{ stats.todayCount || 0 }}</div>
            <div class="stat-label">今日采集</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-item">
            <div class="stat-value positive">{{ stats.positive || 0 }}</div>
            <div class="stat-label">正面舆情</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-item">
            <div class="stat-value negative">{{ stats.negative || 0 }}</div>
            <div class="stat-label">负面舆情</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-item">
            <div class="stat-value warning">{{ stats.unreadAlerts || 0 }}</div>
            <div class="stat-label">未读预警</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" style="margin-top:16px">
      <el-col :span="16">
        <el-card>
          <template #header>情感走势 (24h)</template>
          <div ref="trendChart" style="height:300px"></div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card>
          <template #header>情感分布</template>
          <div ref="pieChart" style="height:300px"></div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" style="margin-top:16px">
      <el-col :span="12">
        <el-card>
          <template #header>热点话题 TOP 10</template>
          <div v-for="topic in topics" :key="topic.id" class="topic-item">
            <span class="topic-rank" :class="{ top3: topics.indexOf(topic) < 3 }">
              {{ topics.indexOf(topic) + 1 }}
            </span>
            <span class="topic-name">{{ topic.topicName }}</span>
            <span class="topic-heat">热度 {{ (topic.heatScore || 0).toFixed(0) }}</span>
            <el-progress :percentage="(topic.sentimentRatio || 0) * 100" :stroke-width="6" style="width:100px" />
          </div>
          <el-empty v-if="topics.length === 0" description="暂无热点话题" :image-size="60" />
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card>
          <template #header>系统状态</template>
          <div v-for="h in health" :key="h.moduleName" class="health-item">
            <el-tag :type="h.status === 'healthy' ? 'success' : h.status === 'degraded' ? 'warning' : 'danger'" size="small">
              {{ h.status }}
            </el-tag>
            <span class="module-name">{{ h.moduleName }}</span>
            <span class="heartbeat">最后心跳: {{ h.lastHeartbeat }}</span>
          </div>
          <el-empty v-if="health.length === 0" description="暂无状态信息" :image-size="60" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue'
import axios from 'axios'
import * as echarts from 'echarts'

const stats = ref({})
const topics = ref([])
const health = ref([])
const trendChart = ref(null)
const pieChart = ref(null)

onMounted(async () => {
  try {
    const [statsRes, trendRes, topicsRes, healthRes] = await Promise.all([
      axios.get('/api/dashboard/stats'),
      axios.get('/api/dashboard/trend?hours=24'),
      axios.get('/api/dashboard/topics?limit=10'),
      axios.get('/api/dashboard/health')
    ])
    stats.value = statsRes.data
    topics.value = topicsRes.data
    health.value = healthRes.data

    nextTick(() => {
      renderTrend(trendRes.data)
      renderPie()
    })
  } catch (e) {
    console.warn('Dashboard load failed', e)
  }
})

function renderTrend(data) {
  if (!trendChart.value || !data.length) return
  const chart = echarts.init(trendChart.value)
  chart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['正面', '负面'] },
    xAxis: { type: 'category', data: data.map(d => d.hour?.substring(11, 16)) },
    yAxis: { type: 'value' },
    series: [
      { name: '正面', type: 'line', smooth: true, data: data.map(d => d.positive), itemStyle: { color: '#52c41a' } },
      { name: '负面', type: 'line', smooth: true, data: data.map(d => d.negative), itemStyle: { color: '#ff4d4f' } }
    ]
  })
}

function renderPie() {
  if (!pieChart.value) return
  const chart = echarts.init(pieChart.value)
  chart.setOption({
    tooltip: { trigger: 'item' },
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      data: [
        { value: stats.value.positive || 0, name: '正面', itemStyle: { color: '#52c41a' } },
        { value: stats.value.negative || 0, name: '负面', itemStyle: { color: '#ff4d4f' } }
      ]
    }]
  })
}
</script>

<style scoped>
.stat-cards .stat-item { text-align: center; }
.stat-value { font-size: 32px; font-weight: bold; }
.stat-value.positive { color: #52c41a; }
.stat-value.negative { color: #ff4d4f; }
.stat-value.warning { color: #faad14; }
.stat-label { color: #999; margin-top: 4px; }
.topic-item { display: flex; align-items: center; gap: 12px; padding: 8px 0; border-bottom: 1px solid #f0f0f0; }
.topic-rank { width: 24px; height: 24px; border-radius: 50%; background: #f0f0f0; display: flex; align-items: center; justify-content: center; font-size: 12px; font-weight: bold; }
.topic-rank.top3 { background: #ff4d4f; color: #fff; }
.topic-name { flex: 1; font-size: 14px; }
.topic-heat { color: #999; font-size: 12px; }
.health-item { display: flex; align-items: center; gap: 12px; padding: 8px 0; border-bottom: 1px solid #f0f0f0; }
.module-name { font-weight: 500; }
.heartbeat { color: #999; font-size: 12px; margin-left: auto; }
</style>
