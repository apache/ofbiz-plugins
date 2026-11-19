<template>
  <td id="vue-column-portlet" :style="'width: ' + width + ';vertical-align: top'" class="d-inline-block">
    <vue-portlet v-for="portlet in portletList" :key="portlet.portalPortletId + '-' + portlet.portletSeqId" v-bind:props="portlet">
    </vue-portlet>
  </td>
</template>

<script>
  import {mapGetters} from 'vuex'
  export default {
    name: "VueColumnPortlet",
    props: ['props'],
    computed: {
      ...mapGetters({
        getColumn: 'ui/column',
        portalPages: 'ui/portalPages'
      }),
      portalPageId() {
        return this.props.portalPageId
      },
      columnSeqId() {
        return this.props.columnSeqId
      },
      portletList() {
        return this.column.hasOwnProperty('listPortlet') ? this.column.listPortlet : []
      },
      columnWidthPercentage() {
        return this.column.hasOwnProperty('columnWidthPercentage') ? this.column.columnWidthPercentage : null
      },
      columnWidthPixels() {
        return this.column.hasOwnProperty('columnWidthPixels') ? this.column.columnWidthPixels : null
      },
      width() {
        if (this.columnWidthPercentage) {
          return this.columnWidthPercentage + '%'
        }
        if (this.columnWidthPixels) {
          return this.columnWidthPixels
        }
        return ''
      },
      column() {
        return this.getColumn({portalPageId: this.portalPageId, columnSeqId: this.columnSeqId})
      }
    }
  }
</script>

<style scoped>

</style>
