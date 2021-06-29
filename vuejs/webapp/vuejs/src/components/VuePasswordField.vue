<template>
  <div id="vue-password-field">
    <v-tooltip top>
      <template v-slot:activator="{ on }">
        <v-textField v-model="value" :label="label" dense :hide-details="noRules" :rules="rules" type="password" v-on="fieldHelpText ? on : null"/>
      </template>
      <span>{{fieldHelpText}}</span>
    </v-tooltip>
  </div>
</template>

<script>
  import {mapGetters} from 'vuex'

  export default {
    name: "VuePasswordField",
    props: ['props', 'updateStore'],
    computed: {
      ...mapGetters({
        getForm: 'form/form',
        getDataFromForm: 'form/fieldInForm',
        uiLabel: 'ui/uiLabel'
      }),
      controls() {
        return {
          required: this.required,
          maxLength: this.maxLength,
        }
      },
      formName() {
        return this.props.attributes.hasOwnProperty('formName') ? this.props.attributes.formName : ''
      },
      fieldTitle() {
        return this.props.attributes.hasOwnProperty('fieldTitle') ? this.props.attributes.fieldTitle : ''
      },
      fieldHelpText() {
        return this.props.attributes.hasOwnProperty('fieldHelpText') ? this.props.attributes.fieldHelpText : ''
      },
      label() {
        return this.required ? this.fieldTitle + ' *' : this.fieldTitle
      },
      maxLength() {
        return this.props.attributes.hasOwnProperty('maxLength') ? this.props.attributes.maxLength : null
      },
      name() {
        return this.props.attributes.hasOwnProperty('name') ? this.props.attributes.name : ''
      },
      noRules() {
        return this.controls.required === false && this.controls.maxLength === null && this.controls.mask === null
      },
      required() {
        return this.props.attributes.hasOwnProperty('required') && this.props.attributes.required.hasOwnProperty('requiredField') && this.props.attributes.required.requiredField === "true"
      },
      rules() {
        let rules = []
        if (this.controls.required) {
          rules.push((v) => !!v || this.uiLabel('required'))
        }
        if (this.controls.maxLength !== null) {
          rules.push((v) => v.length > this.controls.maxLength || `This field must be less than ${this.controls.maxLength} characters` )
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
