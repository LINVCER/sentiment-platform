<template>
  <div class="trend-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>趋势分析</span>
          <div>
            <el-radio-group v-model="timeRange" size="small" @change="loadTrend">
              <el-radio-button label="24">24小时</el-radio-button>
              <el-radio-button label="168">7天</el-radio-button>
              <el-radio-button label="720">30天</el-radio-button>
            </el-radio-group>
          </div>
        </div>
      </template>
      <div ref="trendChart" style="height:400px"></div>
    </el-card>

    <el-row :gutter="16" style="margin-top:16px">
      <el-col :span="12">
        <el-card>
          <template #header>平台对比</template>
          <div ref="platformChart" style="height:300px"></div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card>
          <template #header>地域分布</template>
          <div ref="regionChart" style="height:300px"></div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue'
import axios from 'axios'
import * as echarts from 'echarts'

const timeRange = ref('24')
const trendChart = ref(null)
const platformChart = ref(null)
const regionChart = ref(null)

async function loadTrend() {
  try {
    const { data } = await axios.get(`/api/dashboard/trend?hours=${timeRange.value}`)
    nextTick(() => {
      if (trendChart.value) {
        const chart = echarts.init(trendChart.value)
        chart.setOption({
          tooltip: { trigger: 'axis' },
          legend: { data: ['正面', '负面', '总数'] },
          xAxis: { type: 'category', data: data.map(d => d.hour?.substring(5, 16)) },
          yAxis: { type: 'value' },
          series: [
            { name: '正面', type: 'bar', stack: 'total', data: data.map(d => d.positive), itemStyle: { color: '#52c41a' } },
            { name: '负面', type: 'bar', stack: 'total', data: data.map(d => d.negative), itemStyle: { color: '#ff4d4f' } }
          ]
        })
      }
    })
  } catch (e) {
    console.warn('Trend load failed')
  }
}

onMounted(() => {
  loadTrend()
  nextTick(() => {
    if (platformChart.value) {
      const chart = echarts.init(platformChart.value)
      chart.setOption({
        tooltip: { trigger: 'item' },
        legend: { orient: 'vertical', left: 'left' },
        series: [{
          type: 'pie',
          radius: '60%',
          data: [
            { value: 0, name: '微博' },
            { value: 0, name: '小红书' }
          ]
        }]
      })
    }
  })
})
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
</style>
