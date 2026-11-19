<template>
  <v-dialog :value="status">
    <v-btn fab fixed top right @click.stop="closeDialog">
      <v-icon>{{mdiClose}}</v-icon>
    </v-btn>
    <v-card v-if="content">
      <v-card-text>
        <div v-for="(component, key) in content.viewScreen"
             :key="key"
             v-bind:is="constants.components[component.name]"
             :props="component"></div>
      </v-card-text>
    </v-card>
  </v-dialog>
</template>

<script>
  import constants from '../js/constants'
  import icons from '../js/icons'

  export default {
    name: "AppDialog",
    data() {
      return {
        constants: constants,
        mdiClose: icons['mdi-close']
      }
    },
    computed: {
      content() {
        return this.$store.getters['ui/area']('appDialog')
      },
      status() {
        return this.$store.getters['ui/dialogStatus']('appDialog')
      }
    },
    methods: {
      closeDialog() {
        this.$store.dispatch('ui/setDialogStatus', {dialogId: 'appDialog', dialogStatus: false})
      }
    }
  }
</script>

<style scoped>

</style>
