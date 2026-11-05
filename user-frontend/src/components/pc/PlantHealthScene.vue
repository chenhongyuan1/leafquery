<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as THREE from 'three'
import gsap from 'gsap'
import { createPlantMotion, updatePlantPositions } from '../../utils/plantSceneMotion.js'

const props = defineProps({ isDark: Boolean, isLaunching: Boolean })
const container = ref(null)
const hasWebGL = ref(false)
const motion = createPlantMotion(1600)
const pointer = { x: 0, y: 0 }
const fallbackDots = Array.from({ length: 80 }, (_, i) => ({
  x: 200 + motion.leaf[i * 20 * 3] * 58,
  y: 200 - motion.leaf[i * 20 * 3 + 1] * 58
}))
let scene, camera, renderer, group, particles, glow, filaments, sprite
let positions, frame, observer, motionQuery, launchTween
let elapsed = 0
let lastFrame = 0
let fitDistance = 8
let disposed = false
let contextUnavailable = false

function makeSprite() {
  const canvas = document.createElement('canvas')
  canvas.width = canvas.height = 32
  const context = canvas.getContext('2d')
  if (!context) return null
  const gradient = context.createRadialGradient(16, 16, 0, 16, 16, 16)
  gradient.addColorStop(0, 'rgba(255,255,255,1)')
  gradient.addColorStop(0.42, 'rgba(255,255,255,0.95)')
  gradient.addColorStop(1, 'rgba(255,255,255,0)')
  context.fillStyle = gradient
  context.fillRect(0, 0, 32, 32)
  return new THREE.CanvasTexture(canvas)
}

function applyPalette() {
  if (!particles) return
  const primary = new THREE.Color(props.isDark ? '#6ae6ba' : '#008b70')
  const secondary = new THREE.Color(props.isDark ? '#67bfe9' : '#247fa8')
  const colors = particles.geometry.attributes.color
  const color = new THREE.Color()
  for (let i = 0; i < motion.count; i++) {
    color.copy(primary).lerp(secondary, (i % 113) / 112)
    colors.setXYZ(i, color.r, color.g, color.b)
  }
  colors.needsUpdate = true
  for (const cloud of [particles, glow]) {
    cloud.material.blending = props.isDark ? THREE.AdditiveBlending : THREE.NormalBlending
    cloud.material.needsUpdate = true
  }
  glow.material.opacity = props.isDark ? 0.13 : 0.07
  filaments.children.forEach((line, i) => {
    line.material.color.copy(i % 2 ? secondary : primary)
    line.material.opacity = props.isDark ? 0.23 : 0.2
  })
}

function resize() {
  if (!renderer || !container.value) return
  const { width, height } = container.value.getBoundingClientRect()
  if (!width || !height) return
  camera.aspect = width / height
  camera.updateProjectionMatrix()
  fitDistance = 2.85 / (Math.tan(THREE.MathUtils.degToRad(camera.fov / 2)) * Math.min(1, camera.aspect)) * 1.05
  if (!props.isLaunching) camera.position.z = fitDistance
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 1.5))
  renderer.setSize(width, height, false)
}

function updateFilaments(time) {
  filaments.children.forEach((line, index) => {
    const attribute = line.geometry.attributes.position
    for (let i = 0; i < attribute.count; i++) {
      const t = i / (attribute.count - 1)
      const angle = t * Math.PI * 2.3 + index * Math.PI / 3 + time * 0.11
      const radius = 1.1 + Math.sin(t * Math.PI) * 0.5 + Math.sin(time * 0.23 + index) * 0.16
      attribute.setXYZ(i,
        Math.cos(angle) * radius,
        (t - 0.5) * 3.9 + Math.sin(angle * 1.3 + time * 0.2) * 0.18,
        Math.sin(angle) * radius * 0.75
      )
    }
    attribute.needsUpdate = true
  })
}

function animate(now) {
  frame = null
  if (!renderer || disposed || document.hidden) return
  elapsed += lastFrame ? Math.min((now - lastFrame) / 1000, 0.05) : 0
  lastFrame = now
  updatePlantPositions(positions, motion, elapsed)
  particles.geometry.attributes.position.needsUpdate = true
  updateFilaments(elapsed)
  group.rotation.y = Math.sin(elapsed * 0.09) * 0.5 + elapsed * 0.035 + pointer.x * 0.16
  group.rotation.z = Math.sin(elapsed * 0.12) * 0.09
  group.rotation.x += (pointer.y * 0.12 - group.rotation.x) * 0.045
  renderer.render(scene, camera)
  frame = requestAnimationFrame(animate)
}

function handleVisibility() {
  if (frame != null) cancelAnimationFrame(frame)
  frame = null
  lastFrame = 0
  if (!document.hidden && renderer) frame = requestAnimationFrame(animate)
}

function disposeRenderer() {
  if (frame != null) cancelAnimationFrame(frame)
  frame = null
  launchTween?.kill()
  if (scene) {
    const geometries = new Set()
    const materials = new Set()
    scene.traverse(object => {
      if (object.geometry) geometries.add(object.geometry)
      for (const material of [].concat(object.material || [])) materials.add(material)
    })
    geometries.forEach(geometry => geometry.dispose())
    materials.forEach(material => material.dispose())
  }
  sprite?.dispose()
  sprite = null
  if (renderer) {
    renderer.domElement.removeEventListener('webglcontextlost', handleContextLoss)
    renderer.dispose()
    renderer.forceContextLoss()
    renderer.domElement.remove()
  }
  renderer = scene = camera = group = particles = glow = filaments = null
  hasWebGL.value = false
}

