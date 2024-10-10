import Vue from 'vue'
import VueResource from 'vue-resource'
import VueRouter from 'vue-router'

import VueWait from 'vue-wait'
import _ from 'lodash'
import VueTheMask from 'vue-the-mask'

import App from './components/App'
import Portal from './components/Portal'
import VueForm from './components/VueForm'
import VueField from './components/VueField'
import VueDropDownField from './components/VueDropDownField'
import VueHiddenField from './components/VueHiddenField'
import VueRadioField from './components/VueRadioField'
import VueSubmitField from './components/VueSubmitField'
import VueTextFindField from './components/VueTextFindField'
import VueLabel from './components/VueLabel'
import VueOption from './components/VueOption'
import VueDisplayField from './components/VueDisplayField'
import VueTextField from './components/VueTextField'
import VueLookupField from './components/VueLookupField'
import VueTextAreaField from './components/VueTextAreaField'
import VueDateTimeField from './components/VueDateTimeField'
import VueError from './components/VueError'
import VueAsterisks from './components/VueAsterisks'
import VueTable from './components/VueTable'
import VueTr from './components/VueTr'
import VueSortField from './components/VueSortField'
import VueColumnPortlet from './components/VueColumnPortlet'
import VuePortlet from './components/VuePortlet'
import VueThead from './components/VueThead'
import VueListWrapper from './components/VueListWrapper'
import VueHeader from './components/VueHeader'
import VueHeaderRow from './components/VueHeaderRow'
import VueHeaderRowCell from './components/VueHeaderRowCell'
import VueFieldTitle from './components/VueFieldTitle'
import VueItemRow from './components/VueItemRow'
import VueItemRowCell from './components/VueItemRowCell'
import VueSingleWrapper from './components/VueSingleWrapper'
import VueFieldRow from './components/VueFieldRow'
import VueFieldRowTitleCell from './components/VueFieldRowTitleCell'
import VueFieldRowWidgetCell from './components/VueFieldRowWidgetCell'
import VueFieldGroup from './components/VueFieldGroup'
import VueHyperlinkField from './components/VueHyperlinkField'
import VueNextPrev from './components/VueNextPrev'
import VueScreenlet from './components/VueScreenlet'
import VueMenu from './components/VueMenu'
import VueMenuItem from './components/VueMenuItem'
import VueLink from './components/VueLink'
import VueContainer from './components/VueContainer'
import VueMessageList from './components/VueMessageList'
import VueLogin from './components/VueLogin'
import VueFormatEmptySpace from './components/VueFormatEmptySpace'
import VuePlatformSpecific from './components/VuePlatformSpecific'
import VueNavMenu from './components/VueNavMenu'
import VueNavMenuItem from './components/VueNavMenuItem'
import Screen from './components/Screen'
import VuePasswordField from './components/VuePasswordField'
import VueNavMenuItemInline from './components/VueNavMenuItemInline'
import VueHorizontalSeparator from './components/VueHorizontalSeparator'
import AppMenu from './components/AppMenu'
import AppWait from './components/AppWait'
import AppCpt from './components/AppCpt'
import VueColumn from './components/VueColumn'
import VueColumnContainer from './components/VueColumnContainer'
import SeleniumInfoPanel from './components/SeleniumInfoPanel'
import AppLog from './components/AppLog'
import AppDialog from './components/AppDialog'

// Platform Specific
import ContactMech from './components/platformSpecific/ContactMech'
import store from './store'
import vuetify from '@/plugins/vuetify';

Vue.use(VueResource)
Vue.use(VueRouter)

Vue.use(VueWait)
Vue.use(VueTheMask)

