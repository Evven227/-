<script setup>
import { computed, onMounted, ref } from 'vue'
import { patients, fmtDate } from '../api.js'

const props = defineProps({ account: { type: Object, required: true } })

const list = ref([])
const error = ref('')

const isPatient = computed(() => props.account.roles.includes('PATIENT'))

onMounted(load)

async function load() {
  error.value = ''
  try {
    list.value = await patients.list()
  } catch (e) {
    error.value = e.message
  }
}
</script>

<template>
  <div>
    <p v-if="error" class="error">{{ error }}</p>
    <div class="card">
      <h2>{{ isPatient ? '我的档案' : '患者档案' }}</h2>
      <p class="muted" v-if="isPatient">患者账号只能查看自己的档案。</p>
      <p class="muted" v-else-if="!list.length">本诊所暂无患者档案。</p>
      <table v-if="list.length">
        <thead>
          <tr><th>编号</th><th>姓名</th><th>档案号</th><th>出生日期</th><th>联系电话</th><th>邮箱</th><th>过敏评估</th></tr>
        </thead>
        <tbody>
          <tr v-for="p in list" :key="p.patient_id">
            <td>{{ p.patient_id }}</td>
            <td>{{ p.name }}</td>
            <td>{{ p.master_no }}</td>
            <td>{{ fmtDate(p.birth_date) }}</td>
            <td>{{ p.phone || '—' }}</td>
            <td>{{ p.email || '—' }}</td>
            <td><span class="tag">{{ p.allergy_assessment || '未评估' }}</span></td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
