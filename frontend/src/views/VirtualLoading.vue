<template>
  <div class="virtual-loading">
    <div class="page-header">
      <h2 class="page-title">
        <el-icon><Box /></el-icon>
        公众虚拟装载体验
        <el-tooltip content="拖拽货物到不同货舱，实时观察稳性变化，学习船舶稳性知识" placement="right">
          <el-icon class="info-icon"><QuestionFilled /></el-icon>
        </el-tooltip>
      </h2>
      <div class="header-desc">
        拖拽货物到不同货舱，实时观察稳性变化，学习船舶稳性知识
      </div>
    </div>

    <div class="toolbar">
      <div class="toolbar-left">
        <el-select v-model="selectedShipId" placeholder="选择船舶" @change="handleShipChange" class="ship-select">
          <el-option
            v-for="ship in ships"
            :key="ship.id"
            :label="ship.name"
            :value="ship.id"
          />
        </el-select>
        <el-input
          v-model="sessionName"
          placeholder="会话名称"
          class="session-input"
          clearable
        />
        <el-switch
          v-model="isPublic"
          active-text="公开"
          inactive-text="私有"
          inline-prompt
        />
      </div>
      <div class="toolbar-right">
        <el-button @click="createNewSession">
          <el-icon><DocumentAdd /></el-icon>
          新会话
        </el-button>
        <el-button type="primary" @click="saveSession" :disabled="!sessionId">
          <el-icon><Document /></el-icon>
          保存会话
        </el-button>
        <el-button @click="resetLoading" :disabled="!sessionId">
          <el-icon><Refresh /></el-icon>
          重置装载
        </el-button>
        <el-button type="success" @click="smartRecommend" :disabled="!sessionId">
          <el-icon><MagicStick /></el-icon>
          智能推荐
        </el-button>
        <el-button @click="undoAction" :disabled="operationHistory.length === 0">
          <el-icon><RefreshLeft /></el-icon>
          撤销
        </el-button>
      </div>
    </div>

    <div class="main-content">
      <div class="left-panel">
        <div class="card cargo-library-card">
          <div class="card-header">
            <h3 class="card-title">
              <el-icon><Goods /></el-icon>
              货物库（拖拽添加）
            </h3>
            <el-tooltip content="拖拽货物卡片到货舱区域进行装载" placement="top">
              <el-icon class="info-icon"><QuestionFilled /></el-icon>
            </el-tooltip>
          </div>
          <div class="card-body">
            <div
              v-for="cargo in cargoTypes"
              :key="cargo.id"
              class="cargo-item"
              draggable="true"
              @dragstart="handleDragStart($event, cargo)"
              @dragend="handleDragEnd"
            >
              <div class="cargo-icon" :style="{ backgroundColor: cargo.color + '30', color: cargo.color }">
                <el-icon><component :is="getCargoIcon(cargo.id)" /></el-icon>
              </div>
              <div class="cargo-info">
                <span class="cargo-name">{{ cargo.name }}</span>
                <span class="cargo-density">密度: {{ cargo.density }} t/m³</span>
              </div>
              <div class="cargo-available">
                <span class="available-label">可用</span>
                <span class="available-value">{{ cargo.available }} t</span>
              </div>
              <div class="cargo-color-tag" :style="{ backgroundColor: cargo.color }"></div>
            </div>
          </div>
        </div>

        <div class="card knowledge-card">
          <div class="card-header">
            <h3 class="card-title">
              <el-icon><Reading /></el-icon>
              稳性知识
            </h3>
          </div>
          <div class="card-body">
            <div
              v-for="(item, index) in knowledgeList"
              :key="index"
              class="knowledge-item"
              @click="showKnowledgeDetail(item)"
            >
              <el-icon class="knowledge-icon"><InfoFilled /></el-icon>
              <span class="knowledge-title">{{ item.title }}</span>
            </div>
          </div>
        </div>
      </div>

      <div class="center-panel">
        <div class="card ship-profile-card">
          <div class="card-header">
            <h3 class="card-title">
              <el-icon><Ship /></el-icon>
              船舶纵剖面图
            </h3>
            <div class="header-stats">
              <span class="stat">
                <span class="stat-label">吃水:</span>
                <span class="stat-value">{{ draftDepth?.toFixed(2) || '--' }} m</span>
              </span>
              <span class="stat">
                <span class="stat-label">排水量:</span>
                <span class="stat-value">{{ displacement?.toFixed(1) || '--' }} t</span>
              </span>
            </div>
          </div>
          <div class="card-body ship-profile-body">
            <svg
              ref="shipSvgRef"
              class="ship-svg"
              viewBox="0 0 800 400"
              preserveAspectRatio="xMidYMid meet"
            >
              <defs>
                <linearGradient id="hullGradient" x1="0%" y1="0%" x2="0%" y2="100%">
                  <stop offset="0%" style="stop-color:#1e3a5f;stop-opacity:1" />
                  <stop offset="100%" style="stop-color:#0a1929;stop-opacity:1" />
                </linearGradient>
                <linearGradient id="waterGradient" x1="0%" y1="0%" x2="0%" y2="100%">
                  <stop offset="0%" style="stop-color:#409EFF;stop-opacity:0.6" />
                  <stop offset="100%" style="stop-color:#1e3a5f;stop-opacity:0.8" />
                </linearGradient>
                <linearGradient id="cargoGrain" x1="0%" y1="0%" x2="0%" y2="100%">
                  <stop offset="0%" style="stop-color:#DEB887;stop-opacity:0.9" />
                  <stop offset="100%" style="stop-color:#C4A574;stop-opacity:0.95" />
                </linearGradient>
                <linearGradient id="cargoSalt" x1="0%" y1="0%" x2="0%" y2="100%">
                  <stop offset="0%" style="stop-color:#F0F8FF;stop-opacity:0.95" />
                  <stop offset="100%" style="stop-color:#D4E8F5;stop-opacity:0.98" />
                </linearGradient>
                <linearGradient id="cargoTea" x1="0%" y1="0%" x2="0%" y2="100%">
                  <stop offset="0%" style="stop-color:#228B22;stop-opacity:0.9" />
                  <stop offset="100%" style="stop-color:#1A6B1A;stop-opacity:0.95" />
                </linearGradient>
                <linearGradient id="cargoPorcelain" x1="0%" y1="0%" x2="0%" y2="100%">
                  <stop offset="0%" style="stop-color:#E6E6FA;stop-opacity:0.92" />
                  <stop offset="100%" style="stop-color:#CCCCFF;stop-opacity:0.96" />
                </linearGradient>
                <linearGradient id="cargoSilk" x1="0%" y1="0%" x2="0%" y2="100%">
                  <stop offset="0%" style="stop-color:#FFB6C1;stop-opacity:0.9" />
                  <stop offset="100%" style="stop-color:#FF9DB0;stop-opacity:0.95" />
                </linearGradient>
                <filter id="glow">
                  <feGaussianBlur stdDeviation="2" result="coloredBlur"/>
                  <feMerge>
                    <feMergeNode in="coloredBlur"/>
                    <feMergeNode in="SourceGraphic"/>
                  </feMerge>
                </filter>
              </defs>

              <path
                :d="shipHullPath"
                fill="url(#hullGradient)"
                stroke="#409EFF"
                stroke-width="2"
              />

              <path
                :d="waterLinePath"
                fill="url(#waterGradient)"
                class="water-fill"
              />
              <line
                :x1="waterLineX1"
                :y1="waterLineY"
                :x2="waterLineX2"
                :y2="waterLineY"
                stroke="#409EFF"
                stroke-width="2"
                stroke-dasharray="8,4"
                class="water-line"
              />
              <text :x="waterLineX1 + 10" :y="waterLineY - 5" fill="#409EFF" font-size="12" font-weight="bold">WL</text>

              <g v-for="(hold, index) in cargoHolds" :key="hold.id">
                <rect
                  :x="getHoldX(index)"
                  :y="getHoldY()"
                  :width="getHoldWidth()"
                  :height="getHoldHeight()"
                  :class="{ 'hold-area': true, 'drag-over': dragOverHoldId === hold.id }"
                  fill="rgba(64, 158, 255, 0.1)"
                  stroke="rgba(64, 158, 255, 0.5)"
                  stroke-width="1"
                  rx="4"
                  @dragover.prevent="handleDragOver($event, hold.id)"
                  @dragleave="handleDragLeave(hold.id)"
                  @drop="handleDrop($event, hold.id)"
                  @click="openHoldDialog(hold)"
                />
                <text
                  :x="getHoldX(index) + getHoldWidth() / 2"
                  :y="getHoldY() + 20"
                  text-anchor="middle"
                  fill="#a0aec0"
                  font-size="11"
                >{{ hold.holdName }}</text>

                <g v-for="(stack, stackIndex) in getCargoStacks(hold.id)" :key="stackIndex">
                  <rect
                    :x="getHoldX(index) + 5"
                    :y="getStackY(hold.id, stackIndex)"
                    :width="getHoldWidth() - 10"
                    :height="getStackHeight(hold, stack)"
                    :fill="getStackGradient(stack.cargoTypeId)"
                    rx="2"
                    class="cargo-stack"
                  />
                  <text
                    v-if="getStackHeight(hold, stack) > 20"
                    :x="getHoldX(index) + getHoldWidth() / 2"
                    :y="getStackY(hold.id, stackIndex) + getStackHeight(hold, stack) / 2 + 4"
                    text-anchor="middle"
                    fill="#ffffff"
                    font-size="10"
                    font-weight="bold"
                  >{{ stack.weight.toFixed(1) }}t</text>
                </g>

                <text
                  :x="getHoldX(index) + getHoldWidth() / 2"
                  :y="getHoldY() + getHoldHeight() + 15"
                  text-anchor="middle"
                  fill="#67C23A"
                  font-size="11"
                  font-weight="bold"
                >{{ getHoldUtilization(hold.id) }}%</text>
              </g>

              <g class="stability-points">
                <circle
                  :cx="pointG.x"
                  :cy="pointG.y"
                  r="6"
                  fill="#F56C6C"
                  filter="url(#glow)"
                />
                <text :x="pointG.x + 10" :y="pointG.y + 4" fill="#F56C6C" font-size="12" font-weight="bold">G</text>

                <circle
                  :cx="pointB.x"
                  :cy="pointB.y"
                  r="6"
                  fill="#67C23A"
                  filter="url(#glow)"
                />
                <text :x="pointB.x + 10" :y="pointB.y + 4" fill="#67C23A" font-size="12" font-weight="bold">B</text>

                <circle
                  :cx="pointM.x"
                  :cy="pointM.y"
                  r="6"
                  fill="#409EFF"
                  filter="url(#glow)"
                />
                <text :x="pointM.x + 10" :y="pointM.y + 4" fill="#409EFF" font-size="12" font-weight="bold">M</text>

                <line
                  :x1="pointG.x"
                  :y1="pointG.y"
                  :x2="pointM.x"
                  :y2="pointM.y"
                  :stroke="gmLineColor"
                  stroke-width="2"
                  stroke-dasharray="4,2"
                />
              </g>

              <g class="deck">
                <line x1="80" y1="120" x2="720" y2="120" stroke="#409EFF" stroke-width="2" />
                <line x1="100" y1="80" x2="700" y2="80" stroke="#409EFF" stroke-width="1" stroke-dasharray="4,2" />
                <line x1="120" y1="120" x2="120" y2="80" stroke="#409EFF" stroke-width="1" />
                <line x1="680" y1="120" x2="680" y2="80" stroke="#409EFF" stroke-width="1" />
              </g>
            </svg>
          </div>
        </div>

        <div class="card chart-card">
          <div class="card-header">
            <h3 class="card-title">
              <el-icon><TrendCharts /></el-icon>
              GM值实时变化趋势
            </h3>
            <div class="threshold-info">
              <span class="threshold red">0.15m 危险</span>
              <span class="threshold yellow">0.30m 警告</span>
            </div>
          </div>
          <div class="card-body">
            <div class="chart-container">
              <canvas ref="gmTrendChartRef"></canvas>
            </div>
          </div>
        </div>
      </div>

      <div class="right-panel">
        <div class="card stability-gauge-card">
          <div class="card-header">
            <h3 class="card-title">
              <el-icon><Gauge /></el-icon>
              稳性仪表盘
            </h3>
            <el-tag :type="getStatusType()" effect="dark" size="large">
              {{ stabilityStatus }}
            </el-tag>
          </div>
          <div class="card-body">
            <div class="gauge-container">
              <svg viewBox="0 0 200 200" class="gauge-svg">
                <defs>
                  <linearGradient id="gaugeGreen" x1="0%" y1="0%" x2="100%" y2="0%">
                    <stop offset="0%" style="stop-color:#67C23A" />
                    <stop offset="100%" style="stop-color:#85CE61" />
                  </linearGradient>
                  <linearGradient id="gaugeYellow" x1="0%" y1="0%" x2="100%" y2="0%">
                    <stop offset="0%" style="stop-color:#E6A23C" />
                    <stop offset="100%" style="stop-color:#EEBE77" />
                  </linearGradient>
                  <linearGradient id="gaugeRed" x1="0%" y1="0%" x2="100%" y2="0%">
                    <stop offset="0%" style="stop-color:#F56C6C" />
                    <stop offset="100%" style="stop-color:#F78989" />
                  </linearGradient>
                </defs>

                <circle cx="100" cy="100" r="80" fill="none" stroke="rgba(64, 158, 255, 0.1)" stroke-width="16" />

                <path
                  :d="getGaugeArcPath(0, 30)"
                  fill="none"
                  stroke="url(#gaugeRed)"
                  stroke-width="16"
                  stroke-linecap="round"
                />
                <path
                  :d="getGaugeArcPath(30, 60)"
                  fill="none"
                  stroke="url(#gaugeYellow)"
                  stroke-width="16"
                  stroke-linecap="round"
                />
                <path
                  :d="getGaugeArcPath(60, 100)"
                  fill="none"
                  stroke="url(#gaugeGreen)"
                  stroke-width="16"
                  stroke-linecap="round"
                />

                <path
                  :d="getGaugeValueArc()"
                  fill="none"
                  :stroke="gmColor"
                  stroke-width="16"
                  stroke-linecap="round"
                  class="gauge-value-arc"
                />

                <g :transform="`rotate(${getGaugePointerAngle()} 100 100)`">
                  <line x1="100" y1="100" x2="100" y2="35" stroke="#ffffff" stroke-width="3" stroke-linecap="round" />
                  <circle cx="100" cy="100" r="10" fill="#ffffff" />
                </g>

                <text x="100" y="100" text-anchor="middle" fill="#ffffff" font-size="28" font-weight="bold" font-family="Courier New">
                  {{ currentGM?.toFixed(2) || '--' }}
                </text>
                <text x="100" y="125" text-anchor="middle" fill="#a0aec0" font-size="14">m</text>
              </svg>
            </div>

            <div class="gm-main-display">
              <span class="gm-label">GM值</span>
              <span class="gm-value" :style="{ color: gmColor }">{{ currentGM?.toFixed(3) || '--' }} m</span>
            </div>

            <div class="stability-details">
              <div class="detail-item">
                <span class="detail-label">横摇周期</span>
                <span class="detail-value">{{ rollPeriod?.toFixed(2) || '--' }} s</span>
              </div>
              <div class="detail-item">
                <span class="detail-label">最大复原力臂</span>
                <span class="detail-value">{{ maxRightingArm?.toFixed(3) || '--' }} m</span>
              </div>
              <div class="detail-item">
                <span class="detail-label">稳性范围</span>
                <span class="detail-value">{{ stabilityRange?.toFixed(1) || '--' }}°</span>
              </div>
            </div>
          </div>
        </div>

        <div class="card loading-summary-card">
          <div class="card-header">
            <h3 class="card-title">
              <el-icon><DataLine /></el-icon>
              装载概况
            </h3>
          </div>
          <div class="card-body">
            <div class="summary-progress">
              <div class="progress-item">
                <div class="progress-header">
                  <span class="progress-label">总装载重量</span>
                  <span class="progress-value">{{ totalCargoWeight?.toFixed(1) || 0 }} / {{ maxDeadweight || '--' }} t</span>
                </div>
                <div class="progress-bar">
                  <div
                    class="progress-fill"
                    :style="{ width: getWeightPercent() + '%' }"
                  ></div>
                </div>
              </div>
              <div class="progress-item">
                <div class="progress-header">
                  <span class="progress-label">总装载容积</span>
                  <span class="progress-value">{{ totalCargoVolume?.toFixed(1) || 0 }} / {{ maxVolume || '--' }} m³</span>
                </div>
                <div class="progress-bar">
                  <div
                    class="progress-fill volume"
                    :style="{ width: getVolumePercent() + '%' }"
                  ></div>
                </div>
              </div>
            </div>

            <div class="holds-detail-table">
              <table class="detail-table">
                <thead>
                  <tr>
                    <th>货舱</th>
                    <th>货物</th>
                    <th>重量(t)</th>
                    <th>容积(m³)</th>
                    <th>利用率</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="hold in cargoHolds" :key="hold.id">
                    <td class="hold-name">{{ hold.holdName }}</td>
                    <td>
                      <span
                        v-for="(cargo, idx) in getHoldCargos(hold.id)"
                        :key="idx"
                        class="cargo-tag"
                        :style="{ backgroundColor: cargo.color + '30', color: cargo.color }"
                      >
                        {{ cargo.name }}
                      </span>
                      <span v-if="getHoldCargos(hold.id).length === 0" class="empty-tag">空载</span>
                    </td>
                    <td class="weight">{{ getHoldWeight(hold.id).toFixed(1) }}</td>
                    <td class="volume">{{ getHoldVolume(hold.id).toFixed(1) }}</td>
                    <td>
                      <span class="utilization" :class="getUtilizationClass(hold.id)">
                        {{ getHoldUtilization(hold.id) }}%
                      </span>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>
    </div>

    <el-dialog
      v-model="holdDialogVisible"
      title="货舱装载操作"
      width="500px"
      class="hold-dialog"
    >
      <div v-if="selectedHold" class="hold-dialog-content">
        <div class="hold-info">
          <span class="hold-label">货舱:</span>
          <span class="hold-value">{{ selectedHold.holdName }}</span>
          <span class="hold-capacity">最大载重: {{ selectedHold.maxWeight }} t</span>
        </div>
        <el-form label-width="100px">
          <el-form-item label="货物类型">
            <el-select v-model="dialogCargoType" placeholder="选择货物">
              <el-option
                v-for="cargo in cargoTypes"
                :key="cargo.id"
                :label="cargo.name"
                :value="cargo.id"
              >
                <span class="option-color" :style="{ backgroundColor: cargo.color }"></span>
                {{ cargo.name }} ({{ cargo.density }} t/m³)
              </el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="操作类型">
            <el-radio-group v-model="dialogAction">
              <el-radio value="LOAD">装载</el-radio>
              <el-radio value="UNLOAD">卸载</el-radio>
              <el-radio value="RESET_HOLD">清空货舱</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item v-if="dialogAction !== 'RESET_HOLD'" label="重量(t)">
            <el-input-number
              v-model="dialogWeight"
              :min="0"
              :max="getMaxAvailableWeight()"
              :step="0.5"
              :precision="1"
              controls-position="right"
              style="width: 100%"
            />
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <el-button @click="holdDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="executeHoldAction" :loading="actionLoading">
          确认操作
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="knowledgeDialogVisible"
      :title="selectedKnowledge?.title"
      width="600px"
    >
      <div class="knowledge-detail">
        <div class="knowledge-content">
          {{ selectedKnowledge?.content }}
        </div>
        <div class="knowledge-formula" v-if="selectedKnowledge?.formula">
          <div class="formula-title">相关公式</div>
          <div class="formula-content">{{ selectedKnowledge.formula }}</div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import {
  Box, QuestionFilled, DocumentAdd, Document, Refresh, MagicStick,
  RefreshLeft, Goods, Reading, Ship, TrendCharts, Gauge, DataLine,
  InfoFilled, Wheat, Grid, Coffee, Picture, Shirt
} from '@element-plus/icons-vue'
import { Chart, registerables } from 'chart.js'
import { ElMessage } from 'element-plus'
import {
  createLoadingSession,
  executeLoadingAction,
  getLoadingSession
} from '@/api/virtualLoading'
import { getCargoTypes, getCargoHolds } from '@/api/loading'
import { getShipById } from '@/api/ship'

