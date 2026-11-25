<script setup>
import { computed, onMounted, ref } from 'vue'
import { managerPortal, shiftRequests, fmtDate } from '../../api.js'

const props = defineProps({ account: { type: Object, required: true } })

const pending = ref([])
const history = ref([])
const candidates = ref(null)
const current = ref(null)
const picked = ref(null)
const rejectReason = ref('')
const error = ref('')
const notice = ref('')
const busy = ref(false)

const sameClinic = computed(() => (candidates.value || []).filter(c => c.source_type === 'SAME_CLINIC'))
const crossClinic = computed(() => (candidates.value || []).filter(c => c.source_type === 'CROSS_CLINIC'))

onMounted(load)

async function load() {
  error.value = ''
  try {
    const [p, h] = await Promise.all([
      managerPortal.pendingRequests('SUBMITTED'),
      managerPortal.pendingRequests('IMPLEMENTED')
    ])
    pending.value = p
    history.value = h
  } catch (e) {
    error.value = e.message
  }
}

async function openCoordination(r) {
  error.value = ''; notice.value = ''
  current.value = r
  picked.value = null
  rejectReason.value = ''
  try {
    candidates.value = await managerPortal.candidates(r.request_id)
  } catch (e) {
    error.value = e.message
    candidates.value = null
  }
}

async function approve() {
  if (!current.value || !picked.value) return
  busy.value = true
  error.value = ''; notice.value = ''
  try {
    // 顶班只是换医生，班次时间不变：不传 proposedStartsAt/EndsAt，
    // 后端沿用原班次时间（delta=0），预约只改派医生、时间原样保留。
    // 若传后端返回的 original_starts_at（形如 00:00+00:00）会造成 8 小时时区偏移，
    // 把预约时间整体平移，务必避免。
    await shiftRequests.review(current.value.request_id, {
      action: 'IMPLEMENTED',
      items: [{
        shiftId: current.value.original_shift_id,
        proposedDentistId: picked.value.staff_id,
        // 顶班是周医生去郑医生原岗位上班，诊所保持原班次的诊所（申请人的诊所），
        // 不能用顶班医生的本店，否则跨店顶班会把班次诊所改错。
        proposedClinicId: current.value.clinic_id
      }]
    })
    notice.value = `已安排 ${picked.value.name} 顶班，该日原预约患者已改由 ${picked.value.name} 接诊`
    current.value = null
    candidates.value = null
    picked.value = null
    await load()
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = false
  }
}

async function reject() {
  if (!current.value) return
  if (!rejectReason.value.trim()) { error.value = '驳回必须填写原因'; return }
  busy.value = true
  error.value = ''
  try {
    await shiftRequests.review(current.value.request_id, {
      action: 'REJECT', rejectionReason: rejectReason.value
    })
    notice.value = '已驳回该申请'
    current.value = null
    candidates.value = null
    await load()
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = false
  }
}

function typeText(t) {
  return { LEAVE: '请假', SHIFT_CHANGE: '调班/对调', OTHER: '其他' }[t] || t
}
function statusText(s) {
  return { SUBMITTED: '待审核', COORDINATING: '协调中', IMPLEMENTED: '已实施', REJECTED: '已驳回' }[s] || s
}
</script>

