<script setup>
import { ref, onMounted, onUnmounted, computed, watch } from 'vue'
import { useScroll, useElementBounding, useWindowSize } from '@vueuse/core'

// Hero State
const canvasRef = ref(null)
const mouse = ref({ x: 0, y: 0 })
const connectionDistance = 150
let animationFrameId = null
let particles = []

// Scroll Logic for Horizontal Section
const containerRef = ref(null) // The root scrollable div
const targetSection = ref(null) // The 500vh section

const { y: scrollY } = useScroll(containerRef)
const { height: windowHeight } = useWindowSize()

// Manually calculate progress to ensure reliability with internal scrolling
const scrollProgress = computed(() => {
  if (!targetSection.value || !containerRef.value) return 0
  
  const sectionEl = targetSection.value
  const sectionTop = sectionEl.offsetTop
  const sectionHeight = sectionEl.offsetHeight
  const viewportHeight = windowHeight.value
  
  // Calculate how far we've scrolled into the section
  // When scrollY == sectionTop, we are at the start (progress 0)
  // We want the effect to last until we scroll past the section
  
  const currentScroll = scrollY.value
  const start = sectionTop
  const end = sectionTop + sectionHeight - viewportHeight
  
  const progress = (currentScroll - start) / (end - start)
  return Math.max(0, Math.min(1, progress))
})

// Demo Phone State
const isScanning = ref(true)

// Feature Cards Data
const cards = [
    { title: '精准识别', eng: 'IDENTIFY', img: 'https://images.unsplash.com/photo-1598273611394-4369403a743c?q=80&w=600' },
    { title: '百科图鉴', eng: 'KNOWLEDGE', img: 'https://images.unsplash.com/photo-1550989460-0adf9ea622e2?q=80&w=600' },
    { title: '问答社区', eng: 'COMMUNITY', img: 'https://images.unsplash.com/photo-1574768560155-23c2140a340b?q=80&w=600' },
    { title: '病害诊断', eng: 'DIAGNOSE', img: 'https://images.unsplash.com/photo-1530836369250-ef72a3f5cda8?q=80&w=600' },
    { title: '生长监测', eng: 'MONITOR', img: 'https://images.unsplash.com/photo-1599586120429-48285b6a8a80?q=80&w=600' },
    { title: '数据分析', eng: 'ANALYTICS', img: 'https://images.unsplash.com/photo-1551288049-bebda4e38f71?q=80&w=600' },
    { title: '智能预警', eng: 'ALERT', img: 'https://images.unsplash.com/photo-1581091226825-a6a2a5aee158?q=80&w=600' },
    // Duplicate for longer list
    { title: '精准识别', eng: 'IDENTIFY', img: 'https://images.unsplash.com/photo-1598273611394-4369403a743c?q=80&w=600' },
    { title: '百科图鉴', eng: 'KNOWLEDGE', img: 'https://images.unsplash.com/photo-1550989460-0adf9ea622e2?q=80&w=600' },
    { title: '问答社区', eng: 'COMMUNITY', img: 'https://images.unsplash.com/photo-1574768560155-23c2140a340b?q=80&w=600' },
    { title: '病害诊断', eng: 'DIAGNOSE', img: 'https://images.unsplash.com/photo-1530836369250-ef72a3f5cda8?q=80&w=600' },
    { title: '生长监测', eng: 'MONITOR', img: 'https://images.unsplash.com/photo-1599586120429-48285b6a8a80?q=80&w=600' },
    { title: '数据分析', eng: 'ANALYTICS', img: 'https://images.unsplash.com/photo-1551288049-bebda4e38f71?q=80&w=600' },
    { title: '智能预警', eng: 'ALERT', img: 'https://images.unsplash.com/photo-1581091226825-a6a2a5aee158?q=80&w=600' },
]

