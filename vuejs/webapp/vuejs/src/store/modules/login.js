import Vuex from 'vuex'
import Vue from 'vue'
import queryString from 'query-string'
import constants from './../../js/constants'

Vue.use(Vuex)

const state = {
  credentials: {
    username: '',
    token: '',
    rememberMe: false,
    partyId: ''
  },
  isLoggedIn: false,
  pending: true
}

const mutations = {
  CHECK_FAILURE: (state) => {
    state.isLoggedIn = false
    state.pending = false
  },
  CHECK_SUCCESS: (state) => {
    state.isLoggedIn = true
    state.pending = false
  },
  LOGIN_FAILURE: (state) => {
    state.isLoggedIn = false
    state.pending = false
  },
  LOGIN_SUCCESS: (state, params) => {
    state.isLoggedIn = true
    state.credentials.username = params.username
    state.pending = false
  },
  LOGOUT: (state) => {
    state.credentials.token = ''
    state.credentials.username = ''
    state.credentials.partyId = ''
    state.isLoggedIn = false
    state.pending = false
  },
  START_PENDING: (state) => {
    state.pending = true
  }
}

const getters = {
  isLoggedIn: state => state.isLoggedIn,
  pending: (state) => () => {
    return state.pending
  }
}

const actions = {
  login({commit, dispatch, rootGetters}, credentials) {
    return new Promise((resolve, reject) => {
      setTimeout(() => {
        commit('START_PENDING')
        Vue.http.post(
          constants.hostUrl + rootGetters['backOfficeApi/currentApi'] + constants.login.path,
          queryString.stringify({
            JavaScriptEnabled: 'Y',
            USERNAME: credentials.username,
            PASSWORD: credentials.password
          }),
          {headers: {'Content-Type': 'application/x-www-form-urlencoded'}}
        ).then(response => {
          if (!response.body._ERROR_MESSAGE_ && !response.body._ERROR_MESSAGES_LIST_) {
            dispatch('backOfficeApi/addMessage', {messageContent: rootGetters['ui/uiLabels'].loginSuccessMessage, messageType: 'event'}, {root: true})
            commit('LOGIN_SUCCESS', credentials)
            resolve()
          } else {
            if (response.body._ERROR_MESSAGE_) {
              dispatch('backOfficeApi/addMessage', {messageContent: response.body._ERROR_MESSAGE_, messageType: 'error'}, {root: true})
            }
            if (response.body._ERROR_MESSAGES_LIST_) {
              for (let error in response.body._ERROR_MESSAGE_LIST_) {
                dispatch('backOfficeApi/addMessage', {messageContent: error, messageType: 'error'}, {root: true})
              }
            }
            commit('LOGIN_FAILURE')
            reject(response)
          }
        }, error => {
          if (error.body._ERROR_MESSAGE_) {
            dispatch('backOfficeApi/addMessage', {messageContent: error.body._ERROR_MESSAGE_, messageType: 'error'}, {root: true})
          }
          if (error.body._ERROR_MESSAGES_LIST_) {
            for (let error in error.body._ERROR_MESSAGE_LIST_) {
              dispatch('backOfficeApi/addMessage', {messageContent: error, messageType: 'error'}, {root: true})
            }
          }
          commit('LOGIN_FAILURE')
          reject(error)
        })
      }, 0)
    })
  },
  logout({commit}) {
    return new Promise((resolve) => {
      setTimeout(() => {
        commit('START_PENDING')
        localStorage.removeItem('token')
        commit('LOGOUT')
        resolve()
      }, 0)
    })
  },
  check({commit, rootGetters}) {
    return new Promise((resolve, reject) => {
      setTimeout(() => {
        commit('START_PENDING')
        Vue.http.post(
          constants.hostUrl + rootGetters['backOfficeApi/currentApi'] + constants.ajaxCheckLogin.path,
          queryString.stringify({
          }),
          {headers: {'Content-Type': 'application/x-www-form-urlencoded'}}).then(response => {
          if (response.body.includes('login successful')
            && !response.body._ERROR_MESSAGE_
            && !response.body._ERROR_MESSAGES_LIST_) {
            commit('CHECK_SUCCESS')
            resolve()
          } else {
            commit('CHECK_FAILURE')
            reject()
          }
        }, () => {
          commit('CHECK_FAILURE')
          reject()
        })
      }, 0)
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
