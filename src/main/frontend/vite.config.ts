import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/ws': {
        target: 'ws://localhost:8080',
        ws: true,
      },
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes('node_modules')) return
          if (/[\\/]node_modules[\\/](react|react-dom|react-router|react-router-dom|scheduler)[\\/]/.test(id))
            return 'react-vendor'
          if (/[\\/]node_modules[\\/](antd|@ant-design)[\\/]/.test(id)) return 'antd-vendor'
          if (/[\\/]node_modules[\\/]@xyflow[\\/]/.test(id)) return 'flow-vendor'
          if (/[\\/]node_modules[\\/](zustand|axios)[\\/]/.test(id)) return 'state-vendor'
          if (/[\\/]node_modules[\\/]@monaco-editor[\\/]/.test(id)) return 'monaco-vendor'
          if (/[\\/]node_modules[\\/]@dagrejs[\\/]/.test(id)) return 'dagre-vendor'
        },
      },
    },
  },
})
