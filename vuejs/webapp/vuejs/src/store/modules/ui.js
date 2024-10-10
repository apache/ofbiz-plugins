import Vuex from 'vuex'
import Vue from 'vue'
import queryString from 'query-string'
import constants from './../../js/constants'

Vue.use(Vuex)

const state = {
  currentPortalPage: '',
  currentPortalPageParams: {},
  currentPortalPageDetail: {},
  portalPages: {},
  portlets: {},
  portletTarget: {},
  containers: {},
  areas: {},
  watchers: {},
  updateCpt: 0,
  collapsibleStatus: {},
  dialogStatus: {},
  uiLabels: {},
  seleniumInfoPanels: [],
  locale: {},
  logDrawerState: false,
  areasRefresh: {},
  watchersRefresh: {}
}

const mutations = {
  SET_CURRENT_PORTAL_PAGE: (state, portalPageId) => {
    Vue.set(state, 'currentPortalPage', portalPageId)
  },
  SET_PORTAL_PAGE: (state, {portalPageId, portalPage}) => {
    Vue.set(state.portalPages, portalPageId, portalPage)
    Vue.set(state, 'currentPortalPageDetail', portalPage)
  },
  REMOVE_PORTAL_PAGE: (state, id) => {
    state.portalPages.slice(id)
  },
  SET_CURRENT_PORTAL_PAGE_PARAMS: (state, params) => {
    Vue.set(state, 'currentPortalPageParams', params)
  },
  SET_PORTLET: (state, {portletId, data}) => {
    Vue.set(state.portlets, portletId, data)
  },
  SET_PORTLET_TARGET: (state, {portletId, target}) => {
    Vue.set(state.portletTarget, portletId, target)
  },
  SET_CONTAINER: (state, {containerName, content}) => {
    Vue.set(state.containers, containerName, content)
  },
  SET_AREA: (state, {areaId, areaContent}) => {
    Vue.set(state.areas, areaId, areaContent)
  },
  DELETE_AREA: (state, {areaId}) => {
    Vue.delete(state.areas, areaId)
  },
  INCREMENT_UPDATE_CPT: (state) => {
    Vue.set(state, 'updateCpt', state.updateCpt + 1)
  },
  SET_COLLAPSIBLE_STATUS: (state, {areaId, areaTarget}) => {
    Vue.set(state.collapsibleStatus, areaId, areaTarget)
  },
  SET_DIALOG_STATUS: (state, {dialogId, dialogStatus}) => {
    Vue.set(state.dialogStatus, dialogId, dialogStatus)
  },
  SET_UI_LABELS: (state, uiLabels) => {
    Vue.set(state, 'uiLabels', uiLabels)
  },
  ADD_SELENIUM_INFO_PANEL(state, panel) {
    Vue.set(state, 'seleniumInfoPanels', [...state.seleniumInfoPanels, panel])
  },
  DELETE_SELENIUM_INFO_PANEL(state, panel) {
    state.seleniumInfoPanels.splice(state.seleniumInfoPanels.indexOf(panel), 1)
  },
  SET_LOCALE(state, locale) {
    Vue.set(state, 'locale', locale)
  },
  TOGGLE_LOG_DRAWER(state) {
    Vue.set(state, 'logDrawerState', !state.logDrawerState)
  },
  SET_AREA_REFRESH(state, areaId) {
    Vue.set(state.areasRefresh, areaId, 0)
  },
  REFRESH_AREA(state, areaId) {
    Vue.set(state.areasRefresh, areaId, state.areasRefresh[areaId] + 1)
  },
  SET_WATCHER_REFRESH(state, watcher) {
    Vue.set(state.watchersRefresh, watcher, 0)
  },
  REFRESH_WATCHER(state, watcher) {
    Vue.set(state.watchersRefresh, watcher, state.watchersRefresh[watcher] + 1)
  },
}

