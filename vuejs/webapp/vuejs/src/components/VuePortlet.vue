<template>
  <div id="vue-portlet">
    <div :id="'vue-portlet_' + portletId">
      <div v-if="portlet">
        <div
          v-for="(component, key) in viewScreen"
          :key="key"
          v-bind:is="constants.components[component.name]"
          :props="component">
        </div>
      </div>
      <div v-else-if="isPosted">
        <div
          v-for="(component, key) in children"
          :key="key"
          v-bind:is="constants.components[component.name]"
          :props="component">
        </div>
      </div>
    </div>
  </div>
</template>

<script>
  import {mapGetters} from 'vuex'
  import constants from '../js/constants'

  export default {
    name: "VuePortlet",
    props: ['props'],
    data() {
      return {
        constants: constants,
      }
    },
    computed: {
      ...mapGetters({
        getPortlet: 'ui/area',
        getWatcher: 'data/watcher',
        portalPageId: 'ui/currentPortalPage',
        currentPortalPageParams: 'ui/currentPortalPageParams'
      }),
      children() {
        return this.props.hasOwnProperty('children') ? this.props.children : []
      },
      getParams() {
          return this.$store.getters['data/watcher'](this.watcherName)
      },
      isPosted() {
        return this.props.hasOwnProperty('attributes')
      },
      portalPortletId() {
        return this.props.hasOwnProperty('portalPortletId') ? this.props.portalPortletId : this.props.attributes.portalPortletId
      },
      portlet() {
        return this.getPortlet(this.portletId)
      },
      portletId() {
        return this.portalPortletId + '-' + this.portletSeqId
      },
      portletSeqId() {
        return this.props.hasOwnProperty('portletSeqId') ? this.props.portletSeqId : this.props.attributes.portletSeqId
      },
      viewScreen() {
        return this.portlet && this.portlet.hasOwnProperty('viewScreen') ? this.portlet.viewScreen : []
      },
      watcherName() {
        return this.props.hasOwnProperty('watcherName') && this.props.watcherName ? this.props.watcherName : this.props.hasOwnProperty('attributes') && this.props.attributes.hasOwnProperty('watcherName') ? this.props.attributes.watcherName : ''
      },
      watcherRefresh() {
        return this.$store.getters['ui/watcherRefresh'](this.watcherName)
      }
    },
    created() {
      this.$store.dispatch('ui/deleteArea', {areaId: this.portletId})
      if (this.watcherName) {
        this.$store.dispatch('data/setWatcherAttributes', {
          watcherName: this.watcherName,
          params: this.currentPortalPageParams
        })
        this.$store.dispatch('ui/setWatcherRefresh', this.watcherName)
      } else if (!this.isPosted) {
        this.$store.dispatch('ui/setArea', {
          areaId: this.portalPortletId + '-' + this.portletSeqId,
          targetUrl: this.$store.getters['backOfficeApi/currentApi'] + constants.showPortlet.path,
          params: {...this.currentPortalPageParams, portalPortletId: this.portalPortletId, portalPageId: this.portalPageId, portletSeqId: this.portletSeqId}
        })
      }
    },
    beforeDestroy() {
      this.$store.dispatch('ui/deleteArea', {areaId: this.portletId})
    },
    watch: {
      currentPortalPageParams: function (val) {
        this.$store.dispatch('ui/setArea', {
          areaId: this.portletId,
          targetUrl: this.$store.getters['backOfficeApi/currentApi'] + constants.showPortlet.path,
          params: {...val, portalPortletId: this.portalPortletId, portalPageId: this.portalPageId, portletSeqId: this.portletSeqId}
        })
      },
      getParams: function (val) {
        this.$store.dispatch('ui/setArea', {
          areaId: this.portletId,
          targetUrl: this.$store.getters['backOfficeApi/currentApi'] + constants.showPortlet.path,
          params: {...val, portalPortletId: this.portalPortletId, portalPageId: this.portalPageId, portletSeqId: this.portletSeqId}
        })
      },
      props: function () {
        this.$store.dispatch('ui/deleteArea', {areaId: this.portletId})
        this.$store.dispatch('ui/setWatcherRefresh', this.watcherName)
      },
      watcherRefresh: function (val) {
        if (val > 0) {
          this.$store.dispatch('ui/setArea', {
            areaId: this.portletId,
            targetUrl: this.$store.getters['backOfficeApi/currentApi'] + constants.showPortlet.path,
            params: {...val, portalPortletId: this.portalPortletId, portalPageId: this.portalPageId, portletSeqId: this.portletSeqId}
          })
        }
      }
    }
  }
</script>

<style scoped>

</style>
