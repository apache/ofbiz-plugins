<template>
    <div class="autosuggest-container d-block field-lookup">
      <v-tooltip top>
        <template v-slot:activator="{ on }">
          <v-combobox
              :id="id"
              :label="label"
              v-model="valueStored"
              class="d-inline-flex"
              :items="items"
              hide-no-data
              hide-selected
              :hide-details="noRules"
              :rules="rules"
              no-filter
              :return-object="false"
              :search-input.sync="search"
              v-on="fieldHelpText ? on : null"/>
        </template>
        <span>{{fieldHelpText}}</span>
      </v-tooltip>
      <v-btn icon @click.stop="showModal" class="d-inline-flex"><v-icon>{{getIcon('mdi-arrow-expand')}}</v-icon></v-btn>
      <v-dialog v-model="dialogStatus">
        <v-btn fab fixed top right @click.stop="closeModal"><v-icon>{{getIcon('mdi-close')}}</v-icon></v-btn>
        <v-card class="pa-1">
          <v-card-text class="pa-0">
            <!-- TODO review parameter for vue-container creation, this container is never update by a watcher, only by the initial setArea -->
            <vue-container :props="{attributes: {id: name + '_lookup_modalContent'}}"
                           :auto-update-params="{targetUrl: getCurrentApi + '/' + fieldFormName, params: modalParams}">
            </vue-container>
          </v-card-text>
        </v-card>
      </v-dialog>
      <span v-if="tooltip" :id="'0_lookupId_' + id" class="tooltip d-block">{{tooltip}}</span>
      <p>{{modalResult}}</p>
    </div>
</template>

