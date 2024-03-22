import Vue from 'vue';
import Vuetify from 'vuetify/lib';

Vue.use(Vuetify);

export default new Vuetify({
  icons: {
    iconfont: 'mdiSvg',
  },
  theme: {
    dark: false,
    themes: {
      light: {
        primary: '#41b883',
        secondary: '#35495e',
        accent: '#d52c3e',
        success: '#64dd17',
        error: '#f44336'
      },
      dark: {
        primary: '#41b883',
        secondary: '#35495e',
        accent: '#d52c3e',
        success: '#64dd17',
        error: '#f44336'
      }
    }
  }
});