const getters = {
  currentPortalPage: state => state.currentPortalPage,
  currentPortalPageDetail: state => state.currentPortalPageDetail,
  currentPortalPageParams: state => state.currentPortalPageParams,
  portalPage: state => (id) => {
    return state.portalPages[id]
  },
  portalPages: state => state.portalPages,
  column: state => ({portalPageId, columnSeqId}) => {
    return state.portalPages[portalPageId].listColumnPortlet.find(col => col.columnSeqId === columnSeqId)
  },
  portlet(state) {
    return (id) => {
      return state.portlets[id]
    }
  },
  portlets: state => state.portlets,
  portletTarget(state) {
    return function (id) {
      return state.portletTarget.hasOwnProperty(id) ? state.portletTarget[id] : null
    }
  },
  container(state) {
    return function (containerName) {
      return state.containers.hasOwnProperty(containerName) ? state.containers[containerName] : null
    }
  },
  area(state) {
    return function (areaId) {
      return state.areas.hasOwnProperty(areaId) ? state.areas[areaId] : null
    }
  },
  watcher(state) {
    return function (watcherName) {
      return state.watchers.hasOwnProperty(watcherName) ? state.watchers[watcherName] : null
    }
  },
  updateCpt: state => state.updateCpt,
  collapsibleStatus(state) {
    return function (areaId) {
      return state.collapsibleStatus.hasOwnProperty(areaId) ? state.collapsibleStatus[areaId] : false
    }
  },
  dialogStatus(state) {
    return function (dialogId) {
      return state.dialogStatus.hasOwnProperty(dialogId) ? state.dialogStatus[dialogId] : false
    }
  },
  dialogs: state => state.dialogStatus,
  uiLabels: state => state.uiLabels,
  uiLabel(state) {
    return function (uiLabel) {
      return state.uiLabels.hasOwnProperty(uiLabel) ? state.uiLabels[uiLabel] : uiLabel
    }
  },
  seleniumInfoPanels: state => state.seleniumInfoPanels,
  locale: state => state.locale,
  logDrawerState: state => state.logDrawerState,
  areasRefresh: state => state.areasRefresh,
  areaRefresh(state) {
    return function (areaId) {
      return state.areasRefresh.hasOwnProperty(areaId) ? state.areasRefresh[areaId] : 0
    }
  },
  watchersRefresh: state => state.watchersRefresh,
  watcherRefresh(state) {
    return function (watcher) {
      return state.watchersRefresh.hasOwnProperty(watcher) ? state.watchersRefresh[watcher] : 0
    }
  }
}

