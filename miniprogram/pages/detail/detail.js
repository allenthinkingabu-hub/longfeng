const app = getApp()

Page({
  data: {
    question: null,
    loading: true,
    error: ''
  },

  onLoad(options) {
    const id = options.id
    if (!id) {
      this.setData({ loading: false, error: '参数错误' })
      return
    }
    this.loadDetail(id)
  },

  loadDetail(id) {
    const baseUrl = app.globalData.baseUrl
    wx.request({
      url: `${baseUrl}/api/questions/${id}`,
      method: 'GET',
      success: (res) => {
        if (res.statusCode === 200) {
          const data = res.data
          // imageUrl 是相对路径，拼成完整 URL 供 <image> 使用
          if (data.imageUrl && !data.imageUrl.startsWith('http')) {
            data.imageUrl = `${baseUrl}${data.imageUrl}`
          }
          this.setData({ question: data, loading: false })
        } else {
          this.setData({ loading: false, error: '加载失败，请返回重试' })
        }
      },
      fail: () => {
        this.setData({ loading: false, error: '网络异常，请检查连接' })
      }
    })
  },

  previewImage() {
    const { question } = this.data
    if (!question || !question.imageUrl) return
    const baseUrl = app.globalData.baseUrl
    wx.previewImage({
      urls: [`${baseUrl}${question.imageUrl}`],
      current: `${baseUrl}${question.imageUrl}`
    })
  }
})
