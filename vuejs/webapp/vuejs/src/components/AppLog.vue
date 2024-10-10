<template>
  <v-navigation-drawer right :value="logDrawerState" class="primary text--secondary" id="app-nav" fixed app>
    <v-list>
      <v-list-item v-for="(log, index) in logs" :key="index">
        <v-list-item-content>
          {{ log }}
        </v-list-item-content>
      </v-list-item>
    </v-list>
    <template v-slot:append>
      <v-list>
        <v-list-item @click="clear">
          <v-list-item-icon><v-icon>{{ mdiDelete }}</v-icon></v-list-item-icon>
          <v-list-item-content>
            <v-list-item-title>
              clear
            </v-list-item-title>
          </v-list-item-content>
        </v-list-item>
        <v-list-item @click="addSample">
          <v-list-item-icon><v-icon>{{ mdiPlaylistPlus }}</v-icon></v-list-item-icon>
          <v-list-item-content>
            <v-list-item-title>
              add sample
            </v-list-item-title>
          </v-list-item-content>
        </v-list-item>
      </v-list>
    </template>
  </v-navigation-drawer>
</template>

<script>
  import {mapGetters} from 'vuex'
  import icons from '../js/icons'
  export default {
    name: "AppLog",
    data() {
      return {
        mdiDelete: icons['mdi-delete'],
        mdiPlaylistPlus: icons['mdi-playlist-plus']
      }
    },
    computed: {
      ...mapGetters({
        logDrawerState: 'ui/logDrawerState',
        logs: 'data/logs'
      })
    },
    methods: {
      clear() {
        this.$store.dispatch('data/clearLogs')
      },
      addSample() {
        this.$store.dispatch('data/addLog', 'some useless logs...')
      }
    }
  }
</script>

<style scoped>

</style>