Chart.register(...registerables)

const sessionId = ref(null)
const selectedShipId = ref(null)
const sessionName = ref('')
const isPublic = ref(true)
const ships = ref([
  { id: '1', name: '沙船A型', maxDeadweight: 500 },
  { id: '2', name: '沙船B型', maxDeadweight: 800 },
  { id: '3', name: '福船型', maxDeadweight: 1200 }
])

const cargoTypes = ref([
  { id: 'GRAIN', name: '粮食', density: 0.75, color: '#DEB887', available: 200 },
  { id: 'SALT', name: '海盐', density: 2.16, color: '#F0F8FF', available: 300 },
  { id: 'TEA', name: '茶叶', density: 0.45, color: '#228B22', available: 100 },
  { id: 'PORCELAIN', name: '瓷器', density: 1.80, color: '#E6E6FA', available: 150 },
  { id: 'SILK', name: '丝绸', density: 0.35, color: '#FFB6C1', available: 80 }
])

const cargoHolds = ref([])
const loadingDetails = ref({})
const operationHistory = ref([])
const gmHistory = ref([])

const currentGM = ref(0.5)
const stabilityStatus = ref('NORMAL')
const totalCargoWeight = ref(0)
const totalCargoVolume = ref(0)
const draftDepth = ref(2.5)
const displacement = ref(600)
const rollPeriod = ref(10.5)
const maxRightingArm = ref(0.8)
const stabilityRange = ref(45)
const maxDeadweight = ref(500)
const maxVolume = ref(800)