// Particles Logic
class Particle {
  constructor(canvas) {
    this.canvas = canvas
    this.x = Math.random() * canvas.width
    this.y = Math.random() * canvas.height
    this.vx = (Math.random() - 0.5) * 0.5
    this.vy = (Math.random() - 0.5) * 0.5
    this.size = Math.random() * 2 + 1
    // Brighter Stars: Lighter Green + Higher Opacity (0.4 to 0.9)
    this.color = `rgba(134, 239, 172, ${Math.random() * 0.5 + 0.4})` // Green-300 equivalent
  }

  update() {
    this.x += this.vx
    this.y += this.vy

    // Bounce off edges
    if (this.x < 0 || this.x > this.canvas.width) this.vx *= -1
    if (this.y < 0 || this.y > this.canvas.height) this.vy *= -1

    // Mouse interaction
    const dx = mouse.value.x - this.x
    const dy = mouse.value.y - this.y
    const distance = Math.sqrt(dx * dx + dy * dy)
    
    // Repel slightly
    if (distance < 100) {
      const angle = Math.atan2(dy, dx)
      this.x -= Math.cos(angle) * 1
      this.y -= Math.sin(angle) * 1
    }
  }

  draw(ctx) {
    ctx.fillStyle = this.color
    ctx.beginPath()
    ctx.arc(this.x, this.y, this.size, 0, Math.PI * 2)
    ctx.fill()
  }
}

const initCanvas = () => {
  if (!canvasRef.value) return
  const canvas = canvasRef.value
  const ctx = canvas.getContext('2d')
  
  const resize = () => {
    canvas.width = window.innerWidth
    canvas.height = window.innerHeight
    // Re-init particles on resize
    particles = []
    const particleCount = Math.floor((canvas.width * canvas.height) / 15000)
    for (let i = 0; i < particleCount; i++) {
      particles.push(new Particle(canvas))
    }
  }
  
  window.addEventListener('resize', resize)
  resize()

  const animate = () => {
    if (!canvasRef.value) return
    ctx.clearRect(0, 0, canvas.width, canvas.height)
    
    // Update and draw particles
    particles.forEach(p => {
      p.update()
      p.draw(ctx)
    })

    // Draw connections
    // Optimization: only connect to nearby particles
    for (let i = 0; i < particles.length; i++) {
        for (let j = i + 1; j < particles.length; j++) {
            const dx = particles[i].x - particles[j].x
            const dy = particles[i].y - particles[j].y
            const distance = Math.sqrt(dx * dx + dy * dy)

            if (distance < connectionDistance) {
                ctx.strokeStyle = `rgba(74, 222, 128, ${1 - distance/connectionDistance})` // Fade out
                ctx.lineWidth = 0.5
                ctx.beginPath()
                ctx.moveTo(particles[i].x, particles[i].y)
                ctx.lineTo(particles[j].x, particles[j].y)
                ctx.stroke()
            }
        }
        
        // Connect to mouse
        const dx = particles[i].x - mouse.value.x
        const dy = particles[i].y - mouse.value.y
        const distance = Math.sqrt(dx * dx + dy * dy)
        if (distance < 200) {
             ctx.strokeStyle = `rgba(34, 197, 94, ${1 - distance/200})`
             ctx.lineWidth = 0.8
             ctx.beginPath()
             ctx.moveTo(particles[i].x, particles[i].y)
             ctx.lineTo(mouse.value.x, mouse.value.y)
             ctx.stroke()
        }
    }

    animationFrameId = requestAnimationFrame(animate)
  }
  
  animate()
}

const handleMouseMove = (e) => {
  if (!canvasRef.value) return 
  const rect = canvasRef.value.getBoundingClientRect()
  mouse.value = {
    x: e.clientX - rect.left,
    y: e.clientY - rect.top
  }
}

// Footer Grid Logic
const gridCanvas = ref(null)
const footerSection = ref(null) // Added missing ref
const goBtnRef = ref(null) // Track the button position
const isHoveringFooter = ref(false)
let gridAnimationFrame = null
let gridResizeObserver = null
let hoverStrength = 0 // 0 to 1

