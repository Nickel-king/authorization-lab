// PostCSS 配置（ESM，因 package.json 声明 "type": "module"）
export default {
  // 通过 PostCSS 启用 Tailwind 与自动补全前缀
  plugins: {
    tailwindcss: {},
    autoprefixer: {}
  }
}