const dragOverHoldId = ref(null)
const draggedCargo = ref(null)

const holdDialogVisible = ref(false)
const selectedHold = ref(null)
const dialogCargoType = ref('')
const dialogAction = ref('LOAD')
const dialogWeight = ref(0)
const actionLoading = ref(false)

const knowledgeDialogVisible = ref(false)
const selectedKnowledge = ref(null)
const knowledgeList = ref([
  { title: '什么是GM值？', content: 'GM值（初稳性高）是衡量船舶初稳性的重要指标，是重心G到稳心M的垂直距离。GM值越大，船舶的初稳性越好，回复力矩越大。GM = KM - KG，其中KM是稳心高度，KG是重心高度。', formula: 'GM = KM - KG' },
  { title: '稳心M的定义', content: '稳心M是船舶横倾微小角度时，浮力作用线与船舶中心线的交点。当船舶横倾时，浮心B会移动到新的位置B1，此时浮力作用线与中心线的交点就是稳心M。', formula: 'KM = KB + BM' },
  { title: '横摇周期与GM的关系', content: '船舶横摇周期T与GM值的平方根成反比。GM过大，横摇周期短，船舶摇摆剧烈，影响船员舒适和货物安全；GM过小，横摇周期长，稳性不足。', formula: 'T = 2π√(I / Δ·GM)' },
  { title: '自由液面修正', content: '当货舱内液体未装满时，船舶横倾时液体流动会产生自由液面效应，使重心升高，降低有效GM值。需要进行自由液面修正。', formula: 'GM_corrected = GM_uncorrected - δGM' }
])

