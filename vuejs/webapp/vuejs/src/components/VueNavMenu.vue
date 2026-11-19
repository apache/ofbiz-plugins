<template>
  <v-menu bottom left v-if="!inline" transition="scale-transition" origin="center center">
    <template v-slot:activator="{ on }">
      <v-btn icon v-on="on">
        <v-icon id='mdi-dots-vertical'>{{getIcon('mdi-dots-vertical')}}</v-icon>
      </v-btn>
    </template>
    <v-list dense>
      <vue-nav-menu-item v-for="(component, index) in children"
                         :key="index"
                         :props="component"
                         :updateStore="updateStore"></vue-nav-menu-item>
    </v-list>
  </v-menu>
  <div class="d-flex flex-row-reverse" v-else>
      <vue-nav-menu-item-inline v-for="(component, index) in children"
                         :key="index"
                         :props="component"
                         :updateStore="updateStore"
                         class="text-right d-flex">
      </vue-nav-menu-item-inline>
    </div>
</template>

<script>
  import constants from '../js/constants'
  import icons from '../js/icons'

  export default {
    name: "VueNavMenu",
    props: ['props', 'updateStore'],
    data() {
      return {
        constants: constants,
        on: false,
        maxInline: 3
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
      inline() {
        return this.children.length <= this.maxInline
      },
      style() {
        return this.props.attributes.hasOwnProperty('style') ? this.props.attributes.style : ''
      },
      title() {
        return this.props.attributes.hasOwnProperty('title') ? this.props.attributes.title : ''
      },
    },
    methods: {
      getIcon(icon) {
        return icons.hasOwnProperty(icon) ? icons[icon] : null
      }
    }
  }
</script>

<style scoped>

</style>
