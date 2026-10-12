<template>
  <label class="font-weight-medium ma-2" v-if="!single">{{display}}</label>
</template>

<script>
  export default {
    name: "VueFieldTitle",
    props: ['props'],
    computed: {
      description() {
        return this.props.attributes.hasOwnProperty('description') ? this.props.attributes.description : ''
      },
      display() {
        return this.title ? this.title : this.description
      },
      single() {
        return this.props.attributes.hasOwnProperty('single')
      },
      title() {
        return this.props.attributes.hasOwnProperty('title') ? this.props.attributes.title : ''
      }
    }
  }
</script>

<style scoped>

</style>
