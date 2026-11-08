// Presentation only: records and forecasts are supplied by the page's existing APIs/store.
export function createDataCenterCharts({ dark, activity, distribution, buckets, comparisons, dates, primaryKey, forecast, overview }) {
  const axis = dark ? '#334155' : '#cbd5e1'
  const gridColor = dark ? '#1e293b' : '#e2e8f0'
  const text = dark ? '#94a3b8' : '#64748b'
  const tooltip = { backgroundColor: dark ? '#0b1120' : '#ffffff', borderColor: gridColor, textStyle: { color: dark ? '#e2e8f0' : '#334155', fontSize: 12 }, confine: true }
  const legend = { type: 'scroll', bottom: 0, itemWidth: 8, itemHeight: 8, textStyle: { color: text, fontSize: 12 }, pageTextStyle: { color: text } }
  const base = { backgroundColor: 'transparent', animationDuration: 300 }
  const cartesian = (labels, percent = false) => ({
    ...base,
    tooltip: { ...tooltip, trigger: 'axis' },
    grid: { left: percent ? 44 : 34, right: 14, top: 25, bottom: 30 },
    xAxis: { type: 'category', data: labels, axisTick: { show: false }, axisLine: { lineStyle: { color: axis } }, axisLabel: { color: text, fontSize: 12, hideOverlap: true } },
    yAxis: { type: 'value', min: 0, ...(percent ? { max: 100 } : { minInterval: 1 }), splitLine: { lineStyle: { color: gridColor, type: 'dashed' } }, axisLabel: { color: text, fontSize: 12, ...(percent ? { formatter: '{value}%' } : {}) } }
  })
  const donut = (data, colors) => ({
    ...base, tooltip: { ...tooltip, trigger: 'item' }, legend,
    graphic: data.length ? [] : [{ type: 'text', left: 'center', top: '40%', style: { text: '暂无数据', fill: text, fontSize: 12 } }],
    series: [{ type: 'pie', radius: ['42%', '66%'], center: ['50%', '42%'], label: { show: false }, data, color: colors, emptyCircleStyle: { color: dark ? '#172033' : '#eef2f6' } }]
  })
  return {
    activity: { ...cartesian(activity.map(day => day.label)), series: [{ type: 'line', smooth: true, symbolSize: 4, data: activity.map(day => day.value), lineStyle: { width: 2, color: '#10b981' }, itemStyle: { color: '#10b981' }, areaStyle: { color: 'rgba(16,185,129,.1)' } }] },
    distribution: donut(distribution, ['#10b981', '#3b82f6', '#f59e0b', '#ef4444', '#8b5cf6', '#06b6d4']),
    overview: donut(overview, ['#38bdf8', '#10b981']),
    confidence: { ...cartesian(buckets.map(bucket => bucket.label)), tooltip: { ...tooltip, trigger: 'axis', axisPointer: { type: 'shadow' } }, series: [{ type: 'bar', barMaxWidth: 24, data: buckets.map(bucket => ({ value: bucket.value, itemStyle: { color: bucket.color, borderRadius: [3, 3, 0, 0] } })), label: { show: true, position: 'top', color: text, fontSize: 12 } }] },
    comparison: {
      ...cartesian(dates, true), legend: { ...legend, top: 0, bottom: undefined }, grid: { left: 44, right: 14, top: 38, bottom: 30 },
      series: comparisons.map(item => ({
        name: item.name, type: 'line', smooth: true, symbolSize: item.isSelected ? 6 : 3, data: item.data,
        lineStyle: { width: item.isSelected ? 3 : 1.5, color: item.color, opacity: item.isSelected ? 1 : .65 }, itemStyle: { color: item.color },
        areaStyle: item.isSelected ? { color: item.color, opacity: .08 } : undefined,
        emphasis: { focus: 'series' }, z: item.isSelected ? 10 : 3,
        markLine: item.key === primaryKey ? { silent: true, symbol: 'none', label: { show: false }, data: [{ yAxis: 75, lineStyle: { color: '#ef4444', type: 'dashed', opacity: .4 } }, { yAxis: 55, lineStyle: { color: '#f59e0b', type: 'dashed', opacity: .4 } }] } : undefined
      }))
    },
    structure: {
      ...cartesian(forecast.map((day, i) => i === 0 ? '今天' : day.date), true),
      series: [{ type: 'bar', barMaxWidth: 18, data: forecast.map(day => {
        const value = Math.round(Number(day.riskScore || 0) * 100)
        return { value, itemStyle: { color: value >= 75 ? '#ef4444' : value >= 55 ? '#f59e0b' : '#10b981', borderRadius: [3, 3, 0, 0] } }
      }), label: { show: true, position: 'top', color: text, fontSize: 12, formatter: '{c}%' } }]
    }
  }
}
