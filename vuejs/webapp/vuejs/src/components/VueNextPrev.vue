<template>
  <div id="vue-next-prev" :class="paginateStyle" v-if="show">
    <v-toolbar dark color="secondary" class="mb-1" dense text-center>
      <v-btn icon v-on:click.prevent="first" class="d-inline"><v-icon>{{getIcon('mdi-arrow-collapse-left')}}</v-icon></v-btn>
      <v-btn icon v-on:click.prevent="previous" class="d-inline"><v-icon>{{getIcon('mdi-arrow-left')}}</v-icon></v-btn>
      <v-select v-model="viewIndex" :items="indexList" hide-details prefix="Page : " class="d-inline-flex"></v-select>
      <v-btn icon v-on:click.prevent="next" class="d-inline"><v-icon>{{getIcon('mdi-arrow-right')}}</v-icon></v-btn>
      <v-btn icon v-on:click.prevent="last" class="d-inline"><v-icon>{{getIcon('mdi-arrow-collapse-right')}}</v-icon></v-btn>
      <v-select v-model="viewSize" :items="viewSizeList" hide-details prefix="Items per page : " class="d-inline-flex"></v-select>
      <v-label class=" col-2">{{commonDisplaying}}</v-label>
    </v-toolbar>
  </div>
</template>

<script>
  import icons from '../js/icons'

  export default {
    name: "VueNextPrev",
    props: ['props', 'updateStore'],
    data() {
      return {
        viewSizeList: [20, 30, 50, 100, 200]
      }
    },
    computed: {
      commonDisplaying() {
        return this.props.attributes.commonDisplaying
      },
      highIndex() {
        return this.props.attributes.highIndex
      },
      indexList() {
        let list = []
        for (let i = 0; i < this.numberOfPages; i++) {
          list.push({text: i + 1, value: i})
        }
        return list
      },
      listSize() {
        return this.props.attributes.listSize
      },
      numberOfPages() {
        return Math.trunc(this.listSize % this.viewSize > 0 ? this.listSize / this.viewSize + 1 : this.listSize / this.viewSize)
      },
      pageLabel() {
        return this.props.attributes.pageLabel
      },
      paginateFirstLabel() {
        return this.props.attributes.paginateFirstLabel
      },
      paginateFirstStyle() {
        return this.props.attributes.paginateFirstStyle
      },
      paginateLastLabel() {
        return this.props.attributes.paginateLastLabel
      },
      paginateLastStyle() {
        return this.props.attributes.paginateLastStyle
      },
      paginateNextLabel() {
        return this.props.attributes.paginateNextLabel
      },
      paginateNextStyle() {
        return this.props.attributes.paginateNextStyle
      },
      paginatePreviousLabel() {
        return this.props.attributes.paginatePreviousLabel
      },
      paginatePreviousStyle() {
        return this.props.attributes.paginatePreviousStyle
      },
      paginateStyle() {
        return this.props.attributes.paginateStyle
      },
      paginateTarget() {
        return this.props.attributes.paginateTarget
      },
      paginateViewSizeLabel() {
        return this.props.attributes.paginateViewSizeLabel
      },
      show() {
        return this.listSize > this.viewSize
      },
      viewIndex: {
        get() {
          return this.props.attributes.viewIndex
        },
        set(viewIndex) {
          this.$store.dispatch('data/setWatcherAttributes', {watcherName: this.paginateTarget, params: {VIEW_INDEX_1: viewIndex}})
        }
      },
      viewSize: {
        get() {
          return this.props.attributes.viewSize
        },
        set(viewSize) {
          this.$store.dispatch('data/setWatcherAttributes', {watcherName: this.paginateTarget, params: {VIEW_SIZE_1: viewSize}})
        }
      }
    },
    methods: {
      first() {
        this.$store.dispatch('data/setWatcherAttributes', {watcherName: this.paginateTarget, params: {VIEW_INDEX_1: 0}})
      },
      getIcon(icon) {
        return icons.hasOwnProperty(icon) ? icons[icon] : null
      },
      last() {
        this.$store.dispatch('data/setWatcherAttributes', {watcherName: this.paginateTarget, params: {VIEW_INDEX_1: this.numberOfPages - 1}})
      },
      next() {
        this.$store.dispatch('data/setWatcherAttributes', {watcherName: this.paginateTarget, params: {VIEW_INDEX_1: this.viewIndex + 1}})
      },
      previous() {
        this.$store.dispatch('data/setWatcherAttributes', {watcherName: this.paginateTarget, params: {VIEW_INDEX_1: this.viewIndex - 1}})
      }
    }
  }
</script>

<style scoped>

</style>