<script>
  import {mapGetters} from 'vuex'
  import _ from 'lodash'
  import icons from '../js/icons'

  export default {
    name: "VueLookupField",
    props: ['props', 'updateStore'],
    data() {
      return {
        term: '',
        wordList: [],
        displayFields: [],
        returnField: '',
        modalResult: '',
        search: '',
        displayModal: false
      }
    },
    computed: {
      ...mapGetters({
        getForm: 'form/form',
        getDataFromForm: 'form/fieldInForm',
        uiLabel: 'ui/uiLabel',
        getCurrentApi: 'backOfficeApi/currentApi',
        getDialogStatus: 'ui/dialogStatus'
      }),
      ajaxLookup() {
        return this.props.attributes.hasOwnProperty('ajaxEnabled') ? this.props.attributes.ajaxEnabled ? 'Y' : 'N' : 'N'
      },
      ajaxUrl() {
        return this.props.attributes.hasOwnProperty('ajaxUrl') ? this.props.attributes.ajaxUrl : ''
      },
      controls() {
        return {
          required: this.required,
          maxLength: this.maxLength
        }
      },
      dialogStatus: {
        get() {
          return this.getDialogStatus(this.id)
        },
        set(value) {
          this.$store.dispatch('ui/setDialogStatus', {
            dialogId: this.id,
            dialogStatus: value
          })
        }
      },
      fieldFormName() {
        return this.props.attributes.hasOwnProperty('fieldFormName') ? this.props.attributes.fieldFormName : ''
      },
      fieldHelpText() {
        return this.props.attributes.hasOwnProperty('fieldHelpText') ? this.props.attributes.fieldHelpText : ''
      },
      fieldTitle() {
        return this.props.attributes.hasOwnProperty('fieldTitle') ? this.props.attributes.fieldTitle : ''
      },
      formName() {
        return this.props.attributes.hasOwnProperty('formName') ? this.props.attributes.formName : ''
      },
      id() {
        return this.props.attributes.hasOwnProperty('id') ? this.props.attributes.id : ''
      },
      items() {
        let items = []
        for (let item of this.wordList) {
          let text = ""
          for (const [index, displayField] of this.displayFields.entries()) {
            if (index > 0) {
              text += ' - '
            }
            text += item[displayField]
          }
          text += ' [' + item[this.returnField] + ']'
          items.push({text: text, value: item[this.returnField]})
        }
        return items
      },
      label() {
        return this.required ? this.fieldTitle + ' *' : this.fieldTitle
      },
      maxLength() {
        return this.props.attributes.hasOwnProperty('maxLength') ? this.props.attributes.maxLength : null
      },
      modalParams() {
        let modalParams = {
          presentation: 'layer',
          lookupFieldForm: this.formName,
          lookupField: this.name
        }
        this.targetParameters.forEach((val, id) => {
          modalParams['parm' + id] = val
        })
        return modalParams
      },
      name() {
        return this.props.attributes.hasOwnProperty('name') ? this.props.attributes.name : ''
      },
      noRules() {
        return this.controls.required === false && this.controls.maxLength === null && this.controls.mask === null
      },
      params() {
        let params = {
          ajaxLookup: 'Y',//this.ajaxLookup,
          searchValueFieldName: this.name, //this.name
          term: this.valueStored,
          autocompleterViewSize: "50",
          displayFields: []
        }
        this.targetParameters.forEach((val, id) => {
          params['parm' + id] = val
        })
        return params
      },
      required() {
        return this.props.attributes.hasOwnProperty('required') && this.props.attributes.required.hasOwnProperty('requiredField') && this.props.attributes.required.requiredField === "true"
      },
      rules() {
        let rules = []
        if (this.controls.required) {
          rules.push((v) => !!v || this.uiLabel('required') )
        }
        if (this.controls.maxLength !== null) {
          rules.push((v) => v.length > this.controls.maxLength || `This field must be less than ${this.controls.maxLength} characters` )
        }
        return rules
      },
      searchValueFieldName() {
        return this.props.attributes.hasOwnProperty('fieldFormName') ? this.props.attributes.fieldFormName : ''
      },
      storeForm() {
        return {
          formId: this.formName,
          key: this.name,
          value: this.value
        }
      },
      targetParameters() {
        return this.props.attributes.hasOwnProperty('targetParameters') ? this.props.attributes.targetParameters.map( val => this.getDataFromForm({formId: this.formName, key: val})) : []
      },
      tooltip() {
        let selectedItem = this.wordList.find(item => item[this.name] === this.valueStored)
        if (selectedItem === undefined) {
          return false
        }
        let str = ''
        for (let i = 0; i < this.displayFields.length; i++) {
          str += selectedItem[this.displayFields[i]]
          if (i < this.displayFields.length - 1) {
            str += ' - '
          }
        }
        return str
      },
      value() {
        return this.props.attributes.hasOwnProperty('value') ? this.props.attributes.value : ''
      },
      valueStored: {
        get() {
          return this.getDataFromForm(this.storeForm)
        },
        set(value) {
          this.$store.dispatch('form/setFieldToForm', {
            formId: this.props.attributes.formName,
            key: this.props.attributes.name,
            value: value
          })
        }
      }
    },
    methods: {
      debounceUpdateWordList: _.debounce(function () {
        this.updateWordList()
      }, 250),
      getIcon(icon) {
        return icons.hasOwnProperty(icon) ? icons[icon] : null
      },
      updateWordList() {
        this.$store.dispatch('form/setFieldToForm', {
          formId: this.props.attributes.formName,
          key: this.props.attributes.name,
          value: this.valueStored
        })
        this.$store.dispatch('backOfficeApi/doRequest', {
          uri: this.$store.getters['backOfficeApi/apiUrl'] + '/' + this.fieldFormName,
          mode: 'post',
          params: this.params
        }).then(result => {
          this.returnField = result.body.viewScreen[0].attributes.returnField === null ? '' : result.body.viewScreen[0].attributes.returnField
          this.displayFields = result.body.viewScreen[0].attributes.displayFields
          this.wordList = result.body.viewScreen[0].attributes.autocompleteOptions === null ? [] : result.body.viewScreen[0].attributes.autocompleteOptions
          this.$store.dispatch('ui/incrementUpdateCpt')
          return result.body
        }, error => {
          return error.body
        })
      },
      onSelected(item) {
        this.valueStored = item.item[this.name]
        this.$store.dispatch('form/setFieldToForm', {
          formId: this.props.attributes.formName,
          key: this.props.attributes.name,
          value: this.valueStored
        })
      },
      getSuggestionValue(suggestion) {
        return suggestion.item[this.returnField]
      },
      renderSuggestion(suggestion) {
        let str = ''
        for (let i = 0; i < this.displayFields.length; i++) {
          str += suggestion.item[this.displayFields[i]]
          str += ' - '
        }
        str += `[${suggestion.item[this.returnField]}]`
        return str
      },
      showModal() {
        let params = this.modalParams
        params['presentation'] = 'layer'
        this.$store.dispatch('ui/setArea', {
          areaId: this.name + '_lookup_modalContent',
          targetUrl: `${this.$store.getters['backOfficeApi/currentApi']}/${this.fieldFormName}`,
          params: params
        }).then(() => {
            this.$store.dispatch('ui/setDialogStatus', {
              dialogId: this.id,
              dialogStatus: true
            })
          })
      },
      closeModal() {
        this.$store.dispatch('ui/setDialogStatus', {
          dialogId: this.id,
          dialogStatus: false
        })
      }
    },
    watch: {
      props: function () {
        this.$store.dispatch('form/setFieldToForm', this.storeForm)
      },
      search: function (val) {
        this.valueStored = val
        this.debounceUpdateWordList()
      }
    },
    created() {
      this.$store.dispatch('form/setFieldToForm', this.storeForm)
    }
  }
</script>

<style>
  .autosuggest-container {
    display: flex;
    justify-content: center;
  }
</style>
