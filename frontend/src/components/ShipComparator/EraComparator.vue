<template>
  <div class="era-comparator">
    <div class="era-header">
      <div class="header-info">
        <h3 class="section-title">
          <el-icon><Olympic /></el-icon>
          跨时代对比分析
        </h3>
        <p class="section-desc" v-if="isCrossEra">
          对比古代船舶 {{ result?.ancientCount || 0 }} 艘 vs 现代船舶 {{ result?.modernCount || 0 }} 艘
        </p>
        <p class="section-desc warn" v-else>
          请同时选择古代船舶和现代船舶以进行跨时代对比
        </p>
      </div>
      <el-button
        type="primary"
        @click="handleCompareCross"
        :disabled="!isCrossEra"
      >
        <el-icon><Connection /></el-icon>
        执行跨时代对比
      </el-button>
    </div>

    <div v-if="!result && !loading" class="empty-era">
      <el-icon class="empty-icon"><Connection /></el-icon>
      <p class="empty-text">请同时选择古代和现代船舶，然后点击"执行跨时代对比"</p>
    </div>

    <div v-else-if="result && result.isCrossEra" class="era-content">
      <div class="card bar-chart-card">
        <div class="card-header">
          <h3 class="card-title">
            <el-icon><Histogram /></el-icon>
            古代 vs 现代 平均指标对比
          </h3>
        </div>
        <div class="card-body">
          <div class="chart-container">
            <canvas ref="eraBarChartRef"></canvas>
          </div>
        </div>
      </div>

      <div class="card ratio-card">
        <div class="card-header">
          <h3 class="card-title">
            <el-icon><TrendCharts /></el-icon>
            时代进步倍率分析
          </h3>
          <div class="header-summary">
            <el-tag type="success" effect="dark" size="large">
              综合提升: {{ overallImprovement }}%
            </el-tag>
          </div>
        </div>
        <div class="card-body">
          <el-table :data="ratioTableData" class="ratio-table" stripe>
            <el-table-column prop="label" label="指标" width="140" />
            <el-table-column label="古代平均" width="140" align="right">
              <template #default="{ row }">
                <span class="ancient-value">{{ row.ancientValue }}</span>
              </template>
            </el-table-column>
            <el-table-column label="现代平均" width="140" align="right">
              <template #default="{ row }">
                <span class="modern-value">{{ row.modernValue }}</span>
              </template>
            </el-table-column>
            <el-table-column label="进步倍率" width="120" align="center">
              <template #default="{ row }">
                <span class="ratio-badge" :class="getRatioClass(row.ratio)">
                  {{ row.ratio?.toFixed(2) || '--' }}x
                </span>
              </template>
            </el-table-column>
            <el-table-column label="提升百分比" align="center">
              <template #default="{ row }">
                <div class="progress-wrapper">
                  <el-progress
                    :percentage="Math.min(row.percentage, 100)"
                    :color="getProgressColor(row.percentage)"
                    :stroke-width="12"
                    :show-text="false"
                  />
                  <span class="progress-text">{{ row.percentage?.toFixed(1) || 0 }}%</span>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>

      <div class="card analysis-card">
        <div class="card-header">
          <h3 class="card-title">
            <el-icon><ChatDotRound /></el-icon>
            技术差距分析总结
          </h3>
        </div>
        <div class="card-body">
          <div class="analysis-content" v-html="analysisSummary"></div>
        </div>
      </div>

      <div class="card tech-progress-card">
        <div class="card-header">
          <h3 class="card-title">
            <el-icon><Cpu /></el-icon>
            科技进步雷达
          </h3>
        </div>
        <div class="card-body">
          <div class="progress-grid">
            <div
              v-for="item in techProgressList"
              :key="item.metric"
              class="progress-item"
            >
              <div class="progress-header">
                <span class="progress-label">{{ item.label }}</span>
                <span class="progress-percent" :style="{ color: item.color }">
                  +{{ item.percentage?.toFixed(1) || 0 }}%
                </span>
              </div>
              <div class="progress-bar-wrapper">
                <div class="progress-bar-bg">
                  <div
                    class="progress-bar-fill"
                    :style="{
                      width: Math.min(item.percentage, 100) + '%',
                      backgroundColor: item.color
                    }"
                  ></div>
                </div>
                <div class="progress-marker ancient" title="古代水平"></div>
                <div class="progress-marker modern" title="现代水平"></div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import {
  Olympic, Connection, Histogram, TrendCharts, ChatDotRound, Cpu
} from '@element-plus/icons-vue'
import { Chart, registerables } from 'chart.js'
import { ElMessage } from 'element-plus'

