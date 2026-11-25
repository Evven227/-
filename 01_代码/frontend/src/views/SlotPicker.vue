<script setup>
import { ref, watch, onMounted } from 'vue'
import { shifts, fmtTime } from '../api.js'

const props = defineProps({
  dentistId: { type: [Number, String], default: '' },
  date: { type: String, default: '' },
  modelValue: { type: Object, default: null } // { shiftId, start, end }
})
const emit = defineEmits(['update:modelValue', 'loaded'])

const slots = ref([])
const error = ref('')
const loading = ref(false)

async function load() {
  error.value = ''
  slots.value = []
  if (!props.dentistId || !props.date) return
  loading.value = true
  try {
    slots.value = await shifts.availableSlots(Number(props.dentistId), props.date)
    emit('loaded', slots.value)
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

function pick(slot) {
  if (!slot.available) return
  emit('update:modelValue', { shiftId: slot.shiftId, start: slot.start, end: slot.end })
}

watch(() => [props.dentistId, props.date], load)
onMounted(load)
</script>

<template>
  <div class="slot-picker">
    <p v-if="loading" class="muted">加载可用时段…</p>
    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="!loading && !error && !slots.length" class="muted">
      该牙医当天没有已发布的班次，或暂无可预约时段。
    </p>
    <div class="slot-grid" v-if="slots.length">
      <button v-for="s in slots" :key="s.shiftId + s.start"
        class="slot"
        :class="{
          available: s.available,
          occupied: s.occupied,
          past: s.past,
          selected: modelValue && modelValue.start === s.start && modelValue.shiftId === s.shiftId
        }"
        :disabled="!s.available"
        @click="pick(s)">
        <span class="slot-time">{{ fmtTime(s.start) }}</span>
        <span v-if="s.occupied" class="slot-tag">占用</span>
        <span v-else-if="s.past" class="slot-tag">过去</span>
      </button>
    </div>
    <p v-if="modelValue" class="notice">
      已选时段：{{ fmtTime(modelValue.start) }} ~ {{ fmtTime(modelValue.end) }}
    </p>
  </div>
</template>

<style scoped>
.slot-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(110px, 1fr));
  gap: 8px;
  margin: 10px 0;
}
.slot {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  padding: 8px 4px;
  border: 1px solid #d7dfeb;
  background: #f4f8ff;
  color: #1f3a5f;
  border-radius: 8px;
  font-size: 13px;
  margin: 0;
  cursor: pointer;
}
.slot.available:hover { border-color: #2563eb; background: #e8f0ff; }
.slot.occupied, .slot.past { background: #eef1f6; color: #9aa7b8; cursor: not-allowed; border-style: dashed; }
.slot.selected { border-color: #2563eb; background: #2563eb; color: #fff; }
.slot-time { font-weight: 600; }
.slot-tag { font-size: 11px; opacity: 0.85; }
</style>
