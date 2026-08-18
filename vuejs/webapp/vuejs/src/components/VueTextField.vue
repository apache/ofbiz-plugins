<template>
  <div id="vue-text-field">
    <v-tooltip top>
      <template v-slot:activator="{ on }">
        <v-textField v-if="mask" v-model="value" :id="id" :label="label" :rules="rules" v-mask="parsedMask" error-count="3" validate-on-blur v-on="fieldHelpText ? on : null" hide-details="auto"/>
        <v-textField v-else v-model="value" :id="id" :label="label" :rules="rules" error-count="3" validate-on-blur v-on="fieldHelpText ? on : null" hide-details="auto"/>
      </template>
      <span>{{fieldHelpText}}</span>
    </v-tooltip>

  </div>
</template>

<script>
  import {mapGetters} from 'vuex'

  export default {
    name: "VueTextField",
    props: ['props', 'updateStore'],
    computed: {
      ...mapGetters({
        getForm: 'form/form',
        getDataFromForm: 'form/fieldInForm',
        uiLabel: 'ui/uiLabel'
      }),
      fieldTitle() {
        return this.props.attributes.hasOwnProperty('fieldTitle') ? this.props.attributes.fieldTitle : ''
      },
      fieldHelpText() {
        return this.props.attributes.hasOwnProperty('fieldHelpText') ? this.props.attributes.fieldHelpText : ''
      },
      formName() {
        return this.props.attributes.hasOwnProperty('formName') ? this.props.attributes.formName : ''
      },
      id() {
        return this.props.attributes.hasOwnProperty('id') ? this.props.attributes.id : ''
      },
      label() {
        return this.required ? this.fieldTitle + ' *' : this.fieldTitle
      },
      mask() {
        return this.props.attributes.hasOwnProperty('mask') ? this.props.attributes.mask : null
      },
      maxLength() {
        return this.props.attributes.hasOwnProperty('maxLength') ? this.props.attributes.maxLength : null
      },
      name() {
        return this.props.attributes.hasOwnProperty('name') ? this.props.attributes.name : ''
      },
      noRules() {
        return this.required === false && this.maxLength === null && this.mask === null
      },
      noRulesNoMask() {
        return !(!this.noRules || this.mask)
      },
      parsedMask() {
        return this.props.attributes.hasOwnProperty('mask') ? this.props.attributes.mask.replace(/\*/gi, 'X').replace(/9/gi, '#').replace(/a/gi, 'S') : []
      },
      required() {
        return this.props.attributes.hasOwnProperty('required') && this.props.attributes.required.hasOwnProperty('requiredField') && this.props.attributes.required.requiredField === "true"
      },
      rules() {
        let rules = []
        if (this.required) {
          rules.push((v) => !!v || this.uiLabel('required'))
        }
        if (this.maxLength !== null) {
          rules.push((v) => v.length > this.maxLength || `This field must be less than ${this.maxLength} characters` )
        }
        if (this.mask !== null) {
          rules.push((v) => v.length === this.mask.length || `mask : ${this.mask}`)
        }
        return rules
      },
      storeForm() {
        return {
          formId: this.formName,
          key: this.name,
          value: this.props.attributes.value ? this.props.attributes.value : ''
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
    mounted() {
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
