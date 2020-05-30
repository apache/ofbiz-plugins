<template>
  <table :id='formName' vue-component="vue-list-wrapper" v-if="show">
    <tbody
        v-for="(component, key) in children"
        :key="key"
        v-bind:is="constants.components[component.name]"
        :props="component"
        :updateStore="updateStore">
    </tbody>
  </table>
</template>

<script>
  import constants from '../js/constants'

  export default {
    name: "VueListWrapper",
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
      formName() {
        return this.props.attributes.formName
      },
      listSize() {
        return this.props.attributes.hasOwnProperty("listSize") ? this.props.attributes.listSize : 0
      },
      show() {
        return this.listSize > 0
      }
    },
    created() {
      if (this.props.attributes.hasOwnProperty('errorMessage')) {
        this._vm.flash(this.props.attributes.errorMessage, 'error', 10000)
      }
    }
  }
</script>

<style scoped>

</style>
