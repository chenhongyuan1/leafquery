<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import PromptIcon from './PromptIcon.vue'
import { categoryOptions } from '../../utils/detectionCategories.js'

const props = defineProps({
  placeholder: { type: String, default: '描述问题，或添加一张图片…' },
  submitMessage: { type: String, default: '输入已准备好，识别与问答服务尚未接入。' },
  followUpAvailable: { type: Boolean, default: false },
  busy: { type: Boolean, default: false },
  voiceConnected: { type: Boolean, default: false },
  voiceRecording: { type: Boolean, default: false },
  voiceBusy: { type: Boolean, default: false },
  models: { type: Array, default: () => ['GPT 5.5', 'Opus 4.8', 'Gemini 3.5 Flash', 'Composer 2.5', 'GLM 5.2'] },
  efforts: { type: Array, default: () => ['低', '中', '高'] },
  maxAttachments: { type: Number, default: 6 }
})
const emit = defineEmits(['submit', 'voice-request'])
const root = ref(null)
const textarea = ref(null)
const fileInput = ref(null)
const modelButton = ref(null)
const modelMenu = ref(null)
const dialogClose = ref(null)
const categoryButton = ref(null)
const categoryMenu = ref(null)
const categoryMenuStyle = ref({})
const expanded = ref(false)
const mode = ref('ask')
const askDraft = ref('')
const isIdentify = computed(() => mode.value === 'identify')
const text = computed({
  get: () => isIdentify.value ? '' : askDraft.value,
  set: value => { if (!isIdentify.value) askDraft.value = value }
})
const inputPlaceholder = computed(() => isIdentify.value ? '请添加图片' : props.placeholder)
const categoryMenuOpen = ref(false)
const selectedCategories = ref([])
const selectedCategoryOptions = computed(() => categoryOptions.filter(option => selectedCategories.value.includes(option.value)))
const textareaHeight = ref(80)
const selectedModel = ref(props.models[0] || '')
const effortIndex = ref(props.efforts.length > 1 ? 1 : 0)
const menuOpen = ref(false)
const attachmentDrafts = { ask: ref([]), identify: ref([]) }
const attachments = computed({
  get: () => attachmentDrafts[mode.value].value,
  set: value => { attachmentDrafts[mode.value].value = value }
})
const attachmentLimit = computed(() => isIdentify.value ? 1 : props.maxAttachments)
const hasInlineAttachments = computed(() => isIdentify.value && attachments.value.length > 0)
const activeAttachment = ref(null)
const galleryStyle = ref({})
const status = ref('')
const composing = ref(false)
const effort = computed(() => props.efforts[effortIndex.value] || '')
const hasContent = computed(() => Boolean(text.value.trim() || attachments.value.length))
const showSend = computed(() => isIdentify.value || hasContent.value)
let attachmentId = 0
let previewOrigin = null
let savedOverflow = ''
let focusTimer

const modelMarks = ['◎', '✳', '✦', '◈', '▥']
const modelMark = model => modelMarks[Math.max(0, props.models.indexOf(model)) % modelMarks.length]

async function resize() {
  await nextTick()
  if (!expanded.value) return
  if (hasInlineAttachments.value) { textareaHeight.value = 94; return }
  if (!textarea.value) return
  textarea.value.style.height = '0px'
  const maxHeight = window.innerHeight <= 650 ? 120 : 180
  textareaHeight.value = Math.min(maxHeight, Math.max(80, textarea.value.scrollHeight))
  textarea.value.style.height = `${textareaHeight.value}px`
}

async function expand() {
  expanded.value = true
  await resize()
  if (hasInlineAttachments.value) root.value?.querySelector('.prompt-thumbnail-open')?.focus({ preventScroll: true })
  else textarea.value?.focus({ preventScroll: true })
}

watch(text, () => { status.value = ''; resize() })
watch(hasInlineAttachments, resize)
watch([textareaHeight, () => attachments.value.length], () => nextTick(positionCategoryMenu))
watch(() => props.models, models => {
  if (!models.includes(selectedModel.value)) selectedModel.value = models[0] || ''
})
watch(() => props.efforts, efforts => { effortIndex.value = Math.min(effortIndex.value, Math.max(0, efforts.length - 1)) })

function onOutsidePointer(event) {
  if (root.value?.contains(event.target) || activeAttachment.value) return
  menuOpen.value = false
  categoryMenuOpen.value = false
  if (event.target instanceof Element && event.target.matches('[data-prompt-collapse-area], .pc-page-host')) {
    expanded.value = false
  }
}

function onFocusOut() {
  clearTimeout(focusTimer)
  focusTimer = setTimeout(() => {
    if (!root.value?.contains(document.activeElement) && !activeAttachment.value) {
      menuOpen.value = false
      categoryMenuOpen.value = false
    }
  }, 0)
}

function positionCategoryMenu() {
  if (!categoryMenuOpen.value || !categoryMenu.value || !categoryButton.value) return
  const anchor = categoryButton.value.getBoundingClientRect()
  // offset dimensions exclude the opening scale animation.
  const menu = { width: categoryMenu.value.offsetWidth, height: categoryMenu.value.offsetHeight }
  const above = anchor.top - menu.height - 10
  const beside = above < 76 && anchor.right + menu.width + 28 <= window.innerWidth
  categoryMenuStyle.value = {
    left: `${beside ? anchor.right + 12 : Math.max(16, Math.min(anchor.left, window.innerWidth - menu.width - 16))}px`,
    top: `${Math.max(76, Math.min(beside ? anchor.top : above, window.innerHeight - menu.height - 12))}px`
  }
}

