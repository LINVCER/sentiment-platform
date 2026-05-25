<template>
  <div class="live-feed">
    <div class="feed-header">
      <h2>实时舆情流</h2>
      <div class="filters">
        <el-select v-model="platformFilter" placeholder="平台" clearable size="small" style="width:100px">
          <el-option label="全部" value="" />
          <el-option label="微博" value="weibo" />
          <el-option label="小红书" value="xiaohongshu" />
        </el-select>
        <el-select v-model="sentimentFilter" placeholder="情感" clearable size="small" style="width:100px">
          <el-option label="全部" value="" />
          <el-option label="正面" :value="1" />
          <el-option label="负面" :value="0" />
        </el-select>
        <el-tag :type="connected ? 'success' : 'danger'" size="small">
          {{ connected ? '已连接' : '未连接' }}
        </el-tag>
      </div>
    </div>
    <div class="feed-list" ref="feedList">
      <div v-for="post in filteredPosts" :key="post.id" class="post-card" :class="sentimentClass(post)">
        <div class="post-header">
          <el-tag :type="post.platform === 'weibo' ? 'danger' : 'success'" size="small">
            {{ post.platform === 'weibo' ? '微博' : '小红书' }}
          </el-tag>
          <span class="author">{{ post.author }}</span>
          <span class="time">{{ formatTime(post.publishTime || post.crawlTime) }}</span>
          <el-tag v-if="post.sentiment !== null" :type="post.sentiment === 1 ? 'success' : 'danger'" size="small" effect="dark">
            {{ post.sentiment === 1 ? '正面' : '负面' }} {{ (post.sentimentScore * 100).toFixed(0) }}%
          </el-tag>
          <el-tag v-else type="info" size="small">分析中</el-tag>
        </div>
        <div class="post-content">{{ post.content }}</div>
        <div class="post-footer" v-if="post.keywords">
          <el-tag v-for="kw in post.keywords.split(',')" :key="kw" size="small" type="info" effect="plain">{{ kw }}</el-tag>
        </div>
      </div>
      <el-empty v-if="posts.length === 0" description="暂无数据，等待采集..." />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import axios from 'axios'
import dayjs from 'dayjs'
import relativeTime from 'dayjs/plugin/relativeTime'
import 'dayjs/locale/zh-cn'

dayjs.extend(relativeTime)
dayjs.locale('zh-cn')

const posts = ref([])
const platformFilter = ref('')
const sentimentFilter = ref('')
const connected = ref(false)
const feedList = ref(null)
let stompClient = null

const filteredPosts = computed(() => {
  return posts.value.filter(p => {
    if (platformFilter.value && p.platform !== platformFilter.value) return false
    if (sentimentFilter.value !== '' && p.sentiment !== sentimentFilter.value) return false
    return true
  })
})

function sentimentClass(post) {
  if (post.sentiment === 1) return 'positive'
  if (post.sentiment === 0) return 'negative'
  return 'neutral'
}

function formatTime(t) {
  return dayjs(t).fromNow()
}

onMounted(async () => {
  // Load recent posts
  try {
    const { data } = await axios.get('/api/posts?size=50')
    posts.value = data.records || []
  } catch (e) {
    console.warn('Failed to load posts')
  }

  // Connect WebSocket
  try {
    const SockJS = (await import('sockjs-client')).default
    const { Client } = await import('@stomp/stompjs')
    const socket = new SockJS('/ws')
    stompClient = new Client({
      webSocketFactory: () => socket,
      onConnect: () => {
        connected.value = true
        stompClient.subscribe('/topic/new-post', (msg) => {
          const post = JSON.parse(msg.body)
          posts.value.unshift(post)
          if (posts.value.length > 200) posts.value.pop()
          nextTick(() => {
            if (feedList.value) feedList.value.scrollTop = 0
          })
        })
      },
      onDisconnect: () => { connected.value = false }
    })
    stompClient.activate()
  } catch (e) {
    console.warn('WebSocket connection failed', e)
  }
})

onUnmounted(() => {
  if (stompClient) stompClient.deactivate()
})
</script>

<style scoped>
.live-feed { display: flex; flex-direction: column; height: calc(100vh - 140px); }
.feed-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.feed-header h2 { font-size: 18px; }
.filters { display: flex; gap: 8px; align-items: center; }
.feed-list { flex: 1; overflow-y: auto; }
.post-card {
  background: #fff;
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 12px;
  border-left: 4px solid #d9d9d9;
  transition: box-shadow 0.2s;
}
.post-card:hover { box-shadow: 0 2px 12px rgba(0,0,0,0.1); }
.post-card.positive { border-left-color: #52c41a; }
.post-card.negative { border-left-color: #ff4d4f; }
.post-header { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; font-size: 13px; }
.author { color: #333; font-weight: 500; }
.time { color: #999; }
.post-content { font-size: 14px; line-height: 1.6; color: #333; margin-bottom: 8px; }
.post-footer { display: flex; gap: 4px; flex-wrap: wrap; }
</style>
