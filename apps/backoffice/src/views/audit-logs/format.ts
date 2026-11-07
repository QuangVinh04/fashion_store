export const VISIBLE_ID_CHARS = 8

export function formatOccurredAt(raw: string): string {
  if (!raw) return ''

  const match = raw.match(/^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})/)

  if (match) {
    const [, year, month, day, hour, minute, second] = match

    return `${day}/${month}/${year} ${hour}:${minute}:${second}`
  }

  return raw
}

export function truncateText(text: string, maxLen = VISIBLE_ID_CHARS): string {
  if (!text || text.length <= maxLen) return text

  return `${text.slice(0, VISIBLE_ID_CHARS)}...`
}