function handleContextLoss(event) {
  event.preventDefault()
  contextUnavailable = true
  disposeRenderer()
}

function initialize() {
  if (disposed || renderer || motionQuery?.matches || contextUnavailable || !container.value) return
  try {
    renderer = new THREE.WebGLRenderer({ alpha: true, antialias: true, powerPreference: 'low-power' })
    renderer.setClearColor(0x000000, 0)
    renderer.domElement.setAttribute('aria-hidden', 'true')
    renderer.domElement.addEventListener('webglcontextlost', handleContextLoss)
    container.value.appendChild(renderer.domElement)
    scene = new THREE.Scene()
    camera = new THREE.PerspectiveCamera(44, 1, 0.1, 100)
    group = new THREE.Group()
    scene.add(group)
    positions = updatePlantPositions(new Float32Array(motion.count * 3), motion, elapsed)
    const geometry = new THREE.BufferGeometry()
    geometry.setAttribute('position', new THREE.BufferAttribute(positions, 3).setUsage(THREE.DynamicDrawUsage))
    geometry.setAttribute('color', new THREE.BufferAttribute(new Float32Array(motion.count * 3), 3))
    sprite = makeSprite()
    particles = new THREE.Points(geometry, new THREE.PointsMaterial({
      size: 0.062, map: sprite, vertexColors: true, transparent: true, opacity: 0.94, depthWrite: false
    }))
    glow = new THREE.Points(geometry, new THREE.PointsMaterial({
      size: 0.15, map: sprite, vertexColors: true, transparent: true, opacity: 0.1, depthWrite: false
    }))
    particles.frustumCulled = glow.frustumCulled = false
    group.add(glow, particles)
    filaments = new THREE.Group()
    for (let i = 0; i < 6; i++) {
      const curve = new THREE.BufferGeometry()
      curve.setAttribute('position', new THREE.BufferAttribute(new Float32Array(96 * 3), 3).setUsage(THREE.DynamicDrawUsage))
      const line = new THREE.Line(curve, new THREE.LineBasicMaterial({ transparent: true, depthWrite: false }))
      line.frustumCulled = false
      filaments.add(line)
    }
    group.add(filaments)
    applyPalette()
    updateFilaments(elapsed)
    resize()
    hasWebGL.value = true
    handleVisibility()
  } catch (error) {
    contextUnavailable = true
    disposeRenderer()
    console.warn('[LeafQuery intro] Using the static concept visual.', error)
  }
}

function handleMotionPreference() {
  if (motionQuery.matches) disposeRenderer()
  else initialize()
}

function handlePointer(event) {
  if (!container.value || props.isLaunching) return
  const rect = container.value.getBoundingClientRect()
  pointer.x = ((event.clientX - rect.left) / rect.width - 0.5) * 2
  pointer.y = ((event.clientY - rect.top) / rect.height - 0.5) * 2
}

watch(() => props.isDark, applyPalette)
watch(() => props.isLaunching, launching => {
  if (!launching || !camera) return
  launchTween = gsap.to(camera.position, { z: fitDistance * 0.59, duration: 0.7, ease: 'power2.in' })
})

onMounted(() => {
  motionQuery = window.matchMedia('(prefers-reduced-motion: reduce)')
  motionQuery.addEventListener('change', handleMotionPreference)
  observer = new ResizeObserver(resize)
  observer.observe(container.value)
  document.addEventListener('visibilitychange', handleVisibility)
  initialize()
})

onBeforeUnmount(() => {
  disposed = true
  observer?.disconnect()
  motionQuery?.removeEventListener('change', handleMotionPreference)
  document.removeEventListener('visibilitychange', handleVisibility)
  disposeRenderer()
})
</script>

<template>
  <div ref="container" class="plant-scene" :class="{ 'plant-scene--dark': isDark }" aria-hidden="true"
       @pointermove="handlePointer" @pointerleave="pointer.x = 0; pointer.y = 0">
    <svg v-if="!hasWebGL" class="scene-fallback" viewBox="0 0 400 400" fill="none">
      <path class="fallback-leaf" d="M105 285C64 160 150 82 291 99C305 239 232 322 105 285Z" />
      <path d="M105 285L270 121M144 246L135 172M173 217L174 140M172 218L251 225M204 184L271 187" />
      <path class="fallback-flow" d="M85 240C35 95 275 60 315 195S155 320 105 260M100 280C65 180 175 70 290 120" />
      <circle v-for="(point, index) in fallbackDots" :key="index" :cx="point.x" :cy="point.y" r="1.6" />
    </svg>
  </div>
</template>

<style scoped>
.plant-scene { position: relative; width: 100%; height: 100%; color: #138f7a; }
.plant-scene--dark { color: #58d9b0; }
.plant-scene :deep(canvas) { display: block; width: 100%; height: 100%; }
.scene-fallback { position: absolute; inset: 0; width: 100%; height: 100%; stroke: currentColor; stroke-width: 0.8; }
.scene-fallback path { opacity: 0.65; }
.scene-fallback .fallback-leaf { fill: currentColor; fill-opacity: 0.06; }
.scene-fallback .fallback-flow { opacity: 0.3; }
.scene-fallback circle { fill: currentColor; stroke: none; opacity: 0.8; }
</style>