async function selectMode(nextMode) {
  mode.value = nextMode
  menuOpen.value = false
  categoryMenuOpen.value = false
  status.value = ''
  await expand()
}

async function toggleCategories() {
  categoryMenuOpen.value = !categoryMenuOpen.value
  menuOpen.value = false
  if (categoryMenuOpen.value) {
    await nextTick()
    positionCategoryMenu()
    categoryMenu.value?.querySelector('[role="menuitemcheckbox"]')?.focus({ preventScroll: true })
  }
}

function toggleCategory(value) {
  selectedCategories.value = selectedCategories.value.includes(value)
    ? selectedCategories.value.filter(item => item !== value)
    : [...selectedCategories.value, value]
  status.value = ''
}

function closeCategories() {
  categoryMenuOpen.value = false
  categoryButton.value?.focus({ preventScroll: true })
}

function onCategoryKey(event) {
  if (event.key === 'Escape') { event.preventDefault(); closeCategories(); return }
  const options = [...categoryMenu.value.querySelectorAll('[role="menuitemcheckbox"]')]
  const index = options.indexOf(document.activeElement)
  let next = index
  if (event.key === 'ArrowDown' || event.key === 'ArrowRight') next = (index + 1) % options.length
  else if (event.key === 'ArrowUp' || event.key === 'ArrowLeft') next = (index - 1 + options.length) % options.length
  else if (event.key === 'Home') next = 0
  else if (event.key === 'End') next = options.length - 1
  else return
  event.preventDefault()
  options[next]?.focus()
}

async function toggleMenu() {
  categoryMenuOpen.value = false
  menuOpen.value = !menuOpen.value
  if (menuOpen.value) {
    await nextTick()
    const options = modelMenu.value?.querySelectorAll('[role="menuitemradio"]')
    options?.[Math.max(0, props.models.indexOf(selectedModel.value))]?.focus()
  }
}

function chooseModel(model) {
  selectedModel.value = model
  menuOpen.value = false
  modelButton.value?.focus()
}

function onMenuKey(event) {
  if (event.key === 'Escape') {
    event.preventDefault()
    menuOpen.value = false
    modelButton.value?.focus()
    return
  }
  const options = [...modelMenu.value.querySelectorAll('[role="menuitemradio"]')]
  const index = options.indexOf(document.activeElement)
  let next = index
  if (event.key === 'ArrowDown') next = (index + 1) % options.length
  else if (event.key === 'ArrowUp') next = (index - 1 + options.length) % options.length
  else if (event.key === 'Home') next = 0
  else if (event.key === 'End') next = options.length - 1
  else return
  event.preventDefault()
  options[next]?.focus()
}

function cycleEffort() {
  if (props.efforts.length) effortIndex.value = (effortIndex.value + 1) % props.efforts.length
}

function submit() {
  if (props.busy || props.voiceRecording) return
  if (isIdentify.value) {
    if (!selectedCategories.value.length) {
      categoryButton.value?.focus({ preventScroll: true })
      status.value = '请先选择识别类别。'
      return
    }
    if (!attachments.value.length) { status.value = '请添加图片后再识别。'; return }
  }
  if (!hasContent.value || composing.value) return
  menuOpen.value = false
  categoryMenuOpen.value = false
  emit('submit', text.value, {
    mode: mode.value,
    ...(isIdentify.value ? { categories: [...selectedCategories.value] } : { model: selectedModel.value, effort: effort.value }),
    attachments: attachments.value.map(item => item.file)
  })
  // The parent owns its File references and message preview URLs after submit.
  if (isIdentify.value) {
    attachmentDrafts.identify.value.forEach(item => URL.revokeObjectURL(item.url))
    attachmentDrafts.identify.value = []
    resize()
  }
  status.value = props.submitMessage
}

function onTextKey(event) {
  if (event.isComposing || composing.value || event.keyCode === 229) return
  if (event.key === 'Enter' && !event.shiftKey) { event.preventDefault(); submit() }
  if (event.key === 'Escape') {
    menuOpen.value = false
    categoryMenuOpen.value = false
  }
}

function requestVoice() {
  emit('voice-request')
  if (!props.voiceConnected) status.value = '语音输入尚未接入，请先输入文字或添加图片。'
}

function addFiles(files) {
  const errors = []
  for (const file of files) {
    if (!file.type.startsWith('image/')) { errors.push('仅支持图片附件。'); continue }
    if (file.size > 10 * 1024 * 1024) { errors.push('单张图片不能超过 10MB。'); continue }
    if (attachments.value.length >= attachmentLimit.value) { errors.push(`最多添加 ${attachmentLimit.value} 张图片。`); break }
    attachments.value.push({ id: ++attachmentId, file, name: file.name, url: URL.createObjectURL(file) })
  }
  expand()
  status.value = [...new Set(errors)].join(' ')
}

