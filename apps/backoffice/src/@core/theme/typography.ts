// MUI Imports
import type { Theme } from '@mui/material/styles'

const typography = (fontFamily: string): Theme['typography'] =>
  ({
    fontFamily:
      typeof fontFamily === 'undefined' || fontFamily === ''
        ? [
            'Inter',
            'sans-serif',
            '-apple-system',
            'BlinkMacSystemFont',
            '"Segoe UI"',
            'Roboto',
            '"Helvetica Neue"',
            'Arial',
            'sans-serif',
            '"Apple Color Emoji"',
            '"Segoe UI Emoji"',
            '"Segoe UI Symbol"'
          ].join(',')
        : fontFamily,
    fontSize: 14,
    h1: {
      fontSize: '1.5rem',
      fontWeight: 600,
      lineHeight: 1.3
    },
    h2: {
      fontSize: '1.375rem',
      fontWeight: 600,
      lineHeight: 1.3
    },
    h3: {
      fontSize: '1.25rem',
      fontWeight: 600,
      lineHeight: 1.35
    },
    h4: {
      fontSize: '1.125rem',
      fontWeight: 600,
      lineHeight: 1.4
    },
    h5: {
      fontSize: '1rem',
      fontWeight: 600,
      lineHeight: 1.45
    },
    h6: {
      fontSize: '0.9375rem',
      fontWeight: 600,
      lineHeight: 1.45
    },
    subtitle1: {
      fontSize: '0.9375rem',
      fontWeight: 500,
      lineHeight: 1.45
    },
    subtitle2: {
      fontSize: '0.8125rem',
      fontWeight: 500,
      lineHeight: 1.4
    },
    body1: {
      fontSize: '0.875rem',
      lineHeight: 1.5
    },
    body2: {
      fontSize: '0.8125rem',
      lineHeight: 1.45
    },
    button: {
      fontSize: '0.875rem',
      fontWeight: 500,
      lineHeight: 1.45,
      textTransform: 'none'
    },
    caption: {
      fontSize: '0.75rem',
      lineHeight: 1.35,
      letterSpacing: '0.2px'
    },
    overline: {
      fontSize: '0.75rem',
      fontWeight: 600,
      lineHeight: 1.25,
      letterSpacing: '0.5px'
    }
  }) as Theme['typography']

export default typography
