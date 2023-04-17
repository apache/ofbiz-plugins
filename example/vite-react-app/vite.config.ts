import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vitejs.dev/config/
export default defineConfig({
    plugins: [react()],
    base: '/example/vite-react-app/',
    build: {
        manifest: true,
        outDir: '../webapp/example/vite-react-app',
    },
})
