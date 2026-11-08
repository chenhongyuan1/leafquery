<script setup>
defineProps({ name: { type: String, required: true } })
const paths = {
  arrow: 'M12 20V4m-7 7 7-7 7 7',
  microphone: 'M9 5a3 3 0 0 1 6 0v6a3 3 0 0 1-6 0V5Zm-4 6a7 7 0 0 0 14 0m-7 7v4m-4 0h8',
  plus: 'M12 5v14M5 12h14',
  close: 'm6 6 12 12M18 6 6 18',
  trash: 'M3 6h18M9 6V3h6v3M5 6l1 15h12l1-15M10 10v7m4-7v7',
  check: 'm5 12 4 4L19 6',
  chevron: 'm8 10 4 4 4-4',
  scan: 'M8 3H5a2 2 0 0 0-2 2v3m13-5h3a2 2 0 0 1 2 2v3M3 16v3a2 2 0 0 0 2 2h3m8 0h3a2 2 0 0 0 2-2v-3M7 12h10m-5-4v8',
  chat: 'M8 19H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-6l-5 3v-3Zm0-11h8m-8 5h5',
  history: 'M3 11a9 9 0 1 1 2.6 7M3 4v7h7m2-4v5l3 2',
  panel: 'M14 3v18M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2Zm12 5 2 4-2 4'
}
</script>

<template>
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path :d="paths[name]" /></svg>
</template>