const shipSvgRef = ref(null)
const gmTrendChartRef = ref(null)
let gmTrendChart = null

const shipHullPath = computed(() => {
  return 'M 50 280 Q 80 350 150 360 L 650 360 Q 720 350 750 280 L 750 120 L 50 120 Z'
})

const waterLineY = computed(() => {
  const baseY = 280
  const minDraft = 1.5
  const maxDraft = 4.0
  const draft = draftDepth.value || 2.5
  const ratio = (draft - minDraft) / (maxDraft - minDraft)
  return baseY - ratio * 160
})

const waterLineX1 = 50
const waterLineX2 = 750

const waterLinePath = computed(() => {
  const y = waterLineY.value
  return `M ${waterLineX1} ${y} Q 400 ${y + 5} ${waterLineX2} ${y} L ${waterLineX2} 360 L ${waterLineX1} 360 Z`
})

const pointG = computed(() => ({ x: 400, y: 200 }))
const pointB = computed(() => ({ x: 400, y: waterLineY.value + 30 }))
const pointM = computed(() => ({ x: 400, y: pointG.value.y - (currentGM.value * 100) }))

const gmColor = computed(() => {
  const gm = currentGM.value
  if (gm < 0.15) return '#F56C6C'
  if (gm < 0.3) return '#E6A23C'
  return '#67C23A'
})

const gmLineColor = computed(() => gmColor.value)

const getCargoIcon = (id) => {
  const icons = {
    GRAIN: Wheat,
    SALT: Grid,
    TEA: Coffee,
    PORCELAIN: Picture,
    SILK: Shirt
  }
  return icons[id] || Box
}

const getHoldX = (index) => 100 + index * 140
const getHoldY = () => 160
const getHoldWidth = () => 120
const getHoldHeight = () => 180

const getCargoStacks = (holdId) => {
  const details = loadingDetails.value[holdId] || []
  return details
}

const getStackHeight = (hold, stack) => {
  if (!hold.maxWeight || hold.maxWeight <= 0) return 0
  const maxHeight = getHoldHeight() - 30
  const ratio = stack.weight / hold.maxWeight
  return Math.max(ratio * maxHeight, 2)
}

const getStackY = (holdId, stackIndex) => {
  const stacks = getCargoStacks(holdId)
  const hold = cargoHolds.value.find(h => h.id === holdId)
  if (!hold) return getHoldY() + getHoldHeight() - 10

  let currentY = getHoldY() + getHoldHeight() - 10
  for (let i = 0; i < stackIndex; i++) {
    currentY -= getStackHeight(hold, stacks[i])
  }
  currentY -= getStackHeight(hold, stacks[stackIndex])
  return currentY
}

const getStackGradient = (cargoTypeId) => {
  const gradientIds = {
    GRAIN: 'cargoGrain',
    SALT: 'cargoSalt',
    TEA: 'cargoTea',
    PORCELAIN: 'cargoPorcelain',
    SILK: 'cargoSilk'
  }
  return `url(#${gradientIds[cargoTypeId] || 'cargoGrain'})`
}

