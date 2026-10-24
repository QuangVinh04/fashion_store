import { PHASE_DEVELOPMENT_SERVER } from 'next/constants.js'

/** @type {import('next').NextConfig} */
const nextConfig = {
  basePath: process.env.BASEPATH || undefined,
  eslint: {
    ignoreDuringBuilds: true
  }
}

export default phase => ({
  ...nextConfig,
  distDir: process.env.BACKOFFICE_BUILD_DIR || (phase === PHASE_DEVELOPMENT_SERVER ? '.next-dev' : '.next')
})
