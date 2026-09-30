<template>
  <div class="page-container">
    <!-- 统计卡片 -->
    <el-row :gutter="16">
      <el-col v-for="card in statCards" :key="card.label" :xs="24" :sm="12" :lg="6">
        <div class="stat-card">
          <div class="stat-card__icon" :style="{ background: card.bg }">
            <el-icon :size="22" :color="card.color">
              <component :is="card.icon" />
            </el-icon>
          </div>
          <div class="stat-card__body">
            <div class="stat-card__value">{{ card.value }}</div>
            <div class="stat-card__label">{{ card.label }}</div>
          </div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :xs="24" :lg="14">
        <div class="page-card">
          <div class="card-header">
            <span>近 7 日工作流执行量</span>
            <el-tag type="info" size="small">示例数据</el-tag>
          </div>
          <div ref="lineRef" class="chart" />
        </div>
      </el-col>
      <el-col :xs="24" :lg="10">
        <div class="page-card">
          <div class="card-header">
            <span>模型调用分布</span>
            <el-tag type="info" size="small">示例数据</el-tag>
          </div>
          <div ref="pieRef" class="chart" />
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :span="24">
        <div class="page-card">
          <div class="card-header">
            <span>快捷入口</span>
          </div>
          <div class="quick-links">
            <div v-for="item in quickLinks" :key="item.title" class="quick-link" @click="$router.push(item.path)">
              <el-icon :size="18"><component :is="item.icon" /></el-icon>
              <span>{{ item.title }}</span>
            </div>
          </div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
import * as echarts from 'echarts'

const lineRef = ref()
const pieRef = ref()
let lineChart = null
let pieChart = null

const statCards = [
  { label: '流程定义总数', value: 128, icon: 'Share', color: '#3b82f6', bg: 'rgba(59,130,246,.12)' },
  { label: '今日执行实例', value: 356, icon: 'Promotion', color: '#22c55e', bg: 'rgba(34,197,94,.12)' },
  { label: '智能体数量', value: 24, icon: 'MagicStick', color: '#8b5cf6', bg: 'rgba(139,92,246,.12)' },
  { label: '模型调用次数(今日)', value: 8642, icon: 'DataAnalysis', color: '#f59e0b', bg: 'rgba(245,158,11,.12)' }
]

const quickLinks = [
  { title: '用户管理', path: '/system/user', icon: 'User' },
  { title: '角色管理', path: '/system/role', icon: 'UserFilled' },
  { title: '菜单管理', path: '/system/menu', icon: 'Menu' },
  { title: '字典管理', path: '/system/dict', icon: 'Collection' },
  { title: '参数配置', path: '/system/config', icon: 'Setting' }
]

function renderLine() {
  if (!lineRef.value) return
  lineChart = echarts.init(lineRef.value)
  lineChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['成功', '失败'], right: 0 },
    grid: { left: 12, right: 12, bottom: 8, top: 40, containLabel: true },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: ['09-23', '09-24', '09-25', '09-26', '09-27', '09-28', '09-29']
    },
    yAxis: { type: 'value' },
    series: [
      {
        name: '成功',
        type: 'line',
        smooth: true,
        areaStyle: { opacity: 0.15 },
        itemStyle: { color: '#3b82f6' },
        data: [120, 132, 101, 134, 190, 230, 256]
      },
      {
        name: '失败',
        type: 'line',
        smooth: true,
        areaStyle: { opacity: 0.12 },
        itemStyle: { color: '#f87171' },
        data: [12, 9, 14, 8, 16, 11, 7]
      }
    ]
  })
}

function renderPie() {
  if (!pieRef.value) return
  pieChart = echarts.init(pieRef.value)
  pieChart.setOption({
    tooltip: { trigger: 'item' },
    legend: { bottom: 0 },
    series: [
      {
        name: '模型调用',
        type: 'pie',
        radius: ['42%', '68%'],
        avoidLabelOverlap: true,
        label: { formatter: '{b}: {c}' },
        data: [
          { value: 3120, name: 'GPT-4o', itemStyle: { color: '#3b82f6' } },
          { value: 2410, name: 'DeepSeek-V3', itemStyle: { color: '#22c55e' } },
          { value: 1860, name: 'Qwen-Max', itemStyle: { color: '#8b5cf6' } },
          { value: 1252, name: 'Claude-3.5', itemStyle: { color: '#f59e0b' } }
        ]
      }
    ]
  })
}

function handleResize() {
  lineChart?.resize()
  pieChart?.resize()
}

onMounted(async () => {
  await nextTick()
  renderLine()
  renderPie()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  lineChart?.dispose()
  pieChart?.dispose()
  lineChart = null
  pieChart = null
})
</script>

<style scoped lang="scss">
.stat-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 3px rgba(0, 21, 41, 0.06);
  margin-bottom: 16px;

  &__icon {
    width: 48px;
    height: 48px;
    border-radius: 12px;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  &__value {
    font-size: 22px;
    font-weight: 600;
    color: #303133;
    line-height: 1.2;
  }

  &__label {
    margin-top: 4px;
    font-size: 12px;
    color: #909399;
  }
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  font-size: 14px;
  font-weight: 500;
  color: #303133;
}

.chart {
  width: 100%;
  height: 300px;
}

.quick-links {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.quick-link {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  font-size: 13px;
  color: #606266;
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    color: #3b82f6;
    border-color: #3b82f6;
    background: #f5f9ff;
  }
}
</style>
