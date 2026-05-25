import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/', redirect: '/live' },
  {
    path: '/live',
    component: () => import('../views/LiveFeed.vue'),
    meta: { title: '实时舆情流' }
  },
  {
    path: '/dashboard',
    component: () => import('../views/Dashboard.vue'),
    meta: { title: '数据总览' }
  },
  {
    path: '/trend',
    component: () => import('../views/Trend.vue'),
    meta: { title: '趋势分析' }
  },
  {
    path: '/topics',
    component: () => import('../views/Topics.vue'),
    meta: { title: '话题追踪' }
  },
  {
    path: '/alerts',
    component: () => import('../views/Alerts.vue'),
    meta: { title: '预警中心' }
  },
  {
    path: '/settings',
    component: () => import('../views/Settings.vue'),
    meta: { title: '系统设置' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
