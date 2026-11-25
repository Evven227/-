<script setup>
import { onMounted, ref } from 'vue'
import { receptionPortal, fmtDate, fmtTime, todayLocal } from '../../api.js'

const props = defineProps({ account: { type: Object, required: true } })

const date = ref(todayLocal())
const list = ref([])
const reminders = ref([])
const error = ref('')
const notice = ref('')

onMounted(load)

async function load() {
  error.value = ''
  try {
    const [a, r] = await Promise.all([
      receptionPortal.appointments(date.value),
      receptionPortal.reminders()
    ])
    list.value = a
    reminders.value = r
  } catch (e) {
    error.value = e.message
  }
}

async function checkIn(id) {
  error.value = ''; notice.value = ''
  try {
    await receptionPortal.checkIn(id)
    notice.value = '已确认签到'
    await load()
  } catch (e) {
    error.value = e.message
  }
}

function statusText(s) {
  return { BOOKED: '已预约', CONFIRMED: '已确认', ARRIVED: '已到诊', COMPLETED: '已完成',
           CANCELLED: '已取消', NO_SHOW: '爽约' }[s] || s
}
</script>

<template>
  <section>
    <h2>预约管理与到诊签到</h2>
    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="notice" class="notice">{{ notice }}</p>

    <div class="row">
      <label class="inline">日期<input type="date" v-model="date" @change="load" /></label>
      <span class="muted">共 {{ list.length }} 条预约</span>
    </div>

    <h3>待到诊提醒（未来 24 小时）</h3>
    <table v-if="reminders.length">
      <thead><tr><th>就诊时间</th><th>预约号</th><th>患者</th><th>电话</th><th>提醒状态</th></tr></thead>
      <tbody>
        <tr v-for="r in reminders" :key="r.appointment_id">
          <td>{{ fmtTime(r.starts_at) }}<br /><span class="muted">{{ r.slot_name }}</span></td>
          <td>{{ r.booking_no || r.appointment_id }}</td>
          <td>{{ r.patient_name }}</td>
          <td>{{ r.phone }}</td>
          <td>
            <span v-if="r.reminder_status === 'SENT'" class="tag ok">已发送 {{ fmtTime(r.reminder_at) }}</span>
            <span v-else-if="r.reminder_status === 'PENDING'" class="tag wait">待发送 {{ fmtTime(r.reminder_at) }}</span>
            <span v-else class="muted">未生成</span>
          </td>
        </tr>
      </tbody>
    </table>
    <p v-else class="muted">未来 24 小时没有待到诊的患者。</p>

    <h3>{{ date }} 预约明细</h3>
    <table v-if="list.length">
      <thead>
        <tr>
          <th>时间</th><th>预约号</th><th>患者</th><th>电话</th><th>医生</th>
          <th>状态</th><th>账单</th><th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="a in list" :key="a.appointment_id">
          <td>{{ fmtTime(a.starts_at) }}<br /><span class="muted">{{ a.slot_name }}</span></td>
          <td>{{ a.booking_no || a.appointment_id }}</td>
          <td>{{ a.patient_name }}<br /><span class="muted small">{{ a.special_needs || '' }}</span></td>
          <td>{{ a.phone }}</td>
          <td>{{ a.dentist_name || '待分配' }}</td>
          <td>{{ statusText(a.status) }}</td>
          <td>{{ a.invoice_total ? '¥' + Number(a.invoice_total).toFixed(2) : '—' }}</td>
          <td>
            <button v-if="a.status === 'BOOKED' || a.status === 'CONFIRMED'" class="mini" @click="checkIn(a.appointment_id)">
              确认签到
            </button>
            <span v-else-if="a.checked_in" class="tag ok">已签到</span>
            <span v-else class="muted">—</span>
          </td>
        </tr>
      </tbody>
    </table>
    <p v-else class="muted">当日没有预约记录。</p>
  </section>
</template>

<style scoped>
.row { display: flex; justify-content: space-between; align-items: center; gap: 12px; margin-bottom: 10px; flex-wrap: wrap; }
.inline { display: flex; align-items: center; gap: 6px; margin: 0; font-size: 13px; }
.inline input { width: auto; }
h3 { margin: 16px 0 6px; }
.tag { display: inline-block; padding: 1px 8px; border-radius: 10px; font-size: 12px; }
.tag.ok { background: #dcfce7; color: #166534; }
.tag.wait { background: #fef9c3; color: #854d0e; }
.small { font-size: 12px; }
button.mini { padding: 3px 10px; font-size: 12px; }
</style>
