<template>
  <div id="vue-sort-field">
    <a :class="style" :href="linkUrl" v-on:click.prevent="sort" :title="tooltip">{{title}}</a>
  </div>
</template>

<script>
  import {mapGetters} from 'vuex'

  export default {
    name: "VueSortField",
    props: ['props', 'updateStore'],
    computed: {
      ...mapGetters({
        watcher: 'data/watcher'
      }),
      currentWatcher() {
        return this.paginateTarget ? this.watcher(this.paginateTarget) : {}
      },
      entityField() {
        return this.props.attributes.hasOwnProperty('entityField') ? this.props.attributes.entityField : ''
      },
      linkUrl() {
        return this.props.attributes.hasOwnProperty('linkUrl') ? this.props.attributes.linkUrl : ''
      },
      paginateTarget() {
        return this.props.attributes.hasOwnProperty('paginateTarget') ? this.props.attributes.paginateTarget : ''
      },
      sortField() {
        return this.currentWatcher.hasOwnProperty('sortField') && this.currentWatcher.sortField === this.entityField ? '-' + this.entityField : this.entityField
      },
      style() {
        return this.props.attributes.hasOwnProperty('style') ? this.props.attributes.style : ''
      },
      title() {
        return this.props.attributes.hasOwnProperty('title') ? this.props.attributes.title : ''
      },
      tooltip() {
        return this.props.attributes.hasOwnProperty('tooltip') ? this.props.attributes.tooltip : ''
      },
    },
    methods: {
      sort() {
        if (this.paginateTarget && this.entityField) {
          this.$store.dispatch('data/setWatcherAttributes', {
            watcherName: this.paginateTarget,
            params: {
              orderBy: this.entityField,
              sortField: this.sortField
            }
          })
        }
      }
    }
  }
</script>

<style scoped>

</style>
