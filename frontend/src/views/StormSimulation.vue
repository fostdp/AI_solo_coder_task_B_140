<template>
  <div class="storm-simulation" :class="{ 'danger-flash': showDangerFlash }">
    <div class="simulation-header">
      <div class="header-left">
        <h2 class="page-title">
          <el-icon :size="26"><Lightning /></el-icon>
          风暴模拟
        </h2>
        <p class="page-desc">
          极端海况下船舶抗倾覆能力蒙特卡洛模拟，支持不同海况等级下的稳性风险评估
        </p>
      </div>
      <div class="header-actions">
        <el-button type="info" @click="historyDrawerVisible = true">
          <el-icon><History /></el-icon>
          历史记录
        </el-button>
        <el-button type="success" @click="exportData('json')" :disabled="!simulationResult">
          <el-icon><Download /></el-icon>
          导出JSON
        </el-button>
        <el-button type="success" @click="exportData('image')" :disabled="!simulationResult">
          <el-icon><Picture /></el-icon>
          导出图片
        </el-button>
      </div>
    </div>

    <div class="simulation-content">
      <div class="left-panel">
        <el-card class="config-card" shadow="hover">
          <template #header>
            <div class="card-header">
              <el-icon><Setting /></el-icon>
              <span>风暴参数配置</span>
            </div>
          </template>

          <el-form label-position="top" :model="formData" class="config-form">
            <el-form-item label="海况等级">
              <el-select v-model="formData.stormSeverity" @change="onSeverityChange" placeholder="请选择海况等级">
                <el-option label="热带风暴 (浪高4-6m)" value="TROPICAL_STORM" />
                <el-option label="强风暴 (浪高6-9m)" value="STRONG_STORM" />
                <el-option label="台风 (浪高9-12m)" value="TYPHOON" />
                <el-option label="飓风 (浪高>12m)" value="HURRICANE" />
              </el-select>
            </el-form-item>

            <el-collapse v-model="collapseNames" class="custom-collapse">
              <el-collapse-item title="自定义参数" name="custom">
                <el-form-item label="浪高 (m)">
                  <el-slider
                    v-model="formData.waveHeight"
                    :min="2"
                    :max="15"
                    :step="0.5"
                    show-input
                    :marks="{ 2: '2', 8: '8', 15: '15' }"
                  />
                </el-form-item>
                <el-form-item label="风速 (m/s)">
                  <el-slider
                    v-model="formData.windSpeed"
                    :min="20"
                    :max="60"
                    :step="1"
                    show-input
                    :marks="{ 20: '20', 40: '40', 60: '60' }"
                  />
                </el-form-item>
                <el-form-item label="波浪周期 (s)">
                  <el-slider
                    v-model="formData.wavePeriod"
                    :min="5"
                    :max="20"
                    :step="0.5"
                    show-input
                    :marks="{ 5: '5', 12: '12', 20: '20' }"
                  />
                </el-form-item>
                <el-form-item label="模拟时长 (小时)">
                  <el-slider
                    v-model="formData.simulationDurationHours"
                    :min="6"
                    :max="72"
                    :step="1"
                    show-input
                    :marks="{ 6: '6', 24: '24', 72: '72' }"
                  />
                </el-form-item>
                <el-form-item label="蒙特卡洛迭代次数">
                  <el-slider
                    v-model="formData.monteCarloIterations"
                    :min="1000"
                    :max="100000"
                    :step="1000"
                    show-input
                    :marks="{ 1000: '1k', 50000: '50k', 100000: '100k' }"
                  />
                </el-form-item>
              </el-collapse-item>
            </el-collapse>

            <el-form-item label="装载条件">
              <el-radio-group v-model="formData.loadingCondition">
                <el-radio-button value="BALLAST">压载</el-radio-button>
                <el-radio-button value="HALF_LOAD">半载</el-radio-button>
                <el-radio-button value="FULL_LOAD">满载</el-radio-button>
              </el-radio-group>
            </el-form-item>

            <el-form-item label="船舶选择">
              <el-select
                v-model="selectedShips"
                multiple
                collapse-tags
                collapse-tags-tooltip
                placeholder="选择船舶进行模拟"
                style="width: 100%"
              >
                <el-option
                  v-for="ship in shipList"
                  :key="ship.id"
                  :label="ship.name"
                  :value="ship.id"
                />
              </el-select>
            </el-form-item>

            <el-form-item>
              <el-space class="action-buttons" wrap>
                <el-button
                  type="primary"
                  @click="startSimulation"
                  :loading="simulating"
                  :disabled="selectedShips.length === 0"
                >
                  <el-icon><VideoPlay /></el-icon>
                  {{ simulating ? '模拟中...' : '开始模拟' }}
                </el-button>
                <el-button
                  type="warning"
                  @click="toggleAnimation"
                  :disabled="!simulationResult || !hasTimeSeries"
                >
                  <el-icon>{{ isAnimating ? 'VideoPause' : 'Promotion' }}</el-icon>
                  {{ isAnimating ? '暂停动画' : '模拟动画' }}
                </el-button>
                <el-button type="danger" @click="resetForm">
                  <el-icon><RefreshRight /></el-icon>
                  重置
                </el-button>
              </el-space>
            </el-form-item>
          </el-form>

          <div v-if="simulating" class="progress-section">
            <div class="progress-label">
              <span>蒙特卡洛迭代进度</span>
              <span>{{ simulationProgress }}%</span>
            </div>
            <el-progress
              :percentage="simulationProgress"
              :color="progressColor"
              :stroke-width="12"
            />
          </div>

          <div v-if="isAnimating && animationTime !== null" class="animation-time-panel">
            <el-divider class="divider" />
            <div class="time-display">
              <span class="time-label">当前时刻</span>
              <span class="time-value">{{ formatTime(animationTime) }}</span>
            </div>
            <div class="time-controls">
              <el-button size="small" @click="animationSpeed = 0.5">0.5x</el-button>
              <el-button size="small" type="primary" @click="animationSpeed = 1">1x</el-button>
              <el-button size="small" @click="animationSpeed = 2">2x</el-button>
              <el-button size="small" @click="animationSpeed = 5">5x</el-button>
            </div>
          </div>
        </el-card>
      </div>

      <div class="right-panel">
        <div class="top-metrics-row">
          <div class="gauge-card">
            <div class="card-header">
              <h3 class="card-title">
                <el-icon><Warning /></el-icon>
                倾覆风险
              </h3>
            </div>
            <div class="gauge-container">
              <canvas ref="gaugeRef"></canvas>
            </div>
            <div class="gauge-legend">
              <span class="legend-item"><span class="legend-dot green"></span>安全 < 10%</span>
              <span class="legend-item"><span class="legend-dot yellow"></span>注意 10-30%</span>
              <span class="legend-item"><span class="legend-dot orange"></span>警告 30-60%</span>
              <span class="legend-item"><span class="legend-dot red"></span>危险 > 60%</span>
            </div>
          </div>

          <div class="metrics-grid">
            <div class="metric-card" :class="{ highlight: true }">
              <div class="metric-icon danger">
                <el-icon><WarningFilled /></el-icon>
              </div>
              <div class="metric-info">
                <span class="metric-label">倾覆概率</span>
                <span class="metric-value" :style="{ color: getRiskColor(simulationResult?.capsizingProbability) }">
                  {{ formatPercent(simulationResult?.capsizingProbability) }}
                </span>
                <span class="metric-status">{{ getRiskLevel(simulationResult?.capsizingProbability) }}</span>
              </div>
            </div>

            <div class="metric-card">
              <div class="metric-icon warning">
                <el-icon><RotateRight /></el-icon>
              </div>
              <div class="metric-info">
                <span class="metric-label">最大横摇角</span>
                <span class="metric-value">
                  {{ simulationResult?.maxRollAngleExperienced?.toFixed(1) || '--' }}
                  <small>°</small>
                </span>
                <span class="metric-desc" :class="getAngleClass(simulationResult?.maxRollAngleExperienced)">
                  {{ getAngleStatus(simulationResult?.maxRollAngleExperienced) }}
                </span>
              </div>
            </div>

            <div class="metric-card">
              <div class="metric-icon success">
                <el-icon><Cpu /></el-icon>
              </div>
              <div class="metric-info">
                <span class="metric-label">最小GM值</span>
                <span class="metric-value" :class="getGmClass(simulationResult?.minGmExperienced)">
                  {{ simulationResult?.minGmExperienced?.toFixed(3) || '--' }}
                  <small>m</small>
                </span>
                <span class="metric-desc">{{ getGmStatus(simulationResult?.minGmExperienced) }}</span>
              </div>
            </div>

            <div class="metric-card">
              <div class="metric-icon primary">
                <el-icon><TrendCharts /></el-icon>
              </div>
              <div class="metric-info">
                <span class="metric-label">稳性臂损失</span>
                <span class="metric-value">
                  {{ simulationResult?.rightingArmLossPercentage?.toFixed(1) || '--' }}
                  <small>%</small>
                </span>
              </div>
            </div>

            <div class="metric-card" :class="{ 'risk-highlight': simulationResult?.parametricRollRisk }">
              <div class="metric-icon" :class="simulationResult?.parametricRollRisk ? 'danger' : 'info'">
                <el-icon><DataAnalysis /></el-icon>
              </div>
              <div class="metric-info">
                <span class="metric-label">参数横摇风险</span>
                <el-tag
                  :type="simulationResult?.parametricRollRisk ? 'danger' : 'success'"
                  effect="dark"
                  size="large"
                  class="parametric-tag"
                >
                  {{ simulationResult?.parametricRollRisk ? '是' : '否' }}
                </el-tag>
              </div>
            </div>

            <div class="metric-card">
              <div class="metric-icon warning">
                <el-icon><Compass /></el-icon>
              </div>
              <div class="metric-info">
                <span class="metric-label">横甩概率</span>
                <span class="metric-value">
                  {{ formatPercent(simulationResult?.broachingProbability) }}
                </span>
              </div>
            </div>
          </div>
        </div>

        <div class="charts-row">
          <div class="chart-card">
            <div class="card-header">
              <h3 class="card-title">
                <el-icon><DataLine /></el-icon>
                横摇角时间序列
              </h3>
              <div class="legend-info">
                <span class="legend-item">
                  <span class="legend-color roll"></span>
                  横摇角
                </span>
                <span class="legend-item">
                  <span class="legend-color threshold"></span>
                  倾覆阈值 ±50°
                </span>
              </div>
            </div>
            <div class="chart-container">
              <canvas ref="rollChartRef"></canvas>
            </div>
          </div>

          <div class="chart-card">
            <div class="card-header">
              <h3 class="card-title">
                <el-icon><DataLine /></el-icon>
                GM值时间序列
              </h3>
              <div class="legend-info">
                <span class="legend-item">
                  <span class="legend-color gm"></span>
                  GM值
                </span>
                <span class="legend-item">
                  <span class="legend-color gm-warn"></span>
                  0.3m阈值
                </span>
                <span class="legend-item">
                  <span class="legend-color gm-danger"></span>
                  0.15m阈值
                </span>
              </div>
            </div>
            <div class="chart-container">
              <canvas ref="gmChartRef"></canvas>
            </div>
          </div>
        </div>

        <div class="charts-row">
          <div class="chart-card">
            <div class="card-header">
              <h3 class="card-title">
                <el-icon><Grid /></el-icon>
                风险热力图
              </h3>
              <div class="legend-info">
                <div class="heatmap-legend">
                  <span class="legend-item"><span class="legend-dot green"></span>低</span>
                  <span class="legend-item"><span class="legend-dot yellow"></span>中</span>
                  <span class="legend-item"><span class="legend-dot orange"></span>高</span>
                  <span class="legend-item"><span class="legend-dot red"></span>极高</span>
                </div>
              </div>
            </div>
            <div class="heatmap-container">
              <canvas ref="heatmapRef"></canvas>
            </div>
          </div>

          <div class="chart-card">
            <div class="card-header">
              <h3 class="card-title">
                <el-icon><Histogram /></el-icon>
                多船对比
              </h3>
              <el-radio-group v-model="compareMode" size="small" @change="updateCompareChart">
                <el-radio-button value="probability">倾覆概率</el-radio-button>
                <el-radio-button value="maxRoll">最大横摇角</el-radio-button>
                <el-radio-button value="minGm">最小GM</el-radio-button>
              </el-radio-group>
            </div>
            <div class="chart-container">
              <canvas ref="compareChartRef"></canvas>
            </div>
          </div>
        </div>

        <div v-if="rankedShips.length > 0" class="chart-card ranking-card">
          <div class="card-header">
            <h3 class="card-title">
              <el-icon><Trophy /></el-icon>
              船舶抗倾覆能力排名
            </h3>
          </div>
          <div class="ranking-container">
            <div
              v-for="(item, index) in rankedShips"
              :key="item.shipId"
              class="ranking-item"
            >
              <span class="rank-badge" :class="getRankClass(index)">
                {{ index + 1 }}
              </span>
              <span class="ship-name">{{ getShipName(item.shipId) }}</span>
              <div class="rank-bar-container">
                <div
                  class="rank-bar"
                  :style="{
                    width: `${100 - item.capsizingProbability * 100}%`,
                    backgroundColor: getRiskColor(item.capsizingProbability)
                  }"
                />
              </div>
              <span class="rank-value" :style="{ color: getRiskColor(item.capsizingProbability) }">
                {{ formatPercent(item.capsizingProbability) }}
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <el-drawer
      v-model="historyDrawerVisible"
      title="历史模拟记录"
      direction="rtl"
      size="500px"
    >
      <div class="history-list">
        <div
          v-for="record in historyList"
          :key="record.id"
          class="history-item"
          @click="loadHistoryRecord(record)"
        >
          <div class="history-header">
            <el-tag :type="getSeverityTagType(record.stormSeverity)" size="small">
              {{ getSeverityLabel(record.stormSeverity) }}
            </el-tag>
            <span class="history-time">{{ formatDateTime(record.simulationTime) }}</span>
          </div>
          <div class="history-content">
            <span class="history-ship">{{ getShipName(record.shipId) }}</span>
            <span class="history-prob" :style="{ color: getRiskColor(record.capsizingProbability) }">
              {{ formatPercent(record.capsizingProbability) }}
            </span>
          </div>
          <div class="history-params">
            <span>浪高: {{ record.waveHeight }}m</span>
            <span>风速: {{ record.windSpeed }}m/s</span>
            <span>{{ getLoadingLabel(record.loadingCondition) }}</span>
          </div>
        </div>
        <el-empty v-if="historyList.length === 0" description="暂无历史记录" />
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import {
  Lightning, History, Download, Picture, Setting, VideoPlay, VideoPause,
  Promotion, RefreshRight, Warning, WarningFilled, RotateRight, Cpu,
  TrendCharts, DataAnalysis, Compass, DataLine, Grid, Histogram, Trophy
} from '@element-plus/icons-vue'
import { Chart, registerables } from 'chart.js'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  simulateStorm, batchSimulateStorm, getSimulationHistory, getSimulationById
} from '@/api/stormSimulation'
import { getShips } from '@/api/ship'

