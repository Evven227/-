<script setup>
import { computed, onMounted, ref } from 'vue'
import { performance, fmtDate } from '../api.js'

const props = defineProps({ account: { type: Object, required: true } })

const list = ref([])
const error = ref('')
const notice = ref('')
const busy = ref(false)
const form = ref({
  periodStart: firstDayOfMonth(),
  periodEnd: firstDayOfNextMonth(),
  dentistId: '',
  workloadTargetCount: 20
})

const isReviewer = computed(() =>
  props.account.roles.some(r => ['MANAGER'].includes(r)))
const isDentist = computed(() => props.account.roles.includes('DENTIST'))

function firstDayOfMonth() {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-01`
}
function firstDayOfNextMonth() {
  const d = new Date()
  const y = d.getMonth() === 11 ? d.getFullYear() + 1 : d.getFullYear()
  const m = d.getMonth() === 11 ? 1 : d.getMonth() + 2
  return `${y}-${String(m).padStart(2, '0')}-01`
}

onMounted(load)

async function load() {
  error.value = ''
  try {
    list.value = await performance.list()
  } catch (e) {
    error.value = e.message
  }
}

async function recalculate() {
  error.value = ''
  notice.value = ''
  if (new Date(form.value.periodEnd) <= new Date(form.value.periodStart)) {
    error.value = '周期结束日期必须晚于开始日期'
    return
  }
  busy.value = true
  try {
    const result = await performance.recalculate({
      periodStart: form.value.periodStart,
      periodEnd: form.value.periodEnd,
      dentistId: form.value.dentistId ? Number(form.value.dentistId) : null,
      workloadTargetCount: Number(form.value.workloadTargetCount)
    })
    notice.value = `已重新计算 ${result.length} 位牙医的绩效`
    await load()
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = false
  }
}

async function review(id, action) {
  error.value = ''
  notice.value = ''
  try {
    await performance.review(id, { action })
    notice.value = action === 'CONFIRM' ? '绩效已确认' : '绩效已归档'
    await load()
  } catch (e) {
    error.value = e.message
  }
}

const rewardText = { NONE: '无', NORMAL: '一般', GOOD: '良好', EXCELLENT: '优秀' }
const reviewText = { DRAFT: '草稿', CONFIRMED: '已确认', ARCHIVED: '已归档' }
</script>

<template>
  <div>
    <p v-if="notice" class="notice">{{ notice }}</p>
    <p v-if="error" class="error">{{ error }}</p>

    <!-- 重新计算 -->
    <div class="card actions" v-if="isReviewer">
      <h2>重新计算绩效</h2>
      <div class="form-grid">
        <label>周期开始<input v-model="form.periodStart" type="date" /></label>
        <label>周期结束<input v-model="form.periodEnd" type="date" /></label>
        <label>牙医编号（留空计算全部）<input v-model="form.dentistId" type="number" /></label>
        <label>工作目标量<input v-model="form.workloadTargetCount" type="number" min="1" /></label>
      </div>
      <button @click="recalculate" :disabled="busy">{{ busy ? '计算中…' : '重新计算' }}</button>
      <p class="muted">得分规则：出勤率（已签到+已完成 ÷ 预约数）× 60% + 完成率 × 40%，满分 100。</p>
    </div>

    <!-- 绩效列表 -->
    <div class="card">
      <h2>{{ isDentist && !isReviewer ? '我的绩效' : '绩效列表' }}</h2>
      <table>
        <thead>
          <tr><th>牙医</th><th>诊所</th><th>周期</th><th>预约数</th><th>实到</th><th>取消</th><th>未赴约</th><th>完成</th><th>治疗项目</th><th>得分</th><th>评级</th><th>复核状态</th><th v-if="isReviewer">操作</th></tr>
        </thead>
        <tbody>
          <tr v-for="p in list" :key="p.performance_id">
            <td>{{ p.dentist_name }}（{{ p.dentist_id }}）</td>
            <td>{{ p.clinic_name || '—' }}</td>
            <td>{{ fmtDate(p.period_start) }} ~ {{ fmtDate(p.period_end) }}</td>
            <td>{{ p.appointment_count }}</td>
            <td>{{ p.attended_count }}</td>
            <td>{{ p.cancelled_count }}</td>
            <td>{{ p.no_show_count }}</td>
            <td>{{ p.completed_count }}</td>
            <td>{{ p.treatment_count }}</td>
            <td>{{ p.performance_score == null ? '—' : Number(p.performance_score).toFixed(2) }}</td>
            <td>{{ rewardText[p.reward_level] || p.reward_level }}</td>
            <td><span class="tag">{{ reviewText[p.review_status] || p.review_status }}</span></td>
            <td v-if="isReviewer">
              <button class="mini" v-if="p.review_status === 'DRAFT'" @click="review(p.performance_id, 'CONFIRM')">确认</button>
              <button class="mini" v-if="p.review_status === 'CONFIRMED'" @click="review(p.performance_id, 'ARCHIVE')">归档</button>
            </td>
          </tr>
          <tr v-if="!list.length"><td :colspan="isReviewer ? 13 : 12" class="muted">暂无绩效记录</td></tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
