<template>
  <v-app id="app">
    <app-menu></app-menu>
    <app-log></app-log>
    <v-content>
      <app-cpt></app-cpt>
      <app-wait></app-wait>
      <app-dialog></app-dialog>
      <vue-message-list></vue-message-list>
      <vue-login></vue-login>
      <selenium-info-panel></selenium-info-panel>
      <router-view></router-view>
    </v-content>
  </v-app>
</template>

<script>
  export default {
    name: 'app',
    mounted() {
      let pathname = window.location.pathname
      let api = pathname.substring(0, pathname.indexOf('/', 1)) + '/control'
      let webapp = pathname.substring(pathname.lastIndexOf('/') + 1, pathname.length)
      this.$store.dispatch('backOfficeApi/setApi', api)
      this.$store.dispatch('ui/setUiLabels', api)
      this.$store.dispatch('login/check').then(() => {
        this.$store.dispatch('backOfficeApi/doRequest', {uri: 'getHeaderInfo', mode: 'post', params: {}})
          .then(result => {
            this.$store.dispatch('ui/setLocale', result.body.locale)
          })
        if (webapp === 'showPortalPage1') {
          let search = window.location.search.split('?').join('')
          let parametersList = search.split('&amp;')
          for (let parameter of parametersList) {
            if (parameter.split('=')[0] === 'portalPageId') {
              webapp = parameter.split('=')[1]
            }
          }
          this.$router.push(`/portalPage/${webapp}`)
        } else {
          this.$router.push(`/screen/${webapp}`)
        }
      }, () => {
        this.$router.push('/login')
      })
    }
  }
</script>

<style>
  @import url('https://fonts.googleapis.com/css?family=Roboto:100,300,400,500,700,900');
</style>
