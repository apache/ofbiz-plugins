<template>
  <span id="vue-asterisks" v-if="show">*</span>
</template>

<script>
  export default {
    name: "VueAsterisks",
    props: ['props', 'updateStore'],
    data() {
      return {
      }
    },
    computed: {
      requiredField() {
        return this.props.attributes.hasOwnProperty('requiredField') ? this.props.attributes.requiredField : false
      },
      requiredStyle() {
        return this.props.attributes.hasOwnProperty('requiredStyle') ? this.props.attributes.requiredStyle : false
      },
      show() {
        return this.requiredField && !this.requiredStyle
      }
    }
  }
</script>

<style scoped>

</style>
