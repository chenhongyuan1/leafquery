import test from 'node:test'
import assert from 'node:assert/strict'
import { createPlantMotion, updatePlantPositions } from '../src/utils/plantSceneMotion.js'

test('particle forms have valid matching buffers, including a minimal cloud', () => {
  for (const count of [2, 128, 1600]) {
    const motion = createPlantMotion(count)
    for (const form of [motion.leaf, motion.spiral, motion.bloom]) {
      assert.equal(form.length, count * 3)
      assert.ok(form.every(Number.isFinite))
    }
  }
  assert.throws(() => createPlantMotion(1), RangeError)
})

test('time changes the sculpture itself and reuses the rendering buffer', () => {
  const motion = createPlantMotion(128)
  const positions = new Float32Array(128 * 3)
  assert.equal(updatePlantPositions(positions, motion, 0), positions)
  const firstFrame = positions.slice()
  updatePlantPositions(positions, motion, 8)
  assert.notDeepEqual(positions, firstFrame)
  assert.deepEqual(updatePlantPositions(new Float32Array(384), motion, 8), positions)
})

test('long-running animation stays finite and within the local camera bounds', () => {
  const motion = createPlantMotion(512)
  const positions = new Float32Array(512 * 3)
  for (const time of [0, 1, 30, 60, 300, 3600]) {
    updatePlantPositions(positions, motion, time)
    assert.ok(positions.every(value => Number.isFinite(value) && Math.abs(value) < 2.85))
  }
})

test('evolution is continuous rather than jumping between preset modes', () => {
  const motion = createPlantMotion(128)
  for (const time of [0, 12, 31.999, 64, 180, 3600]) {
    const first = updatePlantPositions(new Float32Array(384), motion, time)
    const second = updatePlantPositions(new Float32Array(384), motion, time + 0.01)
    assert.ok(first.every((value, index) => Math.abs(value - second[index]) < 0.02))
  }
})
