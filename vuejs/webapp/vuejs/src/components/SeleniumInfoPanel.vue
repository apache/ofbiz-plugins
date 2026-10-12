<template>
  <v-overlay v-if="currentInfoPanel">
    <v-card :color="panelColor" light width="400px">
      <v-card-title v-if="panelTitle"><v-spacer></v-spacer>{{ panelTitle }}<v-spacer></v-spacer></v-card-title>
      <v-card-text class="text-center" v-if="panelMessage">{{ panelMessage }}</v-card-text>
    </v-card>
  </v-overlay>
</template>

<script>
  import {mapGetters} from 'vuex'

  export default {
    name: "SeleniumInfoPanel",
    data() {
      return {
        defaultTimeout: 5000
      }
    },
    computed: {
      ...mapGetters({
        seleniumInfoPanels: 'ui/seleniumInfoPanels'
      }),
      currentInfoPanel() {
        return this.seleniumInfoPanels.length > 0 ? this.seleniumInfoPanels[0] : null
      },
      panelMessage() {
        return this.currentInfoPanel.hasOwnProperty('panelMessage') ? this.currentInfoPanel.panelMessage : ''
      },
      panelTitle() {
        return this.currentInfoPanel.hasOwnProperty('panelTitle') ? this.currentInfoPanel.panelTitle : ''
      },
      panelColor() {
        return this.currentInfoPanel.hasOwnProperty('panelColor') ? this.currentInfoPanel.panelColor : ''
      }
    },
    methods: {
      dismiss(infoPanel) {
        this.$store.dispatch('ui/deleteSeleniumInfoPanel', infoPanel)
      },
      showSeleniumInfoPanel({panelMessage, panelTimeout, panelTitle, panelColor}) {
        this.$store.dispatch('ui/addSeleniumInfoPanel', {panelMessage: panelMessage, panelTimeout, panelTitle, panelColor})
      }
    },
    created() {
      window.showSeleniumInfoPanel = this.showSeleniumInfoPanel
    },
    watch: {
      currentInfoPanel(infoPanel) {
        if (infoPanel) {
          setTimeout(() => {
            if (infoPanel === this.currentInfoPanel) {
              this.dismiss(infoPanel)
            }
          }, infoPanel.hasOwnProperty('panelTimeout') && infoPanel.panelTimeout ? infoPanel.panelTimeout : this.defaultTimeout)
        }
      }
    }
  }
</script>

<style scoped>

</style>
