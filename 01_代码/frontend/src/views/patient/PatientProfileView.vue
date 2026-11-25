<script setup>
import { computed, onMounted, ref } from 'vue'
import { patientPortal, fmtDate } from '../../api.js'

const props = defineProps({ account: { type: Object, required: true } })
const profile = ref(null)
const allergies = ref([])
const history = ref([])
const error = ref('')

// 把同一就诊的多条治疗项目聚合成一次就诊记录
const visits = computed(() => {
  const map = new Map()
  for (const r of history.value) {
    if (!map.has(r.encounter_id)) {
      map.set(r.encounter_id, {
        encounter_id: r.encounter_id, status: r.status, arrived_at: r.arrived_at,
        chief_complaint: r.chief_complaint, diagnosis: r.diagnosis,
        dentist_name: r.dentist_name, clinic_name: r.clinic_name, lines: []
      })
    }
    if (r.treatment_line_id) {
      map.get(r.encounter_id).lines.push({
        id: r.treatment_line_id, name: r.name_snapshot, qty: r.quantity,
        price: r.unit_price_snapshot, notes: r.line_notes
      })
    }
  }
  return [...map.values()]
})

const totalSpent = computed(() =>
  visits.value.reduce((sum, v) =>
    sum + v.lines.reduce((s, l) => s + Number(l.qty) * Number(l.price || 0), 0), 0))

onMounted(load)

async function load() {
  error.value = ''
  try {
    const [p, h] = await Promise.all([patientPortal.profile(), patientPortal.history()])
    profile.value = p.profile
    allergies.value = p.allergies
    history.value = h
  } catch (e) {
    error.value = e.message
  }
}

function statusText(s) {
  return { IN_PROGRESS: '就诊中', COMPLETED: '已完成', CANCELLED: '已取消' }[s] || s
}
</script>

<template>
  <section>
    <h2>个人资料</h2>
    <p v-if="error" class="error">{{ error }}</p>

    <div v-if="profile" class="grid">
      <div class="card-mini">
        <h3>基本信息</h3>
        <p><span class="label">病历号</span>{{ profile.master_no }}</p>
        <p><span class="label">姓名</span>{{ profile.name }}</p>
        <p><span class="label">出生日期</span>{{ profile.birth_date || '—' }}</p>
        <p><span class="label">联系电话</span>{{ profile.phone }}</p>
        <p><span class="label">邮箱</span>{{ profile.email || '—' }}</p>
        <p><span class="label">地址</span>{{ profile.address || '—' }}</p>
        <p><span class="label">紧急联系人</span>{{ profile.emergency_name || '—' }} {{ profile.emergency_phone || '' }}</p>
        <p><span class="label">特殊需求</span>{{ profile.special_needs || '—' }}</p>
      </div>

      <div class="card-mini">
        <h3>过敏原</h3>
        <table v-if="allergies.length">
          <thead><tr><th>过敏原</th><th>反应</th><th>备注</th></tr></thead>
          <tbody>
            <tr v-for="a in allergies" :key="a.allergen">
              <td>{{ a.allergen }}</td><td>{{ a.reaction }}</td><td>{{ a.notes || '—' }}</td>
            </tr>
          </tbody>
        </table>
        <p v-else class="muted">已做过敏评估，暂无过敏记录。</p>
      </div>
    </div>

    <h3>历史治疗信息（累计 {{ totalSpent.toFixed(2) }} 元 · {{ visits.length }} 次就诊）</h3>
    <div v-for="v in visits" :key="v.encounter_id" class="visit">
      <div class="visit-head">
        <strong>{{ fmtDate(v.arrived_at) }}</strong>
        <span class="tag">{{ statusText(v.status) }}</span>
        <span class="muted">{{ v.clinic_name }} · {{ v.dentist_name }}</span>
      </div>
      <p v-if="v.chief_complaint" class="muted">主诉：{{ v.chief_complaint }}</p>
      <p v-if="v.diagnosis" class="muted">诊断：{{ v.diagnosis }}</p>
      <table v-if="v.lines.length">
        <thead><tr><th>治疗项目</th><th>数量</th><th>单价</th><th>小计</th><th>备注</th></tr></thead>
        <tbody>
          <tr v-for="l in v.lines" :key="l.id">
            <td>{{ l.name }}</td>
            <td>{{ l.qty }}</td>
            <td>¥{{ Number(l.price).toFixed(2) }}</td>
            <td>¥{{ (Number(l.qty) * Number(l.price)).toFixed(2) }}</td>
            <td>{{ l.notes || '—' }}</td>
          </tr>
        </tbody>
      </table>
      <p v-else class="muted">本次就诊暂无治疗项目记录。</p>
    </div>
    <p v-if="!visits.length" class="muted">暂无就诊记录。</p>
  </section>
</template>

<style scoped>
.grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(320px, 1fr)); gap: 14px; }
.card-mini { border: 1px solid #e3e8f0; border-radius: 10px; padding: 12px 16px; background: #fbfcfe; }
.card-mini h3 { margin-top: 0; }
.card-mini p { margin: 4px 0; }
.label { display: inline-block; width: 86px; color: #6b7a90; font-size: 13px; }
.visit { border-top: 1px solid #eceff4; padding: 12px 0; }
.visit-head { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; margin-bottom: 6px; }
.tag { background: #e8f0ff; color: #1e40af; padding: 1px 8px; border-radius: 10px; font-size: 12px; }
</style>
