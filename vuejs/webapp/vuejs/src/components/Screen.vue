<template>
  <div id="screen">
    <div v-for="(component, id) in screen.viewScreen" :key="id" v-bind:is="constants.components[component.name]" :props="component">
    </div>
  </div>
</template>

<script>
  import constants from './../js/constants'
  import {mapGetters} from 'vuex'

  export default {
    name: "Screen",
    data() {
      return {
        params: {},
        screen: {},
        constants: constants
      }
    },
    computed: {
      ...mapGetters({
        currentApi: 'backOfficeApi/currentApi'
      })
    },
    mounted() {
      let screenId = this.$route.params.screenId
      if (screenId === 'main') {
        screenId = 'mainfjs'
      }
      if (this.$route.params.cover){
         screenId = screenId + '/' + this.$route.params.cover
      }
      let params = this.$route.query
      let mode = ( Object.keys(params).length === 0
                   || (Object.keys(params).length === 1 && params.portalPageId) ) ? 'get' : 'post'
      this.$wait.start(screenId)
      this.$store.dispatch('backOfficeApi/doRequest', {uri: this.currentApi + '/' + screenId, mode: mode, params: params})
        .then(
          response => {
            this.screen = response.body
            this.$wait.end(screenId)
          },
          () => {
            this.$wait.end(screenId)
          }
        )
    },
    watch: {
      '$route': function () {
        let screenId = this.$route.params.screenId
        if (screenId === 'main') {
          screenId = 'mainfjs'
        }
        if (this.$route.params.cover){
            screenId = screenId + '/' + this.$route.params.cover
        }
        let params = this.$route.query
        let mode = ( Object.keys(params).length === 0
                   || (Object.keys(params).length === 1 && params.portalPageId) ) ? 'get' : 'post'
        this.$wait.start(screenId)
        this.$store.dispatch('backOfficeApi/doRequest', {uri: this.currentApi + '/' + screenId, mode: mode, params: params})
          .then(
            response => {
              this.screen = response.body
              this.$wait.end(screenId)
            },
            () => {
              this.$wait.end(screenId)
            }
          )
      }
    }
  }
</script>

<style scoped>

</style>
