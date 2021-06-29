<template>
  <option id="vue-option" v-bind:value="key" :selected="selected">
    {{description}}
  </option>
</template>

<script>
  export default {
    name: "VueOption",
    data() {
      return {
        data: {}
      }
    },
    props: ['props','selected'],
    computed: {
      description() {
        return this.props.hasOwnProperty('description') ? this.props.description : ''
      },
      key() {
        return this.props.hasOwnProperty('key') ? this.props.key : ''
      }
    }
  }
</script>

<style scoped>

</style>
