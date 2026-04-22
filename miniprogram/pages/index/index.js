const app = getApp()

Page({
  data: {
    questions: [],
    loading: false,
    uploading: false
  },

  onShow() {
    this.loadQuestions()
  },

  loadQuestions() {
    const baseUrl = app.globalData.baseUrl
    wx.request({
      url: `${baseUrl}/api/questions`,
      method: 'GET',
      success: (res) => {
        if (res.statusCode === 200) {
          const questions = res.data.map(q => ({
            ...q,
            imageUrl: q.imageUrl && !q.imageUrl.startsWith('http')
              ? `${baseUrl}${q.imageUrl}` : q.imageUrl
          }))
          this.setData({ questions })
        }
      },
      fail: (err) => {
        console.error('加载历史记录失败', err)
      }
    })
  },

  chooseAndUpload() {
    wx.chooseMedia({
      count: 1,
      mediaType: ['image'],
      sourceType: ['album', 'camera'],
      success: (res) => {
        const tempFilePath = res.tempFiles[0].tempFilePath
        this.uploadImage(tempFilePath)
      }
    })
  },

  goDetail(e) {
    const id = e.currentTarget.dataset.id
    wx.navigateTo({ url: `/pages/detail/detail?id=${id}` })
  },

  uploadImage(tempFilePath) {
    const baseUrl = app.globalData.baseUrl
    this.setData({ uploading: true })

    wx.showLoading({ title: 'AI 分析中...', mask: true })

    wx.uploadFile({
      url: `${baseUrl}/api/questions/upload`,
      filePath: tempFilePath,
      name: 'file',
      success: (res) => {
        wx.hideLoading()
        let data
        try {
          data = JSON.parse(res.data)
        } catch (e) {
          wx.showToast({ title: '返回数据解析失败', icon: 'error' })
          return
        }

        if (res.statusCode === 200) {
          wx.navigateTo({
            url: `/pages/detail/detail?id=${data.id}`
          })
        } else {
          wx.showToast({
            title: data.error || '分析失败，请重试',
            icon: 'error',
            duration: 3000
          })
        }
      },
      fail: (err) => {
        wx.hideLoading()
        wx.showToast({ title: '上传失败，请检查网络', icon: 'error' })
        console.error('上传失败', err)
      },
      complete: () => {
        this.setData({ uploading: false })
      }
    })
  }
})