const initGridCanvas = () => {
    if (!gridCanvas.value) return
    const canvas = gridCanvas.value
    const ctx = canvas.getContext('2d')
    let width, height
    
    // Grid Config
    // Match VISION grid: 100px spacing, #22c55e color, 1px width
    const spacing = 100 
    let cols, rows
    
    const resize = () => {
        if (!footerSection.value) return
        
        // Always trust clientHeight/Width from the parent
        const w = footerSection.value.clientWidth
        const h = footerSection.value.clientHeight
        
        // Only update if dimensions actually changed (prevent loops)
        if (canvas.width !== w || canvas.height !== h) {
            width = w
            height = h
            canvas.width = width
            canvas.height = height
            cols = Math.ceil(width / spacing) + 1
            rows = Math.ceil(height / spacing) + 1
        }
    }
    
    // Use ResizeObserver to track container size changes (e.g. text wrapping, layout shifts)
    gridResizeObserver = new ResizeObserver(() => resize())
    if (footerSection.value) gridResizeObserver.observe(footerSection.value)
    
    // Initial call
    resize()
    
    const animate = () => {
        // "Slow start to Fast" Logic
        const target = isHoveringFooter.value ? 1 : 0
        // Move hoverT linearly towards target (Constant speed)
        // Step 0.016 (1s duration) - Slower build up for dramatic effect
        const step = 0.016 
        if (Math.abs(target - hoverStrength) < step) hoverStrength = target
        else hoverStrength += (target - hoverStrength) > 0 ? step : -step
        
        // Apply "Slow to Fast" curve (Power 12)
        // Extremely steep curve: Stays near zero for almost 800ms, then instant snap.
        const outputStrength = Math.pow(hoverStrength, 12)
        
        ctx.clearRect(0, 0, width, height)
        
        // Calculate Dynamic Center based on Button Position
        let centerX = width / 2
        let centerY = height / 2
        
        if (goBtnRef.value && gridCanvas.value) {
            const btnRect = goBtnRef.value.getBoundingClientRect()
            const canvasRect = gridCanvas.value.getBoundingClientRect()
            // Calculate center relative to canvas
            centerX = (btnRect.left + btnRect.width / 2) - canvasRect.left
            centerY = (btnRect.top + btnRect.height / 2) - canvasRect.top
        }

        // GLOBAL ZOOM Animation ("Overall Enlarge")
        // Scale from 1.0 to 1.15 based on hover strength
        ctx.save()
        const currentScale = 1.0 + outputStrength * 0.15
        // Zoom center should be the button center too
        ctx.translate(centerX, centerY)
        ctx.scale(currentScale, currentScale)
        ctx.translate(-centerX, -centerY)

        // Match VISION color: Green-400 (#4ade80) Bright Green
        // Container has opacity-20
        ctx.strokeStyle = '#4ade80' 
        ctx.lineWidth = 1 / currentScale // Keep line width consistent visually, or remove to let them thicken
        
        // Distortion Radius: Expands with hover
        const radius = 100 + outputStrength * 300 // Increased range for dramatic effect
        const force = outputStrength * 150 // Displacement strength
        
        ctx.beginPath()
        
        // Draw Vertical Lines
        for (let i = 0; i < cols; i++) {
            const x = i * spacing
            // Draw line segment by segment for curve
            for (let j = 0; j < height; j += 10) {
                 const y = j
                 const dx = x - centerX
                 const dy = y - centerY
                 const dist = Math.sqrt(dx*dx + dy*dy)
                 
                 let tx = x
                 let ty = y
                 
                 // Distortion Logic (Repel)
                 if (dist < radius) {
                     const factor = (radius - dist) / radius
                     // Ease factor
                     const ease = 1 - Math.pow(1 - factor, 3) 
                     
                     // Push away
                     const angle = Math.atan2(dy, dx)
                     tx += Math.cos(angle) * force * ease
                 }
                 
                 if (j === 0) ctx.moveTo(tx, ty)
                 else ctx.lineTo(tx, ty)
            }
        }
        
        // Draw Horizontal Lines
        for (let i = 0; i < rows; i++) {
            const y = i * spacing
            for (let j = 0; j < width; j += 10) {
                 const x = j
                 const dx = x - centerX
                 const dy = y - centerY
                 const dist = Math.sqrt(dx*dx + dy*dy)
                 
                 let tx = x
                 let ty = y
                 
                 if (dist < radius) {
                     const factor = (radius - dist) / radius
                     const ease = 1 - Math.pow(1 - factor, 3)
                     const angle = Math.atan2(dy, dx)
                     ty += Math.sin(angle) * force * ease
                 }
                 
                 if (j === 0) ctx.moveTo(tx, ty)
                 else ctx.lineTo(tx, ty)
            }
        }
        
        ctx.stroke()
        ctx.restore() // Restore scale state
        gridAnimationFrame = requestAnimationFrame(animate)
    }
    
    animate()
}