Chart.register(...registerables)

const SEVERITY_PRESETS = {
  TROPICAL_STORM: { waveHeight: 5, windSpeed: 30, wavePeriod: 10 },
  STRONG_STORM: { waveHeight: 7.5, windSpeed: 40, wavePeriod: 12 },
  TYPHOON: { waveHeight: 10.5, windSpeed: 50, wavePeriod: 14 },
  HURRICANE: { waveHeight: 13, windSpeed: 58, wavePeriod: 16 }
}

const SEVERITY_LABELS = {
  TROPICAL_STORM: '热带风暴',
  STRONG_STORM: '强风暴',
  TYPHOON: '台风',
  HURRICANE: '飓风'
}

const LOADING_LABELS = {
  BALLAST: '压载',
  HALF_LOAD: '半载',
  FULL_LOAD: '满载'
}

const formData = reactive({
  stormSeverity: 'TROPICAL_STORM',
  waveHeight: 5,
  windSpeed: 30,
  wavePeriod: 10,
  simulationDurationHours: 24,
  monteCarloIterations: 10000,
  loadingCondition: 'HALF_LOAD'
})

const selectedShips = ref([])
const shipList = ref([])
const collapseNames = ref([])
const simulating = ref(false)
const simulationProgress = ref(0)
const simulationResult = ref(null)
const batchResults = ref([])
const compareMode = ref('probability')
const historyDrawerVisible = ref(false)
const historyList = ref([])

