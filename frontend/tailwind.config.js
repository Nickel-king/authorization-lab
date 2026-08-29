// Tailwind CSS 配置（ESM，因 package.json 声明 "type": "module"）
export default {
  // 扫描所有 Vue/JS/HTML 文件以生成对应的工具类
  content: ['./index.html', './src/**/*.{vue,js,ts,jsx,tsx}'],
  theme: {
    extend: {
      // 中台主色调：深蓝/靛青，与 slate-900 构成暗白自适应主题
      colors: {
        primary: {
          50: '#eef2ff',
          100: '#e0e7ff',
          200: '#c7d2fe',
          300: '#a5b4fc',
          400: '#818cf8',
          500: '#6366f1',
          600: '#4f46e5',
          700: '#4338ca'
        }
      }
    }
  },
  plugins: []
}