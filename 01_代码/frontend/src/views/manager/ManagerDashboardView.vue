<script setup>
import { computed, onMounted, ref } from 'vue'
import { managerPortal, fmtDate, todayLocal } from '../../api.js'

const props = defineProps({ account: { type: Object, required: true } })

const rows = ref([])
const summary = ref(null)
const duty = ref([])
const error = ref('')
const today = todayLocal()
const localDaysAgo = (n) => { const d = new Date(); d.setDate(d.getDate() - n); const p = (x) => String(x).padStart(2, '0'); return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}` }
const from = ref(localDaysAgo(6))
const to = ref(today)
const dutyDate = ref(today)

const total = computed(() => summary.value?.total || {})
const byClinic = computed(() => summary.value?.byClinic || [])
const arrivalRate = computed(() => {
  const b = Number(total.value.booked || 0)
  return b > 0 ? ((Number(total.value.arrived || 0) / b) * 100).toFixed(1) : '—'
})

onMounted(loadAll)

async function loadAll() {
  error.value = ''
  try {
    const [d, s, t] = await Promise.all([
      managerPortal.dashboard(from.value, to.value),
      managerPortal.summary(from.value, to.value),
      managerPortal.staffOnDuty(dutyDate.value)
    ])
    rows.value = d
    summary.value = s
    duty.value = t
  } catch (e) {
    error.value = e.message
  }
}

function n(v) { return Number(v || 0) }
</script>

<template>
  <section>
    <h2>营业看板（两家诊所）</h2>
    <p v-if="error" class="error">{{ error }}</p>

    <div class="row">
      <label class="inline">统计区间
        <input type="date" v-model="from" @change="loadAll" /> ~
        <input type="date" v-model="to" @change="loadAll" />
      </label>
      <span class="muted">利润 = 营业额 − 耗材成本</span>
    </div>

    <div class="cards">
      <div class="card"><span>营业额</span><strong>¥{{ n(total.revenue).toFixed(2) }}</strong></div>
      <div class="card"><span>耗材成本</span><strong class="cost">¥{{ n(total.cost).toFixed(2) }}</strong></div>
      <div class="card"><span>利润</span><strong class="profit">¥{{ n(total.profit).toFixed(2) }}</strong></div>
      <div class="card"><span>预约数</span><strong>{{ n(total.booked) }}</strong></div>
      <div class="card"><span>到诊数</span><strong>{{ n(total.arrived) }}</strong></div>
      <div class="card"><span>到诊率</span><strong>{{ arrivalRate }}%</strong></div>
      <div class="card"><span>取消 / 爽约</span><strong>{{ n(total.cancelled) }} / {{ n(total.noshow) }}</strong></div>
    </div>

    <h3>各诊所经营对比</h3>
    <table v-if="byClinic.length">
      <thead>
        <tr><th>诊所</th><th>营业额</th><th>耗材成本</th><th>利润</th><th>预约</th><th>到诊</th></tr>
      </thead>
      <tbody>
        <tr v-for="c in byClinic" :key="c.clinic_id">
          <td>{{ c.clinic_name }}</td>
          <td>¥{{ n(c.revenue).toFixed(2) }}</td>
          <td>¥{{ n(c.cost).toFixed(2) }}</td>
          <td class="profit">¥{{ n(c.profit).toFixed(2) }}</td>
          <td>{{ n(c.booked) }}</td>
          <td>{{ n(c.arrived) }}</td>
        </tr>
      </tbody>
    </table>

    <h3>每日明细</h3>
    <table v-if="rows.length">
      <thead>
        <tr>
          <th>日期</th><th>诊所</th><th>预约</th><th>到诊</th><th>取消</th><th>爽约</th>
          <th>值班医生</th><th>值班前台</th><th>营业额</th><th>成本</th><th>利润</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="r in rows" :key="r.clinic_id + r.biz_date">
          <td>{{ fmtDate(r.biz_date) }}</td>
          <td>{{ r.clinic_name }}</td>
          <td>{{ n(r.booked_cnt) }}</td>
          <td>{{ n(r.arrived_cnt) }}</td>
          <td>{{ n(r.cancelled_cnt) }}</td>
          <td>{{ n(r.noshow_cnt) }}</td>
          <td>{{ n(r.duty_dentist_cnt) }}</td>
          <td>{{ n(r.duty_reception_cnt) }}</td>
          <td>¥{{ n(r.revenue).toFixed(2) }}</td>
          <td>¥{{ n(r.cost).toFixed(2) }}</td>
          <td class="profit">¥{{ n(r.profit).toFixed(2) }}</td>
        </tr>
      </tbody>
    </table>
    <p v-else class="muted">所选区间没有营业数据。</p>

    <div class="row">
      <h3>当日出勤情况</h3>
      <label class="inline">日期<input type="date" v-model="dutyDate" @change="loadAll" /></label>
    </div>
    <table v-if="duty.length">
      <thead><tr><th>诊所</th><th>岗位</th><th>在岗人数</th><th>值班人员</th></tr></thead>
      <tbody>
        <tr v-for="d in duty" :key="d.clinic_id + d.role_type">
          <td>{{ d.clinic_name }}</td>
          <td>{{ d.role_type === 'DENTIST' ? '医生' : '前台' }}</td>
          <td>{{ d.headcount }}</td>
          <td>{{ d.staff_names }}</td>
        </tr>
      </tbody>
    </table>
    <p v-else class="muted">当日无排班数据。</p>
  </section>
</template>

<style scoped>
.row { display: flex; justify-content: space-between; align-items: center; gap: 12px; flex-wrap: wrap; margin: 8px 0; }
.inline { display: flex; align-items: center; gap: 6px; margin: 0; font-size: 13px; }
.inline input { width: auto; }
.cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(140px, 1fr)); gap: 10px; margin: 12px 0 18px; }
.card { border: 1px solid #e3e8f0; border-radius: 10px; padding: 10px 14px; background: #fbfcfe; }
.card span { display: block; color: #6b7a90; font-size: 12px; }
.card strong { font-size: 20px; color: #1f3a5f; }
.profit { color: #166534; }
.cost { color: #b45309; }
h3 { margin: 16px 0 6px; }
</style>