const isAnimating = ref(false)
const animationTime = ref(null)
const animationSpeed = ref(1)
let animationInterval = null
let simulationInterval = null

const gaugeRef = ref(null)
const rollChartRef = ref(null)
const gmChartRef = ref(null)
const heatmapRef = ref(null)
const compareChartRef = ref(null)

let gaugeChart = null
let rollChart = null
let gmChart = null
let heatmapChart = null
let compareChart = null

const showDangerFlash = computed(() => {
  return simulationResult.value?.capsizingProbability > 0.3 && isAnimating.value
})

const hasTimeSeries = computed(() => {
  return simulationResult.value?.rollAngleTimeSeries?.length > 0
})

const progressColor = computed(() => {
  if (simulationProgress.value < 50) return '#67C23A'
  if (simulationProgress.value < 80) return '#E6A23C'
  return '#F56C6C'
})

const rankedShips = computed(() => {
  if (!batchResults.value.length) return []
  return [...batchResults.value].sort((a, b) => a.capsizingProbability - b.capsizingProbability)
})

const getRiskColor = (probability) => {
  if (probability == null) return '#a0aec0'
  const p = probability * 100
  if (p < 10) return '#67C23A'
  if (p < 30) return '#E6A23C'
  if (p < 60) return '#F56C6C'
  return '#F5222D'
}

const getRiskLevel = (probability) => {
  if (probability == null) return '--'
  const p = probability * 100
  if (p < 10) return '安全'
  if (p < 30) return '需注意'
  if (p < 60) return '警告'
  return '危险'
}

const formatPercent = (value) => {
  if (value == null) return '--%'
  return `${(value * 100).toFixed(1)}%`
}

const getGmClass = (gm) => {
  if (gm == null) return ''
  if (gm < 0.15) return 'danger'
  if (gm < 0.3) return 'warning'
  return 'success'
}

const getGmStatus = (gm) => {
  if (gm == null) return '--'
  if (gm < 0.15) return '严重不足'
  if (gm < 0.3) return '偏低'
  if (gm < 0.5) return '正常'
  return '良好'
}

const getAngleClass = (angle) => {
  if (angle == null) return ''
  if (Math.abs(angle) > 50) return 'danger'
  if (Math.abs(angle) > 30) return 'warning'
  return 'success'
}

const getAngleStatus = (angle) => {
  if (angle == null) return '--'
  if (Math.abs(angle) > 50) return '已超倾覆阈值'
  if (Math.abs(angle) > 30) return '接近倾覆阈值'
  return '安全范围内'
}

const getRankClass = (index) => {
  if (index === 0) return 'gold'
  if (index === 1) return 'silver'
  if (index === 2) return 'bronze'
  return ''
}

const getShipName = (shipId) => {
  const ship = shipList.value.find(s => s.id === shipId)
  return ship?.name || `船舶 ${shipId}`
}

const getSeverityLabel = (severity) => SEVERITY_LABELS[severity] || severity
const getLoadingLabel = (condition) => LOADING_LABELS[condition] || condition

