<template>
  <v-btn text dark class="no-text-transform">
    <div
        v-for="(component, index) in children"
        :key="index"
        v-bind:is="constants.components[component.name]"
        :props="component"
        :updateStore="updateStore"
    >
    </div>
  </v-btn>
</template>

<script>
  import constants from '../js/constants'

  export default {
    name: "VueMenuItem",
    props: ['props', 'updateStore'],
    data() {
      return {
        constants: constants
      }
    },
    computed: {
      children() {
        return this.props.hasOwnProperty('children') ? this.props.children : []
      },
      containsNestedMenus() {
        return this.props.attributes.hasOwnProperty('containsNestedMenus') ? this.props.attributes.containsNestedMenus : ''
      },
      linkStr() {
        return this.props.attributes.hasOwnProperty('linkStr') ? this.props.attributes.linkStr : ''
      },
      style() {
        return this.props.attributes.hasOwnProperty('style') ? this.props.attributes.style : ''
      },
      toolTip() {
        return this.props.attributes.hasOwnProperty('toolTip') ? this.props.attributes.toolTip : ''
      }
    }
  }
</script>

<style scoped>
  .no-text-transform {
    text-transform: none;
  }
</style>