<template>
  <section>
    <h2>调班与请假审核</h2>
    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="notice" class="notice">{{ notice }}</p>

    <h3>待审核（{{ pending.length }} 条）</h3>
    <table v-if="pending.length">
      <thead>
        <tr><th>申请ID</th><th>类型</th><th>申请人</th><th>诊所</th><th>原日期</th><th>目标日期</th><th>原因</th><th>操作</th></tr>
      </thead>
      <tbody>
        <tr v-for="r in pending" :key="r.request_id" :class="{ active: current && current.request_id === r.request_id }">
          <td>{{ r.request_id }}</td>
          <td>{{ typeText(r.request_type) }}</td>
          <td>{{ r.applicant_name }}</td>
          <td>{{ r.clinic_name }}</td>
          <td>{{ fmtDate(r.requested_date) }}</td>
          <td>{{ r.swap_target_date ? fmtDate(r.swap_target_date) : '—' }}</td>
          <td class="small">{{ r.reason }}</td>
          <td><button class="mini" @click="openCoordination(r)">协调与审批</button></td>
        </tr>
      </tbody>
    </table>
    <p v-else class="muted">当前没有待审核的申请。</p>

    <div v-if="current" class="panel">
      <div class="row">
        <h3>为「{{ current.applicant_name }}」的 {{ typeText(current.request_type) }} 安排顶班</h3>
        <button class="link" @click="current = null; candidates = null">收起</button>
      </div>
      <p class="muted">
        日期 {{ fmtDate(current.requested_date) }} ·
        优先安排<strong>同一诊所当天休假</strong>的医生，不方便时再安排<strong>另一家店休假</strong>的医生。
      </p>

      <template v-if="candidates && candidates.length">
        <h4>本店可顶班（{{ sameClinic.length }} 人）</h4>
        <div class="chips">
          <button v-for="c in sameClinic" :key="c.staff_id"
                  :class="['chip', { on: picked && picked.staff_id === c.staff_id }]"
                  @click="picked = c">
            {{ c.name }}<span class="muted small"> · 本月已顶班 {{ c.cover_count_month }} 次</span>
          </button>
          <span v-if="!sameClinic.length" class="muted">本店当天没有可安排的休假医生。</span>
        </div>

        <h4>跨店可顶班（{{ crossClinic.length }} 人）</h4>
        <div class="chips">
          <button v-for="c in crossClinic" :key="c.staff_id"
                  :class="['chip cross', { on: picked && picked.staff_id === c.staff_id }]"
                  @click="picked = c">
            {{ c.name }}（{{ c.clinic_name }}）<span class="muted small"> · 本月已顶班 {{ c.cover_count_month }} 次</span>
          </button>
          <span v-if="!crossClinic.length" class="muted">其他门店当天没有可安排的休假医生。</span>
        </div>

        <p v-if="!current.original_shift_id" class="error">该申请人当天没有可调整的班次，无法实施顶班。</p>

        <div class="actions">
          <button :disabled="!picked || !current.original_shift_id || busy" @click="approve">
            确认由 {{ picked ? picked.name : '…' }} 顶班
          </button>
          <input v-model="rejectReason" placeholder="驳回原因（必填）" />
          <button class="secondary" :disabled="busy" @click="reject">驳回申请</button>
        </div>
      </template>
      <p v-else-if="candidates" class="error">当天没有其他休假的医生可以顶班，请考虑驳回或手动协调。</p>
    </div>

    <h3>已实施（{{ history.length }} 条）</h3>
    <table v-if="history.length">
      <thead><tr><th>申请ID</th><th>类型</th><th>申请人</th><th>诊所</th><th>日期</th><th>状态</th></tr></thead>
      <tbody>
        <tr v-for="r in history" :key="r.request_id">
          <td>{{ r.request_id }}</td>
          <td>{{ typeText(r.request_type) }}</td>
          <td>{{ r.applicant_name }}</td>
          <td>{{ r.clinic_name }}</td>
          <td>{{ fmtDate(r.requested_date) }}</td>
          <td>{{ statusText(r.status) }}</td>
        </tr>
      </tbody>
    </table>
    <p v-else class="muted">暂无实施记录。</p>
  </section>
</template>

<style scoped>
.row { display: flex; justify-content: space-between; align-items: center; gap: 12px; flex-wrap: wrap; }
.panel { margin-top: 14px; border: 1px solid #e3e8f0; border-radius: 10px; padding: 12px 16px; background: #fbfcfe; }
.panel h3 { margin-top: 0; }
.panel h4 { margin: 12px 0 6px; font-size: 14px; }
.chips { display: flex; flex-wrap: wrap; gap: 8px; }
.chip {
  border: 1px solid #c7d2e0; background: #fff; border-radius: 999px;
  padding: 6px 14px; cursor: pointer; font-size: 13px;
  color: #172033; /* 覆盖全局 button 白色字 */
}
.chip:hover { border-color: #2563eb; }
.chip.on { background: #2563eb; color: #fff; border-color: #2563eb; }
.chip.cross { border-style: dashed; }
.actions { display: flex; gap: 10px; align-items: center; margin-top: 16px; flex-wrap: wrap; }
.actions input { flex: 1 1 200px; }
.small { font-size: 12px; }
tr.active { background: #eff6ff; }
button.mini { padding: 3px 10px; font-size: 12px; }
button.link { border: none; background: none; color: #2563eb; padding: 0; cursor: pointer; }
h3 { margin: 16px 0 6px; }
</style>
