<script setup>
import { computed, onMounted, ref } from 'vue'
import { shiftRequests, fmtTime } from '../api.js'

const props = defineProps({ account: { type: Object, required: true } })

const list = ref([])
const error = ref('')
const notice = ref('')
const busy = ref(false)
const detail = ref(null)
const reviewForm = ref({ action: 'COORDINATING', rejectionReason: '' })
const items = ref([]) // 协调建议项编辑区

const isDentist = computed(() => props.account.roles.includes('DENTIST'))
const isReviewer = computed(() =>
  props.account.roles.some(r => ['MANAGER'].includes(r)))

const form = ref({
  requestType: 'SHIFT_CHANGE',
  requestedDate: todayStr(),
  swapTargetDate: nextDayStr(),
  reason: ''
})

function todayStr() {
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}
function nextDayStr() {
  const d = new Date(Date.now() + 86400000)
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

onMounted(load)

async function load() {
  error.value = ''
  try {
    list.value = await shiftRequests.list()
  } catch (e) {
    error.value = e.message
  }
}

async function submit() {
  error.value = ''
  notice.value = ''
  if (!form.value.reason.trim()) {
    error.value = '请填写申请原因'
    return
  }
  if (!form.value.requestedDate) {
    error.value = '请选择需要调班的日期'
    return
  }
  if (form.value.swapTargetDate && form.value.swapTargetDate === form.value.requestedDate) {
    error.value = '补班日期不能与需要调班的日期相同'
    return
  }
  busy.value = true
  try {
    await shiftRequests.submit({
      requestType: form.value.requestType,
      reason: form.value.reason,
      requestedDate: form.value.requestedDate,
      swapTargetDate: form.value.swapTargetDate || null
    })
    notice.value = '调班申请已提交'
    form.value.reason = ''
    await load()
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = false
  }
}

async function openDetail(id) {
  error.value = ''
  try {
    detail.value = await shiftRequests.detail(id)
    reviewForm.value = { action: 'COORDINATING', rejectionReason: '' }
    items.value = (detail.value.items || []).map(i => ({
      shiftId: String(i.shift_id),
      proposedDentistId: i.proposed_dentist_id == null ? '' : String(i.proposed_dentist_id),
      proposedClinicId: i.proposed_clinic_id == null ? '' : String(i.proposed_clinic_id),
      proposedStartsAt: i.proposed_starts_at ? toLocalInput(i.proposed_starts_at) : '',
      proposedEndsAt: i.proposed_ends_at ? toLocalInput(i.proposed_ends_at) : ''
    }))
    if (!items.value.length) {
      addItem()
    }
  } catch (e) {
    error.value = e.message
  }
}

function addItem() {
  items.value.push({ shiftId: '', proposedDentistId: '', proposedClinicId: '',
    proposedStartsAt: '', proposedEndsAt: '' })
}

function removeItem(idx) {
  items.value.splice(idx, 1)
}

async function review() {
  error.value = ''
  notice.value = ''
  const action = reviewForm.value.action
  if (action === 'REJECT' && !reviewForm.value.rejectionReason.trim()) {
    error.value = '驳回必须填写原因'
    return
  }
  const needItems = action === 'COORDINATING' || action === 'IMPLEMENTED'
  if (needItems) {
    for (const it of items.value) {
      if (!it.shiftId) { error.value = '每条协调建议都必须选择班次'; return }
    }
    if (action === 'IMPLEMENTED' && !items.value.length) {
      error.value = '实施必须提供至少一条协调建议'
      return
    }
  }
  busy.value = true
  try {
    const payload = {
      action,
      rejectionReason: reviewForm.value.rejectionReason || null,
      items: needItems ? items.value.map(it => ({
        shiftId: Number(it.shiftId),
        proposedDentistId: it.proposedDentistId ? Number(it.proposedDentistId) : null,
        proposedClinicId: it.proposedClinicId ? Number(it.proposedClinicId) : null,
        proposedStartsAt: it.proposedStartsAt || null,
        proposedEndsAt: it.proposedEndsAt || null
      })) : null
    }
    await shiftRequests.review(detail.value.request_id, payload)
    notice.value = action === 'REJECT' ? '已驳回' : action === 'COORDINATING' ? '已登记协调建议' : '已实施，受影响预约已平移'
    detail.value = null
    await load()
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = false
  }
}

const statusText = {
  SUBMITTED: '待审核', REJECTED: '已驳回', COORDINATING: '协调中', IMPLEMENTED: '已实施'
}
const typeText = { SHIFT_CHANGE: '调班', LEAVE: '请假', OTHER: '其他' }

/** 申请时段：有 requested_date 时展示「调班日期 → 补班日期」，否则回退旧的时间区间。 */
function periodText(r) {
  const rd = r.requested_date ? String(r.requested_date).slice(0, 10) : ''
  const sd = r.swap_target_date ? String(r.swap_target_date).slice(0, 10) : ''
  if (rd && sd) return `${rd} 调班 → ${sd} 补班`
  if (rd) return `${rd} 调班`
  if (sd) return `补班 ${sd}`
  return `${fmtTime(r.requested_from)} ~ ${fmtTime(r.requested_to)}`
}
const resolutionText = {
  PENDING: '待处理', CONTACTED: '已联系患者', CONFIRMED: '患者已确认', RESOLVED: '已解决'
}

const canReview = computed(() =>
  isReviewer.value && detail.value && ['SUBMITTED', 'COORDINATING'].includes(detail.value.status))
</script>

<template>
  <div>
    <p v-if="notice" class="notice">{{ notice }}</p>
    <p v-if="error" class="error">{{ error }}</p>

    <!-- 牙医提交申请 -->
    <div class="card actions" v-if="isDentist">
      <h2>提交调班 / 请假申请</h2>
      <div class="form-grid">
        <label>申请类型
          <select v-model="form.requestType">
            <option value="SHIFT_CHANGE">调班</option>
            <option value="LEAVE">请假</option>
            <option value="OTHER">其他</option>
          </select>
        </label>
        <label></label>
        <label>需要调班的日期
          <input type="date" v-model="form.requestedDate" />
        </label>
        <label>补班日期
          <input type="date" v-model="form.swapTargetDate" />
        </label>
      </div>
      <p class="muted small">示例：原定 11-05 的班无法出诊，改到 11-08 补班——上面分别选 11-05 与 11-08。</p>
      <label>申请原因<input v-model="form.reason" placeholder="请填写调班原因" /></label>
      <button @click="submit" :disabled="busy">{{ busy ? '提交中…' : '提交申请' }}</button>
    </div>

    <!-- 申请列表 -->
    <div class="card">
      <h2>{{ isReviewer ? '调班申请审核' : '我的调班申请' }}</h2>
      <table>
        <thead>
          <tr><th>编号</th><th>申请人</th><th>类型</th><th>申请时段</th><th>原因</th><th>状态</th><th>审核人</th><th>操作</th></tr>
        </thead>
        <tbody>
          <tr v-for="r in list" :key="r.request_id">
            <td>{{ r.request_id }}</td>
            <td>{{ r.applicant_name }}（牙医 {{ r.applicant_dentist_id }}）</td>
            <td>{{ typeText[r.request_type] || r.request_type }}</td>
            <td>{{ periodText(r) }}</td>
            <td class="muted">{{ r.reason }}</td>
            <td><span class="tag">{{ statusText[r.status] || r.status }}</span></td>
            <td>{{ r.reviewer_name || '—' }}</td>
            <td>
              <button class="mini" @click="openDetail(r.request_id)">详情</button>
              <button class="mini" v-if="isReviewer && ['SUBMITTED','COORDINATING'].includes(r.status)"
                      @click="openDetail(r.request_id)">审核</button>
            </td>
          </tr>
          <tr v-if="!list.length"><td colspan="8" class="muted">暂无调班申请</td></tr>
        </tbody>
      </table>
    </div>

    <!-- 详情与审核 -->
    <div class="card actions" v-if="detail">
      <h2>调班申请 #{{ detail.request_id }} 详情</h2>
      <p>
        <b>状态：</b>{{ statusText[detail.status] || detail.status }}
        <span v-if="detail.reviewer_name">　<b>审核人：</b>{{ detail.reviewer_name }}</span>
        <span v-if="detail.rejection_reason">　<b>驳回原因：</b>{{ detail.rejection_reason }}</span>
      </p>
      <p class="muted">
        <b>申请时段：</b>{{ periodText(detail) }}
      </p>

      <h3>协调建议</h3>
      <table v-if="detail.items.length">
        <thead>
          <tr><th>原班次</th><th>原牙医</th><th>原时间</th><th v-if="canReview">建议牙医</th><th v-if="canReview">建议诊所</th><th v-if="canReview">建议开始</th><th v-if="canReview">建议结束</th><th>结果班次</th></tr>
        </thead>
        <tbody>
          <template v-for="(i, idx) in detail.items" :key="i.shift_id">
            <tr v-if="!canReview">
              <td>{{ i.shift_id }}</td>
              <td>{{ i.current_dentist_id }}</td>
              <td>{{ fmtTime(i.current_starts_at) }} ~ {{ fmtTime(i.current_ends_at) }}</td>
              <td>{{ i.result_shift_id || '—' }}</td>
            </tr>
            <tr v-else>
              <td>{{ i.shift_id }}</td>
              <td>{{ i.current_dentist_id }}</td>
              <td>{{ fmtTime(i.current_starts_at) }} ~ {{ fmtTime(i.current_ends_at) }}</td>
              <td><input v-model="items[idx].proposedDentistId" type="number" placeholder="牙医ID" /></td>
              <td><input v-model="items[idx].proposedClinicId" type="number" placeholder="诊所ID" /></td>
              <td><input v-model="items[idx].proposedStartsAt" type="datetime-local" /></td>
              <td><input v-model="items[idx].proposedEndsAt" type="datetime-local" /></td>
              <td>{{ i.result_shift_id || '—' }}</td>
            </tr>
          </template>
        </tbody>
      </table>
      <p v-else class="muted">暂无协调建议。</p>
      <button class="mini" v-if="canReview" @click="addItem">添加建议项</button>
      <button class="mini danger" v-for="(it, idx) in items" :key="idx" v-if="canReview && items.length > 1"
              @click="removeItem(idx)">删除第 {{ idx + 1 }} 项</button>

      <h3>受影响预约</h3>
      <table v-if="detail.impacts.length">
        <thead>
          <tr><th>预约编号</th><th>患者</th><th>预约时间</th><th>预约状态</th><th>处理状态</th></tr>
        </thead>
        <tbody>
          <tr v-for="x in detail.impacts" :key="x.appointment_id">
            <td>{{ x.appointment_id }}</td>
            <td>{{ x.patient_name }}</td>
            <td>{{ fmtTime(x.starts_at) }} ~ {{ fmtTime(x.ends_at) }}</td>
            <td>{{ x.status }}</td>
            <td><span class="tag">{{ resolutionText[x.resolution_status] || x.resolution_status }}</span></td>
          </tr>
        </tbody>
      </table>
      <p v-else class="muted">暂无受影响预约。</p>

      <template v-if="canReview">
        <div class="form-grid">
          <label>审核操作
            <select v-model="reviewForm.action">
              <option value="COORDINATING">登记协调建议</option>
              <option value="IMPLEMENTED">实施调整（平移受影响预约）</option>
              <option value="REJECT">驳回</option>
            </select>
          </label>
          <label>驳回原因（驳回时必填）
            <input v-model="reviewForm.rejectionReason" placeholder="驳回原因" />
          </label>
        </div>
        <button @click="review" :disabled="busy">{{ busy ? '处理中…' : '提交审核' }}</button>
      </template>
      <button class="link" @click="detail = null">收起详情</button>
    </div>
  </div>
</template>