Chart.register(...registerables)

const props = defineProps({
  result: {
    type: Object,
    default: () => null
  },
  loading: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['compare-cross'])

const eraBarChartRef = ref(null)
let eraBarChart = null

const isCrossEra = computed(() => {
  if (props.result) {
    return (props.result.ancientCount > 0 && props.result.modernCount > 0)
  }
  return false
})

const METRIC_LABELS = {
  GM: { label: '初稳性高', unit: 'm', color: '#409EFF' },
  GZ_MAX: { label: '最大复原力臂', unit: 'm', color: '#67C23A' },
  RANGE: { label: '稳性范围', unit: '°', color: '#E6A23C' },
  GZ_AREA: { label: '稳性面积', unit: 'm·°', color: '#9C27B0' },
  ROLL_PERIOD: { label: '横摇周期', unit: 's', color: '#F56C6C' },
  DISPLACEMENT: { label: '排水量', unit: 't', color: '#909399' },
  DEADWEIGHT: { label: '载重量', unit: 't', color: '#00BCD4' },
  BOW_HEIGHT: { label: '艏高', unit: 'm', color: '#FF9800' }
}

const overallImprovement = computed(() => {
  if (!props.result?.eraImprovementRatio) return 0
  const ratios = Object.values(props.result.eraImprovementRatio).filter(v => typeof v === 'number' && isFinite(v))
  if (ratios.length === 0) return 0
  const avgRatio = ratios.reduce((sum, r) => sum + r, 0) / ratios.length
  return ((avgRatio - 1) * 100).toFixed(1)
})

const ratioTableData = computed(() => {
  if (!props.result) return []
  const ancient = props.result.ancientAverageMetrics || {}
  const modern = props.result.modernAverageMetrics || {}
  const ratio = props.result.eraImprovementRatio || {}

  return Object.keys(METRIC_LABELS).map(metric => {
    const info = METRIC_LABELS[metric]
    const ancientVal = ancient[metric]
    const modernVal = modern[metric]
    const ratioVal = ratio[metric]
    const percentage = ratioVal ? (ratioVal - 1) * 100 : 0

    return {
      metric,
      label: info.label,
      ancientValue: ancientVal != null ? `${ancientVal.toFixed(3)} ${info.unit}` : '--',
      modernValue: modernVal != null ? `${modernVal.toFixed(3)} ${info.unit}` : '--',
      ratio: ratioVal,
      percentage
    }
  }).filter(item => item.ancientValue !== '--' || item.modernValue !== '--')
})

const techProgressList = computed(() => {
  if (!props.result?.eraImprovementRatio) return []
  const ratio = props.result.eraImprovementRatio

  return Object.keys(METRIC_LABELS).map(metric => {
    const info = METRIC_LABELS[metric]
    const ratioVal = ratio[metric]
    const percentage = ratioVal ? (ratioVal - 1) * 100 : 0

    return {
      metric,
      label: info.label,
      percentage,
      color: info.color
    }
  }).filter(item => item.percentage > 0)
})

const analysisSummary = computed(() => {
  if (!props.result) return ''

  const ancientCount = props.result.ancientCount || 0
  const modernCount = props.result.modernCount || 0
  const ratio = props.result.eraImprovementRatio || {}
  const ancient = props.result.ancientAverageMetrics || {}
  const modern = props.result.modernAverageMetrics || {}

  let text = `<p>本次跨时代对比选取了 <strong>${ancientCount}</strong> 艘古代船舶与 <strong>${modernCount}</strong> 艘现代船舶，从多个维度进行综合评估。</p>`

  text += `<p class="highlight">从综合数据来看，现代船舶相比古代船舶在各项性能指标上实现了全面飞跃：</p>`

  const highlights = []

  if (ratio.DEADWEIGHT) {
    const pct = ((ratio.DEADWEIGHT - 1) * 100).toFixed(0)
    highlights.push(`<li>载货能力方面，现代船舶平均载重量从 <strong>${ancient.DEADWEIGHT?.toFixed(0) || 0}t</strong> 提升至 <strong>${modern.DEADWEIGHT?.toFixed(0) || 0}t</strong>，提升幅度达 <strong style="color:#00BCD4">${pct}%</strong></li>`)
  }

  if (ratio.GM) {
    const pct = ((ratio.GM - 1) * 100).toFixed(0)
    highlights.push(`<li>稳性设计方面，初稳性高GM从 <strong>${ancient.GM?.toFixed(3) || 0}m</strong> 优化至 <strong>${modern.GM?.toFixed(3) || 0}m</strong>，提升 <strong style="color:#409EFF">${pct}%</strong></li>`)
  }

  if (ratio.RANGE) {
    const pct = ((ratio.RANGE - 1) * 100).toFixed(0)
    highlights.push(`<li>稳性范围从 <strong>${ancient.RANGE?.toFixed(1) || 0}°</strong> 扩展至 <strong>${modern.RANGE?.toFixed(1) || 0}°</strong>，抗倾覆能力增强 <strong style="color:#E6A23C">${pct}%</strong></li>`)
  }

  if (ratio.ROLL_PERIOD) {
    const pct = ((ratio.ROLL_PERIOD - 1) * 100).toFixed(0)
    highlights.push(`<li>航行舒适性方面，横摇周期延长 <strong style="color:#F56C6C">${pct}%</strong>，大幅降低了船员的晕船风险</li>`)
  }

  if (highlights.length > 0) {
    text += `<ul class="analysis-list">${highlights.join('')}</ul>`
  }

  text += `<p class="conclusion">
    <strong>总结：</strong>
    从古代木帆船到现代钢制船舶，造船技术经历了质的飞跃。
    材料科学的进步（木材→钢铁）、工程力学的发展、以及精密仪器的应用，
    使得现代船舶在稳性、载货量、安全性等方面全面超越古代船舶，
    综合性能提升约 <strong style="color:#67C23A">${overallImprovement}%</strong>。
    然而，古代船舶在特定航区（如浅水区、内河）的适应性设计仍具有宝贵的参考价值，
    其蕴含的传统造船智慧至今仍值得学习借鉴。
  </p>`

  return text
})

const getRatioClass = (ratio) => {
  if (!ratio) return ''
  if (ratio >= 3) return 'ratio-excellent'
  if (ratio >= 2) return 'ratio-good'
  if (ratio >= 1.5) return 'ratio-ok'
  return 'ratio-normal'
}

const getProgressColor = (percentage) => {
  if (percentage >= 200) return '#67C23A'
  if (percentage >= 100) return '#409EFF'
  if (percentage >= 50) return '#E6A23C'
  return '#909399'
}

const handleCompareCross = () => {
  if (!isCrossEra.value) {
    ElMessage.warning('请同时选择古代船舶和现代船舶')
    return
  }
  emit('compare-cross')
}

const initEraBarChart = () => {
  if (!eraBarChartRef.value || !props.result) return

  if (eraBarChart) eraBarChart.destroy()

  const ancient = props.result.ancientAverageMetrics || {}
  const modern = props.result.modernAverageMetrics || {}

  const displayMetrics = ['GM', 'GZ_MAX', 'RANGE', 'DEADWEIGHT', 'ROLL_PERIOD']
  const labels = displayMetrics.map(m => METRIC_LABELS[m].label)

  const ctx = eraBarChartRef.value.getContext('2d')
  eraBarChart = new Chart(ctx, {
    type: 'bar',
    data: {
      labels,
      datasets: [
        {
          label: '古代船舶 (平均)',
          data: displayMetrics.map(m => ancient[m] || 0),
          backgroundColor: 'rgba(230, 162, 60, 0.8)',
          borderColor: '#E6A23C',
          borderWidth: 1,
          borderRadius: 4
        },
        {
          label: '现代船舶 (平均)',
          data: displayMetrics.map(m => modern[m] || 0),
          backgroundColor: 'rgba(64, 158, 255, 0.8)',
          borderColor: '#409EFF',
          borderWidth: 1,
          borderRadius: 4
        }
      ]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: {
          position: 'bottom',
          labels: {
            color: '#a0aec0',
            padding: 20,
            usePointStyle: true
          }
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
              const metric = displayMetrics[context.dataIndex]
              const unit = METRIC_LABELS[metric].unit
              return `${context.dataset.label}: ${context.raw?.toFixed(3)} ${unit}`
            }
          }
        }
      },
      scales: {
        x: {
          ticks: { color: '#a0aec0' },
          grid: { color: 'rgba(255,255,255,0.05)' }
        },
        y: {
          type: 'logarithmic',
          title: {
            display: true,
            text: '数值 (对数刻度)',
            color: '#a0aec0'
          },
          ticks: { color: '#a0aec0' },
          grid: { color: 'rgba(255,255,255,0.05)' }
        }
      }
    }
  })
}

