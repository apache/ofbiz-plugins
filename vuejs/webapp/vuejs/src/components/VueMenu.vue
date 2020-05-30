<template>
  <v-toolbar id="vue-menu" color="secondary" dark class="ma-0 pa-0" height="35px">
    <v-toolbar-items class="ma-0 pa-0">
      <div v-for="(component, index) in children"
           :key="index"
           v-bind:is="constants.components[component.name]"
           :props="component"
           :updateStore="updateStore">
      </div>
    </v-toolbar-items>
  </v-toolbar>
</template>

<script>
  import constants from '../js/constants'

  export default {
    name: "VueMenu",
    props: ['props', 'updateStore'],
    data() {
      return {
        constants: constants,
        on: false
      }
    },
    computed: {
      boundaryComment() {
        return this.props.attributes.hasOwnProperty('boundaryComment') ? this.props.attributes.boundaryComment : ''
      },
      children() {
        return this.props.hasOwnProperty('children') ? this.props.children : []
      },
      id() {
        return this.props.attributes.hasOwnProperty('id') ? this.props.attributes.id : ''
      },
      style() {
        return this.props.attributes.hasOwnProperty('style') ? this.props.attributes.style : ''
      },
      title() {
        return this.props.attributes.hasOwnProperty('title') ? this.props.attributes.title : ''
      }
    }
  }
</script>

<style scoped>

</style>