function onFilesChosen(event) {
  addFiles(Array.from(event.target.files || []))
  event.target.value = ''
}

function onPaste(event) {
  const files = Array.from(event.clipboardData?.files || []).filter(file => file.type.startsWith('image/'))
  if (files.length && !event.clipboardData.getData('text/plain')) { event.preventDefault(); addFiles(files) }
}

function removeAttachment(id) {
  const target = attachments.value.find(item => item.id === id)
  if (target) URL.revokeObjectURL(target.url)
  attachments.value = attachments.value.filter(item => item.id !== id)
  status.value = ''
}

async function openPreview(attachment, event) {
  previewOrigin = event.currentTarget
  const rect = previewOrigin.getBoundingClientRect()
  galleryStyle.value = { '--origin-x': `${rect.left + rect.width / 2 - window.innerWidth / 2}px`, '--origin-y': `${rect.top + rect.height / 2 - window.innerHeight / 2}px` }
  savedOverflow = document.body.style.overflow
  document.body.style.overflow = 'hidden'
  activeAttachment.value = attachment
  menuOpen.value = false
  categoryMenuOpen.value = false
  await nextTick()
  dialogClose.value?.focus({ preventScroll: true })
}

function closePreview() {
  activeAttachment.value = null
  document.body.style.overflow = savedOverflow
  previewOrigin?.focus({ preventScroll: true })
}

async function resetDraft(nextMode = 'ask') {
  if (activeAttachment.value) closePreview()
  Object.values(attachmentDrafts).forEach(draft => {
    draft.value.forEach(item => URL.revokeObjectURL(item.url))
    draft.value = []
  })
  askDraft.value = ''
  selectedCategories.value = []
  mode.value = nextMode
  menuOpen.value = false
  categoryMenuOpen.value = false
  status.value = ''
  composing.value = false
  await resize()
}
async function appendText(value) {
  if (!value.trim()) { status.value = '未识别到语音内容，请重试。'; return }
  askDraft.value = [askDraft.value, value.trim()].filter(Boolean).join('\n')
  await selectMode('ask')
}
defineExpose({ resetDraft, appendText })

function onDialogKey(event) {
  if (event.key === 'Escape') { event.preventDefault(); closePreview() }
  if (event.key === 'Tab') { event.preventDefault(); dialogClose.value?.focus() }
}

onMounted(() => {
  document.addEventListener('pointerdown', onOutsidePointer)
  window.addEventListener('resize', resize)
  window.addEventListener('resize', positionCategoryMenu)
})
onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', onOutsidePointer)
  window.removeEventListener('resize', resize)
  window.removeEventListener('resize', positionCategoryMenu)
  clearTimeout(focusTimer)
  if (activeAttachment.value) document.body.style.overflow = savedOverflow
  Object.values(attachmentDrafts).forEach(draft => draft.value.forEach(item => URL.revokeObjectURL(item.url)))
})
</script>

