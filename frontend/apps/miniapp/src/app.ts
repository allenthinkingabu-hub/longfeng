// S7 miniapp entry · App() 注册 · 全局状态初始化
App({
  globalData: {
    userInfo: null,
    lang: 'zh-CN',
  },
  onLaunch() {
    // 读登录态 token 到内存 · api.ts wx.getStorageSync('access_token') 消费
  },
});
