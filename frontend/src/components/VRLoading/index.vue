<template>
  <div class="vr-loading">
    <div class="loading-content">
      <div class="left-panel">
        <div class="section-card">
          <div class="card-header">
            <h3 class="card-title">
              <el-icon><Goods /></el-icon>
              货物库
            </h3>
            <el-tag type="info" size="small">拖拽装载</el-tag>
          </div>
          <div class="cargo-library">
            <div
              v-for="cargo in cargoLibrary"
              :key="cargo.id"
              class="cargo-item"
              draggable="true"
              :style="{ borderColor: cargo.color }"
              @dragstart="onDragStart($event, cargo)"
              @dragend="onDragEnd"
            >
              <div class="cargo-color-bar" :style="{ backgroundColor: cargo.color }"></div>
              <div class="cargo-info">
                <div class="cargo-name">{{ cargo.name }}</div>
                <div class="cargo-meta">
                  <span>{{ cargo.weight }}吨/件</span>
                  <span class="cargo-density">密度: {{ cargo.density }}</span>
                </div>
              </div>
              <div class="cargo-icon">
                <el-icon><Menu /></el-icon>
              </div>
            </div>
          </div>
        </div>

        <div class="section-card">
          <div class="card-header">
            <h3 class="card-title">
              <el-icon><Gauge /></el-icon>
              GM实时仪表
            </h3>
            <el-tag :type="getGmTagType(currentSession?.gm)" size="small">
              {{ getGmStatus(currentSession?.gm) }}
            </el-tag>
          </div>
          <div class="gm-gauge-container">
            <svg viewBox="0 0 200 140" class="gm-gauge">
              <defs>
                <linearGradient id="gmGradient" x1="0%" y1="0%" x2="100%" y2="0%">
                  <stop offset="0%" style="stop-color:#F5222D" />
                  <stop offset="30%" style="stop-color:#E6A23C" />
                  <stop offset="60%" style="stop-color:#67C23A" />
                  <stop offset="100%" style="stop-color:#409EFF" />
                </linearGradient>
              </defs>
              <path
                d="M 20 120 A 80 80 0 0 1 180 120"
                fill="none"
                stroke="rgba(255,255,255,0.1)"
                stroke-width="18"
                stroke-linecap="round"
              />
              <path
                d="M 20 120 A 80 80 0 0 1 180 120"
                fill="none"
                stroke="url(#gmGradient)"
                stroke-width="18"
                stroke-linecap="round"
                :stroke-dasharray="gmGaugeDasharray"
                :stroke-dashoffset="gmGaugeDashoffset"
                class="gauge-fill"
              />
              <g :transform="gaugePointerTransform">
                <polygon points="0,-6 60,0 0,6" fill="#ffffff" />
              </g>
              <circle cx="100" cy="120" r="12" fill="#1a1a2e" stroke="#fff" stroke-width="2" />
              <text x="100" y="80" text-anchor="middle" class="gm-value" :fill="getGmColor(currentSession?.gm)">
                {{ currentSession?.gm?.toFixed(3) || '--' }}
              </text>
              <text x="100" y="98" text-anchor="middle" class="gm-unit">米</text>
              <text x="20" y="138" text-anchor="middle" class="gauge-label">-0.5</text>
              <text x="100" y="145" text-anchor="middle" class="gauge-label">0</text>
              <text x="100" y="38" text-anchor="middle" class="gauge-label">0.75</text>
              <text x="180" y="138" text-anchor="middle" class="gauge-label">1.5</text>
            </svg>
          </div>
          <div class="gm-legend">
            <span class="legend-item"><span class="legend-bar danger"></span>危险 < 0.15m</span>
            <span class="legend-item"><span class="legend-bar warning"></span>注意 0.15-0.3m</span>
            <span class="legend-item"><span class="legend-bar safe"></span>安全 > 0.3m</span>
          </div>
        </div>

        <div class="section-card">
          <div class="card-header">
            <h3 class="card-title">
              <el-icon><TrendCharts /></el-icon>
              GM趋势
            </h3>
          </div>
          <div class="trend-chart-container">
            <canvas ref="trendChartRef"></canvas>
          </div>
        </div>

        <div class="section-card action-card">
          <div class="action-buttons">
            <el-button
              type="primary"
              size="large"
              class="create-session-btn"
              @click="handleCreateSession"
              :disabled="!ship || sessionId"
            >
              <el-icon><Refresh /></el-icon>
              {{ sessionId ? '会话已存在' : '创建装载会话' }}
            </el-button>
            <el-space wrap style="width: 100%">
              <el-button
                type="success"
                @click="executeSmartLoad"
                :disabled="!sessionId"
              >
                <el-icon><MagicStick /></el-icon>
                智能装载推荐
              </el-button>
              <el-button
                @click="executeUndo"
                :disabled="!sessionId || !currentSession?.canUndo"
              >
                <el-icon><RefreshLeft /></el-icon>
                撤销操作
              </el-button>
              <el-button
                type="warning"
                @click="executeReset"
                :disabled="!sessionId"
              >
                <el-icon><RefreshRight /></el-icon>
                重置装载
              </el-button>
            </el-space>
          </div>
        </div>
      </div>

      <div class="center-panel">
        <div class="section-card ship-profile-card">
          <div class="card-header">
            <h3 class="card-title">
              <el-icon><Van /></el-icon>
              船舶纵剖面图
            </h3>
            <div class="ship-info-tags">
              <el-tag size="small" type="info" v-if="ship?.name">{{ ship.name }}</el-tag>
              <el-tag size="small" :type="getGmTagType(currentSession?.gm)">
                GM: {{ currentSession?.gm?.toFixed(3) || '--' }}m
              </el-tag>
              <el-tag size="small" type="warning" v-if="currentSession">
                装载率: {{ currentSession?.loadingPercentage?.toFixed(1) || 0 }}%
              </el-tag>
            </div>
          </div>
          <div
            class="ship-profile-container"
            @dragover.prevent="onDragOver"
            @drop="onDrop"
            :class="{ 'drag-over': isDragging }"
          >
            <svg viewBox="0 0 800 400" class="ship-profile">
              <defs>
                <linearGradient id="hullGradient" x1="0%" y1="0%" x2="0%" y2="100%">
                  <stop offset="0%" style="stop-color:#2c3e50" />
                  <stop offset="100%" style="stop-color:#1a252f" />
                </linearGradient>
                <linearGradient id="waterGradient" x1="0%" y1="0%" x2="0%" y2="100%">
                  <stop offset="0%" style="stop-color:rgba(64,158,255,0.4)" />
                  <stop offset="100%" style="stop-color:rgba(64,158,255,0.1)" />
                </linearGradient>
                <pattern id="wavePattern" x="0" y="0" width="40" height="10" patternUnits="userSpaceOnUse">
                  <path d="M0 5 Q10 0 20 5 T40 5" fill="none" stroke="rgba(64,158,255,0.6)" stroke-width="1.5" />
                </pattern>
              </defs>

              <line x1="0" :y1="waterlineY" x2="800" :y2="waterlineY" stroke="rgba(64,158,255,0.6)" stroke-width="2" stroke-dasharray="8,4" />
              <rect x="0" :y="waterlineY" width="800" height="400" fill="url(#waterGradient)" />
              <rect x="0" :y="waterlineY" width="800" height="10" fill="url(#wavePattern)" />

              <polygon
                :points="hullPoints"
                fill="url(#hullGradient)"
                stroke="rgba(64,158,255,0.5)"
                stroke-width="2"
                class="ship-hull"
              />
              <polygon
                :points="deckPoints"
                fill="rgba(52,73,94,0.8)"
                stroke="rgba(255,255,255,0.2)"
                stroke-width="1"
              />

              <g v-for="(hold, idx) in cargoHolds" :key="hold.id" class="cargo-hold">
                <rect
                  :x="hold.x"
                  :y="hold.y"
                  :width="hold.width"
                  :height="hold.height"
                  fill="rgba(0,0,0,0.3)"
                  stroke="rgba(255,255,255,0.2)"
                  stroke-width="1"
                  stroke-dasharray="4,4"
                  rx="4"
                  class="hold-outline"
                />
                <g v-for="(layer, layerIdx) in hold.layers" :key="layerIdx">
                  <rect
                    :x="hold.x + 4"
                    :y="hold.y + hold.height - layer.usedHeight - 4"
                    :width="hold.width - 8"
                    :height="layer.usedHeight"
                    :fill="layer.color"
                    opacity="0.85"
                    rx="2"
                    class="cargo-layer"
                  />
                  <text
                    v-if="layer.weight > 0"
                    :x="hold.x + hold.width / 2"
                    :y="hold.y + hold.height - layer.usedHeight / 2 - 4"
                    text-anchor="middle"
                    class="hold-weight-label"
                  >
                    {{ layer.weight.toFixed(0) }}t
                  </text>
                </g>
                <text
                  :x="hold.x + hold.width / 2"
                  :y="hold.y - 8"
                  text-anchor="middle"
                  class="hold-label"
                >
                  {{ hold.name }}
                </text>
                <text
                  :x="hold.x + hold.width / 2"
                  :y="hold.y + hold.height + 18"
                  text-anchor="middle"
                  class="hold-capacity"
                  :class="{ 'near-full': hold.utilization > 85 }"
                >
                  {{ hold.utilization.toFixed(0) }}%
                </text>
              </g>

              <g class="stability-points">
                <line x1="0" :y1="keelY" x2="800" :y2="keelY" stroke="rgba(255,255,255,0.1)" stroke-width="1" />

                <g v-if="currentSession?.pointG">
                  <circle :cx="380" :cy="currentSession.pointG.y" r="10" fill="#F56C6C" class="point-g" />
                  <circle :cx="380" :cy="currentSession.pointG.y" r="6" fill="#fff" />
                  <line :x1="380" :y1="currentSession.pointG.y" x2="380" :y2="keelY" stroke="#F56C6C" stroke-width="2" stroke-dasharray="4,4" />
                  <text :x="400" :y="currentSession.pointG.y + 5" class="point-label g-label">G (重心)</text>
                  <text :x="370" :y="(currentSession.pointG.y + keelY) / 2" class="dimension-label" text-anchor="end">
                    KG={{ currentSession.pointG.kg?.toFixed(2) }}m
                  </text>
                </g>

                <g v-if="currentSession?.pointB">
                  <circle :cx="420" :cy="currentSession.pointB.y" r="10" fill="#409EFF" class="point-b" />
                  <circle :cx="420" :cy="currentSession.pointB.y" r="6" fill="#fff" />
                  <line :x1="420" :y1="currentSession.pointB.y" x2="420" :y2="keelY" stroke="#409EFF" stroke-width="2" stroke-dasharray="4,4" />
                  <text :x="435" :y="currentSession.pointB.y + 5" class="point-label b-label">B (浮心)</text>
                  <text :x="430" :y="(currentSession.pointB.y + keelY) / 2" class="dimension-label">
                    KB={{ currentSession.pointB.kb?.toFixed(2) }}m
                  </text>
                </g>

                <g v-if="currentSession?.pointM">
                  <circle :cx="460" :cy="currentSession.pointM.y" r="12" fill="#67C23A" class="point-m" />
                  <circle :cx="460" :cy="currentSession.pointM.y" r="7" fill="#fff" />
                  <polygon :points="metacenterPoints" fill="none" stroke="#67C23A" stroke-width="1" opacity="0.4" />
                  <text :x="475" :y="currentSession.pointM.y + 5" class="point-label m-label">M (稳心)</text>
                  <line
                    v-if="currentSession?.pointG"
                    :x1="460"
                    :y1="currentSession.pointG.y"
                    x2="460"
                    :y2="currentSession.pointM.y"
                    stroke="#67C23A"
                    stroke-width="3"
                    class="gm-line"
                  />
                  <text
                    v-if="currentSession?.pointG"
                    :x="470"
                    :y="(currentSession.pointG.y + currentSession.pointM.y) / 2"
                    class="gm-label"
                  >
                    GM={{ currentSession.gm?.toFixed(3) }}m
                  </text>
                </g>

                <line
                  v-if="currentSession?.pointB && currentSession?.pointM"
                  :x1="420"
                  :y1="currentSession.pointB.y"
                  x2="460"
                  :y2="currentSession.pointM.y"
                  stroke="rgba(103,194,58,0.4)"
                  stroke-width="1"
                  stroke-dasharray="2,4"
                />
              </g>

              <g class="ship-dimensions">
                <line x1="50" :y1="360" x2="750" :y2="360" stroke="rgba(255,255,255,0.3)" stroke-width="1" />
                <line x1="50" :y1="355" x2="50" :y2="365" stroke="rgba(255,255,255,0.3)" stroke-width="1" />
                <line x1="750" :y1="355" x2="750" :y2="365" stroke="rgba(255,255,255,0.3)" stroke-width="1" />
                <text x="400" :y="378" text-anchor="middle" class="dim-label">
                  {{ ship?.lengthOverall || '--' }} m (总长)
                </text>
              </g>
            </svg>

            <div v-if="isDragging" class="drag-hint">
              <el-icon><Pointer /></el-icon>
              <span>释放鼠标装载到对应货舱</span>
            </div>
          </div>
        </div>

        <div class="section-card" v-if="currentSession?.smartRecommendation">
          <div class="card-header">
            <h3 class="card-title">
              <el-icon><MagicStick /></el-icon>
              智能装载推荐
            </h3>
            <el-tag type="success" size="small">AI 优化建议</el-tag>
          </div>
          <div class="recommendation-content">
            <div class="recommendation-summary" :class="currentSession.smartRecommendation.riskLevel">
              <el-icon :size="20"><Warning /></el-icon>
              <span>{{ currentSession.smartRecommendation.summary }}</span>
            </div>
            <div class="recommendation-actions">
              <div
                v-for="(action, idx) in currentSession.smartRecommendation.recommendedActions"
                :key="idx"
                class="recommendation-item"
              >
                <span class="action-index">{{ idx + 1 }}</span>
                <span class="action-text">{{ action }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="right-panel">
        <div class="section-card">
          <div class="card-header">
            <h3 class="card-title">
              <el-icon><List /></el-icon>
              装载详情
            </h3>
            <el-tag type="info" size="small" v-if="currentSession">
              总重: {{ totalLoadedWeight?.toFixed(0) }}t
            </el-tag>
          </div>
          <el-table :data="loadingDetailsData" class="loading-table" size="small" stripe>
            <el-table-column prop="holdName" label="货舱" width="80" />
            <el-table-column prop="cargoType" label="货物" width="100">
              <template #default="{ row }">
                <span :style="{ color: row.color }">{{ row.cargoType }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="weight" label="重量(t)" width="80" align="right">
              <template #default="{ row }">
                {{ row.weight?.toFixed(1) }}
              </template>
            </el-table-column>
            <el-table-column prop="cgHeight" label="重心高(m)" width="90" align="right">
              <template #default="{ row }">
                {{ row.cgHeight?.toFixed(2) || '--' }}
              </template>
            </el-table-column>
            <el-table-column prop="utilization" label="装载率" width="90">
              <template #default="{ row }">
                <el-progress
                  :percentage="row.utilization || 0"
                  :stroke-width="8"
                  :color="getUtilizationColor(row.utilization)"
                  :show-text="false"
                />
                <span class="util-text">{{ row.utilization?.toFixed(0) }}%</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="70" align="center">
              <template #default="{ row }">
                <el-button
                  type="danger"
                  size="small"
                  link
                  @click="handleRemoveCargo(row)"
                  :disabled="!sessionId"
                >
                  <el-icon><Delete /></el-icon>
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <div class="section-card">
          <div class="card-header">
            <h3 class="card-title">
              <el-icon><DataAnalysis /></el-icon>
              稳性指标
            </h3>
          </div>
          <div class="stability-metrics">
            <div class="stability-item">
              <span class="metric-label">排水量 Δ</span>
              <span class="metric-value">{{ currentSession?.displacement?.toFixed(1) || '--' }} t</span>
            </div>
            <div class="stability-item">
              <span class="metric-label">吃水 d</span>
              <span class="metric-value">{{ currentSession?.draft?.toFixed(2) || '--' }} m</span>
            </div>
            <div class="stability-item">
              <span class="metric-label">浮心 KB</span>
              <span class="metric-value">{{ currentSession?.kb?.toFixed(3) || '--' }} m</span>
            </div>
            <div class="stability-item">
              <span class="metric-label">稳心半径 BM</span>
              <span class="metric-value">{{ currentSession?.bm?.toFixed(3) || '--' }} m</span>
            </div>
            <div class="stability-item">
              <span class="metric-label">稳心高度 KM</span>
              <span class="metric-value">{{ currentSession?.km?.toFixed(3) || '--' }} m</span>
            </div>
            <div class="stability-item highlight">
              <span class="metric-label">初稳性高 GM</span>
              <span class="metric-value" :style="{ color: getGmColor(currentSession?.gm) }">
                {{ currentSession?.gm?.toFixed(3) || '--' }} m
              </span>
            </div>
            <div class="stability-item">
              <span class="metric-label">自由液面修正</span>
              <span class="metric-value">-{{ currentSession?.freeSurfaceCorrection?.toFixed(4) || '0.0000' }} m</span>
            </div>
            <div class="stability-item">
              <span class="metric-label">横稳心距基线</span>
              <span class="metric-value">{{ currentSession?.kmTransverse?.toFixed(3) || '--' }} m</span>
            </div>
          </div>
        </div>

        <div class="section-card">
          <div class="card-header">
            <h3 class="card-title">
              <el-icon><Reading /></el-icon>
              稳性知识
            </h3>
          </div>
          <div class="knowledge-section">
            <el-collapse class="knowledge-collapse">
              <el-collapse-item title="GM值的重要性" name="gm">
                <div class="knowledge-content">
                  <p><strong>GM (初稳性高)</strong> 是衡量船舶初稳性的关键指标：</p>
                  <ul>
                    <li><el-tag type="danger" size="small">GM < 0.15m</el-tag>：稳性不足，有倾覆风险</li>
                    <li><el-tag type="warning" size="small">0.15 ~ 0.3m</el-tag>：稳性偏低，需注意</li>
                    <li><el-tag type="success" size="small">0.3 ~ 0.5m</el-tag>：稳性良好，适于航行</li>
                    <li><el-tag type="primary" size="small">GM > 0.5m</el-tag>：稳性过大，横摇剧烈</li>
                  </ul>
                  <p class="formula">GM = KM - KG = KB + BM - KG</p>
                </div>
              </el-collapse-item>
              <el-collapse-item title="G/B/M三点关系" name="gbm">
                <div class="knowledge-content">
                  <p><strong>G (重心)</strong>：船舶重力作用点，装载越重越高，KG越大，GM越小</p>
                  <p><strong>B (浮心)</strong>：浮力作用点，排水体积形心，随吃水变化</p>
                  <p><strong>M (稳心)</strong>：船微小倾斜时浮力作用线与中线的交点</p>
                  <div class="relation-diagram">
                    <div class="diagram-item"><span class="dot green"></span>M 在最上方</div>
                    <div class="diagram-arrow">↑ BM (稳心半径)</div>
                    <div class="diagram-item"><span class="dot blue"></span>B 中间</div>
                    <div class="diagram-arrow">↑ KB (浮心距基线)</div>
                    <div class="diagram-item"><span class="dot gray"></span>基线 (龙骨)</div>
                    <div class="diagram-note">GM = KM - KG，M高于G为正稳性</div>
                  </div>
                </div>
              </el-collapse-item>
              <el-collapse-item title="古代帆船稳性智慧" name="ancient">
                <div class="knowledge-content">
                  <p><strong>沙船 (平底)</strong>：吃水浅、重心低，适合内河和近海，GM值偏大</p>
                  <p><strong>福船 (尖底)</strong>：吃水深，适航性好，破浪能力强</p>
                  <p><strong>广船 (高舷)</strong>：重心较高，抗横摇设计独特</p>
                  <p><strong>压载技术</strong>：古代用沙石压底降低重心，现代用压载水舱</p>
                </div>
              </el-collapse-item>
            </el-collapse>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import {
  Goods, Gauge, TrendCharts, Refresh, MagicStick, RefreshLeft, RefreshRight,
  Van, Pointer, Warning, List, Delete, Menu, Reading
} from '@element-plus/icons-vue'
import { Chart, registerables } from 'chart.js'
import { ElMessage } from 'element-plus'

Chart.register(...registerables)

const props = defineProps({
  ship: {
    type: Object,
    default: null
  },
  sessionId: {
    type: String,
    default: ''
  }
})

const emit = defineEmits(['create-session', 'action'])

const CARGO_TYPES = [
  { id: 'GRAIN', name: '粮食', weight: 25, density: '0.75 t/m³', color: '#DEB887' },
  { id: 'SALT', name: '海盐', weight: 40, density: '2.16 t/m³', color: '#F0F8FF' },
  { id: 'TEA', name: '茶叶', weight: 15, density: '0.30 t/m³', color: '#228B22' },
  { id: 'PORCELAIN', name: '瓷器', weight: 50, density: '2.40 t/m³', color: '#E6E6FA' },
  { id: 'SILK', name: '丝绸', weight: 10, density: '0.25 t/m³', color: '#FFB6C1' }
]

const cargoLibrary = ref(CARGO_TYPES)

const currentSession = ref(null)
const isDragging = ref(false)
const draggedCargo = ref(null)
const trendChartRef = ref(null)
let trendChart = null

const cargoHolds = computed(() => {
  if (!currentSession.value?.cargoHolds) {
    return [
      { id: 1, name: '1#舱', x: 100, y: 220, width: 100, height: 100, utilization: 0, layers: [] },
      { id: 2, name: '2#舱', x: 220, y: 200, width: 120, height: 120, utilization: 0, layers: [] },
      { id: 3, name: '3#舱', x: 360, y: 190, width: 120, height: 130, utilization: 0, layers: [] },
      { id: 4, name: '4#舱', x: 500, y: 200, width: 120, height: 120, utilization: 0, layers: [] },
      { id: 5, name: '5#舱', x: 640, y: 220, width: 80, height: 100, utilization: 0, layers: [] }
    ]
  }
  return currentSession.value.cargoHolds.map((h, i) => {
    const defaults = [
      { x: 100, y: 220, width: 100, height: 100 },
      { x: 220, y: 200, width: 120, height: 120 },
      { x: 360, y: 190, width: 120, height: 130 },
      { x: 500, y: 200, width: 120, height: 120 },
      { x: 640, y: 220, width: 80, height: 100 }
    ]
    const def = defaults[i] || defaults[0]
    return {
      ...h,
      ...def,
      name: h.name || `${i + 1}#舱`,
      layers: h.layers || []
    }
  })
})

const waterlineY = computed(() => {
  if (!currentSession.value?.draft || !props.ship?.depth) return 300
  const depth = props.ship.depth || 12
  const ratio = currentSession.value.draft / depth
  return 180 + ratio * 120
})

const keelY = computed(() => 340)

const hullPoints = computed(() => {
  return [
    '60,180',
    '120,160',
    '680,160',
    '740,180',
    '720,340',
    '680,350',
    '120,350',
    '80,340'
  ].join(' ')
})

const deckPoints = computed(() => {
  return [
    '120,160',
    '680,160',
    '680,175',
    '120,175'
  ].join(' ')
})

const metacenterPoints = computed(() => {
  if (!currentSession.value?.pointM) return ''
  const my = currentSession.value.pointM.y
  return `380,${my + 40} 460,${my - 10} 540,${my + 40}`
})

const gmGaugeDasharray = computed(() => {
  const circumference = Math.PI * 80
  return circumference.toFixed(2)
})

const gmGaugeDashoffset = computed(() => {
  const circumference = Math.PI * 80
  const gm = currentSession.value?.gm ?? 0
  const clampedGm = Math.max(-0.5, Math.min(1.5, gm))
  const progress = (clampedGm + 0.5) / 2
  return (circumference * (1 - progress)).toFixed(2)
})

const gaugePointerTransform = computed(() => {
  const gm = currentSession.value?.gm ?? 0
  const clampedGm = Math.max(-0.5, Math.min(1.5, gm))
  const angle = -90 + (clampedGm + 0.5) / 2 * 180
  return `translate(100, 120) rotate(${angle})`
})

const totalLoadedWeight = computed(() => {
  if (!currentSession.value?.cargoHolds) return 0
  return currentSession.value.cargoHolds.reduce((sum, h) => {
    return sum + (h.layers?.reduce((s, l) => s + (l.weight || 0), 0) || 0)
  }, 0)
})

const loadingDetailsData = computed(() => {
  if (!currentSession.value?.cargoHolds) return []
  const rows = []
  currentSession.value.cargoHolds.forEach(hold => {
    if (hold.layers?.length) {
      hold.layers.forEach(layer => {
        rows.push({
          holdName: hold.name,
          cargoType: layer.cargoName || '货物',
          color: layer.color || '#fff',
          weight: layer.weight,
          cgHeight: layer.cgHeight,
          utilization: hold.utilization
        })
      })
    }
  })
  return rows
})

const getGmColor = (gm) => {
  if (gm == null) return '#a0aec0'
  if (gm < 0.15) return '#F56C6C'
  if (gm < 0.3) return '#E6A23C'
  if (gm < 0.5) return '#67C23A'
  return '#409EFF'
}

const getGmTagType = (gm) => {
  if (gm == null) return 'info'
  if (gm < 0.15) return 'danger'
  if (gm < 0.3) return 'warning'
  return 'success'
}

const getGmStatus = (gm) => {
  if (gm == null) return '未开始'
  if (gm < 0.15) return '危险'
  if (gm < 0.3) return '注意'
  if (gm < 0.5) return '良好'
  return '偏大'
}

const getUtilizationColor = (util) => {
  if (!util) return '#909399'
  if (util < 60) return '#67C23A'
  if (util < 85) return '#E6A23C'
  return '#F56C6C'
}

const onDragStart = (event, cargo) => {
  isDragging.value = true
  draggedCargo.value = cargo
  event.dataTransfer.setData('text/plain', JSON.stringify(cargo))
  event.dataTransfer.effectAllowed = 'copy'
}

const onDragEnd = () => {
  isDragging.value = false
  draggedCargo.value = null
}

const onDragOver = (event) => {
  event.dataTransfer.dropEffect = 'copy'
}

const onDrop = (event) => {
  event.preventDefault()
  isDragging.value = false

  if (!props.sessionId || !draggedCargo.value) {
    if (!props.sessionId) {
      ElMessage.warning('请先创建装载会话')
    }
    return
  }

  const cargo = draggedCargo.value
  const rect = event.currentTarget.getBoundingClientRect()
  const x = ((event.clientX - rect.left) / rect.width) * 800
  const y = ((event.clientY - rect.top) / rect.height) * 400

  let targetHold = null
  let minDist = Infinity

  cargoHolds.value.forEach(hold => {
    const cx = hold.x + hold.width / 2
    const cy = hold.y + hold.height / 2
    const dist = Math.sqrt((x - cx) ** 2 + (y - cy) ** 2)

    if (x >= hold.x && x <= hold.x + hold.width && y >= hold.y && y <= hold.y + hold.height) {
      targetHold = hold
    } else if (dist < minDist) {
      minDist = dist
    }
  })

  if (!targetHold) {
    targetHold = cargoHolds.value.reduce((nearest, hold) => {
      const cx = hold.x + hold.width / 2
      const cy = hold.y + hold.height / 2
      const dist = Math.sqrt((x - cx) ** 2 + (y - cy) ** 2)
      return dist < nearest.dist ? { hold, dist } : nearest
    }, { hold: null, dist: Infinity }).hold
  }

  if (targetHold) {
    const actionRequest = {
      action: 'LOAD_CARGO',
      params: {
        holdId: targetHold.id,
        cargoType: cargo.id,
        cargoName: cargo.name,
        weight: cargo.weight,
        color: cargo.color
      }
    }
    emit('action', actionRequest)
    ElMessage.success(`已装载 ${cargo.name} 到 ${targetHold.name}`)
  }

  draggedCargo.value = null
}

const handleCreateSession = () => {
  if (!props.ship) {
    ElMessage.warning('请先选择船舶')
    return
  }
  const request = {
    shipId: props.ship.id
  }
  emit('create-session', request)
}

const handleRemoveCargo = (row) => {
  const hold = cargoHolds.value.find(h => h.name === row.holdName)
  if (!hold) return
  const actionRequest = {
    action: 'UNLOAD_CARGO',
    params: {
      holdId: hold.id,
      cargoType: CARGO_TYPES.find(c => c.name === row.cargoType)?.id || 'UNKNOWN',
      weight: row.weight
    }
  }
  emit('action', actionRequest)
}

const executeSmartLoad = () => {
  const actionRequest = {
    action: 'SMART_LOAD',
    params: {}
  }
  emit('action', actionRequest)
  ElMessage.info('正在执行智能装载优化...')
}

const executeUndo = () => {
  const actionRequest = {
    action: 'UNDO',
    params: {}
  }
  emit('action', actionRequest)
}

const executeReset = () => {
  const actionRequest = {
    action: 'RESET',
    params: {}
  }
  emit('action', actionRequest)
}

const initTrendChart = () => {
  if (!trendChartRef.value) return
  if (trendChart) trendChart.destroy()

  const history = currentSession.value?.gmHistory || []
  const labels = history.map((_, i) => i + 1)
  const data = history.map(h => h.gm)

  const gmThresholdPlugin = {
    id: 'gmThresholdPlugin',
    afterDraw: (chart) => {
      const { ctx, chartArea, scales } = chart
      if (!chartArea) return

      const drawThreshold = (value, color, label) => {
        const y = scales.y.getPixelForValue(value)
        if (y < chartArea.top || y > chartArea.bottom) return

        ctx.save()
        ctx.beginPath()
        ctx.moveTo(chartArea.left, y)
        ctx.lineTo(chartArea.right, y)
        ctx.strokeStyle = color
        ctx.lineWidth = 1.5
        ctx.setLineDash([6, 4])
        ctx.stroke()

        ctx.fillStyle = color
        ctx.font = '10px Arial'
        ctx.textAlign = 'left'
        ctx.fillText(label, chartArea.left + 4, y - 4)
        ctx.restore()
      }

      drawThreshold(0.3, 'rgba(230, 162, 60, 0.8)', '0.3m 安全阈值')
      drawThreshold(0.15, 'rgba(245, 108, 108, 0.8)', '0.15m 危险阈值')
    }
  }

  const ctx = trendChartRef.value.getContext('2d')
  trendChart = new Chart(ctx, {
    type: 'line',
    data: {
      labels,
      datasets: [{
        label: 'GM值 (m)',
        data,
        borderColor: '#67C23A',
        backgroundColor: 'rgba(103, 194, 58, 0.2)',
        fill: true,
        tension: 0.3,
        pointRadius: data.length < 20 ? 4 : 0,
        pointHoverRadius: 6,
        pointBackgroundColor: data.map(v => getGmColor(v)),
        pointBorderColor: '#fff',
        pointBorderWidth: 1,
        borderWidth: 2
      }]
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
          borderColor: 'rgba(103, 194, 58, 0.3)',
          borderWidth: 1,
          padding: 10,
          callbacks: {
            label: (ctx) => {
              const val = ctx.raw
              return [
                `GM: ${val?.toFixed(3)} m`,
                `状态: ${getGmStatus(val)}`
              ]
            }
          }
        }
      },
      scales: {
        x: {
          title: { display: true, text: '装载步骤', color: '#a0aec0' },
          ticks: { color: '#a0aec0', maxTicksLimit: 10 },
          grid: { color: 'rgba(255,255,255,0.05)' }
        },
        y: {
          title: { display: true, text: 'GM (m)', color: '#67C23A' },
          ticks: { color: '#67C23A' },
          grid: { color: 'rgba(255,255,255,0.05)' },
          min: -0.2,
          max: 1.5
        }
      }
    },
    plugins: [gmThresholdPlugin]
  })
}