<template>
  <div ref="root" class="prompt-input" :class="{ 'is-expanded': expanded, 'is-identify': isIdentify }" @focusout="onFocusOut" @paste="onPaste">
    <input ref="fileInput" class="prompt-file-input" type="file" accept="image/*" :multiple="!isIdentify" tabindex="-1" aria-label="选择图片附件" @change="onFilesChosen" />
    <Transition name="mode-reveal">
      <div v-if="expanded" class="prompt-mode-row">
        <div class="prompt-mode-switch" role="group" aria-label="输入模式">
          <span class="prompt-mode-indicator" aria-hidden="true" />
          <button type="button" class="prompt-mode-button" :aria-pressed="isIdentify" @click="selectMode('identify')"><PromptIcon name="scan" />识别</button>
          <button type="button" class="prompt-mode-button" :aria-pressed="!isIdentify" @click="selectMode('ask')"><PromptIcon name="chat" />询问</button>
        </div>
        <Transition name="follow-up-hint">
          <div v-if="followUpAvailable && isIdentify && !menuOpen && !categoryMenuOpen" class="prompt-follow-up-hint">
            <svg viewBox="0 0 64 30" aria-hidden="true"><path d="M61 6C48 2 44 26 29 23S10 16 3 18M3 18l9-6M3 18l9 6" /></svg>
            <span>可以继续询问</span>
          </div>
        </Transition>
        <Transition name="model-menu">
          <div v-if="categoryMenuOpen" ref="categoryMenu" class="prompt-category-menu" :style="categoryMenuStyle" role="menu" aria-label="识别类别" @keydown="onCategoryKey">
            <div class="prompt-category-heading"><span>识别范围</span><span>{{ selectedCategories.length ? `已选 ${selectedCategories.length} 项` : '可多选' }}</span></div>
            <p class="prompt-category-label">作物与病害</p>
            <div class="prompt-category-grid">
              <button v-for="option in categoryOptions.filter(item => item.group === 'disease')" :key="option.key" type="button" role="menuitemcheckbox" :aria-checked="selectedCategories.includes(option.value)" @click="toggleCategory(option.value)">
                <span aria-hidden="true">{{ option.icon }}</span><span>{{ option.label }}</span><span class="prompt-category-check"><PromptIcon v-if="selectedCategories.includes(option.value)" name="check" /></span>
              </button>
            </div>
            <p class="prompt-category-label">虫害检测</p>
            <div class="prompt-category-grid prompt-category-pest">
              <button v-for="option in categoryOptions.filter(item => item.group === 'pest')" :key="option.key" type="button" role="menuitemcheckbox" :aria-checked="selectedCategories.includes(option.value)" @click="toggleCategory(option.value)">
                <span aria-hidden="true">{{ option.icon }}</span><span>{{ option.label }}</span><span class="prompt-category-check"><PromptIcon v-if="selectedCategories.includes(option.value)" name="check" /></span>
              </button>
            </div>
            <div class="prompt-category-footer"><span>选择需要识别的范围</span><button type="button" @click="closeCategories">完成<PromptIcon name="check" /></button></div>
          </div>
        </Transition>
      </div>
    </Transition>
      <div v-if="expanded && !isIdentify && attachments.length" class="prompt-attachments" aria-label="图片附件">
        <TransitionGroup name="attachment">
          <div v-for="(item, index) in attachments" :key="item.id" class="prompt-thumbnail" :style="{ '--index': index }">
            <button type="button" class="prompt-thumbnail-open" :aria-label="`预览 ${item.name}`" @click="openPreview(item, $event)"><img :src="item.url" :alt="item.name" draggable="false" /></button>
            <button type="button" class="prompt-thumbnail-remove" :aria-label="`移除 ${item.name}`" @click="removeAttachment(item.id)"><PromptIcon name="close" /></button>
          </div>
        </TransitionGroup>
      </div>
    <div class="prompt-card" :class="{ 'has-model-menu': menuOpen }" :style="{ height: expanded ? `${textareaHeight + 62}px` : '58px' }" @transitionend="positionCategoryMenu">
      <button v-if="!expanded" type="button" class="prompt-collapsed" aria-label="展开输入框" @click="expand">{{ inputPlaceholder }}</button>
      <div v-if="expanded && hasInlineAttachments" class="prompt-attachments prompt-inline-attachments" aria-label="待识别图片">
        <TransitionGroup name="attachment">
          <div v-for="(item, index) in attachments" :key="item.id" class="prompt-thumbnail" :style="{ '--index': index }">
            <button type="button" class="prompt-thumbnail-open" :aria-label="`预览 ${item.name}`" @click="openPreview(item, $event)"><img :src="item.url" :alt="item.name" draggable="false" /></button>
            <button type="button" class="prompt-thumbnail-remove" :aria-label="`移除 ${item.name}`" @click="removeAttachment(item.id)"><PromptIcon name="close" /></button>
          </div>
        </TransitionGroup>
      </div>
      <textarea v-show="expanded && !hasInlineAttachments" ref="textarea" v-model="text" class="prompt-textarea" :placeholder="inputPlaceholder" :readonly="isIdentify" :aria-label="isIdentify ? '添加待识别图片' : '输入问题'" rows="2" @keydown="onTextKey" @compositionstart="composing = true" @compositionend="composing = false" />
      <div v-show="expanded" class="prompt-toolbar">
        <div v-if="!isIdentify && models.length" class="prompt-model-control">
          <button ref="modelButton" type="button" class="prompt-tool prompt-model-button" aria-haspopup="menu" :aria-expanded="menuOpen" :aria-label="`选择模型，当前 ${selectedModel}`" title="保留模型展示名称，实际调用采用管理端配置" @click="toggleMenu" @keydown.down.prevent="menuOpen || toggleMenu()">
            <span class="prompt-model-mark" :data-model="models.indexOf(selectedModel)">{{ modelMark(selectedModel) }}</span>
            <Transition name="morph-text" mode="out-in"><span :key="selectedModel" class="prompt-model-name">{{ selectedModel }}</span></Transition>
            <PromptIcon name="chevron" class="prompt-chevron" />
          </button>
          <Transition name="model-menu">
            <div v-if="menuOpen" ref="modelMenu" class="prompt-model-menu" role="menu" aria-label="选择模型" @keydown="onMenuKey">
              <span class="prompt-menu-caption">模型选择 · 调用采用后台配置</span>
              <button v-for="(model, index) in models" :key="model" type="button" role="menuitemradio" :aria-checked="selectedModel === model" @click="chooseModel(model)">
                <span class="prompt-model-mark" :data-model="index">{{ modelMark(model) }}</span><span>{{ model }}</span>
                <PromptIcon v-if="selectedModel === model" name="check" class="prompt-model-check" />
              </button>
            </div>
          </Transition>
        </div>
        <button v-if="!isIdentify && efforts.length" type="button" class="prompt-tool prompt-effort" :aria-label="`思考强度：${effort}，点击切换`" @click="cycleEffort">
          <span class="prompt-effort-bars" aria-hidden="true"><i /><i :class="{ dim: effortIndex < 1 }" /><i :class="{ dim: effortIndex < 2 }" /></span>
          <Transition name="morph-text" mode="out-in"><span :key="effort">{{ effort }}</span></Transition>
        </button>
        <button v-if="isIdentify" ref="categoryButton" type="button" class="prompt-tool prompt-choose-categories" :class="{ 'is-icon-only': selectedCategoryOptions.length }" aria-label="选择识别类型" title="选择识别类型" aria-haspopup="menu" :aria-expanded="categoryMenuOpen" @click="toggleCategories" @keydown.down.prevent="categoryMenuOpen || toggleCategories()"><PromptIcon name="scan" /><span v-if="!selectedCategoryOptions.length">选择识别类型</span><PromptIcon v-else name="chevron" class="prompt-chevron" /></button>
        <TransitionGroup v-if="isIdentify && selectedCategoryOptions.length" name="category-pill" tag="div" class="prompt-selected-categories" aria-label="已选择类别">
          <span v-for="option in selectedCategoryOptions" :key="option.key" class="prompt-category-pill">
            <span aria-hidden="true">{{ option.icon }}</span><span>{{ option.label }}</span><button type="button" :aria-label="`取消${option.label}类别`" @click="toggleCategory(option.value)"><PromptIcon name="close" /></button>
          </span>
        </TransitionGroup>
        <button v-if="!isIdentify || !attachments.length" type="button" class="prompt-tool prompt-add" :disabled="attachments.length >= attachmentLimit" :aria-label="`添加图片，最多 ${attachmentLimit} 张`" @click="fileInput?.click()"><PromptIcon name="plus" /></button>
      </div>
      <button type="button" class="prompt-action" :class="{ 'is-recording': voiceRecording }" :disabled="busy && !voiceRecording" :aria-busy="busy" :aria-label="voiceRecording ? '停止录音' : voiceBusy ? '正在转写' : busy ? '正在处理' : isIdentify ? '开始识别' : showSend ? '发送' : '语音输入'" @click="voiceRecording ? requestVoice() : showSend ? submit() : requestVoice()">
        <Transition name="action-icon" mode="out-in"><PromptIcon :key="voiceRecording ? 'recording' : showSend ? 'send' : 'voice'" :name="voiceRecording ? 'close' : showSend ? 'arrow' : 'microphone'" /></Transition>
      </button>
    </div>
    <p class="prompt-status" role="status" aria-live="polite">{{ status }}</p>
    <Teleport to="body">
      <Transition name="prompt-gallery">
        <div v-if="activeAttachment" class="prompt-gallery" role="dialog" aria-modal="true" :aria-label="`图片预览：${activeAttachment.name}`" :style="galleryStyle" @click.self="closePreview" @keydown="onDialogKey">
          <div class="prompt-gallery-image"><img :src="activeAttachment.url" :alt="activeAttachment.name" draggable="false" /></div>
          <button ref="dialogClose" type="button" class="prompt-gallery-close" aria-label="关闭图片预览" @click="closePreview"><PromptIcon name="close" /></button>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<style scoped>
