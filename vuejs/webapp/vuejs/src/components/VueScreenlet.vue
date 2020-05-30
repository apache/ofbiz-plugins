<template>
  <div vue-component="vue-screenlet" :id="screenletId" class="ma-1">
    <v-toolbar v-if="showMore" dense color="primary" class="screenlet-title-bar ma-0 pa-0 screenletToolbar">
      <v-toolbar-title class="title secondary--text">{{title}}</v-toolbar-title>
      <v-spacer></v-spacer>
      <vue-nav-menu v-if="navMenu" :props="navMenu" :updateStore="updateStore"></vue-nav-menu>
      <v-tooltip top>
        <template v-slot:activator="{ on }">
          <v-btn id="toggleCollapse" icon v-if="collapsible" @click="toggle" v-on="on">
            <v-icon color="secondary">{{ collapseIcon }}</v-icon>
          </v-btn>
        </template>
        <span>{{toolTip}}</span>
      </v-tooltip>
    </v-toolbar>
    <v-expand-transition>
      <v-card tile :id="collapsibleAreaId" v-show="!collapsed" class="ma-0 pa-0 screenletContent">
        <v-card-text class="ma-0 pa-0">
          <div
              v-for="(component, key) in props.children"
              :key="key"
              v-bind:is="constants.components[component.name]"
              :props="component"
              :updateStore="updateStore"
              class="ma-0 pa-0">
          </div>
        </v-card-text>
      </v-card>
    </v-expand-transition>
  </div>
</template>

<script>
  import constants from '../js/constants'
  import icons from '../js/icons'
  import {mapGetters} from 'vuex'

  export default {
    name: "VueScreenlet",
    props: ['props', 'updateStore'],
    data() {
      return {
        constants: constants,
        mdiArrowCollapseUp: icons['mdi-arrow-collapse-up'],
        mdiArrowExpandDown: icons['mdi-arrow-expand-down']
      }
    },
    computed: {
      ...mapGetters({
        collapsibleStatus: 'ui/collapsibleStatus',
        uiLabels: 'ui/uiLabels'
      }),
      bodyChildren() {
        return this.props.children.filter(component => !['Menu', 'ScreenletSubWidget'].includes(component.name))
      },
      collapsed() {
        return this.collapsibleStatus(this.id)
      },
      collapseIcon() {
        return this.collapsed ? this.mdiArrowExpandDown : this.mdiArrowCollapseUp
      },
      collapseToolTip() {
        return this.uiLabels.hasOwnProperty('collapseToolTip') ? this.uiLabels.collapseToolTip : ''
      },
      collapsible() {
        return this.props.attributes.hasOwnProperty('collapsible') && this.props.attributes.collapsible
      },
      collapsibleAreaId() {
        return this.props.attributes.hasOwnProperty('collapsibleAreaId') ? this.props.attributes.collapsibleAreaId : ''
      },
      expandToolTip() {
        return this.uiLabels.hasOwnProperty('expandToolTip') ? this.uiLabels.expandToolTip : ''
      },
      saveCollapsed() { // TODO to be used to manage it, needed a common uri to post the save
        return this.props.attributes.hasOwnProperty('collapsible')
      },
      headerChildren() {
        return this.props.children.filter(component => ['Menu'].includes(component.name))
      },
      id() {
        return this.props.attributes.hasOwnProperty('id') ? this.props.attributes.id : ''
      },
      name() {
        return this.props.attributes.hasOwnProperty('name') ? this.props.attributes.name : ''
      },
      navMenu() {
        return this.props.attributes.hasOwnProperty('navMenu') ? this.props.attributes.navMenu : null
      },
      showMore() {
        return this.props.attributes.hasOwnProperty('showMore') ? this.props.attributes.showMore : ''
      },
      style() {
        if (this.props.attributes.collapsible) {
          if (this.collapsed) {
            return {display: 'none'}
          } else {
            return {}
          }
        } else {
          return {}
        }
      },
      tabMenu() {
        return this.props.attributes.hasOwnProperty('tabMenu') ? this.props.attributes.tabMenu : null
      },
      screenletId() {
        return `${this.id}-${this.name}`
      },
      title() {
        return this.props.attributes.hasOwnProperty('title') ? this.props.attributes.title : ''
      },
      toolTip() {
        if (this.collapsed) {
          return this.expandToolTip
        } else {
          return this.collapseToolTip
        }
      }
    },
    methods: {
      toggle() {
        this.$store.dispatch('ui/setCollapsibleStatus', {areaId: this.id, areaTarget: !this.collapsed})
      }
    },
    created() {
      if (this.collapsible) {
        this.$store.dispatch('ui/setCollapsibleStatus', {
          areaId: this.id,
          areaTarget: this.collapsed
        })
      }
    }
  }
</script>

<style scoped>
  .screenletToolbar {
    border-top-left-radius: 5px;
    border-top-right-radius: 5px;
  }

  .screenletContent {
    border-bottom-left-radius: 5px;
    border-bottom-right-radius: 5px;
  }
</style>
