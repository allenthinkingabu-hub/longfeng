// 后端服务地址，本地开发时使用
// 微信开发者工具需勾选「不校验合法域名」
const BASE_URL = 'https://longfengjiaoyu.com'

App({
  globalData: {
    baseUrl: BASE_URL
  },
  onLaunch() {
    console.log('错题分析器启动')
  }
})
