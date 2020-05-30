<template>
  <v-tooltip top>
    <template v-slot:activator="{ on }">
      <v-radio-group id="vue-radio-field" :label="label" v-model="value" row :hide-details="noRules" :rules="rules" v-on="fieldHelpText ? on : null">
        <v-radio v-for="item in props.attributes.items" :key="item.key" :label="item.description" :value="item.key">
        </v-radio>
        <v-tooltip bottom v-if="value">
          <template v-slot:activator="{ on }">
            <v-btn icon small v-on="on" v-on:click="clear"><v-icon id='mdi-close'>mdi-close</v-icon></v-btn>
          </template>
          <span>Clear</span>
        </v-tooltip>
        <vue-error v-if="event" component="event"/>
      </v-radio-group>
    </template>
    <span>{{fieldHelpText}}</span>
  </v-tooltip>
</template>

<script>
  import {mapGetters} from 'vuex'

  export default {
    name: "VueRadioField",
    props: ['props', 'updateStore'],
    computed: {
      ...mapGetters({
        getDataFromForm: 'form/fieldInForm',
        getForm: 'form/form',
        uiLabel: 'ui/uiLabel'
      }),
      currentValue() {
        return this.props.attributes.hasOwnProperty('currentValue') ? this.props.attributes.currentValue : ''
      },
      event() {
        return this.props.attributes.hasOwnProperty('event') ? this.props.attributes.event : ''
      },
      fieldTitle(){
        return this.props.attributes.hasOwnProperty('fieldTitle') ? this.props.attributes.fieldTitle : ''
      },
      fieldHelpText() {
        return this.props.attributes.hasOwnProperty('fieldHelpText') ? this.props.attributes.fieldHelpText : ''
      },
      formName() {
        return this.props.attributes.hasOwnProperty('formName') ? this.props.attributes.formName : ''
      },
      label() {
        return this.required ? this.fieldTitle + ' *' : this.fieldTitle
      },
      name() {
        return this.props.attributes.hasOwnProperty('name') ? this.props.attributes.name : ''
      },
      noRules() {
        return this.required === false
      },
      required() {
        return this.props.attributes.hasOwnProperty('requiredField') &&  this.props.attributes.requiredField === true
      },
      rules() {
        let rules = []
        if (this.required) {
          rules.push((v) => !!v || this.uiLabel('required'))
        }
        return rules
      },
      storeForm() {
        return {
          formId: this.formName,
          key: this.name,
          value: this.currentValue
        }
      },
      value: {
        get() {
          return this.getDataFromForm(this.storeForm)
        },
        set(value) {
          this.$store.dispatch('form/setFieldToForm', {
            formId: this.formName,
            key: this.name,
            value: value
          })
        }
      }
    },
    methods: {
      clear() {
        this.value = ''
      }
    },
    created() {
      this.$store.dispatch('form/setFieldToForm', this.storeForm)
    },
    watch: {
      props: function () {
        this.$store.dispatch('form/setFieldToForm', this.storeForm)
      }
    }
  }
</script>

<style scoped>

</style>