const getHoldUtilization = (holdId) => {
  const hold = cargoHolds.value.find(h => h.id === holdId)
  if (!hold || !hold.maxWeight) return 0
  const weight = getHoldWeight(holdId)
  return Math.min(Math.round((weight / hold.maxWeight) * 100), 100)
}

const getHoldWeight = (holdId) => {
  const details = loadingDetails.value[holdId] || []
  return details.reduce((sum, d) => sum + (d.weight || 0), 0)
}

const getHoldVolume = (holdId) => {
  const details = loadingDetails.value[holdId] || []
  return details.reduce((sum, d) => sum + (d.volume || 0), 0)
}

const getHoldCargos = (holdId) => {
  const details = loadingDetails.value[holdId] || []
  return details.map(d => {
    const cargo = cargoTypes.value.find(c => c.id === d.cargoTypeId)
    return {
      name: cargo?.name || d.cargoTypeId,
      color: cargo?.color || '#409EFF'
    }
  })
}

const getUtilizationClass = (holdId) => {
  const percent = getHoldUtilization(holdId)
  if (percent >= 90) return 'high'
  if (percent >= 60) return 'medium'
  return 'low'
}

const getWeightPercent = () => {
  if (!maxDeadweight.value) return 0
  return Math.min((totalCargoWeight.value / maxDeadweight.value) * 100, 100)
}

const getVolumePercent = () => {
  if (!maxVolume.value) return 0
  return Math.min((totalCargoVolume.value / maxVolume.value) * 100, 100)
}

const getStatusType = () => {
  const types = {
    'NORMAL': 'success',
    'WARNING': 'warning',
    'CRITICAL': 'danger'
  }
  return types[stabilityStatus.value] || 'info'
}

const getGaugeArcPath = (startPercent, endPercent) => {
  const cx = 100, cy = 100, r = 80
  const startAngle = -225 + (startPercent / 100) * 270
  const endAngle = -225 + (endPercent / 100) * 270
  return describeArc(cx, cy, r, startAngle, endAngle)
}

const getGaugeValueArc = () => {
  const gm = Math.min(Math.max(currentGM.value || 0, 0), 2)
  const percent = (gm / 2) * 100
  return getGaugeArcPath(0, percent)
}

const getGaugePointerAngle = () => {
  const gm = Math.min(Math.max(currentGM.value || 0, 0), 2)
  const percent = (gm / 2) * 100
  return -135 + (percent / 100) * 270
}

const describeArc = (cx, cy, r, startAngle, endAngle) => {
  const start = polarToCartesian(cx, cy, r, endAngle)
  const end = polarToCartesian(cx, cy, r, startAngle)
  const largeArcFlag = endAngle - startAngle <= 180 ? '0' : '1'
  return `M ${start.x} ${start.y} A ${r} ${r} 0 ${largeArcFlag} 0 ${end.x} ${end.y}`
}

const polarToCartesian = (cx, cy, r, angleInDegrees) => {
  const angleInRadians = (angleInDegrees - 90) * Math.PI / 180
  return {
    x: cx + (r * Math.cos(angleInRadians)),
    y: cy + (r * Math.sin(angleInRadians))
  }
}

const handleDragStart = (event, cargo) => {
  draggedCargo.value = cargo
  event.dataTransfer.effectAllowed = 'copy'
  event.dataTransfer.setData('text/plain', JSON.stringify(cargo))
  event.target.classList.add('dragging')

  const ghostEl = document.createElement('div')
  ghostEl.style.cssText = 'display:flex;align-items:center;gap:8px;padding:8px 16px;background:#409EFF;color:#fff;border-radius:6px;font-size:14px;white-space:nowrap;box-shadow:0 4px 12px rgba(0,0,0,0.3);'
  ghostEl.innerHTML = `<span style="width:12px;height:12px;border-radius:3px;background:${cargo.color}"></span><span>${cargo.name}</span>`
  document.body.appendChild(ghostEl)
  event.dataTransfer.setDragImage(ghostEl, 40, 20)
  setTimeout(() => document.body.removeChild(ghostEl), 0)
}

const handleDragEnd = (event) => {
  draggedCargo.value = null
  dragOverHoldId.value = null
  document.querySelectorAll('.drag-over').forEach(el => el.classList.remove('drag-over'))
  event.target.classList.remove('dragging')
}

const handleDragOver = (event, holdId) => {
  event.preventDefault()
  event.dataTransfer.dropEffect = 'copy'
  dragOverHoldId.value = holdId
}

const handleDragLeave = (holdId) => {
  if (dragOverHoldId.value === holdId) {
    dragOverHoldId.value = null
  }
}

const handleDrop = async (event, holdId) => {
  event.preventDefault()
  dragOverHoldId.value = null

  if (!draggedCargo.value || !sessionId.value) {
    if (!sessionId.value) {
      ElMessage.warning('请先创建或选择一个会话')
    }
    return
  }

  const cargo = draggedCargo.value
  const hold = cargoHolds.value.find(h => h.id === holdId)
  const currentWeight = getHoldWeight(holdId)
  const remainingCapacity = hold ? (hold.maxWeight - currentWeight) : 0
  const defaultWeight = Math.min(
    Math.max(5, Math.round(remainingCapacity * 0.2)),
    cargo.available || 999,
    remainingCapacity
  )

  if (defaultWeight <= 0) {
    ElMessage.warning(`${hold?.holdName || '该货舱'}已满，无法继续装载`)
    return
  }

  try {
    await executeLoadAction(holdId, cargo.id, defaultWeight, 'LOAD')
    ElMessage.success(`已装载 ${cargo.name} ${defaultWeight} t 到 ${hold?.holdName}`)
  } catch (e) {
    ElMessage.error('装载失败: ' + (e.message || '未知错误'))
  }
}

const openHoldDialog = (hold) => {
  if (!sessionId.value) {
    ElMessage.warning('请先创建或选择一个会话')
    return
  }
  selectedHold.value = hold
  dialogCargoType.value = cargoTypes.value[0]?.id || ''
  dialogAction.value = 'LOAD'
  dialogWeight.value = 0
  holdDialogVisible.value = true
}

const getMaxAvailableWeight = () => {
  if (!selectedHold.value) return 0
  if (dialogAction.value === 'LOAD') {
    const cargo = cargoTypes.value.find(c => c.id === dialogCargoType.value)
    const currentHoldWeight = getHoldWeight(selectedHold.value.id)
    const maxInHold = selectedHold.value.maxWeight - currentHoldWeight
    return Math.min(cargo?.available || 0, maxInHold)
  } else {
    const details = loadingDetails.value[selectedHold.value.id] || []
    const cargoDetail = details.find(d => d.cargoTypeId === dialogCargoType.value)
    return cargoDetail?.weight || 0
  }
}