Vue.component('portal', Portal)
Vue.component('screen', Screen)
Vue.component('vue-form', VueForm)
Vue.component('vue-field', VueField)
Vue.component('vue-drop-down-field', VueDropDownField)
Vue.component('vue-hidden-field', VueHiddenField)
Vue.component('vue-hyperlink-field', VueHyperlinkField)
Vue.component('vue-radio-field', VueRadioField)
Vue.component('vue-submit-field', VueSubmitField)
Vue.component('vue-text-find-field', VueTextFindField)
Vue.component('vue-label', VueLabel)
Vue.component('vue-option', VueOption)
Vue.component('vue-display-field', VueDisplayField)
Vue.component('vue-text-field', VueTextField)
Vue.component('vue-lookup-field', VueLookupField)
Vue.component('vue-text-area-field', VueTextAreaField)
Vue.component('vue-date-time-field', VueDateTimeField)
Vue.component('vue-error', VueError)
Vue.component('vue-asterisks', VueAsterisks)
Vue.component('vue-table', VueTable)
Vue.component('vue-tr', VueTr)
Vue.component('vue-sort-field', VueSortField)
Vue.component('vue-column-portlet', VueColumnPortlet)
Vue.component('vue-portlet', VuePortlet)
Vue.component('vue-thead', VueThead)
Vue.component('vue-list-wrapper', VueListWrapper)
Vue.component('vue-header', VueHeader)
Vue.component('vue-header-row', VueHeaderRow)
Vue.component('vue-header-row-cell', VueHeaderRowCell)
Vue.component('vue-field-title', VueFieldTitle)
Vue.component('vue-item-row', VueItemRow)
Vue.component('vue-item-row-cell', VueItemRowCell)
Vue.component('vue-single-wrapper', VueSingleWrapper)
Vue.component('vue-field-row', VueFieldRow)
Vue.component('vue-field-row-title-cell', VueFieldRowTitleCell)
Vue.component('vue-field-row-widget-cell', VueFieldRowWidgetCell)
Vue.component('vue-field-group', VueFieldGroup)
Vue.component('vue-next-prev', VueNextPrev)
Vue.component('vue-screenlet', VueScreenlet)
Vue.component('vue-menu', VueMenu)
Vue.component('vue-menu-item', VueMenuItem)
Vue.component('vue-link', VueLink)
Vue.component('vue-container', VueContainer)
Vue.component('vue-message-list', VueMessageList)
Vue.component( 'vue-login', VueLogin)
Vue.component('vue-format-empty-space', VueFormatEmptySpace)
Vue.component('vue-platform-specific', VuePlatformSpecific)
Vue.component('vue-nav-menu', VueNavMenu)
Vue.component('vue-nav-menu-item', VueNavMenuItem)
Vue.component('vue-password-field', VuePasswordField)
Vue.component('vue-nav-menu-item-inline', VueNavMenuItemInline)
Vue.component('vue-horizontal-separator', VueHorizontalSeparator)
Vue.component('app-menu', AppMenu)
Vue.component('app-wait', AppWait)
Vue.component('app-cpt', AppCpt)
Vue.component('vue-column', VueColumn)
Vue.component('vue-column-container', VueColumnContainer)
Vue.component('selenium-info-panel', SeleniumInfoPanel)
Vue.component('app-log', AppLog)
Vue.component('app-dialog', AppDialog)

Vue.component('ContactMech', ContactMech)

Object.defineProperty(Vue.prototype, '$_', {value: _})

const showDebug = false
Object.defineProperty(Vue.prototype, '$debug', {value: (process.env.NODE_ENV !== 'production') && showDebug})

Vue.mixin({
  methods: {
    parseProps() {
      if (this.$props && this.$props.props) {
        let props = this.$props.props
        let data = {}
        if (props) {
          this.props.attributes.map(attr => {
            if (attr.value === 'false') {
              data[attr.key] = false
            } else if (attr.value === 'true') {
              data[attr.key] = true
            } else {
              data[attr.key] = attr.value
            }
          })
          return data
        }
      }
    },
    getNestedObject(nestedObject, pathArray) {
      return pathArray.reduce((obj, key) =>
        (obj && obj[key] !== 'undefined') ? obj[key] : undefined, nestedObject)
    },
    async asyncForEach(array, callback) {
      for (let index = 0; index < array.length; index++) {
        await callback(array[index], index, array)
      }
    },
    parseUrl(url, map) {
      let parsedUrl = url
      while (parsedUrl.includes('{')) {
        let regexKey = /{(\w+)}/
        let regexReplace = /{\w+}/
        if (map[regexKey.exec(parsedUrl)[1]]) {
          parsedUrl = parsedUrl.replace(regexReplace, map[regexKey.exec(parsedUrl)[1]])
        } else {
          parsedUrl = parsedUrl.replace(regexReplace, `__${regexKey.exec(parsedUrl)[1]}_not_found__`)
          this.$store.dispatch('backOfficeApi/addMessage', {messageContent: `${regexKey.exec(parsedUrl)[1]} not found for url ${url}`})
        }
      }
      return parsedUrl
    },
    log(message) {
      this.$store.dispatch('data/addLog', message)
    }
  }
})

const router = new VueRouter({
  mode: 'hash',
  routes: [
    {path: '/portalPage/:portalPageId', props: true, component: Portal},
    {path: '/screen/:screenId', props: true, component: Screen},
    {path: '/screen/:screenId/:cover', props: true, component: Screen},
  ]
})

new Vue({
  el: '#app',
  props: ['content'],
  store: store,
  router: router,
  wait: new VueWait({
    useVuex: true
  }),
  vuetify,
  css: {
    extract: false,
    loaderOptions: {
      sass: {
        data: `@import "~@./sass/main.scss"`,
      },
      css: {
        extract: false
      }
    },
  },
  render: h => h(App)
})
