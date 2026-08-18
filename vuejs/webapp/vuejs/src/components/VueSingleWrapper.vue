<template>
  <table id="vue-single-wrapper" role="none">
    <tr
      v-for="(component, key) in children"
      :key="key"
      v-bind:is="constants.components[component.name]"
      :props="component"
      :updateStore="updateStore">
    </tr>
  </table>
</template>

<script>
  import constants from '../js/constants'

  export default {
    name: "VueSingleWrapper",
    props: ['props', 'updateStore'],
    data() {
      return {
        constants: constants
      }
    },
    computed: {
      children() {
        return this.props.hasOwnProperty('children') ? this.props.children : []
      }
    }
  }
</script>

<style scoped>

</style>
