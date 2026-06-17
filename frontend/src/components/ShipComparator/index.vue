<template>
  <div class="ship-comparator">
    <div class="config-section">
      <div class="card config-card">
        <div class="card-header">
          <h3 class="card-title">
            <el-icon><Setting /></el-icon>
            对比配置
          </h3>
        </div>
        <div class="card-body">
          <el-form :model="comparisonConfig" label-width="110px">
            <el-form-item label="对比名称">
              <el-input
                v-model="comparisonConfig.comparisonName"
                placeholder="请输入对比名称"
                maxlength="50"
                show-word-limit
                :disabled="disabled"
              />
            </el-form-item>

            <el-form-item label="选择船舶">
              <el-tree
                ref="shipTreeRef"
                :data="shipTreeData"
                show-checkbox
                node-key="id"
                :props="{ label: 'label', children: 'children' }"
                :default-expand-all="true"
                class="ship-tree"
                @check="handleShipCheck"
              >
                <template #default="{ node, data }">
                  <span class="tree-node">
                    <el-icon v-if="data.type === 'category'" class="category-icon">
                      <component :is="data.icon" />
                    </el-icon>
                    <el-icon v-else-if="data.type === 'family'" class="family-icon" :style="{ color: data.color }">
                      <Ship />
                    </el-icon>
                    <span>{{ data.label }}</span>
                    <el-tag
                      v-if="data.type === 'ship'"
                      :type="data.category === 'ANCIENT' ? 'warning' : 'info'"
                      size="small"
                      effect="dark"
                      class="ship-tag"
                    >
                      {{ data.category === 'ANCIENT' ? '古代' : '现代' }}
                    </el-tag>
                  </span>
                </template>
              </el-tree>
            </el-form-item>

            <el-form-item label="对比指标">
              <el-checkbox-group v-model="comparisonConfig.comparisonCriteria" :disabled="disabled">
                <el-row :gutter="8">
                  <el-col :span="12">
                    <el-checkbox value="GM" label="GM">初稳性高</el-checkbox>
                  </el-col>
                  <el-col :span="12">
                    <el-checkbox value="GZ_MAX" label="GZ_MAX">最大复原力臂</el-checkbox>
                  </el-col>
                  <el-col :span="12">
                    <el-checkbox value="RANGE" label="RANGE">稳性范围</el-checkbox>
                  </el-col>
                  <el-col :span="12">
                    <el-checkbox value="GZ_AREA" label="GZ_AREA">稳性曲线面积</el-checkbox>
                  </el-col>
                  <el-col :span="12">
                    <el-checkbox value="ROLL_PERIOD" label="ROLL_PERIOD">横摇周期</el-checkbox>
                  </el-col>
                  <el-col :span="12">
                    <el-checkbox value="DISPLACEMENT" label="DISPLACEMENT">排水量</el-checkbox>
                  </el-col>
                  <el-col :span="12">
                    <el-checkbox value="DEADWEIGHT" label="DEADWEIGHT">载重量</el-checkbox>
                  </el-col>
                  <el-col :span="12">
                    <el-checkbox value="BOW_HEIGHT" label="BOW_HEIGHT">艏高</el-checkbox>
                  </el-col>
                </el-row>
              </el-checkbox-group>
            </el-form-item>

            <el-form-item label="装载条件">
              <el-radio-group v-model="comparisonConfig.loadingCondition" size="default" :disabled="disabled">
                <el-radio-button value="BALLAST">压载</el-radio-button>
                <el-radio-button value="HALF_LOAD">半载</el-radio-button>
                <el-radio-button value="FULL_LOAD">满载</el-radio-button>
              </el-radio-group>
            </el-form-item>

            <el-form-item label="参考波高">
              <el-input-number
                v-model="comparisonConfig.referenceWaveHeight"
                :min="0.5"
                :max="10"
                :step="0.5"
                :precision="1"
                controls-position="right"
                :disabled="disabled"
              />
              <span class="unit">m</span>
            </el-form-item>

            <el-form-item>
              <el-button
                type="primary"
                size="large"
                @click="runComparison"
                :loading="comparing"
                :disabled="selectedShipIds.length < 2 || disabled"
                class="compare-btn"
              >
                <el-icon><Operation /></el-icon>
                开始对比
              </el-button>
              <el-button size="large" @click="resetConfig" :disabled="disabled">
                <el-icon><Refresh /></el-icon>
                重置
              </el-button>
            </el-form-item>
          </el-form>
        </div>
      </div>

      <div class="card legend-card">
        <div class="card-header">
          <h3 class="card-title">
            <el-icon><Collection /></el-icon>
            船型配色说明
          </h3>
        </div>
        <div class="card-body">
          <div class="legend-list">
            <div class="legend-item">
              <span class="legend-color" style="background: #409EFF"></span>
              <span class="legend-name">沙船</span>
              <span class="legend-desc">平底、方头、方艄</span>
            </div>
            <div class="legend-item">
              <span class="legend-color" style="background: #67C23A"></span>
              <span class="legend-name">福船</span>
              <span class="legend-desc">尖底、首尖尾宽</span>
            </div>
            <div class="legend-item">
              <span class="legend-color" style="background: #E6A23C"></span>
              <span class="legend-name">广船</span>
              <span class="legend-desc">尖底、体窄身长</span>
            </div>
            <div class="legend-item">
              <span class="legend-color" style="background: #909399"></span>
              <span class="legend-name">现代散货船</span>
              <span class="legend-desc">现代钢质船舶</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="result-section">
      <div v-if="!comparisonResult" class="empty-result">
        <el-icon class="empty-icon"><DataLine /></el-icon>
        <p class="empty-text">选择至少2艘船舶，配置参数后点击"开始对比"查看结果</p>
      </div>

      <div v-else class="result-content">
        <div class="ranking-section">
          <h3 class="section-title">
            <el-icon><Trophy /></el-icon>
            综合排名
          </h3>
          <div class="ranking-cards">
            <div
              v-for="(item, index) in sortedComparisonItems"
              :key="item.shipId"
              class="ranking-card"
              :class="'rank-' + item.rank"
            >
              <div class="rank-badge">
                <span v-if="item.rank === 1">🥇</span>
                <span v-else-if="item.rank === 2">🥈</span>
                <span v-else-if="item.rank === 3">🥉</span>
                <span v-else>{{ item.rank }}</span>
              </div>
              <div class="ship-info">
                <div class="ship-header">
                  <span class="ship-name" :style="{ color: getShipColor(item.shipType) }">
                    {{ item.shipName }}
                  </span>
                  <el-tag
                    :type="item.category === 'ANCIENT' ? 'warning' : 'info'"
                    size="small"
                    effect="dark"
                  >
                    {{ item.category === 'ANCIENT' ? '古代' : '现代' }}
                  </el-tag>
                </div>
                <div class="ship-type">{{ getShipTypeName(item.shipType) }}</div>
              </div>
              <div class="score-info">
                <div class="score-value">{{ item.score?.toFixed(1) || '--' }}</div>
                <div class="score-label">综合评分</div>
              </div>
            </div>
          </div>
        </div>

        <div class="card table-card">
          <div class="card-header">
            <h3 class="card-title">
              <el-icon><Histogram /></el-icon>
              对比详情
            </h3>
          </div>
          <div class="card-body">
            <el-table
              :data="sortedComparisonItems"
              :default-sort="{ prop: 'rank', order: 'ascending' }"
              class="comparison-table"
              stripe
            >
              <el-table-column prop="rank" label="排名" width="70" align="center" sortable>
                <template #default="{ row }">
                  <span v-if="row.rank === 1" class="rank-gold">🥇</span>
                  <span v-else-if="row.rank === 2" class="rank-silver">🥈</span>
                  <span v-else-if="row.rank === 3" class="rank-bronze">🥉</span>
                  <span v-else>{{ row.rank }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="shipName" label="船名" width="120">
                <template #default="{ row }">
                  <span :style="{ color: getShipColor(row.shipType) }">{{ row.shipName }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="shipType" label="船型" width="100">
                <template #default="{ row }">
                  <el-tag
                    :type="row.category === 'ANCIENT' ? 'warning' : 'info'"
                    size="small"
                    effect="dark"
                  >
                    {{ getShipTypeName(row.shipType) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column
                v-if="comparisonConfig.comparisonCriteria.includes('GM')"
                prop="metrics.GM"
                label="GM (m)"
                sortable
              >
                <template #default="{ row }">
                  <span :class="getGmClass(row.metrics?.GM)">
                    {{ row.metrics?.GM?.toFixed(4) || '--' }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column
                v-if="comparisonConfig.comparisonCriteria.includes('GZ_MAX')"
                prop="metrics.GZ_MAX"
                label="GZ_MAX (m)"
                sortable
              >
                <template #default="{ row }">
                  {{ row.metrics?.GZ_MAX?.toFixed(4) || '--' }}
                </template>
              </el-table-column>
              <el-table-column
                v-if="comparisonConfig.comparisonCriteria.includes('RANGE')"
                prop="metrics.RANGE"
                label="稳性范围 (°)"
                sortable
              >
                <template #default="{ row }">
                  {{ row.metrics?.RANGE?.toFixed(1) || '--' }}
                </template>
              </el-table-column>
              <el-table-column
                v-if="comparisonConfig.comparisonCriteria.includes('GZ_AREA')"
                prop="metrics.GZ_AREA"
                label="稳性面积 (m·°)"
                sortable
              >
                <template #default="{ row }">
                  {{ row.metrics?.GZ_AREA?.toFixed(2) || '--' }}
                </template>
              </el-table-column>
              <el-table-column
                v-if="comparisonConfig.comparisonCriteria.includes('ROLL_PERIOD')"
                prop="metrics.ROLL_PERIOD"
                label="横摇周期 (s)"
                sortable
              >
                <template #default="{ row }">
                  {{ row.metrics?.ROLL_PERIOD?.toFixed(2) || '--' }}
                </template>
              </el-table-column>
              <el-table-column
                v-if="comparisonConfig.comparisonCriteria.includes('DISPLACEMENT')"
                prop="metrics.DISPLACEMENT"
                label="排水量 (t)"
                sortable
              >
                <template #default="{ row }">
                  {{ row.metrics?.DISPLACEMENT?.toFixed(0) || '--' }}
                </template>
              </el-table-column>
              <el-table-column
                v-if="comparisonConfig.comparisonCriteria.includes('DEADWEIGHT')"
                prop="metrics.DEADWEIGHT"
                label="载重量 (t)"
                sortable
              >
                <template #default="{ row }">
                  {{ row.metrics?.DEADWEIGHT?.toFixed(0) || '--' }}
                </template>
              </el-table-column>
              <el-table-column
                v-if="comparisonConfig.comparisonCriteria.includes('BOW_HEIGHT')"
                prop="metrics.BOW_HEIGHT"
                label="艏高 (m)"
                sortable
              >
                <template #default="{ row }">
                  {{ row.metrics?.BOW_HEIGHT?.toFixed(2) || '--' }}
                </template>
              </el-table-column>
              <el-table-column prop="score" label="综合评分" width="100" sortable>
                <template #default="{ row }">
                  <el-tag type="primary" effect="dark" size="small">
                    {{ row.score?.toFixed(1) || '--' }}
                  </el-tag>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </div>

        <div class="charts-row">
          <div class="card chart-card">
            <div class="card-header">
              <h3 class="card-title">
                <el-icon><PieChart /></el-icon>
                核心指标雷达图
              </h3>
            </div>
            <div class="card-body">
              <div class="chart-container">
                <canvas ref="radarChartRef"></canvas>
              </div>
            </div>
          </div>

          <div class="card chart-card">
            <div class="card-header">
              <h3 class="card-title">
                <el-icon><BarChart /></el-icon>
                关键指标对比
              </h3>
            </div>
            <div class="card-body">
              <div class="chart-container">
                <canvas ref="barChartRef"></canvas>
              </div>
            </div>
          </div>
        </div>

        <div class="card chart-card">
          <div class="card-header">
            <h3 class="card-title">
              <el-icon><DataLine /></el-icon>
              GZ曲线对比
            </h3>
          </div>
          <div class="card-body">
            <div class="chart-container large">
              <canvas ref="gzChartRef"></canvas>
            </div>
          </div>
        </div>

        <div class="card analysis-card">
          <div class="card-header">
            <h3 class="card-title">
              <el-icon><ChatDotRound /></el-icon>
              排名分析说明
            </h3>
          </div>
          <div class="card-body">
            <div class="analysis-content" v-html="analysisText"></div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import {
  Setting, Operation, Refresh, Collection,
  DataLine, Trophy, Histogram, PieChart, BarChart,
  ChatDotRound, Ship, Compass, Crop, Van, Flag
} from '@element-plus/icons-vue'
import { Chart, registerables } from 'chart.js'
import { ElMessage } from 'element-plus'

Chart.register(...registerables)

const props = defineProps({
  ships: {
    type: Object,
    default: () => ({})
  },
  disabled: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['compare', 'update:loading'])

const shipTreeRef = ref(null)
const radarChartRef = ref(null)
const barChartRef = ref(null)
const gzChartRef = ref(null)
let radarChart = null
let barChart = null
let gzChart = null

const comparing = ref(false)
const comparisonResult = ref(null)
const availableShips = ref(props.ships || {})

const comparisonConfig = reactive({
  comparisonName: '',
  shipIds: [],
  comparisonCriteria: ['GM', 'GZ_MAX', 'RANGE', 'DEADWEIGHT', 'ROLL_PERIOD'],
  loadingCondition: 'FULL_LOAD',
  referenceWaveHeight: 3,
  createdBy: 'system'
})

watch(() => props.ships, (newVal) => {
  availableShips.value = newVal || {}
}, { deep: true })

watch(comparing, (newVal) => {
  emit('update:loading', newVal)
})

const shipTreeData = computed(() => {
  const result = []
  const categories = {
    ANCIENT: { label: '古代船舶', icon: 'Compass', type: 'category', children: [] },
    MODERN: { label: '现代船舶', icon: 'Van', type: 'category', children: [] }
  }

  for (const [category, families] of Object.entries(availableShips.value || {})) {
    for (const [family, ships] of Object.entries(families || {})) {
      const familyNode = {
        id: `${category}_${family}`,
        label: getFamilyName(family),
        type: 'family',
        color: getFamilyColor(family),
        children: []
      }

      for (const ship of ships || []) {
        familyNode.children.push({
          id: ship.id,
          label: ship.name,
          type: 'ship',
          category: category,
          shipType: ship.shipType,
          color: getFamilyColor(family)
        })
      }

      if (familyNode.children.length > 0) {
        categories[category].children.push(familyNode)
      }
    }
  }

  if (categories.ANCIENT.children.length > 0) {
    result.push(categories.ANCIENT)
  }
  if (categories.MODERN.children.length > 0) {
    result.push(categories.MODERN)
  }

  return result
})

const selectedShipIds = computed(() => {
  return comparisonConfig.shipIds
})

const sortedComparisonItems = computed(() => {
  if (!comparisonResult.value?.comparisonItems) return []
  return [...comparisonResult.value.comparisonItems].sort((a, b) => a.rank - b.rank)
})

const analysisText = computed(() => {
  const items = sortedComparisonItems.value
  if (items.length === 0) return ''

  let text = `<p>本次对比共选取 <strong>${items.length}</strong> 艘船舶，在 <strong>${getLoadingConditionText(comparisonConfig.loadingCondition)}</strong> 状态、参考波高 <strong>${comparisonConfig.referenceWaveHeight}m</strong> 条件下进行综合评估。</p>`

  const topShip = items[0]
  text += `<p class="highlight">综合排名第一的是 <strong style="color: ${getShipColor(topShip.shipType)}">${topShip.shipName}</strong>，综合评分为 <strong>${topShip.score?.toFixed(1)}</strong> 分。`

  const gmValues = items.map(i => ({ name: i.shipName, value: i.metrics?.GM, type: i.shipType })).filter(i => i.value != null)
  if (gmValues.length > 0) {
    const highestGm = gmValues.reduce((max, curr) => curr.value > max.value ? curr : max)
    const lowestGm = gmValues.reduce((min, curr) => curr.value < min.value ? curr : min)
    text += `${getShipTypeName(highestGm.type)}因设计特点GM最高（${highestGm.value?.toFixed(3)}m），${getShipTypeName(lowestGm.type)}GM相对较低（${lowestGm.value?.toFixed(3)}m）。`
  }

  const rollPeriodValues = items.map(i => ({ name: i.shipName, value: i.metrics?.ROLL_PERIOD, type: i.shipType })).filter(i => i.value != null)
  if (rollPeriodValues.length > 0) {
    const longestRoll = rollPeriodValues.reduce((max, curr) => curr.value > max.value ? curr : max)
    text += `横摇舒适性方面，${getShipTypeName(longestRoll.type)}横摇周期最长（${longestRoll.value?.toFixed(2)}s），航行体验更为平稳舒适。`
  }

  const deadweightValues = items.map(i => ({ name: i.shipName, value: i.metrics?.DEADWEIGHT, type: i.shipType })).filter(i => i.value != null)
  if (deadweightValues.length > 0) {
    const highestDwt = deadweightValues.reduce((max, curr) => curr.value > max.value ? curr : max)
    const ancientShips = items.filter(i => i.category === 'ANCIENT')
    const modernShips = items.filter(i => i.category === 'MODERN')
    if (ancientShips.length > 0 && modernShips.length > 0) {
      const avgAncientScore = ancientShips.reduce((sum, i) => sum + (i.score || 0), 0) / ancientShips.length
      const avgModernScore = modernShips.reduce((sum, i) => sum + (i.score || 0), 0) / modernShips.length
      text += `跨代对比显示，现代船舶在载货能力上优势明显（${getShipTypeName(highestDwt.type)}载重量达${highestDwt.value?.toFixed(0)}t），`
      text += `而古代船舶在稳性设计上展现了传统智慧，${avgAncientScore > avgModernScore ? '部分古代船型综合评分甚至超越现代船舶' : '现代船舶综合性能更优'}。`
    }
  }

  text += '</p>'

  if (items.length >= 2) {
    text += `<p class="conclusion">综合来看，各船型各有优劣：`
    const advantages = []
    for (const item of items) {
      const adv = getShipAdvantage(item)
      if (adv) advantages.push(`<strong style="color: ${getShipColor(item.shipType)}">${item.shipName}</strong>${adv}`)
    }
    text += advantages.join('；') + '。'
    text += `用户可根据实际航行需求（近海/远洋、货运/客运）选择最合适的船型。</p>`
  }

  return text
})

const getFamilyName = (family) => {
  const names = {
    SHACHUAN: '沙船家族',
    FUCHUAN: '福船家族',
    GUANGCHUAN: '广船家族',
    BULK_CARRIER: '散货船家族',
    CONTAINER: '集装箱船家族',
    TANKER: '油轮家族'
  }
  return names[family] || family
}

const getFamilyColor = (family) => {
  const colors = {
    SHACHUAN: '#409EFF',
    FUCHUAN: '#67C23A',
    GUANGCHUAN: '#E6A23C',
    BULK_CARRIER: '#909399',
    CONTAINER: '#9C27B0',
    TANKER: '#F56C6C'
  }
  return colors[family] || '#909399'
}

const getShipTypeName = (type) => {
  const names = {
    SHACHUAN: '沙船',
    FUCHUAN: '福船',
    GUANGCHUAN: '广船',
    BULK_CARRIER: '散货船',
    CONTAINER: '集装箱船',
    TANKER: '油轮'
  }
  return names[type] || type
}

const getShipColor = (type) => {
  return getFamilyColor(type)
}

const getGmClass = (gm) => {
  if (gm == null) return ''
  if (gm < 0.15) return 'gm-danger'
  if (gm < 0.3) return 'gm-warning'
  return 'gm-success'
}

const getLoadingConditionText = (condition) => {
  const texts = {
    BALLAST: '压载',
    HALF_LOAD: '半载',
    FULL_LOAD: '满载'
  }
  return texts[condition] || condition
}

const getShipAdvantage = (item) => {
  const metrics = item.metrics || {}
  const advantages = []

  if (metrics.GM >= 0.5) {
    advantages.push('稳性极佳')
  } else if (metrics.GM >= 0.3) {
    advantages.push('稳性良好')
  }

  if (metrics.ROLL_PERIOD >= 12) {
    advantages.push('舒适性好')
  }

  if (metrics.RANGE >= 60) {
    advantages.push('稳性范围大')
  }

  if (metrics.DEADWEIGHT >= 50000) {
    advantages.push('载货能力强')
  }

  if (advantages.length === 0) {
    return '综合性能均衡'
  }

  return '优势在于' + advantages.join('、')
}

const handleShipCheck = () => {
  const checkedNodes = shipTreeRef.value?.getCheckedNodes() || []
  comparisonConfig.shipIds = checkedNodes
    .filter(node => node.type === 'ship')
    .map(node => node.id)
}

const runComparison = () => {
  if (selectedShipIds.value.length < 2) {
    ElMessage.warning('请至少选择2艘船舶进行对比')
    return
  }

  if (comparisonConfig.comparisonCriteria.length === 0) {
    ElMessage.warning('请至少选择一项对比指标')
    return
  }

  comparing.value = true
  try {
    const request = {
      ...comparisonConfig,
      comparisonName: comparisonConfig.comparisonName || `对比_${new Date().toLocaleDateString('zh-CN')}`
    }
    emit('compare', request)
  } finally {
    comparing.value = false
  }
}

const resetConfig = () => {
  comparisonConfig.comparisonName = ''
  comparisonConfig.shipIds = []
  comparisonConfig.comparisonCriteria = ['GM', 'GZ_MAX', 'RANGE', 'DEADWEIGHT', 'ROLL_PERIOD']
  comparisonConfig.loadingCondition = 'FULL_LOAD'
  comparisonConfig.referenceWaveHeight = 3
  shipTreeRef.value?.setCheckedKeys([])
  comparisonResult.value = null
  destroyCharts()
}

const normalizeValues = (values) => {
  const min = Math.min(...values)
  const max = Math.max(...values)
  if (max === min) return values.map(() => 50)
  return values.map(v => ((v - min) / (max - min)) * 100)
}

const setResult = (result) => {
  comparisonResult.value = result
  nextTick(() => {
    initCharts()
  })
}

defineExpose({ setResult })

const initCharts = () => {
  initRadarChart()
  initBarChart()
  initGzChart()
}

const initRadarChart = () => {
  if (!radarChartRef.value || !comparisonResult.value) return

  if (radarChart) radarChart.destroy()

  const items = sortedComparisonItems.value
  const radarMetrics = ['GM', 'GZ_MAX', 'RANGE', 'DEADWEIGHT', 'ROLL_PERIOD']
  const labels = ['初稳性高', '最大复原力臂', '稳性范围', '载重量', '横摇周期']

  const datasets = items.map(item => {
    const values = radarMetrics.map(m => item.metrics?.[m] || 0)
    const normalizedValues = normalizeValues(values)

    return {
      label: item.shipName,
      data: normalizedValues,
      borderColor: getShipColor(item.shipType),
      backgroundColor: getShipColor(item.shipType) + '33',
      borderWidth: 2,
      pointBackgroundColor: getShipColor(item.shipType),
      pointBorderColor: '#fff',
      pointHoverRadius: 6
    }
  })

  const ctx = radarChartRef.value.getContext('2d')
  radarChart = new Chart(ctx, {
    type: 'radar',
    data: {
      labels,
      datasets
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: {
          position: 'bottom',
          labels: { color: '#a0aec0', padding: 20, usePointStyle: true }
        },
        tooltip: {
          backgroundColor: 'rgba(12, 25, 41, 0.95)',
          titleColor: '#ffffff',
          bodyColor: '#a0aec0',
          borderColor: 'rgba(64, 158, 255, 0.3)',
          borderWidth: 1,
          padding: 12,
          callbacks: {
            label: (context) => {
              const item = items[context.datasetIndex]
              const metric = radarMetrics[context.dataIndex]
              const rawValue = item.metrics?.[metric]
              const unit = getMetricUnit(metric)
              return `${item.shipName}: ${rawValue?.toFixed(2)} ${unit} (${context.raw?.toFixed(1)}分)`
            }
          }
        }
      },
      scales: {
        r: {
          min: 0,
          max: 100,
          beginAtZero: true,
          ticks: {
            color: '#a0aec0',
            backdropColor: 'transparent',
            stepSize: 20
          },
          grid: {
            color: 'rgba(255,255,255,0.1)'
          },
          angleLines: {
            color: 'rgba(255,255,255,0.1)'
          },
          pointLabels: {
            color: '#ffffff',
            font: { size: 12 }
          }
        }
      }
    }
  })
}

const getMetricUnit = (metric) => {
  const units = {
    GM: 'm',
    GZ_MAX: 'm',
    RANGE: '°',
    GZ_AREA: 'm·°',
    ROLL_PERIOD: 's',
    DISPLACEMENT: 't',
    DEADWEIGHT: 't',
    BOW_HEIGHT: 'm'
  }
  return units[metric] || ''
}

const initBarChart = () => {
  if (!barChartRef.value || !comparisonResult.value) return

  if (barChart) barChart.destroy()

  const items = sortedComparisonItems.value
  const labels = items.map(i => i.shipName)

  const datasets = [
    {
      label: 'GM (m)',
      data: items.map(i => i.metrics?.GM || 0),
      backgroundColor: 'rgba(64, 158, 255, 0.8)',
      borderColor: '#409EFF',
      borderWidth: 1,
      yAxisID: 'y'
    },
    {
      label: 'GZ_MAX (m)',
      data: items.map(i => i.metrics?.GZ_MAX || 0),
      backgroundColor: 'rgba(103, 194, 58, 0.8)',
      borderColor: '#67C23A',
      borderWidth: 1,
      yAxisID: 'y'
    },
    {
      label: '稳性范围 (°)',
      data: items.map(i => i.metrics?.RANGE || 0),
      backgroundColor: 'rgba(230, 162, 60, 0.8)',
      borderColor: '#E6A23C',
      borderWidth: 1,
      yAxisID: 'y1'
    }
  ]

  const ctx = barChartRef.value.getContext('2d')
  barChart = new Chart(ctx, {
    type: 'bar',
    data: {
      labels,
      datasets
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: {
          position: 'bottom',
          labels: { color: '#a0aec0', padding: 20, usePointStyle: true }
        },
        tooltip: {
          backgroundColor: 'rgba(12, 25, 41, 0.95)',
          titleColor: '#ffffff',
          bodyColor: '#a0aec0',
          borderColor: 'rgba(64, 158, 255, 0.3)',
          borderWidth: 1,
          padding: 12
        }
      },
      scales: {
        x: {
          ticks: { color: '#a0aec0' },
          grid: { color: 'rgba(255,255,255,0.05)' }
        },
        y: {
          type: 'linear',
          display: true,
          position: 'left',
          title: {
            display: true,
            text: 'GM / GZ_MAX (m)',
            color: '#409EFF'
          },
          ticks: { color: '#409EFF' },
          grid: { color: 'rgba(255,255,255,0.05)' }
        },
        y1: {
          type: 'linear',
          display: true,
          position: 'right',
          title: {
            display: true,
            text: '稳性范围 (°)',
            color: '#E6A23C'
          },
          ticks: { color: '#E6A23C' },
          grid: { drawOnChartArea: false }
        }
      }
    }
  })
}

const initGzChart = () => {
  if (!gzChartRef.value || !comparisonResult.value) return

  if (gzChart) gzChart.destroy()

  const items = sortedComparisonItems.value

  const datasets = items.map(item => {
    const curvePoints = item.gzCurvePoints || generateMockGzCurve(item.metrics)
    return {
      label: item.shipName,
      data: curvePoints.map(p => p.gz),
      borderColor: getShipColor(item.shipType),
      backgroundColor: getShipColor(item.shipType) + '22',
      fill: false,
      tension: 0.3,
      pointRadius: 0,
      pointHoverRadius: 6,
      borderWidth: 2
    }
  })

  const labels = items[0]?.gzCurvePoints?.map(p => `${p.angle}°`) || generateMockGzCurve().map(p => `${p.angle}°`)

  const ctx = gzChartRef.value.getContext('2d')
  gzChart = new Chart(ctx, {
    type: 'line',
    data: {
      labels,
      datasets
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      interaction: {
        mode: 'index',
        intersect: false
      },
      plugins: {
        legend: {
          position: 'bottom',
          labels: { color: '#a0aec0', padding: 20, usePointStyle: true }
        },
        tooltip: {
          backgroundColor: 'rgba(12, 25, 41, 0.95)',
          titleColor: '#ffffff',
          bodyColor: '#a0aec0',
          borderColor: 'rgba(64, 158, 255, 0.3)',
          borderWidth: 1,
          padding: 12,
          callbacks: {
            title: (items) => `横倾角: ${items[0].label}`,
            label: (item) => `${item.dataset.label}: ${item.raw?.toFixed(4)} m`
          }
        }
      },
      scales: {
        x: {
          title: {
            display: true,
            text: '横倾角 φ (°)',
            color: '#a0aec0'
          },
          ticks: { color: '#a0aec0', maxTicksLimit: 15 },
          grid: { color: 'rgba(255,255,255,0.05)' }
        },
        y: {
          title: {
            display: true,
            text: '复原力臂 GZ (m)',
            color: '#a0aec0'
          },
          ticks: { color: '#a0aec0' },
          grid: { color: 'rgba(255,255,255,0.05)' }
        }
      }
    }
  })
}

const generateMockGzCurve = (metrics) => {
  const points = []
  const gm = metrics?.GM || 0.5
  const gzMax = metrics?.GZ_MAX || 1.0
  const range = metrics?.RANGE || 60

  for (let angle = 0; angle <= 90; angle += 5) {
    let gz = 0
    if (angle <= 30) {
      gz = gm * Math.sin(angle * Math.PI / 180)
    } else if (angle <= range) {
      const t = (angle - 30) / (range - 30)
      const gmGz = gm * Math.sin(angle * Math.PI / 180)
      gz = gmGz + t * (gzMax - gmGz)
    } else {
      const t = (angle - range) / (90 - range)
      gz = gzMax * (1 - t)
    }
    points.push({ angle, gz: Math.max(0, gz) })
  }
  return points
}

const destroyCharts = () => {
  if (radarChart) {
    radarChart.destroy()
    radarChart = null
  }
  if (barChart) {
    barChart.destroy()
    barChart = null
  }
  if (gzChart) {
    gzChart.destroy()
    gzChart = null
  }
}

onMounted(() => {
})

onUnmounted(() => {
  destroyCharts()
})
</script>

<style scoped lang="scss">
.ship-comparator {
  width: 100%;
  display: grid;
  grid-template-columns: 400px 1fr;
  gap: 16px;
}

.config-section {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.card {
  background: rgba(12, 25, 41, 0.6);
  border: 1px solid rgba(64, 158, 255, 0.2);
  border-radius: 12px;
  overflow: hidden;

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
  }

  .card-body {
    padding: 20px;
  }
}

.ship-tree {
  max-height: 280px;
  overflow-y: auto;
  background: rgba(0, 0, 0, 0.2);
  border-radius: 8px;
  padding: 8px;

  :deep(.el-tree-node__content) {
    height: 32px;

    &:hover {
      background: rgba(64, 158, 255, 0.1);
    }
  }

  :deep(.el-tree-node__label) {
    color: #ffffff;
    font-size: 13px;
  }

  .tree-node {
    display: flex;
    align-items: center;
    gap: 6px;
    width: 100%;

    .category-icon {
      color: #409EFF;
    }

    .ship-tag {
      margin-left: auto;
    }
  }
}

.compare-btn {
  width: 100%;
  margin-right: 12px;
}

.unit {
  color: #a0aec0;
  margin-left: 8px;
  font-size: 13px;
}

.legend-list {
  display: flex;
  flex-direction: column;
  gap: 12px;

  .legend-item {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 10px 12px;
    background: rgba(0, 0, 0, 0.2);
    border-radius: 8px;

    .legend-color {
      width: 16px;
      height: 16px;
      border-radius: 4px;
      flex-shrink: 0;
    }

    .legend-name {
      color: #ffffff;
      font-weight: 500;
      min-width: 80px;
    }

    .legend-desc {
      color: #a0aec0;
      font-size: 12px;
    }
  }
}

.result-section {
  .empty-result {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    height: 400px;
    background: rgba(12, 25, 41, 0.6);
    border: 1px solid rgba(64, 158, 255, 0.2);
    border-radius: 12px;
    color: #a0aec0;

    .empty-icon {
      font-size: 64px;
      color: rgba(64, 158, 255, 0.3);
      margin-bottom: 16px;
    }

    .empty-text {
      font-size: 16px;
    }
  }
}

.ranking-section {
  margin-bottom: 16px;

  .section-title {
    display: flex;
    align-items: center;
    gap: 8px;
    color: #ffffff;
    font-size: 18px;
    margin: 0 0 16px 0;

    .el-icon {
      color: #E6A23C;
    }
  }
}

.ranking-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 16px;

  .ranking-card {
    background: rgba(12, 25, 41, 0.6);
    border: 1px solid rgba(64, 158, 255, 0.2);
    border-radius: 12px;
    padding: 20px;
    display: flex;
    align-items: center;
    gap: 16px;
    transition: all 0.3s;

    &:hover {
      transform: translateY(-3px);
      border-color: rgba(64, 158, 255, 0.4);
    }

    &.rank-1 {
      border: 2px solid rgba(255, 215, 0, 0.5);
      background: linear-gradient(135deg, rgba(255, 215, 0, 0.1), rgba(12, 25, 41, 0.6));
    }

    &.rank-2 {
      border: 2px solid rgba(192, 192, 192, 0.5);
      background: linear-gradient(135deg, rgba(192, 192, 192, 0.1), rgba(12, 25, 41, 0.6));
    }

    &.rank-3 {
      border: 2px solid rgba(205, 127, 50, 0.5);
      background: linear-gradient(135deg, rgba(205, 127, 50, 0.1), rgba(12, 25, 41, 0.6));
    }

    .rank-badge {
      width: 48px;
      height: 48px;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 24px;
      font-weight: bold;
      background: rgba(0, 0, 0, 0.3);
      flex-shrink: 0;
      color: #ffffff;
    }

    .ship-info {
      flex: 1;
      min-width: 0;

      .ship-header {
        display: flex;
        align-items: center;
        gap: 8px;
        margin-bottom: 4px;

        .ship-name {
          font-size: 16px;
          font-weight: bold;
          color: #ffffff;
        }
      }

      .ship-type {
        color: #a0aec0;
        font-size: 12px;
      }
    }

    .score-info {
      text-align: right;

      .score-value {
        font-size: 28px;
        font-weight: bold;
        color: #409EFF;
        font-family: 'Courier New', monospace;
        line-height: 1;
      }

      .score-label {
        color: #a0aec0;
        font-size: 12px;
        margin-top: 4px;
      }
    }
  }
}

.comparison-table {
  :deep(.el-table) {
    background: transparent;
    --el-table-header-bg-color: rgba(0, 0, 0, 0.3);
    --el-table-row-hover-bg-color: rgba(64, 158, 255, 0.05);
    --el-table-tr-bg-color: transparent;
    --el-table-text-color: #ffffff;
    --el-table-header-text-color: #a0aec0;
    --el-table-border-color: rgba(64, 158, 255, 0.1);
  }

  :deep(.el-table th) {
    background: rgba(0, 0, 0, 0.3);
    color: #a0aec0;
    font-weight: 500;
  }

  :deep(.el-table td) {
    color: #ffffff;
    font-family: 'Courier New', monospace;
  }

  :deep(.el-table--striped .el-table__body tr.el-table__row--striped td) {
    background: rgba(0, 0, 0, 0.1);
  }

  .gm-danger {
    color: #F56C6C;
    font-weight: bold;
  }

  .gm-warning {
    color: #E6A23C;
    font-weight: bold;
  }

  .gm-success {
    color: #67C23A;
    font-weight: bold;
  }

  .rank-gold, .rank-silver, .rank-bronze {
    font-size: 18px;
  }
}

.charts-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-bottom: 16px;
}

.chart-container {
  height: 320px;

  &.large {
    height: 350px;
  }
}

.table-card,
.chart-card,
.analysis-card {
  margin-bottom: 16px;
}

.analysis-content {
  color: #ffffff;
  line-height: 1.8;
  font-size: 14px;

  p {
    margin: 0 0 12px 0;

    &:last-child {
      margin-bottom: 0;
    }

    &.highlight {
      padding: 12px 16px;
      background: rgba(64, 158, 255, 0.1);
      border-left: 4px solid #409EFF;
      border-radius: 6px;
    }

    &.conclusion {
      padding: 12px 16px;
      background: rgba(103, 194, 58, 0.1);
      border-left: 4px solid #67C23A;
      border-radius: 6px;
    }
  }

  strong {
    color: #409EFF;
  }
}

:deep(.el-checkbox__label) {
  color: #ffffff;
}

:deep(.el-form-item__label) {
  color: #a0aec0;
}

:deep(.el-input__wrapper) {
  background: rgba(0, 0, 0, 0.2);
  box-shadow: 0 0 0 1px rgba(64, 158, 255, 0.2) inset;
}

:deep(.el-input__inner) {
  color: #ffffff;
}

:deep(.el-input-number .el-input__inner) {
  color: #ffffff;
}

:deep(.el-radio-button__inner) {
  background: rgba(0, 0, 0, 0.2);
  border-color: rgba(64, 158, 255, 0.2);
  color: #a0aec0;

  &:hover {
    color: #409EFF;
  }
}

:deep(.el-radio-button.is-active .el-radio-button__inner) {
  background: #409EFF;
  border-color: #409EFF;
  color: #ffffff;
}

@media (max-width: 1400px) {
  .ship-comparator {
    grid-template-columns: 1fr;
  }

  .charts-row {
    grid-template-columns: 1fr;
  }

  .ranking-cards {
    grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  }
}
</style>
