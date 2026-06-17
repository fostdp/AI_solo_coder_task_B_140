import { createRouter, createWebHashHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    redirect: '/dashboard'
  },
  {
    path: '/dashboard',
    name: 'dashboard',
    component: () => import('@/views/Dashboard.vue')
  },
  {
    path: '/3d-view',
    name: '3d-view',
    component: () => import('@/views/Ship3DView.vue')
  },
  {
    path: '/stability',
    name: 'stability',
    component: () => import('@/views/StabilityAnalysis.vue')
  },
  {
    path: '/loading',
    name: 'loading',
    component: () => import('@/views/LoadingOptimization.vue')
  },
  {
    path: '/history',
    name: 'history',
    component: () => import('@/views/HistoryData.vue')
  },
  {
    path: '/ship-comparison',
    name: 'ship-comparison',
    component: () => import('@/views/ShipComparison.vue')
  },
  {
    path: '/storm-simulation',
    name: 'storm-simulation',
    component: () => import('@/views/StormSimulation.vue')
  },
  {
    path: '/virtual-loading',
    name: 'virtual-loading',
    component: () => import('@/views/VirtualLoading.vue')
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

export default router
