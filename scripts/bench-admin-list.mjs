// Đo latency các API danh sách admin qua backoffice-bff. Chạy: node scripts/bench-admin-list.mjs
// Đổi tài khoản/đường dẫn bằng biến môi trường BFF_URL, BENCH_USER, BENCH_PASSWORD, BENCH_RUNS.
const bffUrl = process.env.BFF_URL ?? 'http://localhost:8084'
const username = process.env.BENCH_USER ?? 'admin@fashion.local'
const password = process.env.BENCH_PASSWORD ?? 'Admin@12345'
const runs = Number(process.env.BENCH_RUNS ?? 30)
const warmupRuns = 5

const endpoints = [
  { name: 'Products (no filter)', url: '/admin/products?page=0&size=20&sort=createdAt,desc' },
  { name: 'Products (keyword)', url: '/admin/products?page=0&size=20&sort=createdAt,desc&keyword=a' },
  { name: 'Orders', url: '/api/v1/admin/orders?page=0&size=20&sort=createdAt,desc' },
  { name: 'Inventory', url: '/admin/inventory?page=0&size=20&state=ALL' },
  { name: 'Dashboard', url: '/api/v1/admin/dashboard?days=30&lowStockThreshold=10' }
]

const cookies = new Map()

function saveCookies(headers) {
  for (const cookie of headers.getSetCookie()) {
    const [pair] = cookie.split(';')
    const index = pair.indexOf('=')
    cookies.set(pair.slice(0, index).trim(), pair.slice(index + 1))
  }
}

const cookieHeader = () => [...cookies].map(([key, value]) => `${key}=${value}`).join('; ')

async function login() {
  const start = await fetch(`${bffUrl}/oauth2/authorization/keycloak`, { redirect: 'manual' })
  saveCookies(start.headers)
  const form = await fetch(start.headers.get('location'), { headers: { Cookie: cookieHeader() } })
  saveCookies(form.headers)
  const action = (await form.text()).match(/action="([^"]+)"/)?.[1]?.replace(/&amp;/g, '&')
  if (!action) throw new Error('Không thấy form đăng nhập Keycloak')

  const submit = await fetch(action, {
    method: 'POST',
    redirect: 'manual',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded', Cookie: cookieHeader() },
    body: new URLSearchParams({ username, password, credentialId: '' })
  })
  saveCookies(submit.headers)
  const callback = submit.headers.get('location')
  if (!callback) throw new Error('Đăng nhập thất bại, kiểm tra BENCH_USER/BENCH_PASSWORD')

  const done = await fetch(callback, { redirect: 'manual', headers: { Cookie: cookieHeader() } })
  saveCookies(done.headers)
}

async function timeRequest(url) {
  const startedAt = performance.now()
  const response = await fetch(`${bffUrl}${url}`, { headers: { Cookie: cookieHeader() } })
  await response.arrayBuffer()
  if (!response.ok) throw new Error(`${url} -> HTTP ${response.status}`)
  return performance.now() - startedAt
}

const percentile = (sorted, ratio) => sorted[Math.min(sorted.length - 1, Math.ceil(sorted.length * ratio) - 1)]

await login()
console.log(`runs=${runs}, warm-up=${warmupRuns} (bỏ khỏi kết quả)\n`)
for (const endpoint of endpoints) {
  for (let i = 0; i < warmupRuns; i++) await timeRequest(endpoint.url)
  const times = []
  for (let i = 0; i < runs; i++) times.push(await timeRequest(endpoint.url))
  times.sort((a, b) => a - b)
  const average = times.reduce((sum, value) => sum + value, 0) / times.length
  console.log(
    `${endpoint.name.padEnd(22)} avg ${average.toFixed(1)}ms | p50 ${percentile(times, 0.5).toFixed(1)}ms | ` +
      `p95 ${percentile(times, 0.95).toFixed(1)}ms | max ${times.at(-1).toFixed(1)}ms`
  )
}
