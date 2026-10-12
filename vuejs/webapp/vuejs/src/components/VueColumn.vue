<template>
  <v-col id="vue-column" class="ma-0 pa-0" :cols="grid.cols" :sm="grid.sm" :md="grid.md" :lg="grid.lg" :xl="grid.lg">
    <div v-for="(component, id) in children" :key="id" v-bind:is="constants.components[component.name]"
         :props="component">
    </div>
  </v-col>
</template>

<script>
  import constants from '../js/constants'

  export default {
    name: "VueColumn",
    props: ['props'],
    data() {
      return {
        constants: constants
      }
    },
    computed: {
      children() {
        return this.props.hasOwnProperty('children') ? this.props.children : []
      },
      style() {
        return this.props.attributes.hasOwnProperty('style') ? this.props.attributes.style : ''
      },
      grid() {
        let gridVal = {
          cols: false,
          sm: false,
          md: false,
          lg: false,
          xl: false
        }
        if (this.style) {
          this.style.split().forEach(val => {
            let tmp = val.split('-')
            gridVal[tmp[0]] = tmp[1]
          })
        }
        return gridVal
      }
    }
  }
</script>

<style scoped>

</style>
