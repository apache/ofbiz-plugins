import Vuex from 'vuex'
import Vue from 'vue'

Vue.use(Vuex)

const state = {
  forms: {},
  formValidate: {}
}

const mutations = {
  ADD_FORM: (state, formName) => {
    Vue.set(state.forms, formName, {})
  },
  SET_FIELD_TO_FORM: (state, data) => {
    Vue.set(state.forms, data.formId, {...state.forms[data.formId], [data.key]: data.value})
  },
  ADD_FORM_VALIDATE: (state, {formName, validate}) => {
    Vue.set(state.formValidate, formName, validate)
  }
}

const getters = {
  forms: state => state.forms,
  form: state => {
    return function (formId) {
      return state.forms[formId]
    }
  },
  fieldInForm: state => (data) => {
    return state.forms[data.formId][data.key]
  },
  formValidate: state => {
    return function (formName) {
      return state.formValidate[formName]
    }
  }
}

const actions = {
  addForm({commit}, formName) {
    commit('ADD_FORM', formName)
  },
  setFieldToForm({commit, state}, data) {
    if (!state.forms[data.formId]) {
      commit('ADD_FORM', data.formId)
    }
    commit('SET_FIELD_TO_FORM', data)
  },
  addFormValidate({commit}, {formName, validate}) {
    commit('ADD_FORM_VALIDATE', {formName, validate})
  }
}

export default {
  namespaced: true,
  state,
  getters,
  actions,
  mutations
}