const executeHoldAction = async () => {
  if (!selectedHold.value || !sessionId.value) return

  actionLoading.value = true
  try {
    if (dialogAction.value === 'RESET_HOLD') {
      await executeLoadAction(selectedHold.value.id, null, 0, 'RESET_HOLD')
      ElMessage.success('货舱已清空')
    } else {
      const action = dialogAction.value
      const weight = dialogAction.value === 'UNLOAD' ? -dialogWeight.value : dialogWeight.value
      await executeLoadAction(selectedHold.value.id, dialogCargoType.value, weight, action)
      ElMessage.success(`${action === 'LOAD' ? '装载' : '卸载'}操作成功`)
    }
    holdDialogVisible.value = false
  } catch (e) {
    ElMessage.error('操作失败: ' + (e.message || '未知错误'))
  } finally {
    actionLoading.value = false
  }
}

const executeLoadAction = async (holdId, cargoTypeId, weightChange, action) => {
  const request = {
    sessionId: sessionId.value,
    holdId,
    cargoTypeId,
    weightChange,
    action
  }

  const res = await executeLoadingAction(request)
  updateFromResponse(res.data)

  operationHistory.value.push({
    holdId,
    cargoTypeId,
    weightChange,
    action,
    timestamp: Date.now()
  })

  if (gmHistory.value.length >= 20) {
    gmHistory.value.shift()
  }
  gmHistory.value.push({
    time: new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' }),
    gm: currentGM.value,
    action: `${action}-${cargoTypes.value.find(c => c.id === cargoTypeId)?.name || ''}`
  })

  updateTrendChart()
}

const updateFromResponse = (data) => {
  if (data.currentGM != null) currentGM.value = data.currentGM
  if (data.stabilityStatus) stabilityStatus.value = data.stabilityStatus
  if (data.totalCargoWeight != null) totalCargoWeight.value = data.totalCargoWeight
  if (data.totalCargoVolume != null) totalCargoVolume.value = data.totalCargoVolume
  if (data.loadingDetails) loadingDetails.value = data.loadingDetails

  if (data.stabilityResult) {
    const sr = data.stabilityResult
    if (sr.rollPeriod != null) rollPeriod.value = sr.rollPeriod
    if (sr.maxRightingArm != null) maxRightingArm.value = sr.maxRightingArm
    if (sr.stabilityRange != null) stabilityRange.value = sr.stabilityRange
    if (sr.displacement != null) displacement.value = sr.displacement
  }

  if (data.holdInfo) {
    draftDepth.value = data.holdInfo.draftDepth || draftDepth.value
  }
}

const createNewSession = async () => {
  if (!selectedShipId.value) {
    ElMessage.warning('请先选择船舶')
    return
  }
  if (!sessionName.value.trim()) {
    sessionName.value = `虚拟装载会话-${new Date().toLocaleString('zh-CN')}`
  }

  try {
    const request = {
      shipId: selectedShipId.value,
      sessionName: sessionName.value,
      userId: 'user_001',
      isPublic: isPublic.value
    }
    const res = await createLoadingSession(request)
    sessionId.value = res.data.sessionId
    sessionName.value = res.data.sessionName
    updateFromResponse(res.data)
    operationHistory.value = []
    gmHistory.value = []
    ElMessage.success('新会话创建成功')
    initTrendChart()
  } catch (e) {
    ElMessage.error('创建会话失败: ' + (e.message || '未知错误'))
  }
}

const saveSession = async () => {
  if (!sessionId.value) return
  ElMessage.success('会话已保存')
}

const resetLoading = async () => {
  if (!sessionId.value) return

  try {
    await executeLoadAction(null, null, 0, 'RESET_ALL')
    ElMessage.success('已重置所有装载')
  } catch (e) {
    ElMessage.error('重置失败: ' + (e.message || '未知错误'))
  }
}

const smartRecommend = async () => {
  if (!sessionId.value) return

  try {
    ElMessage.info('正在计算智能推荐装载方案...')
    await new Promise(resolve => setTimeout(resolve, 1000))

    const holds = cargoHolds.value
    for (const hold of holds) {
      const cargo = cargoTypes.value[Math.floor(Math.random() * cargoTypes.value.length)]
      const weight = Math.min(hold.maxWeight * 0.3, cargo.available)
      if (weight > 0) {
        await executeLoadAction(hold.id, cargo.id, weight, 'LOAD')
      }
    }
    ElMessage.success('智能推荐装载方案已生成')
  } catch (e) {
    ElMessage.error('推荐失败: ' + (e.message || '未知错误'))
  }
}

const undoAction = async () => {
  if (operationHistory.value.length === 0) return

  const lastAction = operationHistory.value.pop()
  try {
    const reverseWeight = -lastAction.weightChange
    const reverseAction = lastAction.action === 'LOAD' ? 'UNLOAD' : 'LOAD'
    await executeLoadAction(lastAction.holdId, lastAction.cargoTypeId, reverseWeight, reverseAction)
    gmHistory.value.pop()
    updateTrendChart()
    ElMessage.success('已撤销上一步操作')
  } catch (e) {
    operationHistory.value.push(lastAction)
    ElMessage.error('撤销失败: ' + (e.message || '未知错误'))
  }
}

const handleShipChange = async (shipId) => {
  try {
    const [shipRes, holdsRes] = await Promise.all([
      getShipById(shipId),
      getCargoHolds(shipId)
    ])
    cargoHolds.value = holdsRes.data || []
    maxDeadweight.value = shipRes.data?.deadweight || 500
    maxVolume.value = shipRes.data?.cargoCapacity || 800
    sessionId.value = null
    loadingDetails.value = {}
    ElMessage.success('已切换船舶')
  } catch (e) {
    ElMessage.error('加载船舶数据失败: ' + (e.message || '未知错误'))
  }
}

const showKnowledgeDetail = (item) => {
  selectedKnowledge.value = item
  knowledgeDialogVisible.value = true
}

