<!-- Component used to manage menu in a pop-up not in a line-->
<template>
  <v-list-item :id="id" link @click="propagate">
    <v-list-item-content>
    <div
        :ref="ref"
        v-for="(component, index) in children"
        :key="index"
        v-bind:is="constants.components[component.name]"
        :props="component"
        :updateStore="updateStore"
        :inline="false"
        :clickDisabled="true"
    ></div>
    </v-list-item-content>
  </v-list-item>
</template>

<script>
  import constants from '../js/constants'

  export default {
    name: "VueNavMenuItem",
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
      containsNestedMenus() {
        return this.props.attributes.hasOwnProperty('containsNestedMenus') ? this.props.attributes.containsNestedMenus : ''
      },
      id() {
        // noinspection JSPotentiallyInvalidTargetOfIndexedPropertyAccess
        return this.children[0].attributes.text.split(' ').join('_')
      },
      linkStr() {
        return this.props.attributes.hasOwnProperty('linkStr') ? this.props.attributes.linkStr : ''
      },
      ref() {
        return this.id + '_link'
      },
      style() {
        return this.props.attributes.hasOwnProperty('style') ? this.props.attributes.style : ''
      },
      toolTip() {
        return this.props.attributes.hasOwnProperty('toolTip') ? this.props.attributes.toolTip : ''
      }
    },
    methods: {
      propagate() {
        this.$refs[this.ref][0].handleUpdate()
      }
    }
  }
</script>

<style scoped>

</style>