const actions = {
  setPortalPage({commit}, {portalPageId, portalPage}) {
    commit('SET_PORTAL_PAGE', {portalPageId, portalPage})
    commit('SET_CURRENT_PORTAL_PAGE', portalPageId)
  },
  setPortlet({commit, getters}, {portalPortletId, portletSeqId, params = {}}) {
    return new Promise((resolve, reject) => {
      setTimeout(() => {
        Vue.http.post(getters['backOfficeApi/apiUrl'] + getters['backOfficeApi/currentApi'] + constants.showPortlet.path,
          queryString.stringify({
            portalPortletId: portalPortletId,
            ...params
          }),
          {headers: {'Content-Type': 'application/x-www-form-urlencoded'}}
        ).then(response => {
          commit('SET_PORTLET', {portletId: portalPortletId + '-' + portletSeqId, data: response.body})
          resolve(portalPortletId)
        }, () => {
          reject()
        })
      }, 0)
    })
  },
  setPortletTarget({commit}, {portletId, target}) {
    commit('SET_PORTLET_TARGET', {portletId, target})
  },
  setContainer({commit, getters}, {containerName, containerTarget, params = {}}) {
    return new Promise((resolve, reject) => {
      setTimeout(() => {
        Vue.http.post(getters['backOfficeApi/apiUrl'] + getters['backOfficeApi/currentApi'] + constants.showPortlet.path,
          queryString.stringify({
            portalPortletId: containerTarget,
            ...params
          }),
          {headers: {'Content-Type': 'application/x-www-form-urlencoded'}}
        ).then(response => {
          commit('SET_CONTAINER', {containerName, content: response.body})
          resolve(containerName)
        }, () => {
          reject()
        })
      }, 0)
    })
  },
  setArea({commit, dispatch}, {areaId, targetUrl, params = {}, mode = 'post'}) {
    return new Promise((resolve, reject) => {
      setTimeout(() => {
        dispatch('wait/start', areaId, {root: true})
        dispatch('incrementUpdateCpt')
        dispatch(
          'backOfficeApi/doRequest',
          {uri: constants.hostUrl + targetUrl.replace('amp;', ''), params, mode},
          {root: true}
        ).then(response => {
          if (response.body.hasOwnProperty('_ERROR_MESSAGE_')) {
            dispatch('addErrorMessage', {errorMessage: response.body['_ERROR_MESSAGE_']})
            setTimeout(() => {
              dispatch('wait/end', areaId, {root: true})
            }, 0)
            reject()
          }
          if (response.body.hasOwnProperty('_ERROR_MESSAGE_LIST_')) {
            for (let errorMessage of response.body['_ERROR_MESSAGE_LIST_']) {
              dispatch('addErrorMessage', {errorMessage})
            }
            setTimeout(() => {
              dispatch('wait/end', areaId, {root: true})
            }, 0)
            reject()
          }
          commit('SET_AREA', {areaId: areaId, areaContent: response.body})
          setTimeout(() => {
            dispatch('wait/end', areaId, {root: true})
          }, 0)
          resolve(areaId)
        }, error => {
          setTimeout(() => {
            dispatch('wait/end', areaId, {root: true})
          }, 0)
          reject(error)
        })
      }, 0)
    })
  },
  deleteArea({commit}, {areaId}) {
    commit('DELETE_AREA', {areaId})
  },
  incrementUpdateCpt({commit}) {
    commit('INCREMENT_UPDATE_CPT')
  },
  initialize({dispatch}, location) {
    let pathname = location.pathname
    let search = location.search
    let params = {portalPageId: this._vm.$route.params.portalPageId}
    search.substr(1).split('&amp;').forEach(param => {
      let tmp = param.split('=')
      params[tmp[0]] = tmp[1]
    })
    let api = pathname.substring(0, pathname.indexOf('/', 1)) + '/control'
    dispatch('loadPortalPageDetail', {api: api, params: params})
  },
  loadPortalPageDetail({commit, dispatch}, {api, params}) {
    dispatch('backOfficeApi/setApi', api, {root: true})
    dispatch('backOfficeApi/doRequest', {uri: constants.hostUrl + api + constants.portalPageDetail.path, mode: 'post', params}, {root: true}).then(response => {
      let portalPage = response.body
      commit('SET_CURRENT_PORTAL_PAGE_PARAMS', params)
      commit('SET_PORTAL_PAGE', {portalPageId: params.portalPageId, portalPage})
      commit('SET_CURRENT_PORTAL_PAGE', params.portalPageId)
    })
  },
  setCollapsibleStatus({commit}, {areaId, areaTarget}) {
    commit('SET_COLLAPSIBLE_STATUS', {areaId, areaTarget})
  },
  setDialogStatus({commit}, {dialogId, dialogStatus}) {
    commit('SET_DIALOG_STATUS', {dialogId, dialogStatus})
  },
  closeAllDialogs({commit, getters}) {
    for (let dialogId of Object.keys(getters.dialogs)) {
      commit('SET_DIALOG_STATUS', {dialogId, dialogStatus: false})
    }
  },
  setUiLabels({commit, dispatch}, api) {
    dispatch('backOfficeApi/doRequest', {uri: constants.hostUrl + api + constants.getCommonUiLabel, mode: 'post'}, {root: true})
      .then(response => {
        commit('SET_UI_LABELS', response.body.commonUiLabels)
      })
  },
  addSeleniumInfoPanel({commit}, {panelMessage, panelTimeout, panelTitle, panelColor}) {
    commit('ADD_SELENIUM_INFO_PANEL', {panelMessage, panelTimeout, panelTitle, panelColor})
  },
  deleteSeleniumInfoPanel({commit}, infoPanel) {
    commit('DELETE_SELENIUM_INFO_PANEL', infoPanel)
  },
  setLocale({commit}, locale) {
    commit('SET_LOCALE', locale)
  },
  toggleLogDrawer({commit}) {
    commit('TOGGLE_LOG_DRAWER')
  },
  setAreaRefresh({commit, getters}, areaId) {
    if (!getters['areasRefresh'][areaId]) {
      commit('SET_AREA_REFRESH', areaId)
    }
  },
  refreshArea({commit, getters}, areaId) {
    if (!getters['areasRefresh'].hasOwnProperty(areaId)) {
      commit('SET_AREA_REFRESH', areaId)
    }
    commit('REFRESH_AREA', areaId)
  },
  setWatcherRefresh({commit, getters}, watcherName) {
    if (!getters['watchersRefresh'][watcherName]) {
      commit('SET_AREA_REFRESH', watcherName)
    }
  },
  refreshWatcher({commit, getters}, watcherName) {
    if (!getters['watchersRefresh'].hasOwnProperty(watcherName)) {
      commit('SET_WATCHER_REFRESH', watcherName)
    }
    commit('REFRESH_WATCHER', watcherName)
    getters['watchersRefresh'].forEach((watcher) => {
      if (watcher.split('-').length > 1 && watcher.split('-').includes(watcherName)) {
        commit('REFRESH_WATCHER', watcher)
      }
    })
  }
}

export default {
  namespaced: true,
  state,
  getters,
  actions,
  mutations
}
