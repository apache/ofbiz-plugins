module.exports = {
  css: {
    extract: false,
  },
  configureWebpack: {
    output: {
      libraryExport: 'default'
    }
  },
  chainWebpack: (config) => {
    config.optimization.minimizer('terser').tap((args) => {
      args[0].terserOptions.compress.drop_console = true
      args[0].terserOptions.compress.pure_funcs = ['console.log']
      return args
    })
  },
  filenameHashing: false
}
