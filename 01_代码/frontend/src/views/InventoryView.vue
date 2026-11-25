<script setup>
import { computed, onMounted, ref } from 'vue'
import { inventory, fmtTime } from '../api.js'

const props = defineProps({ account: { type: Object, required: true } })

const stock = ref([])
const lowStock = ref([])
const movements = ref([])
const error = ref('')
const notice = ref('')
const busy = ref(false)
const form = ref({
  clinicId: props.account.clinicId || '',
  materialId: '',
  movementType: 'INBOUND',
  quantity: 1,
  reason: ''
})

const isHq = computed(() => props.account.roles.includes('MANAGER'))

onMounted(load)

async function load() {
  error.value = ''
  try {
    const [s, l, m] = await Promise.all([
      inventory.stock(), inventory.lowStock(), inventory.movements()
    ])
    stock.value = s
    lowStock.value = l
    movements.value = m
  } catch (e) {
    error.value = e.message
  }
}

async function submit() {
  error.value = ''
  notice.value = ''
  if (!form.value.clinicId) {
    error.value = '请填写诊所编号'
    return
  }
  if (!form.value.materialId) {
    error.value = '请填写物料编号'
    return
  }
  if (!form.value.reason.trim()) {
    error.value = '请填写变动原因'
    return
  }
  busy.value = true
  try {
    await inventory.createMovement({
      clinicId: Number(form.value.clinicId),
      materialId: Number(form.value.materialId),
      movementType: form.value.movementType,
      quantity: Number(form.value.quantity),
      reason: form.value.reason
    })
    notice.value = '库存变动已登记'
    form.value = { ...form.value, materialId: '', quantity: 1, reason: '' }
    await load()
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = false
  }
}

const typeText = { INBOUND: '入库', USAGE: '领用', ADJUSTMENT: '盘点调整' }
</script>

<template>
  <div>
    <p v-if="notice" class="notice">{{ notice }}</p>
    <p v-if="error" class="error">{{ error }}</p>

    <!-- 库存预警 -->
    <div class="card actions" v-if="lowStock.length">
      <h2>库存预警（{{ lowStock.length }} 项低于预警值）</h2>
      <table>
        <thead>
          <tr><th>诊所</th><th>物料</th><th>单位</th><th>现有库存</th><th>预警值</th></tr>
        </thead>
        <tbody>
          <tr v-for="s in lowStock" :key="s.clinic_id + '-' + s.material_id">
            <td>{{ s.clinic_name }}（{{ s.clinic_id }}）</td>
            <td>{{ s.material_name }}（{{ s.material_code }}）</td>
            <td>{{ s.unit }}</td>
            <td class="error">{{ s.on_hand }}</td>
            <td>{{ s.warning_threshold }}</td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 库存变动登记 -->
    <div class="card actions">
      <h2>登记库存变动</h2>
      <div class="form-grid">
        <label>诊所编号
          <input v-model="form.clinicId" type="number" :disabled="!isHq" />
        </label>
        <label>物料编号<input v-model="form.materialId" type="number" placeholder="MATERIAL.material_id" /></label>
        <label>变动类型
          <select v-model="form.movementType">
            <option value="INBOUND">入库</option>
            <option value="USAGE">领用</option>
            <option value="ADJUSTMENT">盘点调整（数量可为负）</option>
          </select>
        </label>
        <label>数量<input v-model="form.quantity" type="number" step="0.001" /></label>
      </div>
      <label>变动原因<input v-model="form.reason" placeholder="如：例行补货 / 治疗领用 / 盘点差异" /></label>
      <button @click="submit" :disabled="busy">{{ busy ? '提交中…' : '登记变动' }}</button>
    </div>

    <!-- 库存列表 -->
    <div class="card">
      <h2>库存列表</h2>
      <table>
        <thead>
          <tr><th>诊所</th><th>物料</th><th>单位</th><th>现有库存</th><th>预警值</th><th>状态</th></tr>
        </thead>
        <tbody>
          <tr v-for="s in stock" :key="s.clinic_id + '-' + s.material_id">
            <td>{{ s.clinic_name }}（{{ s.clinic_id }}）</td>
            <td>{{ s.material_name }}（{{ s.material_code }}）</td>
            <td>{{ s.unit }}</td>
            <td>{{ s.on_hand }}</td>
            <td>{{ s.warning_threshold }}</td>
            <td><span class="tag" :class="{ warn: s.low_stock }">{{ s.low_stock ? '低于预警' : '正常' }}</span></td>
          </tr>
          <tr v-if="!stock.length"><td colspan="6" class="muted">暂无库存记录</td></tr>
        </tbody>
      </table>
    </div>

    <!-- 出入库记录 -->
    <div class="card">
      <h2>最近出入库记录</h2>
      <table>
        <thead>
          <tr><th>编号</th><th>诊所</th><th>物料</th><th>类型</th><th>数量</th><th>原因</th><th>时间</th></tr>
        </thead>
        <tbody>
          <tr v-for="m in movements" :key="m.movement_id">
            <td>{{ m.movement_id }}</td>
            <td>{{ m.clinic_name }}（{{ m.clinic_id }}）</td>
            <td>{{ m.material_name }}</td>
            <td>{{ typeText[m.movement_type] || m.movement_type }}</td>
            <td>{{ m.quantity_delta }}</td>
            <td class="muted">{{ m.reason }}</td>
            <td>{{ fmtTime(m.occurred_at) }}</td>
          </tr>
          <tr v-if="!movements.length"><td colspan="7" class="muted">暂无出入库记录</td></tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