.prompt-input, .prompt-gallery {
  --prompt-card: #fff; --prompt-text: #26332e; --prompt-muted: #7b8782; --prompt-border: #e0e6e3;
  --prompt-hover: #f0f4f2; --prompt-accent: #168669; --prompt-tray: #f1f4f2; --prompt-ring: #48a78c;
  color: var(--prompt-text); font-family: inherit;
}
.dark .prompt-input, .dark .prompt-gallery {
  --prompt-card: #1b2525; --prompt-text: #e1e9e5; --prompt-muted: #9aa8a2; --prompt-border: #34413e;
  --prompt-hover: #2b3934; --prompt-accent: #a5e4cd; --prompt-tray: #222e2a; --prompt-ring: #6bc3a4;
}
.prompt-input { position: relative; width: 420px; max-width: 100%; flex: 0 1 auto; transition: width .4s cubic-bezier(.175,.885,.32,1.15); }
.prompt-input.is-expanded { width: 680px; }
.prompt-input button, .prompt-input textarea, .prompt-gallery button { box-sizing: border-box; font: inherit; }
.prompt-input button, .prompt-gallery button { cursor: pointer; -webkit-tap-highlight-color: transparent; }
.prompt-input button:focus-visible, .prompt-gallery button:focus-visible { outline: 2px solid var(--prompt-ring); outline-offset: 3px; }
.prompt-input svg, .prompt-gallery svg { display: block; width: 19px; height: 19px; flex-shrink: 0; }
.prompt-file-input { display: none; }
.prompt-mode-row { position: relative; z-index: 6; display: flex; align-items: center; height: 40px; margin: 0 10px 12px; }
.prompt-action:disabled { opacity: .55; cursor: wait; }
.prompt-action.is-recording { background: #b84646; animation: recording-pulse 1.2s infinite; }
@keyframes recording-pulse { 50% { box-shadow: 0 0 0 6px #b8464620; } }
.prompt-mode-switch { position: relative; display: inline-flex; gap: 4px; padding: 3px; border: 1px solid var(--prompt-border); border-radius: 15px; background: var(--prompt-tray); box-shadow: 0 2px 6px #142e2905; }
.prompt-mode-indicator { position: absolute; top: 3px; bottom: 3px; left: 3px; width: 86px; border-radius: 11px; background: var(--prompt-card); box-shadow: 0 1px 4px #142e2912; transform: translateX(90px); transition: transform .32s cubic-bezier(.22,1,.36,1); }
.is-identify .prompt-mode-indicator { transform: translateX(0); }
.prompt-mode-switch .prompt-mode-button { position: relative; display: flex; align-items: center; justify-content: center; gap: 7px; width: 86px; height: 32px; border: 0; border-radius: 11px; background: transparent; color: var(--prompt-muted); font-size: 14px; font-weight: 600; transition: color .2s, transform .2s; }
.prompt-mode-button[aria-pressed=true] { color: var(--prompt-accent); }
.prompt-mode-button:hover { color: var(--prompt-text); }
.prompt-mode-button:active { transform: scale(.95); }
.prompt-mode-button svg { width: 16px; height: 16px; }
.prompt-follow-up-hint { display: flex; align-items: center; gap: 6px; margin-left: 10px; color: var(--prompt-accent); font-size: 13px; white-space: nowrap; pointer-events: none; }
.prompt-follow-up-hint svg { width: 56px; height: 30px; flex-shrink: 0; fill: none; stroke: currentColor; stroke-width: 1.7; stroke-linecap: round; stroke-linejoin: round; stroke-dasharray: 100; animation: follow-up-draw .7s ease-out both; }
.follow-up-hint-enter-active, .follow-up-hint-leave-active { transition: opacity .2s, transform .25s; }
.follow-up-hint-enter-from, .follow-up-hint-leave-to { opacity: 0; transform: translateX(6px); }
@keyframes follow-up-draw { from { stroke-dashoffset: 100; } to { stroke-dashoffset: 0; } }
.prompt-category-menu { position: fixed; z-index: 30; width: 310px; max-width: calc(100vw - 32px); max-height: calc(100dvh - 88px); overflow-y: auto; box-sizing: border-box; padding: 14px; border: 1px solid var(--prompt-border); border-radius: 20px; background: var(--prompt-card); box-shadow: 0 16px 50px -14px #051e2845; transform-origin: bottom left; scrollbar-width: thin; }
.prompt-category-heading { display: flex; justify-content: space-between; align-items: center; gap: 16px; padding: 0 3px 12px; border-bottom: 1px solid var(--prompt-border); font-size: 16px; font-weight: 600; }
.prompt-category-heading span:last-child { font-size: 12px; font-weight: 500; color: var(--prompt-accent); }
.prompt-category-label { margin: 14px 3px 9px; color: var(--prompt-muted); font-size: 12px; }
.prompt-category-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 6px; }
.prompt-category-pest { grid-template-columns: 1fr; }
.prompt-category-grid button { display: flex; align-items: center; gap: 9px; min-height: 41px; padding: 0 10px; border: 1px solid transparent; border-radius: 11px; color: var(--prompt-text); background: var(--prompt-hover); font-size: 14px; transition: border-color .18s, background .18s, transform .18s; }
.prompt-category-grid button:hover { border-color: var(--prompt-border); transform: translateY(-1px); }
.prompt-category-grid button[aria-checked=true] { color: var(--prompt-accent); border-color: color-mix(in srgb, var(--prompt-accent) 30%, transparent); background: color-mix(in srgb, var(--prompt-accent) 9%, var(--prompt-card)); }
.prompt-category-check { display: grid; place-items: center; width: 15px; height: 15px; margin-left: auto; border: 1px solid var(--prompt-border); border-radius: 5px; }
.prompt-category-grid [aria-checked=true] .prompt-category-check { border-color: var(--prompt-accent); background: var(--prompt-accent); color: var(--prompt-card); }
.prompt-category-check svg { width: 12px; height: 12px; }
.prompt-category-footer { display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-top: 14px; padding: 10px 2px 0; border-top: 1px solid var(--prompt-border); color: var(--prompt-muted); font-size: 11px; }
.prompt-category-footer button { display: flex; align-items: center; gap: 5px; padding: 5px 8px; border: 0; border-radius: 8px; color: var(--prompt-accent); background: var(--prompt-hover); font-size: 12px; }
.prompt-category-footer svg { width: 13px; height: 13px; }
.prompt-selected-categories { position: relative; display: flex; align-items: center; gap: 6px; min-width: 0; max-width: calc(100% - 40px); padding: 3px 1px; overflow-x: auto; scrollbar-width: none; }
.prompt-category-pill { display: flex; align-items: center; flex-shrink: 0; gap: 5px; height: 28px; padding: 0 3px 0 8px; border: 1px solid color-mix(in srgb, var(--prompt-accent) 17%, transparent); border-radius: 999px; color: var(--prompt-accent); background: color-mix(in srgb, var(--prompt-accent) 7%, var(--prompt-card)); font-size: 13px; }
.prompt-category-pill > span:first-child { font-size: 12px; }
.prompt-category-pill button { display: grid; place-items: center; width: 20px; height: 20px; padding: 0; border: 0; border-radius: 50%; color: inherit; background: transparent; opacity: 0; transform: scale(.7); transition: opacity .16s, transform .16s, background .16s; }
.prompt-category-pill:hover button, .prompt-category-pill:focus-within button { opacity: 1; transform: scale(1); }
.prompt-category-pill button:hover { background: color-mix(in srgb, var(--prompt-accent) 15%, transparent); }
.prompt-category-pill button svg { width: 12px; height: 12px; }
.prompt-choose-categories svg { width: 16px; height: 16px; }
.prompt-choose-categories.is-icon-only { flex-shrink: 0; gap: 3px; padding-inline: 4px; }
.mode-reveal-enter-active, .mode-reveal-leave-active { transition: opacity .22s, transform .28s cubic-bezier(.22,1,.36,1); }
.mode-reveal-enter-from, .mode-reveal-leave-to { opacity: 0; transform: translateY(8px) scale(.97); }
.category-pill-enter-active, .category-pill-leave-active, .category-pill-move { transition: opacity .18s, transform .22s; }
.category-pill-enter-from, .category-pill-leave-to { opacity: 0; transform: translateY(5px) scale(.9); }
.category-pill-leave-active { position: absolute; }
@media (hover: none) { .prompt-category-pill button { opacity: 1; transform: none; } }
.prompt-card { position: relative; z-index: 2; width: 100%; border: 1px solid var(--prompt-border); background: var(--prompt-card); border-radius: 25px; box-shadow: 0 5px 18px -12px #142e2930, 0 1px 3px #142e2906; transition: height .32s cubic-bezier(.175,.885,.32,1.1), border-color .2s, box-shadow .2s; }
.prompt-card:focus-within { border-color: var(--prompt-ring); box-shadow: 0 0 0 3px color-mix(in srgb, var(--prompt-ring) 10%, transparent), 0 6px 20px -14px #142e2935; }
.prompt-card.has-model-menu { z-index: 8; }
.prompt-collapsed { position: absolute; inset: 0 56px 0 0; padding: 0 22px; border: 0; border-radius: 25px; background: transparent; color: var(--prompt-muted); text-align: left; font-size: 17px !important; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
.prompt-textarea { display: block; width: 100%; height: 80px; max-height: 180px; padding: 19px 22px 4px; resize: none; border: 0; border-radius: 25px 25px 0 0; background: transparent; color: var(--prompt-text); font-size: 18px !important; line-height: 28px !important; outline: 0; overflow-y: auto; scrollbar-width: thin; scrollbar-color: var(--prompt-border) transparent; }
.prompt-textarea::placeholder { color: var(--prompt-muted); }
.prompt-toolbar { position: absolute; bottom: 12px; left: 13px; right: 62px; display: flex; align-items: center; gap: 6px; height: 34px; }
.prompt-tool { display: flex; align-items: center; justify-content: center; gap: 7px; height: 34px; padding: 0 10px; border: 0; border-radius: 12px; color: var(--prompt-muted); background: transparent; font-size: 14px !important; font-weight: 550 !important; white-space: nowrap; transition: background .16s, color .16s; }
.prompt-tool:hover, .prompt-tool[aria-expanded=true] { background: var(--prompt-hover); color: var(--prompt-text); }
.prompt-model-control { position: relative; min-width: 0; }
.prompt-model-button { max-width: 100%; }
.prompt-model-name { display: block; overflow: hidden; text-overflow: ellipsis; }
.prompt-model-mark { display: inline-flex; align-items: center; justify-content: center; width: 20px; height: 20px; flex-shrink: 0; font-size: 21px; line-height: 1; color: var(--prompt-text); }
.prompt-model-mark[data-model="1"] { color: #cf8d6f; }
.prompt-model-mark[data-model="2"] { color: #6d9de8; }
.prompt-model-mark[data-model="3"] { color: #9b93cb; }
.prompt-model-mark[data-model="4"] { color: #7a9ec6; }
.prompt-chevron { width: 12px !important; height: 12px !important; opacity: .7; }
.prompt-effort-bars { display: flex; align-items: flex-end; gap: 2px; height: 16px; }
.prompt-effort-bars i { display: block; width: 3px; height: 6px; border-radius: 2px; background: currentColor; transition: opacity .2s; }
.prompt-effort-bars i:nth-child(2) { height: 10px; }
.prompt-effort-bars i:nth-child(3) { height: 14px; }
.prompt-effort-bars .dim { opacity: .28; }
.prompt-add { margin-left: auto; width: 34px; padding: 0; flex-shrink: 0; }
.prompt-add:disabled { opacity: .35; cursor: default; }
.prompt-action { position: absolute; right: 10px; bottom: 10px; display: flex; align-items: center; justify-content: center; width: 36px; height: 36px; padding: 0; border: 0; border-radius: 50%; background: var(--prompt-accent); color: var(--prompt-card); transition: transform .2s, opacity .2s; }
.prompt-action:hover { opacity: .85; }
.prompt-action:active { transform: scale(.92); }
.prompt-action svg { position: absolute; }
.prompt-model-menu { position: absolute; bottom: calc(100% + 14px); left: 0; z-index: 5; width: 234px; padding: 7px; border: 1px solid var(--prompt-border); border-radius: 18px; background: var(--prompt-card); box-shadow: 0 16px 40px -14px #051e2845; transform-origin: bottom left; }
.prompt-menu-caption { display: block; padding: 5px 10px 8px; font-size: 11px; letter-spacing: .06em; color: var(--prompt-muted); }
.prompt-model-menu button { display: flex; align-items: center; gap: 11px; height: 40px; width: 100%; padding: 0 10px; border: 0; border-radius: 11px; background: transparent; color: var(--prompt-text); font-size: 14px; text-align: left; transition: background .18s, transform .18s; }
.prompt-model-menu button:hover, .prompt-model-menu button:focus-visible { background: var(--prompt-hover); outline-offset: -2px; }
.prompt-model-menu button:active { transform: scale(.98); }
.prompt-model-check { width: 15px !important; height: 15px !important; margin-left: auto; color: var(--prompt-accent); }
.prompt-status { position: absolute; top: 100%; left: 16px; right: 16px; margin: 13px 0 0; font-size: 13px; line-height: 20px; color: var(--prompt-muted); text-align: center; }
.prompt-attachments { position: relative; z-index: 1; display: flex; align-items: center; gap: 10px; width: calc(100% - 38px); margin: 0 auto -12px; padding: 12px 12px 23px; border: 1px solid var(--prompt-border); border-bottom: 0; border-radius: 20px 20px 0 0; background: var(--prompt-tray); overflow-x: auto; scrollbar-width: thin; }
.prompt-attachments.prompt-inline-attachments { box-sizing: border-box; width: 100%; height: 94px; margin: 0; padding: 16px 20px 10px; border: 0; border-radius: 24px 24px 0 0; background: transparent; box-shadow: none; overflow-y: hidden; }
.prompt-inline-attachments .prompt-thumbnail { width: 68px; height: 68px; }
.prompt-thumbnail { position: relative; width: 54px; height: 54px; flex-shrink: 0; }
.prompt-thumbnail-open { display: block; width: 100%; height: 100%; padding: 0; border: 1px solid var(--prompt-border); border-radius: 12px; overflow: hidden; background: var(--prompt-card); transition: transform .2s; }
.prompt-thumbnail-open:hover { transform: scale(1.04); }
.prompt-thumbnail img { width: 100%; height: 100%; object-fit: cover; }
.prompt-thumbnail-remove { position: absolute; top: -5px; right: -5px; display: grid; place-items: center; width: 20px; height: 20px; padding: 3px; border: 1px solid var(--prompt-border); border-radius: 50%; background: var(--prompt-card); color: var(--prompt-muted); opacity: 0; transform: scale(.7); transition: opacity .15s, transform .15s; }
.prompt-thumbnail:hover .prompt-thumbnail-remove, .prompt-thumbnail:focus-within .prompt-thumbnail-remove { opacity: 1; transform: scale(1); }
.prompt-thumbnail-remove svg { width: 12px; height: 12px; }
.prompt-gallery { position: fixed; inset: 0; z-index: 150; display: flex; align-items: center; justify-content: center; background: #0b171bb8; backdrop-filter: blur(12px); }
.prompt-gallery-image { display: flex; align-items: center; justify-content: center; max-width: min(86vw, 900px); max-height: 80vh; border-radius: 20px; overflow: hidden; box-shadow: 0 30px 100px #0005; }
.prompt-gallery-image img { display: block; max-width: min(86vw, 900px); max-height: 80vh; object-fit: contain; }
.prompt-gallery-close { position: absolute; top: 24px; right: 24px; display: grid; place-items: center; width: 42px; height: 42px; padding: 0; border: 1px solid var(--prompt-border); border-radius: 50%; color: var(--prompt-text); background: var(--prompt-card); }
.model-menu-enter-active, .model-menu-leave-active { transition: opacity .2s, transform .25s cubic-bezier(.175,.885,.32,1.2); }
.model-menu-enter-from, .model-menu-leave-to { opacity: 0; transform: translateY(10px) scale(.94); }
.morph-text-enter-active, .morph-text-leave-active { transition: opacity .1s, transform .1s; }
.morph-text-enter-from { opacity: 0; transform: translateY(4px); }
.morph-text-leave-to { opacity: 0; transform: translateY(-4px); }
.action-icon-enter-active, .action-icon-leave-active { transition: opacity .15s, transform .15s; }
.action-icon-enter-from, .action-icon-leave-to { opacity: 0; transform: scale(.5) rotate(-40deg); }
.attachment-tray-enter-active, .attachment-tray-leave-active { transition: opacity .2s, transform .25s; }
.attachment-tray-enter-from, .attachment-tray-leave-to { opacity: 0; transform: translateY(16px); }
.attachment-enter-active { transition: opacity .25s, transform .25s; transition-delay: calc(var(--index) * 25ms); }
.attachment-enter-from { opacity: 0; transform: translateY(10px) scale(.85); }
.prompt-gallery-enter-active, .prompt-gallery-leave-active { transition: opacity .3s; }
.prompt-gallery-enter-active .prompt-gallery-image, .prompt-gallery-leave-active .prompt-gallery-image { transition: transform .35s cubic-bezier(.175,.885,.32,1.1); }
.prompt-gallery-enter-from, .prompt-gallery-leave-to { opacity: 0; }
.prompt-gallery-enter-from .prompt-gallery-image, .prompt-gallery-leave-to .prompt-gallery-image { transform: translate(var(--origin-x), var(--origin-y)) scale(.12); }
@media (max-width: 1050px) { .prompt-input.is-expanded { width: 600px; } }
@media (max-height: 650px) { .prompt-textarea { max-height: 120px; } .prompt-model-menu { bottom: 100%; } }
@media (prefers-reduced-motion: reduce) { .prompt-input, .prompt-input *, .prompt-gallery, .prompt-gallery * { animation: none !important; transition: none !important; } }
</style>
