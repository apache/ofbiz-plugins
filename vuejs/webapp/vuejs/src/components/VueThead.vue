<template>
  <thead id="vue-thead">
    <tr
      v-for="(component, key) in children"
      :key="key"
      v-bind:is="constants.components[component.name]"
      :props="component"
      :updateStore="updateStore">
    </tr>
  </thead>
</template>

<script>
  import constants from '../js/constants'

  export default {
    name: "VueThead",
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