const destroyCharts = () => {
  if (eraBarChart) {
    eraBarChart.destroy()
    eraBarChart = null
  }
}

watch(() => props.result, async (newVal) => {
  if (newVal && newVal.isCrossEra) {
    await nextTick()
    initEraBarChart()
  }
}, { deep: true })

onMounted(() => {
  if (props.result && props.result.isCrossEra) {
    nextTick(() => initEraBarChart())
  }
})

onUnmounted(() => {
  destroyCharts()
})
</script>

<style scoped lang="scss">
.era-comparator {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.era-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 16px 20px;
  background: linear-gradient(135deg, rgba(230, 162, 60, 0.1), rgba(64, 158, 255, 0.1));
  border: 1px solid rgba(64, 158, 255, 0.3);
  border-radius: 12px;

  .header-info {
    flex: 1;

    .section-title {
      display: flex;
      align-items: center;
      gap: 8px;
      color: #ffffff;
      font-size: 18px;
      margin: 0 0 6px 0;

      .el-icon {
        color: #E6A23C;
      }
    }

    .section-desc {
      color: #a0aec0;
      font-size: 13px;
      margin: 0;

      &.warn {
        color: #F56C6C;
      }
    }
  }
}

.empty-era {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 300px;
  background: rgba(12, 25, 41, 0.6);
  border: 1px solid rgba(64, 158, 255, 0.2);
  border-radius: 12px;
  color: #a0aec0;

  .empty-icon {
    font-size: 56px;
    color: rgba(230, 162, 60, 0.3);
    margin-bottom: 16px;
  }

  .empty-text {
    font-size: 15px;
  }
}

