import Vuex from 'vuex'
import Vue from 'vue'

Vue.use(Vuex)

const state = {
  entities: {},
  watchers: {},
  logs: []
}

const mutations = {
  SET_ENTITY: (state, data) => {
    Vue.set(state.entities, data.entityName, {
      list: {},
      primaryKey: data.primaryKey
    })
  },
  SET_ENTITY_ROW: (state, data) => {
    if (state.entities[data.entityName] === undefined) {
      return 'Entities doesn\'t exist'
    }
    if (!state.entities[data.entityName].list[data.primaryKey]) {
      Vue.set(state.entities[data.entityName].list, data.primaryKey, {})
    }
    Object.keys(data.data).forEach(key => {
      Vue.set(state.entities[data.entityName].list[data.primaryKey], key, data.data[key])
    })
  },
  SET_WATCHER: (state, {watcherName, params}) => {
    Vue.set(state.watchers, watcherName, {...params})
  },
  SET_WATCHER_ATTRIBUTES: (state, {watcherName, params}) => {
    Vue.set(state.watchers, watcherName, {...state.watchers[watcherName], ...params})
  },
  ADD_LOG: (state, log) => {
    state.logs.push(log)
  },
  CLEAR_LOGS: (state) => {
    Vue.set(state, 'logs', [])
  }
}

const getters = {
  entities: state => state.entities,
  entity: state => entityName => {
    return state.entities[entityName]
  },
  entityRow: state => ({entityName, id}) => {
    return state.entities[entityName].list.find(row => row.stId === id)
  },
  entityRowAttribute(state) {
    return function ({entityName, id, attribute}) {
      if (state.entities[entityName] === undefined ||
        state.entities[entityName].list[id] === undefined ||
        state.entities[entityName].list[id][attribute] === undefined
      ) {
        return ''
      } else {
        return state.entities[entityName].list[id][attribute]
      }
    }
  },
  watcher(state) {
    return function (watcherName) {
      return state.watchers[watcherName]
    }
  },
  watchers(state) {
    return state.watchers
  },
  watchersList(state) {
    return Object.keys(state.watchers)
  },
  logs(state) {
    return state.logs
  }
}

const actions = {
  setEntity({commit}, data) {
    return new Promise((resolve) => {
      setTimeout(() => {
        if (!state.entities[data.entityName]) {
          commit('SET_ENTITY', data)
        }
        resolve(data)
      }, 0)
    })
  },
  setEntityRow({commit}, data) {
    return new Promise((resolve) => {
      setTimeout(() => {
        commit('SET_ENTITY_ROW', data)
        resolve(data)
      }, 0)
    })
  },
  setWatcher({commit, getters}, {watcherName, params}) {
    commit('SET_WATCHER', {watcherName, params})
    getters['watchersList'].forEach((watcher) => {
      if (watcher.split('-').length > 1 && watcher.split('-').includes(watcherName)) {
        let merged = {}
        for (let component of watcher.split('-')) {
          merged = {...getters['watchers'][component], ...merged}
        }
        commit('SET_WATCHER', {watcherName: watcher, params: merged})
      }
    })
  },
  initializeWatcher({commit, getters}, watcherName) {
      if (!getters['watchersList'].includes(watcherName)) {
        commit('SET_WATCHER', {watcherName: watcherName, params: {}})
      }
  },
  setWatcherAttributes({commit}, data) {
    commit('SET_WATCHER_ATTRIBUTES', data)
  },
  addLog({commit}, log) {
    commit('ADD_LOG', log)
  },
  clearLogs({commit}) {
    commit('CLEAR_LOGS')
  }
}

export default {
  namespaced: true,
  state,
  getters,
  actions,
  mutations
}
