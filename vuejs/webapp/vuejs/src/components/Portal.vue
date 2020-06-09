<template>
  <div id="portal">
    <div v-if="portalPageDetail" class="d-block">
        <vue-column-portlet v-for="column in portalPageDetail.listColumnPortlet" :key="column.portalPageId + '-' + column.columnSeqId"
                            :props="{portalPageId: portalPage, columnSeqId: column.columnSeqId}">
        </vue-column-portlet>
    </div>
  </div>
</template>

<script>
  import {mapGetters} from 'vuex'

  export default {
    name: "Portal",
    data() {
      return {
        params: {}
      }
    },
    computed: {
      ...mapGetters({
        portalPage: 'ui/currentPortalPage',
        portalPageDetail: 'ui/currentPortalPageDetail',
        currentApi: 'backOfficeApi/currentApi'
      })
    },
    created() {
      let search = window.location.search
      let params = this.$route.query
      search.substr(1).split('&').forEach(param => {
        let tmp = param.split('=')
        params[tmp[0]] = tmp[1]
      })
      params.portalPageId = this.$route.params.portalPageId
      this.$store.dispatch('ui/loadPortalPageDetail', {api: this.currentApi, params})
    },
    watch: {
      '$route': function () {
        let search = window.location.search
        let params = this.$route.query
        search.substr(1).split('&').forEach(param => {
          let tmp = param.split('=')
          params[tmp[0]] = tmp[1]
        })
        params.portalPageId = this.$route.params.portalPageId
        this.$store.dispatch('ui/loadPortalPageDetail', {api: this.currentApi, params})
      }
    }
  }
</script>

<style scoped>

</style>