onMounted(() => {
  initCanvas()
  initGridCanvas() // Start grid animation
  window.addEventListener('mousemove', handleMouseMove)
})

onUnmounted(() => {
  if (animationFrameId) cancelAnimationFrame(animationFrameId)
  if (gridAnimationFrame) cancelAnimationFrame(gridAnimationFrame)
  if (gridResizeObserver) gridResizeObserver.disconnect()
  window.removeEventListener('mousemove', handleMouseMove)
})

</script>

<template>
  <div ref="containerRef" class="w-full h-full bg-slate-950 text-white overflow-x-hidden overflow-y-auto font-sans selection:bg-green-500 selection:text-black scroll-smooth">
    
    <!-- Hero Section -->
    <section class="relative w-full h-screen flex items-center justify-center overflow-hidden">
        <!-- Interactive Canvas Background -->
        <canvas ref="canvasRef" class="absolute inset-0 z-0 opacity-80"></canvas>
        <div class="absolute inset-0 bg-gradient-to-b from-transparent via-slate-950/50 to-slate-950 z-1 pointer-events-none"></div>

        <div class="relative z-10 text-center px-4 max-w-5xl mx-auto">
            <div 
              class="inline-block mb-6 px-4 py-1.5 rounded-full border border-green-500/30 bg-green-500/10 backdrop-blur-md text-green-400 text-sm font-bold tracking-wider uppercase animate-fade-in-up"
            >
              Next Gen Agriculture AI
            </div>
            
            <h1 class="text-6xl md:text-8xl font-black tracking-tighter mb-8 leading-tight">
                <span class="block bg-clip-text text-transparent bg-gradient-to-r from-white via-slate-200 to-slate-500" v-motion-slide-visible-once-bottom :delay="200">
                    智能识别
                </span>
                <span class="block bg-clip-text text-transparent bg-gradient-to-r from-green-400 to-emerald-600 pb-4" v-motion-slide-visible-once-bottom :delay="400">
                    重塑未来农业
                </span>
            </h1>
            
            <p class="text-xl md:text-2xl text-slate-400 max-w-2xl mx-auto mb-12 font-light leading-relaxed" v-motion-slide-visible-once-bottom :delay="600">
                LeafQuery 利用前沿的计算机视觉技术，瞬间诊断作物病虫害。
                <span class="text-white font-medium">准确率高达 99.8%</span>，为全球 500 万农户提供专业指导。
            </p>
            
            <div class="flex flex-col md:flex-row items-center justify-center space-y-4 md:space-y-0 md:space-x-6" v-motion-slide-visible-once-bottom :delay="800">
                <button class="bg-white text-black px-8 py-4 rounded-full font-bold text-lg hover:bg-green-400 transition-colors shadow-[0_0_30px_rgba(255,255,255,0.3)] hover:shadow-[0_0_50px_rgba(74,222,128,0.6)]">
                    立即免费试用
                </button>
                <button class="px-8 py-4 rounded-full font-bold text-lg border border-white/20 hover:bg-white/10 transition-colors backdrop-blur-sm flex items-center space-x-2 group">
                    <span>观看见证视频 </span>
                    <span class="group-hover:translate-x-1 transition-transform">→</span>
                </button>
            </div>
        </div>

        <!-- Scroll Down Indicator -->
        <div class="absolute bottom-10 left-1/2 -translate-x-1/2 animate-bounce cursor-pointer opacity-50 hover:opacity-100 transition-opacity">
            <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 14l-7 7m0 0l-7-7m7 7V3"></path></svg>
        </div>
    </section>

    <!-- Features Section with Scroll Parallax -->
    <section class="py-32 px-6 relative z-10 bg-slate-950">
        <div class="max-w-7xl mx-auto">
            <div class="grid grid-cols-1 md:grid-cols-2 gap-20 items-center">
                
                <!-- Demo Phone Display -->
                <div class="relative group" v-motion-slide-visible-once-left>
                    <!-- Glowing Background -->
                    <div class="absolute -inset-4 bg-gradient-to-r from-green-500 to-emerald-600 rounded-[3rem] blur-2xl opacity-20 group-hover:opacity-40 transition-opacity duration-500"></div>
                    
                    <!-- Phone Frame -->
                    <div class="relative bg-slate-900 border-[8px] border-slate-800 rounded-[2.5rem] overflow-hidden shadow-2xl h-[600px] w-[320px] mx-auto transform group-hover:-translate-y-2 transition-transform duration-500">
                        <!-- Simulated App Screen -->
                        <div class="relative h-full w-full bg-slate-800 overflow-hidden">
                             <!-- Scan Effect Layer -->
                             <div class="absolute inset-0 z-20 pointer-events-none">
                                 <!-- Scanning Line -->
                                 <div class="absolute top-0 w-full h-1 bg-green-400 shadow-[0_0_20px_rgba(74,222,128,0.8)] animate-scan"></div>
                                 <!-- Grid Overlay -->
                                 <div class="absolute inset-0 bg-[url('https://www.transparenttextures.com/patterns/grid-me.png')] opacity-10"></div>
                                 <!-- Target Box -->
                                 <div class="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-48 h-48 border-2 border-green-500/50 rounded-lg">
                                     <div class="absolute top-0 left-0 w-4 h-4 border-t-4 border-l-4 border-green-400 -mt-1 -ml-1"></div>
                                     <div class="absolute top-0 right-0 w-4 h-4 border-t-4 border-r-4 border-green-400 -mt-1 -mr-1"></div>
                                     <div class="absolute bottom-0 left-0 w-4 h-4 border-b-4 border-l-4 border-green-400 -mb-1 -ml-1"></div>
                                     <div class="absolute bottom-0 right-0 w-4 h-4 border-b-4 border-r-4 border-green-400 -mb-1 -mr-1"></div>
                                 </div>
                                 <!-- Analysis Text -->
                                 <div class="absolute top-1/2 left-1/2 -translate-x-1/2 translate-y-28 bg-black/60 backdrop-blur-md px-4 py-2 rounded-lg text-green-400 text-xs font-mono">
                                     ANALYZING... 98%
                                 </div>
                             </div>
                             <!-- Image being scanned -->
                             <img src="https://images.unsplash.com/photo-1591081658714-f576fb7ea3ed?ixlib=rb-1.2.1&auto=format&fit=crop&w=600&q=80" class="w-full h-full object-cover opacity-80" alt="Plant Leaf" />
                        </div>
                    </div>
                </div>

                <!-- Text Content -->
                <div class="space-y-12">
                   <div v-motion-slide-visible-once-right :delay="200">
                       <h3 class="text-green-500 font-bold uppercase tracking-widest mb-4">Core Technology</h3>
                       <h2 class="text-4xl md:text-5xl font-bold mb-6">毫秒级 <br> 边缘计算识别</h2>
                       <p class="text-slate-400 text-lg leading-relaxed">
                           无需联网，基于 MobileNetV3 优化的轻量级模型直接在您的手机端运行。
                           每一次快门按下，都是一次数亿次浮点运算的奇迹。
                       </p>
                   </div>

                   <div class="grid grid-cols-2 gap-8">
                       <div class="p-6 bg-white/5 rounded-2xl border border-white/10 hover:border-green-500/50 transition-colors" v-motion-slide-visible-once-right :delay="300">
                           <div class="text-3xl mb-4">⚡</div>
                           <h4 class="font-bold text-xl mb-2">极速响应</h4>
                           <p class="text-sm text-slate-400">平均识别耗时 < 0.2s，把握每一个稍纵即逝的瞬间。</p>
                       </div>
                       <div class="p-6 bg-white/5 rounded-2xl border border-white/10 hover:border-green-500/50 transition-colors" v-motion-slide-visible-once-right :delay="400">
                           <div class="text-3xl mb-4">🎯</div>
                           <h4 class="font-bold text-xl mb-2">超高精度</h4>
                           <p class="text-sm text-slate-400">针对 100+ 种常见农作物病害进行专项微调训练。</p>
                       </div>
                   </div>
                </div>
            </div>
        </div>
    </section>

    <!-- Horizontal Scroll Showcase -->
    <section ref="targetSection" class="relative h-[800vh] bg-slate-950">
        <div class="sticky top-0 h-screen overflow-hidden flex flex-col justify-center items-center bg-slate-950 perspective-1000">
            
            <!-- Stage 1: The Static Background Text "VISION" (Gradient Scan Effect) -->
            <!-- Animation: 
                 1. Text stays mostly static in position.
                 2. A "Light Scan" moves across the text surface based on scroll.
            -->
            <div 
              class="absolute inset-0 flex items-center justify-center z-0 pointer-events-none select-none will-change-transform"
              :style="{
                  transform: `translate3d(${-scrollProgress * 5}vw, 0, 0)` // Very subtle movement
              }"
            >
                 <!-- Scan Effect Text -->
                 <!-- Background is 200% wide. We move from 100% to 0% as we scroll. -->
                 <!-- Gradient: Dark Slate -> Bright Green/White -> Dark Slate -->
                 <h2 
                    class="text-[25vw] font-black leading-none tracking-tighter bg-clip-text text-transparent"
                    :style="{
                        backgroundImage: `linear-gradient(120deg, #0f172a 40%, #4ade80 50%, #ffffff 52%, #4ade80 54%, #0f172a 60%)`,
                        backgroundSize: '250% 100%',
                        backgroundPositionX: `${100 - scrollProgress * 100}%`
                    }"
                 >
                    VISION
                 </h2>
            </div>

            <!-- Stage 2: The Foreground Cards (Inside Cylinder Track) -->
            <!-- Perspective: Viewer is standing at the center axis of the cylinder -->
            <!-- Added z-10 to ensure cards appear ABOVE the background grid -->
            <div 
              class="absolute inset-0 flex items-center justify-center will-change-transform z-10"
              style="perspective: 1000px; transform-style: preserve-3d;"
            >
                 <div 
                    v-for="(card, index) in cards" 
                    :key="index"
                    class="absolute bg-slate-900 border border-green-500/50 rounded-[2rem] p-8 overflow-hidden group shadow-[0_0_50px_rgba(34,197,94,0.2)] backface-visible will-change-transform"
                    :style="(() => {
                        // Dynamic Arc Calculation (Inside View)
                        // Adjusted for 'Mega Size' & 'Flatter Curve'
                        const spacing = 0.9 // Wider spacing for larger cards
                        
                        // Calculate normalized position
                        // 16 cards * 0.9 = 14.4 units
                        const activeScroll = Math.max(0, scrollProgress - 0.05) * 16.0 
                        
                        // rawPos: Positive (Right) -> Negative (Left)
                        // Start offset increased to move start point further right
                        const rawPos = (index * spacing) + 3.0 - activeScroll

                        // Angle: Map position to degrees around the viewer
                        // Reduced multiplier (40) makes the curve feel 'flatter' (angular change per unit is less)
                        const angleDeg = -rawPos * 40 

                        // Visibility Clipping
                        if (Math.abs(angleDeg) > 90) return { display: 'none' }

                        const radius = 140 // vw (Larger radius = Flatter curvature)

                        // Stagger (Vertical Gap)
                        // Increased to +/- 32vh for distinct rows
                        const ty = index % 2 === 0 ? -60 : 60
                        
                        // Opacity Fade at edges
                        const opacity = 1 - Math.pow(Math.abs(angleDeg) / 70, 2)

                        return {
                            width: '100vh',  // Bigger
                            height: '130vh', // Bigger
                            opacity: Math.max(0, opacity),
                            // Inside Cylinder Logic:
                            transform: `rotateY(${angleDeg}deg) translateZ(${-radius}vw) translateY(${ty}vh)`,
                            zIndex: Math.floor(100 - Math.abs(rawPos) * 10)
                        }
                    })()"
                 >
                      <img :src="card.img" class="absolute inset-0 w-full h-full object-cover opacity-50 group-hover:opacity-70 transition-opacity" />
                      <div class="absolute inset-0 bg-gradient-to-t from-slate-950 via-slate-950/40 to-transparent"></div>
                      <div class="relative z-20">
                          <h3 class="text-5xl font-black text-white mb-2">{{ card.title }}</h3>
                          <p class="text-green-400 font-mono text-lg tracking-widest">{{ card.eng }}</p>
                      </div>
                 </div>
            </div>

            <!-- Grid Overlay to enhance speed feeling -->
             <div class="absolute inset-0 z-0 pointer-events-none opacity-20">
                <div class="w-full h-full" 
                     style="background-image: linear-gradient(#4ade80 1px, transparent 1px), linear-gradient(90deg, #4ade80 1px, transparent 1px); background-size: 100px 100px;">
                </div>
            </div>
        </div>
    </section>

    <!-- Parallax Stats Section -->
    <section class="py-20 relative bg-green-900/10 border-y border-white/5 overflow-hidden">
        <div class="absolute inset-0 bg-[url('https://www.transparenttextures.com/patterns/carbon-fibre.png')] opacity-10"></div>
        <div class="max-w-7xl mx-auto px-6 grid grid-cols-2 md:grid-cols-4 gap-8 text-center">
            <div v-motion-pop-visible-once :delay="100">
                <div class="text-5xl font-black text-white mb-2">500W+</div>
                <div class="text-green-400 font-bold text-sm tracking-widest uppercase">累计识别次数</div>
            </div>
            <div v-motion-pop-visible-once :delay="200">
                <div class="text-5xl font-black text-white mb-2">99.8%</div>
                <div class="text-green-400 font-bold text-sm tracking-widest uppercase">识别准确率</div>
            </div>
            <div v-motion-pop-visible-once :delay="300">
                <div class="text-5xl font-black text-white mb-2">50+</div>
                <div class="text-green-400 font-bold text-sm tracking-widest uppercase">支持作物种类</div>
            </div>
            <div v-motion-pop-visible-once :delay="400">
                <div class="text-5xl font-black text-white mb-2">0s</div>
                <div class="text-green-400 font-bold text-sm tracking-widest uppercase">网络延迟依赖</div>
            </div>
        </div>
    </section>

    <!-- Download CTA with Physics Grid -->
    <section class="py-32 px-6 text-center relative overflow-hidden" ref="footerSection">
         <!-- Physics Grid Canvas -->
         <!-- Vision section uses opacity-20 on container for grid lines -->
         <!-- User requested exact match with vision -->
         <canvas ref="gridCanvas" class="absolute inset-0 z-0 opacity-20 pointer-events-none"></canvas>
         
         <div class="relative z-10 max-w-3xl mx-auto">
             <h2 class="text-4xl md:text-6xl font-bold mb-8">准备好以此改变农业了吗？</h2>
             <p class="text-xl text-slate-400 mb-12">
                 立即下载 LeafQuery App，将农业专家的智慧装进口袋。
                 <br>支持 iOS 与 Android 双平台。
             </p>
             
             <div 
                class="bg-transparent p-8 inline-flex flex-col items-center group"
                @mouseenter="isHoveringFooter = true"
                @mouseleave="isHoveringFooter = false"
             >
                 <!-- Interactive GO/QR Component -->
                 <div ref="goBtnRef" class="relative w-64 h-64 flex items-center justify-center cursor-pointer">
                     
                     <!-- 1. The Morphing Circle (Background & Border) -->
                     <div class="absolute bg-slate-900 border-2 border-green-500/30 rounded-full flex items-center justify-center transition-all duration-1000 ease-[cubic-bezier(0.95,0,0.05,1)] w-20 h-20 group-hover:w-96 group-hover:h-96 group-hover:border-green-400 shadow-[0_0_0_rgba(74,222,128,0)] group-hover:shadow-[0_0_50px_rgba(74,222,128,0.2)]">
                     </div>

                     <!-- 2. The 'GO' Text (Visible Idle, Hidden Hover) -->
                     <div class="absolute z-10 flex items-center justify-center transition-all duration-300 group-hover:opacity-0 group-hover:scale-50">
                         <span class="text-green-500 font-black text-2xl tracking-tighter">GO</span>
                     </div>

                     <!-- 3. The QR Code (Hidden Idle, Visible Hover) -->
                     <div class="absolute z-10 opacity-0 scale-50 group-hover:opacity-100 group-hover:scale-100 transition-all duration-500 delay-75 pointer-events-none">
                        <div class="w-32 h-32 grid grid-cols-6 gap-1">
                           <div v-for="i in 36" :key="i" class="w-full h-full rounded-[1px]" :class="Math.random() > 0.5 ? 'bg-green-400' : 'bg-white/10'"></div>
                        </div>
                     </div>
                     
                     <!-- Pulse Effect (Only visible when idle) -->
                     <div class="absolute w-20 h-20 bg-green-500/20 rounded-full animate-ping pointer-events-none group-hover:hidden"></div>
                 </div>
                 
                 <div class="text-slate-400 font-mono text-sm mt-4 opacity-0 group-hover:opacity-100 transition-opacity duration-500">
                     Scan to Download
                 </div>
             </div>
         </div>
    </section>

    <!-- Footer -->
    <footer class="py-12 px-6 border-t border-white/10 text-center text-slate-500 text-sm">
        <p>&copy; 2026 LeafQuery AI. All rights reserved.</p>
    </footer>
  </div>
</template>

<style scoped>
@keyframes scan {
  0% { top: 0%; opacity: 0; }
  10% { opacity: 1; }
  90% { opacity: 1; }
  100% { top: 100%; opacity: 0; }
}
.animate-scan {
  animation: scan 3s cubic-bezier(0.4, 0, 0.2, 1) infinite;
  background: linear-gradient(to bottom, transparent, #4ade80, transparent);
}

.pattern-grid-lg {
  background-image: 
    linear-gradient(45deg, #000 25%, transparent 25%), 
    linear-gradient(-45deg, #000 25%, transparent 25%), 
    linear-gradient(45deg, transparent 75%, #000 75%), 
    linear-gradient(-45deg, transparent 75%, #000 75%);
  background-size: 20px 20px;
  background-position: 0 0, 0 10px, 10px -10px, -10px 0px;
}

@keyframes fade-in-up {
  from { opacity: 0; transform: translateY(20px); }
  to { opacity: 1; transform: translateY(0); }
}
.animate-fade-in-up {
  animation: fade-in-up 0.8s ease-out forwards;
}
</style>
