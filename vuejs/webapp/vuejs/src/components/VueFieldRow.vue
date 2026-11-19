<template>
  <tr id="vue-field-row">
   <td v-for="(component, key) in children" :key="key">
    <div
      v-bind:is="constants.components[component.name]"
      :props="component"
      :updateStore="updateStore">
    </div>
   </td>
  </tr>
</template>

<script>
  import constants from '../js/constants'

  export default {
    name: "VueFieldRow",
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
