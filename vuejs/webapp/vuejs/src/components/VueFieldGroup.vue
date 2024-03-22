<template>
  <v-expansion-panels :id="id" class="ma-1" v-model="panels">
    <v-expansion-panel :disabled="!collapsible">
      <v-expansion-panel-header v-if="title !== ''">
            {{title}}
      </v-expansion-panel-header>
      <v-expansion-panel-content :id="collapsibleAreaId">
        <div
            v-for="(component, key) in children"
            :key="key"
            v-bind:is="constants.components[component.name]"
            :props="component"
            :updateStore="updateStore">
        </div>
      </v-expansion-panel-content>
    </v-expansion-panel>
  </v-expansion-panels>
</template>

<script>
  import constants from '../js/constants'

  export default {
    name: "VueFieldGroup",
    props: ['props', 'updateStore'],
    data() {
      return {
        constants: constants,
        panels: []
      }
    },
    computed: {
      children() {
        return this.props.hasOwnProperty('children') ? this.props.children : []
      },
      collapseToolTip() {
        return this.props.attributes.hasOwnProperty('collapseToolTip') ? this.props.attributes.collapseToolTip : ''
      },
      collapsed() {
        return this.props.attributes.hasOwnProperty('collapsed') ? this.props.attributes.collapsed : false
      },
      collapsible() {
        return this.props.attributes.hasOwnProperty('collapsible') ? this.props.attributes.collapsible : false
      },
      collapsibleAreaId() {
        return this.props.attributes.hasOwnProperty('collapsibleAreaId') ? this.props.attributes.collapsibleAreaId : ''
      },
      expanded() {
        return !this.collapsed
      },
      expandToolTip() {
        return this.props.attributes.hasOwnProperty('expandToolTip') ? this.props.attributes.expandToolTip : ''
      },
      iconClass() {
        return this.collapsed ? 'collapsed' : 'expanded'
      },
      id() {
        return this.props.attributes.hasOwnProperty('id') ? this.props.attributes.id : ''
      },
      style() {
        return this.props.attributes.hasOwnProperty('style') ? this.props.attributes.style : ''
      },
      title() {
        return this.props.attributes.hasOwnProperty('title') ? this.props.attributes.title : ''
      },
      tooltip() {
        return this.collapsed ? this.expandToolTip : this.collapseToolTip
      }
    },
    methods: {
      toggleCollapse() {
        this.props.attributes.collapsed = !this.props.attributes.collapsed
      }
    },
    created() {
      if (!this.props.attributes.collapsed) {
        this.panels = 0
      }
    }
  }
</script>

<style scoped>

</style>
