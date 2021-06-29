import Vuex from 'vuex'
import Vue from 'vue'
import constants from '../../js/constants'
import queryString from 'query-string'

Vue.use(Vuex)

const state = {
  messageList: [],
  currentApi: ''
}

const mutations = {
  ADD_MESSAGE(state, {messageContent, messageType}) {
    Vue.set(state, 'messageList', [...state.messageList, {messageContent, messageType}])
  },
  DELETE_MESSAGE(state, {message}) {
    state.messageList.splice(state.messageList.indexOf(message), 1)
  },
  SET_CURRENT_API(state, api) {
    Vue.set(state, 'currentApi', api)
  }
}

const getters = {
  messageList: state => state.messageList,
  currentApi: state => state.currentApi,
  apiUrl: state => constants.hostUrl + state.currentApi
}

const actions = {
  doRequest({commit, dispatch, rootGetters}, {uri, mode, params, hideEventMessage, hideErrorMessage}) {
    return new Promise((resolve, reject) => {
      setTimeout(async () => {
        let waitForLogin = false
        do {
          if (waitForLogin) {
            await new Promise((res) => {
              setTimeout(() => {
                res()
              }, 1000)
            })
          }
          let promise = null
          switch (mode) {
            case 'post':
              promise = dispatch('doPost', {uri, params})
              break
            case 'get':
              promise = dispatch('doGet', {uri})
              break
            case 'put':
              promise = dispatch('doPut', {uri, params})
              break
            case 'delete':
              promise = dispatch('doDelete', {uri, params})
              break
            default:
              promise = dispatch('doPost', {uri, params})
              break
          }

          let response = await promise.catch((error) => {
            reject(error)
          })

          if (typeof response.body === 'string' && response.body.includes('login failed')) {
            if (!waitForLogin) {
              dispatch('ui/setDialogStatus', {
                dialogId: 'loginDialog',
                dialogStatus: true
              }, {root: true})
              await dispatch('login/logout', {}, {root: true})
              waitForLogin = true
            }
            continue
          }
          if (response.body.hasOwnProperty('_ERROR_MESSAGE_')) {
            if (!hideErrorMessage) {
              commit('ADD_MESSAGE', {messageContent: response.body['_ERROR_MESSAGE_'], messageType: 'error'})
            }
            reject(response)
          }
          if (response.body.hasOwnProperty('_ERROR_MESSAGE_LIST_')) {
            if (!hideErrorMessage) {
              for (let errorMessage of response.body['_ERROR_MESSAGE_LIST_']) {
                commit('ADD_MESSAGE', {messageContent: errorMessage, messageType: 'error'})
              }
            }
            reject(response)
          }
          if (!hideEventMessage) {
            if (response.body.hasOwnProperty('_EVENT_MESSAGE_')) {
              commit('ADD_MESSAGE', {messageContent: response.body['_EVENT_MESSAGE_'], messageType: 'event'})
            }
            if (response.body.hasOwnProperty('_EVENT_MESSAGE_LIST_')) {
              for (let eventMessage of response.body['_EVENT_MESSAGE_LIST_']) {
                commit('ADD_MESSAGE', {messageContent: eventMessage, messageType: 'event'})
              }
            }
          }
          if (response.body.hasOwnProperty('viewEntities')) {
            let entities = []
            let records = []
            Object.keys(response.body.viewEntities).forEach((key) => {
              entities.push(dispatch('data/setEntity', {
                entityName: key,
                primaryKey: response.body.viewEntities[key].primaryKeys.join('-')
              }, {
                root: true
              }))
            })
            Promise.all(entities).then(all => {
              all.forEach((entity => {
                response.body.viewEntities[entity.entityName].list.forEach((record) => {
                  if (record.stId !== null) {
                    let data = {
                      entityName: entity.entityName,
                      primaryKey: record.stId,
                      data: record
                    }
                    records.push(dispatch('data/setEntityRow', data, {root: true}))
                  }
                })
              }))
            })
            Promise.all(records).then(() => {
              resolve(response)
            })
          } else {
            resolve(response)
          }
        } while (!rootGetters['login/isLoggedIn'])
      }, 0)
    })
  },
  doPost(context, {uri, params}) {
    return new Promise((resolve, reject) => {
      setTimeout(() => {
        Vue.http.post(uri,
          queryString.stringify({...params}),
          {headers: {'Content-Type': 'application/x-www-form-urlencoded'}}
        ).then(
          response => {
            resolve(response)
          }, error => {
            reject(error)
          })
      }, 0)
    })
  },
  doGet(context, {uri}) {
    return new Promise((resolve, reject) => {
      setTimeout(() => {
        Vue.http.get(uri,
          queryString.stringify({}),
          {headers: {'Content-Type': 'application/x-www-form-urlencoded'}}
        ).then(
          response => {
            resolve(response)
          }, error => {
            reject(error)
          })
      }, 0)
    })
  },
  doPut(context, {uri}) {
    return new Promise((resolve, reject) => {
      setTimeout(() => {
        Vue.http.put(uri,
          queryString.stringify({}),
          {headers: {'Content-Type': 'application/x-www-form-urlencoded'}}
        ).then(
          response => {
            resolve(response)
          }, error => {
            reject(error)
          })
      }, 0)
    })
  },
  doDelete(context, {uri}) {
    return new Promise((resolve, reject) => {
      setTimeout(() => {
        Vue.http.delete(uri,
          queryString.stringify({}),
          {headers: {'Content-Type': 'application/x-www-form-urlencoded'}}
        ).then(
          response => {
            resolve(response)
          }, error => {
            reject(error)
          })
      }, 0)
    })
  },
  addMessage({commit}, {messageContent, messageType}) {
    commit('ADD_MESSAGE', {messageContent, messageType})
  },
  deleteMessage({commit}, {message}) {
    commit('DELETE_MESSAGE', {message})
  },
  setApi({commit}, api) {
    commit('SET_CURRENT_API', api)
  }
}

export default {
  namespaced: true,
  state,
  getters,
  actions,
  mutations
}
