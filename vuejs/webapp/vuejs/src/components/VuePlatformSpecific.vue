<template>
  <div id="vue-platform-specific">
    <div v-bind:is="componentName" :props="attributes"></div>
  </div>
</template>

<script>
  export default {
    name: "VuePlatformSpecific",
    props: ['props', 'updateStore'],
    computed: {
      attributes() {
        return this.props.hasOwnProperty('attributes') ? this.props.attributes : {}
      },
      componentName() {
        return this.props.attributes.hasOwnProperty('componentName') ? this.props.attributes.componentName : ''
      }
    }
  }
</script>

<style scoped>

</style>