const getSeverityTagType = (severity) => {
  const types = {
    TROPICAL_STORM: 'info',
    STRONG_STORM: 'warning',
    TYPHOON: 'danger',
    HURRICANE: 'danger'
  }
  return types[severity] || 'info'
}

const formatTime = (time) => {
  if (time == null) return '--'
  const hours = Math.floor(time)
  const minutes = Math.floor((time - hours) * 60)
  return `${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}`
}

const formatDateTime = (dateStr) => {
  if (!dateStr) return '--'
  const date = new Date(dateStr)
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

const onSeverityChange = (value) => {
  const preset = SEVERITY_PRESETS[value]
  if (preset) {
    formData.waveHeight = preset.waveHeight
    formData.windSpeed = preset.windSpeed
    formData.wavePeriod = preset.wavePeriod
  }
}

const generateHeatmapData = () => {
  const data = []
  for (let waveH = 2; waveH <= 15; waveH += 1) {
    for (let windS = 20; windS <= 60; windS += 5) {
      const baseProb = (waveH - 2) / 13 * 0.5 + (windS - 20) / 40 * 0.5
      const prob = Math.min(0.95, Math.max(0, baseProb + (Math.random() - 0.5) * 0.1))
      data.push({
        x: waveH,
        y: windS,
        v: prob
      })
    }
  }
  return data
}

const initGauge = () => {
  if (!gaugeRef.value) return
  if (gaugeChart) gaugeChart.destroy()

  const canvas = gaugeRef.value
  const ctx = canvas.getContext('2d')
  const width = canvas.width = canvas.offsetWidth
  const height = canvas.height = canvas.offsetHeight
  const centerX = width / 2
  const centerY = height / 2
  const radius = Math.min(width, height) / 2 - 20

  ctx.clearRect(0, 0, width, height)

  const riskColors = ['#67C23A', '#E6A23C', '#F56C6C', '#F5222D']
  const segments = [
    { start: -Math.PI * 0.75, end: -Math.PI * 0.375, color: riskColors[0] },
    { start: -Math.PI * 0.375, end: 0, color: riskColors[1] },
    { start: 0, end: Math.PI * 0.375, color: riskColors[2] },
    { start: Math.PI * 0.375, end: Math.PI * 0.75, color: riskColors[3] }
  ]

  segments.forEach(seg => {
    ctx.beginPath()
    ctx.arc(centerX, centerY, radius, seg.start, seg.end)
    ctx.strokeStyle = seg.color
    ctx.lineWidth = 20
    ctx.lineCap = 'round'
    ctx.stroke()
  })

  const prob = simulationResult.value?.capsizingProbability || 0
  const angle = -Math.PI * 0.75 + prob * Math.PI * 1.5

  const pointerLength = radius - 30
  ctx.save()
  ctx.translate(centerX, centerY)
  ctx.rotate(angle)

  ctx.beginPath()
  ctx.moveTo(0, -8)
  ctx.lineTo(pointerLength, 0)
  ctx.lineTo(0, 8)
  ctx.closePath()
  ctx.fillStyle = '#F5222D'
  ctx.fill()

  ctx.beginPath()
  ctx.arc(0, 0, 12, 0, Math.PI * 2)
  ctx.fillStyle = '#1a1a2e'
  ctx.fill()
  ctx.strokeStyle = '#F5222D'
  ctx.lineWidth = 2
  ctx.stroke()

  ctx.restore()

  ctx.font = 'bold 32px Courier New'
  ctx.fillStyle = getRiskColor(prob)
  ctx.textAlign = 'center'
  ctx.textBaseline = 'middle'
  ctx.fillText(formatPercent(prob), centerX, centerY + 30)

  for (let i = 0; i <= 100; i += 10) {
    const tickAngle = -Math.PI * 0.75 + (i / 100) * Math.PI * 1.5
    const isMajor = i % 25 === 0
    const innerR = radius - (isMajor ? 30 : 25)
    const outerR = radius - 10

    ctx.beginPath()
    ctx.moveTo(
      centerX + Math.cos(tickAngle) * innerR,
      centerY + Math.sin(tickAngle) * innerR
    )
    ctx.lineTo(
      centerX + Math.cos(tickAngle) * outerR,
      centerY + Math.sin(tickAngle) * outerR
    )
    ctx.strokeStyle = 'rgba(255,255,255,0.5)'
    ctx.lineWidth = isMajor ? 2 : 1
    ctx.stroke()

    if (isMajor) {
      ctx.font = '11px Arial'
      ctx.fillStyle = '#a0aec0'
      const labelR = radius - 45
      ctx.fillText(
        `${i}%`,
        centerX + Math.cos(tickAngle) * labelR,
        centerY + Math.sin(tickAngle) * labelR
      )
    }
  }

  gaugeChart = { destroy: () => { ctx.clearRect(0, 0, width, height) } }
}

const initRollChart = () => {
  if (!rollChartRef.value) return
  if (rollChart) rollChart.destroy()

  const timeSeries = simulationResult.value?.rollAngleTimeSeries || []
  const labels = timeSeries.map(d => d.time)
  const data = timeSeries.map(d => d.rollAngle)

  const displayCount = Math.min(96, labels.length)
  const step = Math.max(1, Math.floor(labels.length / displayCount))
  const displayLabels = labels.filter((_, i) => i % step === 0)
  const displayData = data.filter((_, i) => i % step === 0)

  const ctx = rollChartRef.value.getContext('2d')
  rollChart = new Chart(ctx, {
    type: 'line',
    data: {
      labels: displayLabels.map(t => formatTime(t)),
      datasets: [
        {
          label: '横摇角 (°)',
          data: displayData,
          borderColor: '#409EFF',
          backgroundColor: 'rgba(64, 158, 255, 0.2)',
          fill: true,
          tension: 0.2,
          pointRadius: 0,
          pointHoverRadius: 5,
          borderWidth: 2
        },
        {
          label: '倾覆阈值 +50°',
          data: Array(displayLabels.length).fill(50),
          borderColor: '#F5222D',
          borderDash: [8, 4],
          pointRadius: 0,
          fill: false,
          borderWidth: 1.5
        },
        {
          label: '倾覆阈值 -50°',
          data: Array(displayLabels.length).fill(-50),
          borderColor: '#F5222D',
          borderDash: [8, 4],
          pointRadius: 0,
          fill: false,
          borderWidth: 1.5
        }
      ]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      interaction: { mode: 'index', intersect: false },
      plugins: {
        legend: { display: false },
        tooltip: {
          backgroundColor: 'rgba(12, 25, 41, 0.95)',
          titleColor: '#fff',
          bodyColor: '#a0aec0',
          borderColor: 'rgba(64, 158, 255, 0.3)',
          borderWidth: 1,
          padding: 10
        }
      },
      scales: {
        x: {
          title: { display: true, text: '时间', color: '#a0aec0' },
          ticks: { color: '#a0aec0', maxTicksLimit: 8 },
          grid: { color: 'rgba(255,255,255,0.05)' }
        },
        y: {
          title: { display: true, text: '横摇角 (°)', color: '#409EFF' },
          ticks: { color: '#409EFF' },
          grid: { color: 'rgba(255,255,255,0.05)' },
          min: -60,
          max: 60
        }
      }
    }
  })
}

const initGmChart = () => {
  if (!gmChartRef.value) return
  if (gmChart) gmChart.destroy()

  const timeSeries = simulationResult.value?.gmTimeSeries || []
  const labels = timeSeries.map(d => d.time)
  const data = timeSeries.map(d => d.gm)

  const displayCount = Math.min(96, labels.length)
  const step = Math.max(1, Math.floor(labels.length / displayCount))
  const displayLabels = labels.filter((_, i) => i % step === 0)
  const displayData = data.filter((_, i) => i % step === 0)

  const ctx = gmChartRef.value.getContext('2d')
  gmChart = new Chart(ctx, {
    type: 'line',
    data: {
      labels: displayLabels.map(t => formatTime(t)),
      datasets: [
        {
          label: 'GM值 (m)',
          data: displayData,
          borderColor: '#67C23A',
          backgroundColor: 'rgba(103, 194, 58, 0.2)',
          fill: true,
          tension: 0.2,
          pointRadius: 0,
          pointHoverRadius: 5,
          borderWidth: 2
        },
        {
          label: '安全阈值 0.3m',
          data: Array(displayLabels.length).fill(0.3),
          borderColor: '#E6A23C',
          borderDash: [8, 4],
          pointRadius: 0,
          fill: false,
          borderWidth: 1.5
        },
        {
          label: '危险阈值 0.15m',
          data: Array(displayLabels.length).fill(0.15),
          borderColor: '#F5222D',
          borderDash: [8, 4],
          pointRadius: 0,
          fill: false,
          borderWidth: 1.5
        }
      ]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      interaction: { mode: 'index', intersect: false },
      plugins: {
        legend: { display: false },
        tooltip: {
          backgroundColor: 'rgba(12, 25, 41, 0.95)',
          titleColor: '#fff',
          bodyColor: '#a0aec0',
          borderColor: 'rgba(64, 158, 255, 0.3)',
          borderWidth: 1,
          padding: 10
        }
      },
      scales: {
        x: {
          title: { display: true, text: '时间', color: '#a0aec0' },
          ticks: { color: '#a0aec0', maxTicksLimit: 8 },
          grid: { color: 'rgba(255,255,255,0.05)' }
        },
        y: {
          title: { display: true, text: 'GM值 (m)', color: '#67C23A' },
          ticks: { color: '#67C23A' },
          grid: { color: 'rgba(255,255,255,0.05)' },
          min: 0,
          max: 1.2
        }
      }
    }
  })
}

const initHeatmap = () => {
  if (!heatmapRef.value) return
  if (heatmapChart) heatmapChart.destroy()

  const canvas = heatmapRef.value
  const ctx = canvas.getContext('2d')
  const width = canvas.width = canvas.offsetWidth
  const height = canvas.height = canvas.offsetHeight

  const padding = { top: 40, right: 30, bottom: 50, left: 50 }
  const chartWidth = width - padding.left - padding.right
  const chartHeight = height - padding.top - padding.bottom

  const heatmapData = generateHeatmapData()
  const waveHeights = [...new Set(heatmapData.map(d => d.x))].sort((a, b) => a - b)
  const windSpeeds = [...new Set(heatmapData.map(d => d.y))].sort((a, b) => a - b)

  const cellWidth = chartWidth / waveHeights.length
  const cellHeight = chartHeight / windSpeeds.length

  const getColor = (value) => {
    if (value < 0.1) return 'rgba(103, 194, 58, 0.8)'
    if (value < 0.3) return 'rgba(230, 162, 60, 0.8)'
    if (value < 0.6) return 'rgba(245, 108, 108, 0.8)'
    return 'rgba(245, 34, 45, 0.9)'
  }

  heatmapData.forEach(d => {
    const xIdx = waveHeights.indexOf(d.x)
    const yIdx = windSpeeds.indexOf(d.y)
    const x = padding.left + xIdx * cellWidth
    const y = padding.top + yIdx * cellHeight

    ctx.fillStyle = getColor(d.v)
    ctx.fillRect(x, y, cellWidth - 1, cellHeight - 1)
  })

  ctx.fillStyle = '#a0aec0'
  ctx.font = '12px Arial'
  ctx.textAlign = 'center'
  waveHeights.forEach((wh, i) => {
    const x = padding.left + i * cellWidth + cellWidth / 2
    ctx.fillText(`${wh}`, x, height - 15)
  })
  ctx.fillText('浪高 (m)', width / 2, height - 5)

  ctx.textAlign = 'right'
  ctx.textBaseline = 'middle'
  windSpeeds.forEach((ws, i) => {
    const y = padding.top + i * cellHeight + cellHeight / 2
    ctx.fillText(`${ws}`, padding.left - 8, y)
  })
  ctx.save()
  ctx.translate(15, height / 2)
  ctx.rotate(-Math.PI / 2)
  ctx.fillText('风速 (m/s)', 0, 0)
  ctx.restore()

  const currentWh = formData.waveHeight
  const currentWs = formData.windSpeed
  const xIdx = waveHeights.findIndex(w => Math.abs(w - currentWh) < 0.6)
  const yIdx = windSpeeds.findIndex(w => Math.abs(w - currentWs) < 3)
  if (xIdx >= 0 && yIdx >= 0) {
    const x = padding.left + xIdx * cellWidth
    const y = padding.top + yIdx * cellHeight
    ctx.strokeStyle = '#fff'
    ctx.lineWidth = 2
    ctx.strokeRect(x, y, cellWidth - 1, cellHeight - 1)
  }

  heatmapChart = { destroy: () => { ctx.clearRect(0, 0, width, height) } }
}

const initCompareChart = () => {
  if (!compareChartRef.value) return
  if (compareChart) compareChart.destroy()

  if (!batchResults.value.length) return

  const labels = batchResults.value.map(r => getShipName(r.shipId))
  let data, label, color, yAxisLabel

  switch (compareMode.value) {
    case 'probability':
      data = batchResults.value.map(r => (r.capsizingProbability * 100).toFixed(1))
      label = '倾覆概率 (%)'
      color = batchResults.value.map(r => getRiskColor(r.capsizingProbability))
      yAxisLabel = '概率 (%)'
      break
    case 'maxRoll':
      data = batchResults.value.map(r => Math.abs(r.maxRollAngleExperienced).toFixed(1))
      label = '最大横摇角 (°)'
      color = '#409EFF'
      yAxisLabel = '角度 (°)'
      break
    case 'minGm':
      data = batchResults.value.map(r => r.minGmExperienced?.toFixed(3) || 0)
      label = '最小GM值 (m)'
      color = '#67C23A'
      yAxisLabel = 'GM (m)'
      break
  }

  const ctx = compareChartRef.value.getContext('2d')
  compareChart = new Chart(ctx, {
    type: 'bar',
    data: {
      labels,
      datasets: [{
        label,
        data,
        backgroundColor: color,
        borderRadius: 4,
        borderSkipped: false
      }]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      indexAxis: 'y',
      plugins: {
        legend: { display: false },
        tooltip: {
          backgroundColor: 'rgba(12, 25, 41, 0.95)',
          titleColor: '#fff',
          bodyColor: '#a0aec0',
          borderColor: 'rgba(64, 158, 255, 0.3)',
          borderWidth: 1,
          padding: 10
        }
      },
      scales: {
        x: {
          title: { display: true, text: yAxisLabel, color: '#a0aec0' },
          ticks: { color: '#a0aec0' },
          grid: { color: 'rgba(255,255,255,0.05)' }
        },
        y: {
          ticks: { color: '#a0aec0' },
          grid: { color: 'rgba(255,255,255,0.05)' }
        }
      }
    }
  })
}

const updateCompareChart = () => {
  initCompareChart()
}

const startSimulation = async () => {
  if (selectedShips.value.length === 0) {
    ElMessage.warning('请选择至少一艘船舶')
    return
  }

  stopAnimation()
  simulating.value = true
  simulationProgress.value = 0

  const progressStep = 100 / (formData.monteCarloIterations / 500)
  simulationInterval = setInterval(() => {
    simulationProgress.value = Math.min(99, simulationProgress.value + progressStep)
  }, 50)

  try {
    const params = { ...formData }

    if (selectedShips.value.length === 1) {
      const res = await simulateStorm(selectedShips.value[0], params)
      simulationResult.value = res.data
      batchResults.value = [{ ...res.data, shipId: selectedShips.value[0] }]
    } else {
      const res = await batchSimulateStorm({
        shipIds: selectedShips.value,
        ...params
      })
      batchResults.value = res.data || []
      simulationResult.value = batchResults.value[0] || null
    }

    simulationProgress.value = 100
    ElMessage.success('模拟完成')

    await nextTick()
    initAllCharts()
    loadHistoryData()
  } catch (e) {
    ElMessage.error('模拟失败: ' + (e.message || '未知错误'))
  } finally {
    clearInterval(simulationInterval)
    simulating.value = false
  }
}

const initAllCharts = () => {
  initGauge()
  initRollChart()
  initGmChart()
  initHeatmap()
  initCompareChart()
}

const toggleAnimation = () => {
  if (isAnimating.value) {
    stopAnimation()
  } else {
    startAnimation()
  }
}

const startAnimation = () => {
  if (!simulationResult.value?.rollAngleTimeSeries?.length) return

  isAnimating.value = true
  animationTime.value = 0

  const timeSeries = simulationResult.value.rollAngleTimeSeries
  const totalTime = timeSeries[timeSeries.length - 1]?.time || 24

  animationInterval = setInterval(() => {
    if (animationTime.value >= totalTime) {
      animationTime.value = 0
    } else {
      animationTime.value += 0.1 * animationSpeed.value
    }
  }, 100)
}

const stopAnimation = () => {
  isAnimating.value = false
  if (animationInterval) {
    clearInterval(animationInterval)
    animationInterval = null
  }
}

const resetForm = async () => {
  if (simulating.value) {
    ElMessage.warning('请等待模拟完成')
    return
  }

  try {
    await ElMessageBox.confirm('确定要重置所有参数吗？', '确认重置', {
      type: 'warning'
    })
  } catch {
    return
  }

  stopAnimation()
  Object.assign(formData, {
    stormSeverity: 'TROPICAL_STORM',
    waveHeight: 5,
    windSpeed: 30,
    wavePeriod: 10,
    simulationDurationHours: 24,
    monteCarloIterations: 10000,
    loadingCondition: 'HALF_LOAD'
  })
  selectedShips.value = []
  simulationResult.value = null
  batchResults.value = []
  simulationProgress.value = 0
  animationTime.value = null

  destroyAllCharts()
  ElMessage.success('已重置')
}

const destroyAllCharts = () => {
  if (gaugeChart) { gaugeChart.destroy(); gaugeChart = null }
  if (rollChart) { rollChart.destroy(); rollChart = null }
  if (gmChart) { gmChart.destroy(); gmChart = null }
  if (heatmapChart) { heatmapChart.destroy(); heatmapChart = null }
  if (compareChart) { compareChart.destroy(); compareChart = null }
}

const loadShips = async () => {
  try {
    const res = await getShips()
    shipList.value = res.data?.content || res.data || []
  } catch (e) {
    console.error('加载船舶列表失败', e)
  }
}

const loadHistoryData = async () => {
  if (!selectedShips.value.length) return
  try {
    const res = await getSimulationHistory(selectedShips.value[0], 20)
    historyList.value = res.data?.content || res.data || []
  } catch (e) {
    console.error('加载历史记录失败', e)
  }
}

const loadHistoryRecord = async (record) => {
  try {
    const res = await getSimulationById(record.id)
    simulationResult.value = res.data
    batchResults.value = [{ ...res.data, shipId: record.shipId }]
    selectedShips.value = [record.shipId]
    historyDrawerVisible.value = false

    await nextTick()
    initAllCharts()
    ElMessage.success('已加载历史记录')
  } catch (e) {
    ElMessage.error('加载失败: ' + (e.message || '未知错误'))
  }
}

const exportData = (type) => {
  if (!simulationResult.value) return

  if (type === 'json') {
    const dataStr = JSON.stringify(simulationResult.value, null, 2)
    const blob = new Blob([dataStr], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `storm-simulation-${Date.now()}.json`
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('JSON导出成功')
  } else if (type === 'image') {
    ElMessage.info('图片导出功能开发中...')
  }
}

const handleResize = () => {
  if (simulationResult.value) {
    setTimeout(() => initAllCharts(), 100)
  }
}

watch(compareMode, () => {
  updateCompareChart()
})

watch(() => formData.waveHeight, () => {
  if (heatmapRef.value && simulationResult.value) {
    initHeatmap()
  }
})

watch(() => formData.windSpeed, () => {
  if (heatmapRef.value && simulationResult.value) {
    initHeatmap()
  }
})

onMounted(() => {
  loadShips()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  stopAnimation()
  clearInterval(simulationInterval)
  destroyAllCharts()
  window.removeEventListener('resize', handleResize)
})
</script>

<style scoped lang="scss">
.storm-simulation {
  width: 100%;
  padding: 0 4px;
  transition: background-color 0.3s;

  &.danger-flash {
    animation: flashWarning 1s infinite;
  }
}

@keyframes flashWarning {
  0%, 100% { background-color: transparent; }
  50% { background-color: rgba(245, 108, 108, 0.1); }
}

.simulation-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 20px;

  .header-left {
    flex: 1;
  }

  .page-title {
    display: flex;
    align-items: center;
    gap: 10px;
    color: #ffffff;
    font-size: 22px;
    margin: 0 0 6px 0;

    .el-icon {
      color: #F56C6C;
    }
  }

  .page-desc {
    color: #a0aec0;
    font-size: 13px;
    margin: 0;
  }

  .header-actions {
    display: flex;
    gap: 12px;
  }
}

.simulation-content {
  display: grid;
  grid-template-columns: 340px 1fr;
  gap: 16px;
}

.left-panel {
  .config-card {
    background: rgba(12, 25, 41, 0.6);
    border: 1px solid rgba(64, 158, 255, 0.2);
    border-radius: 12px;
    backdrop-filter: blur(10px);

    :deep(.el-card__header) {
      border-bottom: 1px solid rgba(64, 158, 255, 0.15);
      padding: 16px 20px;
    }

    :deep(.el-card__body) {
      padding: 16px 20px;
    }

    .card-header {
      display: flex;
      align-items: center;
      gap: 8px;
      color: #ffffff;
      font-weight: 500;
      font-size: 16px;

      .el-icon {
        color: #409EFF;
      }
    }
  }

  .config-form {
    :deep(.el-form-item__label) {
      color: #a0aec0;
      font-size: 13px;
      font-weight: 500;
    }
  }

  .custom-collapse {
    :deep(.el-collapse-item__header) {
      color: #409EFF;
      font-size: 13px;
      font-weight: 500;
    }

    :deep(.el-collapse-item__wrap) {
      border-bottom: 1px solid rgba(64, 158, 255, 0.1);
    }
  }

  .action-buttons {
    width: 100%;
  }

  .progress-section {
    margin-top: 16px;
    padding-top: 16px;
    border-top: 1px solid rgba(64, 158, 255, 0.15);

    .progress-label {
      display: flex;
      justify-content: space-between;
      margin-bottom: 8px;
      color: #a0aec0;
      font-size: 12px;
    }
  }

  .animation-time-panel {
    margin-top: 16px;

    .divider {
      margin: 0 0 12px 0;
      --el-border-color: rgba(64, 158, 255, 0.15);
    }

    .time-display {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 12px;

      .time-label {
        color: #a0aec0;
        font-size: 13px;
      }

      .time-value {
        color: #409EFF;
        font-size: 24px;
        font-weight: bold;
        font-family: 'Courier New', monospace;
      }
    }

    .time-controls {
      display: flex;
      gap: 8px;

      .el-button {
        flex: 1;
      }
    }
  }
}

.right-panel {
  .top-metrics-row {
    display: grid;
    grid-template-columns: 280px 1fr;
    gap: 16px;
    margin-bottom: 16px;
  }

  .gauge-card,
  .chart-card,
  .ranking-card {
    background: rgba(12, 25, 41, 0.6);
    border: 1px solid rgba(64, 158, 255, 0.2);
    border-radius: 12px;
    overflow: hidden;
    backdrop-filter: blur(10px);
  }

  .gauge-card {
    .card-header {
      display: flex;
      align-items: center;
      padding: 16px 20px;
      border-bottom: 1px solid rgba(64, 158, 255, 0.15);

      .card-title {
        display: flex;
        align-items: center;
        gap: 8px;
        margin: 0;
        font-size: 16px;
        color: #ffffff;

        .el-icon {
          color: #F56C6C;
        }
      }
    }

    .gauge-container {
      height: 220px;
      padding: 10px 20px 0;

      canvas {
        width: 100%;
        height: 100%;
      }
    }

    .gauge-legend {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      padding: 0 20px 16px;

      .legend-item {
        display: flex;
        align-items: center;
        gap: 4px;
        font-size: 11px;
        color: #a0aec0;

        .legend-dot {
          width: 8px;
          height: 8px;
          border-radius: 50%;

          &.green { background: #67C23A; }
          &.yellow { background: #E6A23C; }
          &.orange { background: #F56C6C; }
          &.red { background: #F5222D; }
        }
      }
    }
  }

  .metrics-grid {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 12px;

    .metric-card {
      background: rgba(0, 0, 0, 0.2);
      border: 1px solid rgba(64, 158, 255, 0.15);
      border-radius: 10px;
      padding: 14px;
      display: flex;
      gap: 12px;
      transition: all 0.3s;

      &:hover {
        border-color: rgba(64, 158, 255, 0.3);
        transform: translateY(-2px);
      }

      &.highlight {
        border-left: 4px solid #F56C6C;
      }

      &.risk-highlight {
        animation: riskPulse 2s infinite;
        border-color: rgba(245, 108, 108, 0.5);
      }

      .metric-icon {
        width: 44px;
        height: 44px;
        border-radius: 10px;
        display: flex;
        align-items: center;
        justify-content: center;
        font-size: 20px;
        flex-shrink: 0;

        &.danger {
          background: linear-gradient(135deg, rgba(245, 108, 108, 0.3), rgba(245, 108, 108, 0.1));
          color: #F56C6C;
        }

        &.warning {
          background: linear-gradient(135deg, rgba(230, 162, 60, 0.3), rgba(230, 162, 60, 0.1));
          color: #E6A23C;
        }

        &.success {
          background: linear-gradient(135deg, rgba(103, 194, 58, 0.3), rgba(103, 194, 58, 0.1));
          color: #67C23A;
        }

        &.primary {
          background: linear-gradient(135deg, rgba(64, 158, 255, 0.3), rgba(64, 158, 255, 0.1));
          color: #409EFF;
        }

        &.info {
          background: linear-gradient(135deg, rgba(144, 147, 153, 0.3), rgba(144, 147, 153, 0.1));
          color: #909399;
        }
      }

      .metric-info {
        flex: 1;
        min-width: 0;

        .metric-label {
          display: block;
          color: #a0aec0;
          font-size: 12px;
          margin-bottom: 4px;
        }

        .metric-value {
          display: block;
          color: #ffffff;
          font-size: 20px;
          font-weight: bold;
          font-family: 'Courier New', monospace;
          line-height: 1.2;

          small {
            font-size: 11px;
            color: #a0aec0;
            font-weight: normal;
          }

          &.danger { color: #F56C6C; }
          &.warning { color: #E6A23C; }
          &.success { color: #67C23A; }
        }

        .metric-status,
        .metric-desc {
          display: block;
          color: #a0aec0;
          font-size: 11px;
          margin-top: 4px;

          &.danger { color: #F56C6C; }
          &.warning { color: #E6A23C; }
          &.success { color: #67C23A; }
        }

        .metric-status {
          font-weight: 500;
        }

        .parametric-tag {
          margin-top: 4px;
        }
      }
    }
  }

  @keyframes riskPulse {
    0%, 100% { box-shadow: 0 0 0 0 rgba(245, 108, 108, 0); }
    50% { box-shadow: 0 0 15px 2px rgba(245, 108, 108, 0.3); }
  }

  .charts-row {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 16px;
    margin-bottom: 16px;
  }

  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 16px 20px;
    border-bottom: 1px solid rgba(64, 158, 255, 0.15);

    .card-title {
      display: flex;
      align-items: center;
      gap: 8px;
      margin: 0;
      font-size: 16px;
      color: #ffffff;

      .el-icon {
        color: #409EFF;
      }
    }

    .legend-info {
      display: flex;
      gap: 16px;

      .legend-item {
        display: flex;
        align-items: center;
        gap: 6px;
        font-size: 12px;
        color: #a0aec0;

        .legend-color {
          width: 12px;
          height: 3px;
          border-radius: 2px;

          &.roll { background: #409EFF; }
          &.threshold { background: #F5222D; }
          &.gm { background: #67C23A; }
          &.gm-warn { background: #E6A23C; }
          &.gm-danger { background: #F5222D; }
        }

        .legend-dot {
          width: 8px;
          height: 8px;
          border-radius: 50%;

          &.green { background: #67C23A; }
          &.yellow { background: #E6A23C; }
          &.orange { background: #F56C6C; }
          &.red { background: #F5222D; }
        }
      }
    }
  }

  .chart-container {
    height: 280px;
    padding: 16px 20px;

    canvas {
      width: 100% !important;
      height: 100% !important;
    }
  }

  .heatmap-container {
    height: 300px;
    padding: 16px 20px;

    canvas {
      width: 100% !important;
      height: 100% !important;
    }
  }

  .ranking-card {
    margin-bottom: 16px;

    .ranking-container {
      padding: 16px 20px;
      display: flex;
      flex-direction: column;
      gap: 10px;
    }

    .ranking-item {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 10px 14px;
      background: rgba(0, 0, 0, 0.2);
      border-radius: 8px;
      transition: all 0.3s;

      &:hover {
        background: rgba(64, 158, 255, 0.1);
        transform: translateX(4px);
      }

      .rank-badge {
        width: 28px;
        height: 28px;
        border-radius: 50%;
        display: flex;
        align-items: center;
        justify-content: center;
        font-weight: bold;
        font-size: 13px;
        color: #fff;
        background: rgba(64, 158, 255, 0.3);
        flex-shrink: 0;

        &.gold {
          background: linear-gradient(135deg, #FFD700, #FFA500);
          box-shadow: 0 0 10px rgba(255, 215, 0, 0.5);
        }

        &.silver {
          background: linear-gradient(135deg, #C0C0C0, #A8A8A8);
          box-shadow: 0 0 10px rgba(192, 192, 192, 0.5);
        }

        &.bronze {
          background: linear-gradient(135deg, #CD7F32, #8B4513);
          box-shadow: 0 0 10px rgba(205, 127, 50, 0.5);
        }
      }

      .ship-name {
        color: #ffffff;
        font-size: 14px;
        font-weight: 500;
        width: 120px;
        flex-shrink: 0;
      }

      .rank-bar-container {
        flex: 1;
        height: 8px;
        background: rgba(0, 0, 0, 0.3);
        border-radius: 4px;
        overflow: hidden;

        .rank-bar {
          height: 100%;
          border-radius: 4px;
          transition: width 0.5s ease;
        }
      }

      .rank-value {
        font-size: 16px;
        font-weight: bold;
        font-family: 'Courier New', monospace;
        width: 70px;
        text-align: right;
      }
    }
  }
}

.history-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: 80vh;
  overflow-y: auto;

  .history-item {
    padding: 14px;
    background: rgba(12, 25, 41, 0.6);
    border: 1px solid rgba(64, 158, 255, 0.2);
    border-radius: 8px;
    cursor: pointer;
    transition: all 0.3s;

    &:hover {
      border-color: rgba(64, 158, 255, 0.4);
      transform: translateX(-4px);
    }

    .history-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 10px;

      .history-time {
        color: #a0aec0;
        font-size: 12px;
      }
    }

    .history-content {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 8px;

      .history-ship {
        color: #ffffff;
        font-size: 14px;
        font-weight: 500;
      }

      .history-prob {
        font-size: 18px;
        font-weight: bold;
        font-family: 'Courier New', monospace;
      }
    }

    .history-params {
      display: flex;
      gap: 12px;

      span {
        color: #a0aec0;
        font-size: 12px;
      }
    }
  }
}

@media (max-width: 1400px) {
  .simulation-content {
    grid-template-columns: 300px 1fr;
  }

  .right-panel {
    .top-metrics-row {
      grid-template-columns: 250px 1fr;
    }

    .metrics-grid {
      grid-template-columns: repeat(2, 1fr);
    }
  }
}

@media (max-width: 1200px) {
  .simulation-content {
    grid-template-columns: 1fr;
  }

  .right-panel {
    .top-metrics-row {
      grid-template-columns: 1fr;
    }

    .metrics-grid {
      grid-template-columns: repeat(3, 1fr);
    }

    .charts-row {
      grid-template-columns: 1fr;
    }
  }
}

@media (max-width: 768px) {
  .right-panel {
    .metrics-grid {
      grid-template-columns: repeat(2, 1fr);
    }
  }
}
</style>