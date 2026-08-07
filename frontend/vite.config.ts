import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          react: ['react', 'react-dom', 'react-router-dom'],
          charts: ['recharts'],
        },
      },
    },
  },
  server: {
    port: 5173,
    // Allow accessing the dev server via LAN IP and HTTPS tunnels (ngrok/cloudflared).
    // Without this, Vite will block unknown Host headers like "*.ngrok-free.dev".
    allowedHosts: true,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        configure: (proxy) => {
          proxy.on('proxyReq', (proxyReq) => {
            // When accessing Vite via LAN IP (phone testing), browsers send Origin=http://<lan-ip>:5173.
            // If we forward that Origin to the backend, Spring CORS may reject it (403) depending on config.
            // Since this is a same-origin dev proxy, we can safely strip Origin.
            proxyReq.removeHeader('origin');
          });
        },
      },
      '/ws': {
        target: 'ws://localhost:8080',
        ws: true,
        configure: (proxy) => {
          proxy.on('proxyReqWs', (proxyReq) => {
            proxyReq.removeHeader('origin');
          });
          proxy.on('error', (err: NodeJS.ErrnoException) => {
            if (err.code === 'ECONNRESET') {
              return;
            }
            console.error('[vite] ws proxy error:', err);
          });
        },
      },
    },
  },
});
