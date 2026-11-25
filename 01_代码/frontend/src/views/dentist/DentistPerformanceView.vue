<script setup>
import { computed, onMounted, ref } from 'vue'
import { dentistPortal, fmtDate, monthLocal } from '../../api.js'

const props = defineProps({ account: { type: Object, required: true } })
const data = ref(null)
const error = ref('')
const month = ref(monthLocal())

const perf = computed(() => data.value?.performance || null)
const covers = computed(() => data.value?.coverShifts || [])

onMounted(load)

async function load() {
  error.value = ''
  try {
    data.value = await dentistPortal.performance(month.value)
  } catch (e) {
    error.value = e.message
  }
}

function reviewText(s) {
  return { DRAFT: '待生成', PENDING: '待确认', CONFIRMED: '已确认' }[s] || s || '—'
}
</script>

<template>
  <section>
    <h2>我的绩效</h2>
    <p v-if="error" class="error">{{ error }}</p>

    <div class="row">
      <label class="inline">统计月份<input type="month" v-model="month" @change="load" /></label>
      <span class="muted">满勤（无请假无调班）计奖励；请假扣绩效；替他人顶班加绩效。</span>
    </div>

    <template v-if="perf && !perf.note">
      <div class="cards">
        <div class="card"><span>应出勤天数</span><strong>{{ perf.scheduled_days }}</strong></div>
        <div class="card"><span>请假天数</span><strong class="bad">{{ perf.leave_days }}</strong></div>
        <div class="card"><span>申请调班</span><strong>{{ perf.swap_applied_count }}</strong></div>
        <div class="card"><span>顶班次数</span><strong class="good">{{ perf.cover_shift_count }}</strong></div>
        <div class="card"><span>是否满勤</span><strong :class="perf.full_attendance ? 'good' : 'bad'">{{ perf.full_attendance ? '是' : '否' }}</strong></div>
        <div class="card"><span>最终得分</span><strong>{{ perf.final_score ?? '—' }}</strong></div>
      </div>

      <h3>绩效构成</h3>
      <table>
        <tbody>
          <tr><td>满勤奖励</td><td class="num good">+{{ Number(perf.attendance_bonus || 0).toFixed(0) }}</td></tr>
          <tr><td>请假扣款</td><td class="num bad">-{{ Number(perf.leave_penalty || 0).toFixed(0) }}</td></tr>
          <tr><td>顶班补贴</td><td class="num good">+{{ Number(perf.cover_bonus || 0).toFixed(0) }}</td></tr>
          <tr><td>基础工作绩效</td><td class="num">{{ Number(perf.performance_score || 0).toFixed(0) }}</td></tr>
          <tr><td>完成人次 / 治疗项目数</td><td class="num">{{ perf.completed_count }} / {{ perf.treatment_count }}</td></tr>
          <tr class="total"><td>最终得分</td><td class="num">{{ perf.final_score ?? '—' }}（{{ perf.reward_level }}）</td></tr>
        </tbody>
      </table>

      <p class="muted">复核状态：{{ reviewText(perf.review_status) }} · 统计区间 {{ perf.period_start }} ~ {{ perf.period_end }}</p>
    </template>
    <p v-else class="muted">{{ perf?.note || '加载中…' }}</p>

    <h3>本月顶班明细（{{ covers.length }} 次）</h3>
    <table v-if="covers.length">
      <thead><tr><th>日期</th><th>诊所</th><th>来源</th></tr></thead>
      <tbody>
        <tr v-for="c in covers" :key="c.shift_date">
          <td>{{ fmtDate(c.shift_date) }}</td>
          <td>{{ c.clinic_name }}</td>
          <td>{{ c.substitute_from === 'CROSS_CLINIC' ? '跨店支援' : '同店支援' }}</td>
        </tr>
      </tbody>
    </table>
    <p v-else class="muted">本月暂无顶班记录。</p>
  </section>
</template>

<style scoped>
.row { display: flex; justify-content: space-between; align-items: center; gap: 12px; flex-wrap: wrap; margin-bottom: 12px; }
.inline { display: flex; align-items: center; gap: 6px; margin: 0; font-size: 13px; }
.inline input { width: auto; }
.cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(130px, 1fr)); gap: 10px; margin: 10px 0 18px; }
.card { border: 1px solid #e3e8f0; border-radius: 10px; padding: 10px 14px; background: #fbfcfe; }
.card span { display: block; color: #6b7a90; font-size: 12px; }
.card strong { font-size: 20px; color: #1f3a5f; }
.good { color: #166534; }
.bad { color: #b91c1c; }
.num { text-align: right; }
tr.total td { font-weight: 700; border-top: 1px solid #e6eaf0; }
h3 { margin: 16px 0 6px; }
</style>
