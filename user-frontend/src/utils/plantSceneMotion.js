// Precompute botanical forms once; animate their continuous blend without mode steps.
export function createPlantMotion(count) {
  if (!Number.isInteger(count) || count < 2) throw new RangeError('At least two particles are required')
  const leaf = new Float32Array(count * 3)
  const spiral = new Float32Array(count * 3)
  const bloom = new Float32Array(count * 3)
  const phase = new Float32Array(count)
  const goldenAngle = Math.PI * (3 - Math.sqrt(5))
  for (let i = 0; i < count; i++) {
    const index = i * 3
    const t = i / (count - 1)
    const angle = i * goldenAngle
    const along = t * 2 - 1
    const width = Math.sin(Math.PI * t) * 1.55
    const x = Math.cos(angle) * width * 0.74
    const y = along * 2.3
    leaf.set([x * 0.91 + y * 0.4, y * 0.91 - x * 0.4,
      Math.sin(angle) * width * 0.2 + Math.sin(along * Math.PI) * 0.38], index)
    const turn = t * Math.PI * 10
    const radius = 1.08 + Math.sin(t * Math.PI) * 0.55
    spiral.set([Math.cos(turn) * radius, along * 1.95, Math.sin(turn) * radius * 0.8], index)
    const flowerRadius = 1.48 + Math.sin(angle * 5) * 0.38 + along * 0.19
    bloom.set([Math.cos(angle) * flowerRadius, Math.sin(angle) * flowerRadius,
      Math.sin(angle * 3) * 0.5 + along * 0.3], index)
    phase[i] = angle % (Math.PI * 2)
  }
  return { count, leaf, spiral, bloom, phase }
}

export function updatePlantPositions(output, motion, time) {
  const a = (0.5 + 0.5 * Math.sin(time * 0.19 + 0.8)) ** 3
  const b = (0.5 + 0.5 * Math.sin(time * 0.13 + 2.8)) ** 3
  const c = (0.5 + 0.5 * Math.sin(time * 0.17 + 4.5)) ** 3
  const total = a + b + c
  const wa = a / total
  const wb = b / total
  const wc = c / total
  for (let i = 0; i < motion.count; i++) {
    const index = i * 3
    const flow = Math.sin(time * 0.63 + motion.phase[i]) * 0.09
    const x = motion.leaf[index] * wa + motion.spiral[index] * wb + motion.bloom[index] * wc
    const y = motion.leaf[index + 1] * wa + motion.spiral[index + 1] * wb + motion.bloom[index + 1] * wc
    const z = motion.leaf[index + 2] * wa + motion.spiral[index + 2] * wb + motion.bloom[index + 2] * wc
    const twist = Math.sin(time * 0.28 + y * 0.55) * 0.23
    output[index] = x * Math.cos(twist) - z * Math.sin(twist) + flow
    output[index + 1] = y * (1 + Math.sin(time * 0.37) * 0.045) + flow * 0.55
    output[index + 2] = x * Math.sin(twist) + z * Math.cos(twist) + Math.cos(time * 0.47 + motion.phase[i]) * 0.08
  }
  return output
}