const setSessionData = (session) => {
  currentSession.value = session
  nextTick(() => {
    initTrendChart()
  })
}

defineExpose({ setSessionData })

const destroyCharts = () => {
  if (trendChart) {
    trendChart.destroy()
    trendChart = null
  }
}

watch(() => props.sessionId, () => {
  if (!props.sessionId) {
    currentSession.value = null
    destroyCharts()
  }
})

watch(() => props.ship, () => {
  if (!props.ship) {
    currentSession.value = null
    destroyCharts()
  }
})

onMounted(() => {
  if (currentSession.value?.gmHistory?.length) {
    nextTick(() => initTrendChart())
  }
})

onUnmounted(() => {
  destroyCharts()
})
</script>

<style scoped lang="scss">
.vr-loading {
  width: 100%;
}

.loading-content {
  display: grid;
  grid-template-columns: 300px 1fr 340px;
  gap: 16px;
}

.section-card {
  background: rgba(12, 25, 41, 0.6);
  border: 1px solid rgba(64, 158, 255, 0.2);
  border-radius: 12px;
  overflow: hidden;
  backdrop-filter: blur(10px);
  margin-bottom: 16px;

  &:last-child {
    margin-bottom: 0;
  }
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 18px;
  border-bottom: 1px solid rgba(64, 158, 255, 0.15);

  .card-title {
    display: flex;
    align-items: center;
    gap: 8px;
    margin: 0;
    font-size: 15px;
    color: #ffffff;

    .el-icon {
      color: #409EFF;
    }
  }

  .ship-info-tags {
    display: flex;
    gap: 6px;
    flex-wrap: wrap;
  }
}

