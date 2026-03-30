<script setup>
import { computed } from 'vue'
import { hasAnySelection, toggleSelectionItem } from '../../utils/diagnosisSelection'

const props = defineProps({
  cropOptions: {
    type: Array,
    default: () => []
  },
  targetOptions: {
    type: Array,
    default: () => []
  },
  selectedCropNames: {
    type: Array,
    default: () => []
  },
  selectedTargetNames: {
    type: Array,
    default: () => []
  },
  loading: {
    type: Boolean,
    default: false
  },
  actionLabel: {
    type: String,
    default: '确认选择并生成诊断'
  },
  selectionConflict: {
    type: Boolean,
    default: false
  },
  selectionConflictReason: {
    type: String,
    default: ''
  },
  reviewRequired: {
    type: Boolean,
    default: false
  },
  reviewReason: {
    type: String,
    default: ''
  }
})

const emit = defineEmits([
  'update:selectedCropNames',
  'update:selectedTargetNames',
  'submit'
])

const canSubmit = computed(() => hasAnySelection(props.selectedCropNames, props.selectedTargetNames) && !props.loading)

const toggleCrop = cropName => {
  emit('update:selectedCropNames', toggleSelectionItem(props.selectedCropNames, cropName))
}

const toggleTarget = targetName => {
  emit('update:selectedTargetNames', toggleSelectionItem(props.selectedTargetNames, targetName))
}
</script>

<template>
  <section class="rounded-3xl border border-slate-200 bg-white/95 p-5 shadow-sm dark:border-slate-700 dark:bg-slate-900/90">
    <div class="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
      <div>
        <h3 class="text-base font-bold text-slate-900 dark:text-slate-100">补充作物与病虫害线索</h3>
        <p class="text-sm text-slate-500 dark:text-slate-400">
          至少选择一项。你的选择会作为 Dify 复核提示，不会直接覆盖视觉结果。
        </p>
      </div>
      <div class="rounded-full bg-slate-100 px-3 py-1 text-xs font-bold text-slate-500 dark:bg-slate-800 dark:text-slate-300">
        多选
      </div>
    </div>

    <div class="mt-4">
      <div class="mb-2 text-xs font-bold uppercase tracking-wide text-slate-400 dark:text-slate-500">作物</div>
      <div class="flex flex-wrap gap-2">
        <button
          v-for="option in cropOptions"
          :key="option.value"
          type="button"
          class="rounded-full border px-3 py-2 text-sm font-medium transition-all"
          :class="selectedCropNames.includes(option.label)
            ? 'border-emerald-500 bg-emerald-50 text-emerald-700 dark:border-emerald-400 dark:bg-emerald-500/15 dark:text-emerald-200'
            : 'border-slate-200 bg-slate-50 text-slate-600 hover:border-emerald-300 hover:text-emerald-600 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-300'"
          @click="toggleCrop(option.label)"
        >
          {{ option.label }}
        </button>
      </div>
    </div>

    <div class="mt-4">
      <div class="mb-2 text-xs font-bold uppercase tracking-wide text-slate-400 dark:text-slate-500">病虫害对象</div>
      <div v-if="targetOptions.length" class="flex flex-wrap gap-2">
        <button
          v-for="option in targetOptions"
          :key="option.label"
          type="button"
          class="rounded-full border px-3 py-2 text-sm font-medium transition-all"
          :class="selectedTargetNames.includes(option.label)
            ? 'border-slate-900 bg-slate-900 text-white dark:border-emerald-400 dark:bg-emerald-400 dark:text-slate-950'
            : 'border-slate-200 bg-slate-50 text-slate-600 hover:border-slate-300 hover:text-slate-900 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-300'"
          @click="toggleTarget(option.label)"
        >
          {{ option.label }}
        </button>
      </div>
      <p v-else class="text-sm text-slate-400 dark:text-slate-500">当前没有可选的病虫害候选，可以只选择作物后继续。</p>
    </div>

    <div v-if="selectionConflictReason" class="mt-4 rounded-2xl border px-4 py-3 text-sm"
         :class="selectionConflict
           ? 'border-orange-200 bg-orange-50 text-orange-700 dark:border-orange-500/30 dark:bg-orange-500/10 dark:text-orange-200'
           : 'border-slate-200 bg-slate-50 text-slate-500 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-300'">
      {{ selectionConflictReason }}
    </div>

    <div v-if="reviewReason" class="mt-3 rounded-2xl border px-4 py-3 text-sm"
         :class="reviewRequired
           ? 'border-amber-200 bg-amber-50 text-amber-700 dark:border-amber-500/30 dark:bg-amber-500/10 dark:text-amber-200'
           : 'border-slate-200 bg-slate-50 text-slate-500 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-300'">
      {{ reviewReason }}
    </div>

    <button
      type="button"
      class="mt-5 w-full rounded-2xl px-4 py-3.5 text-sm font-bold transition-all"
      :class="canSubmit
        ? 'bg-slate-900 text-white hover:bg-black dark:bg-emerald-400 dark:text-slate-950 dark:hover:bg-emerald-300'
        : 'cursor-not-allowed bg-slate-200 text-slate-400 dark:bg-slate-800 dark:text-slate-500'"
      :disabled="!canSubmit"
      @click="$emit('submit')"
    >
      {{ loading ? '正在生成诊断…' : actionLabel }}
    </button>
  </section>
</template>
