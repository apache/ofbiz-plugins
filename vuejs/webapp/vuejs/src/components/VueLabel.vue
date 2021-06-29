<template>
  <div>
    <h1 :id="id" v-if="isHeader" v-bind:is="style">{{text}}</h1>
    <span :id="id" v-else :class="style">{{text}}</span>
  </div>
</template>

<script>
  export default {
    name: "VueLabel",
    props: ['props', 'updateStore'],
    computed: {
      id() {
        return this.props.attributes.hasOwnProperty('id') ? this.props.attributes.id : ''
      },
      isHeader() {
        return ['h1', 'h2', 'h3', 'h4', 'h5', 'h6'].includes(this.style.toLocaleLowerCase())
      },
      style() {
        return this.props.attributes.hasOwnProperty('style') ? this.props.attributes.style : ''
      },
      text() {
        return this.props.attributes.hasOwnProperty('text') ? this.props.attributes.text : ''
      }
    }
  }
</script>

<style scoped>

</style>