.left-panel {
  .cargo-library {
    padding: 12px;
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  .cargo-item {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 10px 12px;
    background: rgba(0, 0, 0, 0.2);
    border: 2px solid transparent;
    border-radius: 8px;
    cursor: grab;
    transition: all 0.2s;

    &:hover {
      transform: translateX(4px);
      background: rgba(64, 158, 255, 0.1);
    }

    &:active {
      cursor: grabbing;
    }

    .cargo-color-bar {
      width: 6px;
      height: 40px;
      border-radius: 3px;
      flex-shrink: 0;
    }

    .cargo-info {
      flex: 1;
      min-width: 0;

      .cargo-name {
        color: #fff;
        font-size: 14px;
        font-weight: 500;
        margin-bottom: 2px;
      }

      .cargo-meta {
        display: flex;
        flex-direction: column;
        gap: 1px;

        span {
          color: #a0aec0;
          font-size: 11px;
        }

        .cargo-density {
          opacity: 0.7;
        }
      }
    }

    .cargo-icon {
      color: #606266;
      opacity: 0.5;
      font-size: 16px;
    }
  }

  .gm-gauge-container {
    padding: 0 10px;

    .gm-gauge {
      width: 100%;
      height: auto;

      .gauge-fill {
        transition: stroke-dashoffset 0.5s ease;
      }

      .gm-value {
        font-size: 22px;
        font-weight: bold;
        font-family: 'Courier New', monospace;
      }

      .gm-unit {
        font-size: 11px;
        fill: #a0aec0;
      }

      .gauge-label {
        font-size: 9px;
        fill: #718096;
      }
    }
  }

  .gm-legend {
    display: flex;
    flex-direction: column;
    gap: 6px;
    padding: 8px 16px 16px;

    .legend-item {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 11px;
      color: #a0aec0;

      .legend-bar {
        width: 24px;
        height: 6px;
        border-radius: 3px;

        &.danger { background: linear-gradient(90deg, #F5222D, #F56C6C); }
        &.warning { background: linear-gradient(90deg, #F56C6C, #E6A23C); }
        &.safe { background: linear-gradient(90deg, #E6A23C, #67C23A); }
      }
    }
  }

  .trend-chart-container {
    height: 180px;
    padding: 12px 16px;

    canvas {
      width: 100% !important;
      height: 100% !important;
    }
  }

  .action-card {
    padding: 16px;

    .action-buttons {
      display: flex;
      flex-direction: column;
      gap: 12px;

      .create-session-btn {
        width: 100%;
      }
    }
  }
}

.center-panel {
  .ship-profile-card {
    margin-bottom: 16px;
  }

  .ship-profile-container {
    position: relative;
    padding: 16px;
    min-height: 440px;

    &.drag-over {
      background: rgba(64, 158, 255, 0.05);
    }

    .ship-profile {
      width: 100%;
      height: auto;

      .ship-hull {
        filter: drop-shadow(0 4px 12px rgba(0,0,0,0.5));
      }

      .cargo-layer {
        transition: all 0.3s ease;
      }

      .hold-outline {
        transition: all 0.2s;
      }

      .hold-weight-label {
        fill: rgba(255,255,255,0.95);
        font-size: 11px;
        font-weight: bold;
        pointer-events: none;
      }

      .hold-label {
        fill: #a0aec0;
        font-size: 12px;
        font-weight: 500;
      }

      .hold-capacity {
        fill: #718096;
        font-size: 11px;
        font-family: 'Courier New', monospace;

        &.near-full {
          fill: #F56C6C;
          font-weight: bold;
        }
      }

      .point-g, .point-b, .point-m {
        filter: drop-shadow(0 0 8px currentColor);
      }

      .point-g { color: #F56C6C; animation: pulse-red 2s infinite; }
      .point-b { color: #409EFF; animation: pulse-blue 2s infinite; }
      .point-m { color: #67C23A; animation: pulse-green 2s infinite; }

      .point-label {
        font-size: 11px;
        font-weight: 500;

        &.g-label { fill: #F56C6C; }
        &.b-label { fill: #409EFF; }
        &.m-label { fill: #67C23A; }
      }

      .dimension-label {
        font-size: 10px;
        fill: #a0aec0;
        font-family: 'Courier New', monospace;
      }

      .gm-line {
        stroke-linecap: round;
      }

      .gm-label {
        fill: #67C23A;
        font-size: 12px;
        font-weight: bold;
        font-family: 'Courier New', monospace;
      }

      .dim-label {
        fill: #718096;
        font-size: 11px;
      }
    }

    .drag-hint {
      position: absolute;
      top: 50%;
      left: 50%;
      transform: translate(-50%, -50%);
      background: rgba(64, 158, 255, 0.9);
      color: #fff;
      padding: 12px 24px;
      border-radius: 8px;
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 14px;
      pointer-events: none;
      animation: pulse-hint 1s infinite;
    }
  }

  .recommendation-content {
    padding: 16px;

    .recommendation-summary {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 12px 16px;
      border-radius: 8px;
      margin-bottom: 12px;
      font-weight: 500;

      &.low {
        background: rgba(103, 194, 58, 0.15);
        color: #67C23A;
        border-left: 4px solid #67C23A;
      }

      &.medium {
        background: rgba(230, 162, 60, 0.15);
        color: #E6A23C;
        border-left: 4px solid #E6A23C;
      }

      &.high {
        background: rgba(245, 108, 108, 0.15);
        color: #F56C6C;
        border-left: 4px solid #F56C6C;
      }
    }

    .recommendation-actions {
      display: flex;
      flex-direction: column;
      gap: 8px;

      .recommendation-item {
        display: flex;
        align-items: flex-start;
        gap: 10px;
        padding: 10px 14px;
        background: rgba(0, 0, 0, 0.2);
        border-radius: 6px;

        .action-index {
          width: 22px;
          height: 22px;
          border-radius: 50%;
          background: rgba(64, 158, 255, 0.2);
          color: #409EFF;
          font-size: 12px;
          font-weight: bold;
          display: flex;
          align-items: center;
          justify-content: center;
          flex-shrink: 0;
        }

        .action-text {
          color: #a0aec0;
          font-size: 13px;
          line-height: 1.5;
          padding-top: 2px;
        }
      }
    }
  }
}

@keyframes pulse-red {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.6; }
}

@keyframes pulse-blue {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.6; }
}

@keyframes pulse-green {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.6; }
}

@keyframes pulse-hint {
  0%, 100% { transform: translate(-50%, -50%) scale(1); }
  50% { transform: translate(-50%, -50%) scale(1.05); }
}

.right-panel {
  .loading-table {
    :deep(.el-table) {
      --el-table-bg-color: transparent;
      --el-table-tr-bg-color: transparent;
      --el-table-header-bg-color: rgba(0, 0, 0, 0.2);
      --el-table-row-hover-bg-color: rgba(64, 158, 255, 0.1);
      --el-table-border-color: rgba(64, 158, 255, 0.1);
    }

    :deep(.el-table th) {
      color: #a0aec0;
      font-size: 12px;
    }

    :deep(.el-table td) {
      color: #e2e8f0;
      font-size: 12px;
    }

    .util-text {
      display: block;
      font-size: 10px;
      color: #a0aec0;
      text-align: center;
      margin-top: 2px;
    }
  }

  .stability-metrics {
    padding: 12px 18px;
    display: flex;
    flex-direction: column;
    gap: 8px;

    .stability-item {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 8px 12px;
      background: rgba(0, 0, 0, 0.2);
      border-radius: 6px;
      transition: all 0.2s;

      &:hover {
        background: rgba(64, 158, 255, 0.1);
      }

      &.highlight {
        border-left: 3px solid #67C23A;
        background: rgba(103, 194, 58, 0.08);
      }

      .metric-label {
        color: #a0aec0;
        font-size: 12px;
      }

      .metric-value {
        color: #fff;
        font-size: 13px;
        font-weight: 600;
        font-family: 'Courier New', monospace;
      }
    }
  }

  .knowledge-section {
    padding: 8px 12px 16px;

    .knowledge-collapse {
      :deep(.el-collapse-item__header) {
        color: #409EFF;
        font-size: 13px;
        font-weight: 500;
        background: transparent;
        border: none;
        padding-left: 0;
      }

      :deep(.el-collapse-item__wrap) {
        border-bottom: 1px solid rgba(64, 158, 255, 0.1);
      }

      :deep(.el-collapse-item__content) {
        background: transparent;
      }
    }

    .knowledge-content {
      padding: 8px 0 16px;
      color: #a0aec0;
      font-size: 12px;
      line-height: 1.8;

      p { margin: 0 0 8px 0; }

      strong {
        color: #e2e8f0;
      }

      ul {
        padding-left: 18px;
        margin: 8px 0;

        li {
          margin-bottom: 6px;
          display: flex;
          align-items: center;
          gap: 6px;
        }
      }

      .formula {
        background: rgba(64, 158, 255, 0.15);
        padding: 8px 12px;
        border-radius: 6px;
        text-align: center;
        font-family: 'Courier New', monospace;
        color: #409EFF;
        font-weight: 500;
        margin-top: 8px;
      }

      .relation-diagram {
        background: rgba(0, 0, 0, 0.2);
        padding: 12px;
        border-radius: 6px;
        margin-top: 8px;

        .diagram-item {
          display: flex;
          align-items: center;
          gap: 8px;
          padding: 4px 0;

          .dot {
            width: 10px;
            height: 10px;
            border-radius: 50%;

            &.green { background: #67C23A; }
            &.blue { background: #409EFF; }
            &.gray { background: #718096; }
          }
        }

        .diagram-arrow {
          color: #718096;
          font-size: 11px;
          padding-left: 18px;
          opacity: 0.8;
        }

        .diagram-note {
          margin-top: 8px;
          padding-top: 8px;
          border-top: 1px solid rgba(255,255,255,0.1);
          color: #67C23A;
          font-weight: 500;
        }
      }
    }
  }
}

@media (max-width: 1400px) {
  .loading-content {
    grid-template-columns: 280px 1fr 300px;
  }
}

@media (max-width: 1200px) {
  .loading-content {
    grid-template-columns: 1fr;
  }
}
</style>