.era-content {
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
    padding: 14px 20px;
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
  }

  .card-body {
    padding: 20px;
  }
}

.chart-container {
  height: 320px;
}

.ratio-table {
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

  .ancient-value {
    color: #E6A23C;
  }

  .modern-value {
    color: #409EFF;
  }

  .ratio-badge {
    display: inline-block;
    padding: 4px 10px;
    border-radius: 4px;
    font-weight: bold;
    font-size: 13px;

    &.ratio-excellent {
      background: rgba(103, 194, 58, 0.2);
      color: #67C23A;
    }

    &.ratio-good {
      background: rgba(64, 158, 255, 0.2);
      color: #409EFF;
    }

    &.ratio-ok {
      background: rgba(230, 162, 60, 0.2);
      color: #E6A23C;
    }

    &.ratio-normal {
      background: rgba(144, 147, 153, 0.2);
      color: #909399;
    }
  }
}

.progress-wrapper {
  display: flex;
  align-items: center;
  gap: 10px;

  .progress-text {
    min-width: 60px;
    text-align: right;
    color: #67C23A;
    font-weight: bold;
    font-family: 'Courier New', monospace;
  }
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
      background: rgba(230, 162, 60, 0.1);
      border-left: 4px solid #E6A23C;
      border-radius: 6px;
    }

    &.conclusion {
      padding: 12px 16px;
      background: rgba(103, 194, 58, 0.1);
      border-left: 4px solid #67C23A;
      border-radius: 6px;
    }
  }

  .analysis-list {
    padding-left: 20px;
    margin: 0 0 12px 0;

    li {
      margin-bottom: 8px;
      color: #a0aec0;

      strong {
        color: #ffffff;
      }
    }
  }

  strong {
    color: #409EFF;
  }
}

.progress-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;

  .progress-item {
    background: rgba(0, 0, 0, 0.2);
    padding: 12px 16px;
    border-radius: 8px;
    border: 1px solid rgba(64, 158, 255, 0.1);

    .progress-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 8px;

      .progress-label {
        color: #ffffff;
        font-size: 13px;
        font-weight: 500;
      }

      .progress-percent {
        font-weight: bold;
        font-family: 'Courier New', monospace;
        font-size: 13px;
      }
    }

    .progress-bar-wrapper {
      position: relative;
      height: 12px;

      .progress-bar-bg {
        height: 100%;
        background: rgba(0, 0, 0, 0.3);
        border-radius: 6px;
        overflow: hidden;

        .progress-bar-fill {
          height: 100%;
          border-radius: 6px;
          transition: width 0.5s ease;
        }
      }
    }
  }
}

@media (max-width: 1024px) {
  .progress-grid {
    grid-template-columns: 1fr;
  }
}
</style>
