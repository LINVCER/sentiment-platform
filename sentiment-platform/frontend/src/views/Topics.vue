<template>
  <div class="topics-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>话题追踪</span>
          <el-input v-model="search" placeholder="搜索话题..." clearable style="width:200px" size="small" />
        </div>
      </template>
      <el-table :data="topics" stripe style="width:100%">
        <el-table-column label="排名" width="60">
          <template #default="{ $index }">
            <span class="rank" :class="{ top3: $index < 3 }">{{ $index + 1 }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="topicName" label="话题" />
        <el-table-column prop="postCount" label="帖子数" width="80" sortable />
        <el-table-column prop="heatScore" label="热度" width="80" sortable>
          <template #default="{ row }">{{ (row.heatScore || 0).toFixed(0) }}</template>
        </el-table-column>
        <el-table-column label="正面占比" width="120">
          <template #default="{ row }">
            <el-progress :percentage="((row.sentimentRatio || 0) * 100)" :stroke-width="8"
              :color="(row.sentimentRatio || 0) > 0.5 ? '#52c41a' : '#ff4d4f'" />
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 'active' ? 'success' : 'info'" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="viewDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="detailVisible" :title="selectedTopic?.topicName" width="700px">
      <div v-if="selectedTopic">
        <p><strong>关键词:</strong> {{ selectedTopic.keywords }}</p>
        <p><strong>帖子数:</strong> {{ selectedTopic.postCount }} | <strong>热度:</strong> {{ (selectedTopic.heatScore || 0).toFixed(0) }}</p>
        <el-divider />
        <h4>相关帖子</h4>
        <div v-for="p in topicPosts" :key="p.id" class="topic-post">
          <el-tag :type="p.sentiment === 1 ? 'success' : 'danger'" size="small">
            {{ p.sentiment === 1 ? '正面' : '负面' }}
          </el-tag>
          <span>{{ p.content?.substring(0, 100) }}...</span>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import axios from 'axios'

const topics = ref([])
const search = ref('')
const detailVisible = ref(false)
const selectedTopic = ref(null)
const topicPosts = ref([])

onMounted(async () => {
  try {
    const { data } = await axios.get('/api/topics?size=50')
    topics.value = data.records || []
  } catch (e) {
    console.warn('Topics load failed')
  }
})

async function viewDetail(topic) {
  selectedTopic.value = topic
  detailVisible.value = true
  try {
    const { data } = await axios.get(`/api/topics/${topic.id}`)
    topicPosts.value = data.posts || []
  } catch (e) {
    topicPosts.value = []
  }
}
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
.rank { width: 24px; height: 24px; border-radius: 50%; background: #f0f0f0; display: inline-flex; align-items: center; justify-content: center; font-size: 12px; }
.rank.top3 { background: #ff4d4f; color: #fff; }
.topic-post { padding: 8px 0; border-bottom: 1px solid #f0f0f0; display: flex; gap: 8px; align-items: flex-start; }
</style>