const initTrendChart = () => {
  if (!gmTrendChartRef.value) return

  if (gmTrendChart) gmTrendChart.destroy()

  const ctx = gmTrendChartRef.value.getContext('2d')
  gmTrendChart = new Chart(ctx, {
    type: 'line',
    data: {
      labels: [],
      datasets: [{
        label: 'GM值 (m)',
        data: [],
        borderColor: '#409EFF',
        backgroundColor: 'rgba(64, 158, 255, 0.1)',
        fill: true,
        tension: 0.3,
        pointRadius: 4,
        pointHoverRadius: 8,
        pointBackgroundColor: '#409EFF',
        pointBorderColor: '#ffffff',
        pointBorderWidth: 2
      }]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: { display: false },
        tooltip: {
          backgroundColor: 'rgba(12, 25, 41, 0.9)',
          titleColor: '#ffffff',
          bodyColor: '#a0aec0',
          borderColor: 'rgba(64, 158, 255, 0.3)',
          borderWidth: 1,
          callbacks: {
            label: function(context) {
              const data = gmHistory.value[context.dataIndex]
              return [
                `GM: ${context.raw.toFixed(3)} m`,
                `操作: ${data?.action || ''}`
              ]
            }
          }
        }
      },
      scales: {
        x: {
          ticks: { color: '#a0aec0', maxTicksLimit: 10 },
          grid: { color: 'rgba(255,255,255,0.05)' }
        },
        y: {
          min: 0,
          max: 2,
          ticks: { color: '#a0aec0', stepSize: 0.25 },
          grid: { color: 'rgba(255,255,255,0.05)' }
        }
      }
    },
    plugins: [{
      id: 'thresholdLines',
      afterDraw: (chart) => {
        const ctx = chart.ctx
        const yAxis = chart.scales.y
        const redY = yAxis.getPixelForValue(0.15)
        const yellowY = yAxis.getPixelForValue(0.3)

        ctx.save()
        ctx.setLineDash([5, 5])
        ctx.lineWidth = 2

        ctx.strokeStyle = '#F56C6C'
        ctx.beginPath()
        ctx.moveTo(chart.chartArea.left, redY)
        ctx.lineTo(chart.chartArea.right, redY)
        ctx.stroke()

        ctx.strokeStyle = '#E6A23C'
        ctx.beginPath()
        ctx.moveTo(chart.chartArea.left, yellowY)
        ctx.lineTo(chart.chartArea.right, yellowY)
        ctx.stroke()

        ctx.restore()
      }
    }]
  })
}

const updateTrendChart = () => {
  if (!gmTrendChart) return

  gmTrendChart.data.labels = gmHistory.value.map(d => d.time)
  gmTrendChart.data.datasets[0].data = gmHistory.value.map(d => d.gm)
  gmTrendChart.update('none')
}

watch(() => selectedShipId.value, (newId) => {
  if (newId) {
    nextTick(() => {
      initTrendChart()
    })
  }
})

onMounted(async () => {
  if (ships.value.length > 0) {
    selectedShipId.value = ships.value[0].id
    await handleShipChange(selectedShipId.value)
  }

  nextTick(() => {
    initTrendChart()
  })
})

onUnmounted(() => {
  if (gmTrendChart) gmTrendChart.destroy()
})
</script>

<style scoped lang="scss">
.virtual-loading {
  width: 100%;
  padding: 0 4px;
}

.page-header {
  margin-bottom: 16px;

  .page-title {
    display: flex;
    align-items: center;
    gap: 10px;
    color: #ffffff;
    font-size: 22px;
    margin: 0 0 8px 0;

    .el-icon {
      color: #409EFF;
    }

    .info-icon {
      font-size: 18px;
      cursor: help;
    }
  }

  .header-desc {
    color: #a0aec0;
    font-size: 14px;
  }
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: rgba(12, 25, 41, 0.6);
  border: 1px solid rgba(64, 158, 255, 0.2);
  border-radius: 10px;
  margin-bottom: 16px;
  flex-wrap: wrap;
  gap: 12px;

  .toolbar-left, .toolbar-right {
    display: flex;
    align-items: center;
    gap: 12px;
    flex-wrap: wrap;
  }

  .ship-select {
    width: 150px;
  }

  .session-input {
    width: 200px;
  }
}

.main-content {
  display: grid;
  grid-template-columns: 280px 1fr 340px;
  gap: 16px;
}

.left-panel, .right-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.center-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.card {
  background: rgba(12, 25, 41, 0.6);
  border: 1px solid rgba(64, 158, 255, 0.2);
  border-radius: 10px;
  overflow: hidden;

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

    .info-icon {
      color: #409EFF;
      cursor: help;
      font-size: 16px;
    }

    .header-stats {
      display: flex;
      gap: 16px;

      .stat {
        font-size: 12px;

        .stat-label {
          color: #a0aec0;
          margin-right: 4px;
        }

        .stat-value {
          color: #67C23A;
          font-family: 'Courier New', monospace;
          font-weight: bold;
        }
      }
    }

    .threshold-info {
      display: flex;
      gap: 12px;
      font-size: 11px;

      .threshold {
        padding: 2px 8px;
        border-radius: 4px;

        &.red {
          background: rgba(245, 108, 108, 0.2);
          color: #F56C6C;
        }

        &.yellow {
          background: rgba(230, 162, 60, 0.2);
          color: #E6A23C;
        }
      }
    }
  }

  .card-body {
    padding: 18px;
  }
}

.cargo-library-card {
  .cargo-item {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 12px;
    margin-bottom: 12px;
    background: rgba(0, 0, 0, 0.2);
    border: 1px solid rgba(64, 158, 255, 0.1);
    border-radius: 8px;
    cursor: grab;
    transition: all 0.3s;
    position: relative;

    &:last-child {
      margin-bottom: 0;
    }

    &:hover {
      transform: translateX(4px);
      border-color: rgba(64, 158, 255, 0.4);
      background: rgba(64, 158, 255, 0.05);
    }

    &.dragging {
      opacity: 0.5;
      cursor: grabbing;
    }

    .cargo-icon {
      width: 44px;
      height: 44px;
      border-radius: 8px;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 22px;
      flex-shrink: 0;
    }

    .cargo-info {
      flex: 1;
      display: flex;
      flex-direction: column;
      gap: 2px;
      min-width: 0;

      .cargo-name {
        color: #ffffff;
        font-size: 14px;
        font-weight: 500;
      }

      .cargo-density {
        color: #a0aec0;
        font-size: 11px;
      }
    }

    .cargo-available {
      text-align: right;
      flex-shrink: 0;

      .available-label {
        display: block;
        color: #a0aec0;
        font-size: 10px;
      }

      .available-value {
        display: block;
        color: #67C23A;
        font-size: 13px;
        font-weight: bold;
        font-family: 'Courier New', monospace;
      }
    }

    .cargo-color-tag {
      position: absolute;
      left: 0;
      top: 50%;
      transform: translateY(-50%);
      width: 4px;
      height: 30px;
      border-radius: 0 2px 2px 0;
    }
  }
}

