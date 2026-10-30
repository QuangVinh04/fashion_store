'use client'

import { useState } from 'react'

import { IconButton, Tooltip } from '@mui/material'

export default function CopyButton({ text, label = 'Sao chép' }: { text: string; label?: string }) {
  const [copied, setCopied] = useState(false)

  const handleCopy = async (event: React.MouseEvent) => {
    event.stopPropagation()

    try {
      await navigator.clipboard.writeText(text)
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    } catch {
      /* clipboard fallback */
    }
  }

  return (
    <Tooltip title={copied ? 'Đã sao chép!' : label}>
      <IconButton size='small' onClick={handleCopy} aria-label={label} sx={{ ml: 1, p: 0.5 }}>
        <i
          className={
            copied
              ? 'ri-check-line text-success text-base'
              : 'ri-file-copy-line text-textSecondary text-base'
          }
        />
      </IconButton>
    </Tooltip>
  )
}
