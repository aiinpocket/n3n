import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Backend port is overridable so dev works against non-default deployments
// (e.g. BACKEND_PORT=18080 npm run dev)
const backendPort = process.env.BACKEND_PORT || '8080'

export default defineConfig({
  plugins: [react()],
  // sockjs-client references the Node-style `global`; map it for the browser
  define: {
    global: 'globalThis',
  },
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: `http://localhost:${backendPort}`,
        changeOrigin: true,
      },
      '/ws': {
        target: `ws://localhost:${backendPort}`,
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
