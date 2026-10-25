/**
 * Animate UI motion tokens
 * Based on official Animate UI specifications (animate-ui.com)
 */

export const spring = {
  press: { type: "spring", stiffness: 550, damping: 30, restSpeed: 10 },
  pop: { type: "spring", stiffness: 400, damping: 29 },
  dialog: { type: "spring", stiffness: 150, damping: 25 },
  selection: { type: "spring", stiffness: 300, damping: 30 },
  highlight: { type: "spring", stiffness: 500, damping: 40 },
  sheet: { type: "spring", stiffness: 300, damping: 35, restDelta: 0.01 },
  toast: { type: "spring", stiffness: 280, damping: 24 },
} as const;

export const ease = {
  out: [0.23, 1, 0.32, 1],
  soft: [0.215, 0.61, 0.355, 1],
  drawer: [0.34, 1.2, 0.64, 1],
  away: [0.33, 0.17, 0.67, 0.5],
  color: [0.25, 0.1, 0.25, 1],
  leave: [0, 0, 0.58, 1],
  enter: [0.25, 0.1, 0.35, 1],
  resize: [0.4, 0, 0.1, 1],
} as const;

export const transition = {
  overlay: { duration: 0.35, ease: ease.soft },
  dialogFade: { duration: 0.35, ease: ease.soft },
  dismiss: { duration: 0.2, ease: ease.away },
  sheet: { duration: 0.38, ease: ease.soft },
  menu: { duration: 0.22, ease: ease.soft },
  color: { duration: 0.2, ease: ease.color },
} as const;

export const scale = {
  press: 0.97,
  dialog: 0.95,
  popover: 0.85,
  toast: 0.96,
} as const;
