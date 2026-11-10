<template>
  <td vue-component="vue-item-row-cell" :fieldname='fieldName'>
    <div
      v-for="(component, key) in children"
      :key="key"
      v-bind:is="constants.components[component.name]"
      :props="component"
      :updateStore="updateStore">
    </div>
  </td>
</template>

<script>
  import constants from '../js/constants'

  export default {
    name: "VueItemRowCell",
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
      fieldName() { // currently needed for selenium test
        return this.props.attributes.fieldName
      }
    }
  }
</script>

<style scoped>

</style>
