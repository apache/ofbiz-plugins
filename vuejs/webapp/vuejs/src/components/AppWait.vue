<template>
  <div>
    <input type="hidden" id="loader-wrapper-hidden" v-if="!blockUi" v-show="waitAny"/>
    <v-overlay :value="waitAny">
      <v-progress-circular
          id="loader-wrapper"
          indeterminate
          :size=64
          color="success"
      > </v-progress-circular>
    </v-overlay>
  </div>
</template>

<script>
  import constants from '../js/constants'

  export default {
    name: "AppWait",
    computed: {
      blockUi() {
        return constants.blockUi
      },
      waitAny() {
        return this.$wait.any
      }
    },
  }
</script>

<style scoped>

</style>