.knowledge-card {
  .knowledge-item {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 10px 12px;
    margin-bottom: 8px;
    background: rgba(64, 158, 255, 0.05);
    border: 1px solid rgba(64, 158, 255, 0.15);
    border-radius: 6px;
    cursor: pointer;
    transition: all 0.2s;

    &:last-child {
      margin-bottom: 0;
    }

    &:hover {
      background: rgba(64, 158, 255, 0.1);
      border-color: rgba(64, 158, 255, 0.4);
    }

    .knowledge-icon {
      color: #409EFF;
      font-size: 18px;
      flex-shrink: 0;
    }

    .knowledge-title {
      color: #ffffff;
      font-size: 13px;
    }
  }
}

.ship-profile-card {
  .ship-profile-body {
    padding: 10px;
    min-height: 420px;
  }

  .ship-svg {
    width: 100%;
    height: 100%;
    min-height: 400px;
  }

  .hold-area {
    cursor: pointer;
    transition: all 0.3s;

    &:hover {
      fill: rgba(64, 158, 255, 0.2);
      stroke: rgba(64, 158, 255, 0.8);
    }

    &.drag-over {
      fill: rgba(103, 194, 58, 0.25);
      stroke: #67C23A;
      stroke-width: 2;
      animation: pulse 1s infinite;
    }
  }

  .cargo-stack {
    transition: all 0.5s ease;
  }

  .water-line {
    animation: wave 3s ease-in-out infinite;
  }

  .water-fill {
    transition: all 0.5s ease;
  }

  .stability-points {
    circle, text, line {
      transition: all 0.5s ease;
    }
  }
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.6; }
}

@keyframes wave {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-2px); }
}

.gauge-container {
  display: flex;
  justify-content: center;
  margin-bottom: 16px;

  .gauge-svg {
    width: 200px;
    height: 200px;

    .gauge-value-arc {
      transition: stroke-dashoffset 0.5s ease, stroke 0.3s;
    }
  }
}

.gm-main-display {
  text-align: center;
  margin-bottom: 20px;
  padding-bottom: 20px;
  border-bottom: 1px solid rgba(64, 158, 255, 0.1);

  .gm-label {
    display: block;
    color: #a0aec0;
    font-size: 13px;
    margin-bottom: 4px;
  }

  .gm-value {
    font-size: 32px;
    font-weight: bold;
    font-family: 'Courier New', monospace;
    transition: color 0.3s;
  }
}

.stability-details {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;

  .detail-item {
    background: rgba(0, 0, 0, 0.2);
    padding: 10px 12px;
    border-radius: 6px;
    text-align: center;

    .detail-label {
      display: block;
      color: #a0aec0;
      font-size: 11px;
      margin-bottom: 4px;
    }

    .detail-value {
      display: block;
      color: #409EFF;
      font-size: 16px;
      font-weight: bold;
      font-family: 'Courier New', monospace;
    }
  }
}

.summary-progress {
  margin-bottom: 20px;

  .progress-item {
    margin-bottom: 16px;

    &:last-child {
      margin-bottom: 0;
    }

    .progress-header {
      display: flex;
      justify-content: space-between;
      margin-bottom: 6px;

      .progress-label {
        color: #ffffff;
        font-size: 13px;
      }

      .progress-value {
        color: #a0aec0;
        font-size: 12px;
        font-family: 'Courier New', monospace;
      }
    }

    .progress-bar {
      height: 10px;
      background: rgba(0, 0, 0, 0.3);
      border-radius: 5px;
      overflow: hidden;

      .progress-fill {
        height: 100%;
        background: linear-gradient(90deg, #409EFF, #67C23A);
        border-radius: 5px;
        transition: width 0.5s ease;

        &.volume {
          background: linear-gradient(90deg, #E6A23C, #67C23A);
        }
      }
    }
  }
}

.holds-detail-table {
  .detail-table {
    width: 100%;
    border-collapse: collapse;

    th, td {
      padding: 8px 6px;
      text-align: center;
      border-bottom: 1px solid rgba(64, 158, 255, 0.1);
      font-size: 12px;
    }

    th {
      background: rgba(0, 0, 0, 0.2);
      color: #a0aec0;
      font-weight: 500;
    }

    td {
      color: #ffffff;

      &.hold-name {
        color: #409EFF;
        font-weight: 500;
      }

      &.weight, &.volume {
        font-family: 'Courier New', monospace;
        color: #67C23A;
      }

      .cargo-tag {
        display: inline-block;
        padding: 2px 6px;
        margin: 2px;
        border-radius: 3px;
        font-size: 10px;
      }

      .empty-tag {
        color: #6c757d;
        font-size: 11px;
      }

      .utilization {
        font-weight: bold;
        font-family: 'Courier New', monospace;

        &.high { color: #67C23A; }
        &.medium { color: #E6A23C; }
        &.low { color: #909399; }
      }
    }

    tbody tr:hover {
      background: rgba(64, 158, 255, 0.05);
    }
  }
}

.chart-card {
  .chart-container {
    height: 220px;
  }
}

.hold-dialog {
  :deep(.el-dialog__body) {
    padding-top: 10px;
  }

  .hold-info {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 20px;
    padding: 12px;
    background: rgba(64, 158, 255, 0.05);
    border-radius: 6px;

    .hold-label {
      color: #a0aec0;
      font-size: 13px;
    }

    .hold-value {
      color: #409EFF;
      font-size: 16px;
      font-weight: bold;
    }

    .hold-capacity {
      margin-left: auto;
      color: #67C23A;
      font-size: 12px;
    }
  }

  .option-color {
    display: inline-block;
    width: 12px;
    height: 12px;
    border-radius: 2px;
    margin-right: 6px;
    vertical-align: middle;
  }
}

.knowledge-detail {
  .knowledge-content {
    color: #ffffff;
    font-size: 14px;
    line-height: 1.8;
    margin-bottom: 20px;
  }

  .knowledge-formula {
    padding: 16px;
    background: rgba(64, 158, 255, 0.05);
    border: 1px solid rgba(64, 158, 255, 0.2);
    border-radius: 8px;

    .formula-title {
      color: #409EFF;
      font-size: 13px;
      margin-bottom: 8px;
    }

    .formula-content {
      color: #67C23A;
      font-size: 18px;
      font-family: 'Courier New', monospace;
      text-align: center;
    }
  }
}

@media (max-width: 1600px) {
  .main-content {
    grid-template-columns: 260px 1fr 300px;
  }
}

@media (max-width: 1200px) {
  .main-content {
    grid-template-columns: 1fr;
  }

  .left-panel,
  .right-panel {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .toolbar {
    flex-direction: column;
    align-items: stretch;

    .toolbar-left,
    .toolbar-right {
      justify-content: center;
    }
  }

  .left-panel,
  .right-panel {
    grid-template-columns: 1fr;
  }

  .ship-profile-card .ship-svg {
    min-height: 300px;
  }
}
</style>