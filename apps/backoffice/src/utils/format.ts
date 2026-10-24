export function financialMoney(value: string, currency = 'VND') {
  const amount = /^(-?\d+)(?:\.0+)?$/.exec(value)

  if (!amount || currency !== 'VND') return `${value} ${currency}`

  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency, maximumFractionDigits: 0 }).format(
    BigInt(amount[1])
  )
}
