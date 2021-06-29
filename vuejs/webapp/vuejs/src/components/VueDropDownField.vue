<template>
  <v-tooltip top>
    <template v-slot:activator="{ on }">
      <div :id="id" :name="name" class="mt-4" v-on="fieldHelpText ? on : null">
        <input type="hidden" :name="name" :value="value" :formname="formName"/>
        <v-select :label="label" :items="options" item-value="key" item-text="description" v-model="value"
                  :hide-details="!required" dense clearable :rules="rules">
          <template slot="item" slot-scope="data">
        <span :id="data.item.key">
          {{data.item.description}}
        </span>
          </template>
        </v-select>
      </div>
    </template>
    <span>{{fieldHelpText}}</span>
  </v-tooltip>
</template>

<script>
  import {mapGetters} from 'vuex'

  export default {
    name: "VueDropDownField",
    props: ['props', 'updateStore'],
    computed: {
      ...mapGetters({
        getForm: 'form/form',
        getDataFromForm: 'form/fieldInForm',
        uiLabel: 'ui/uiLabel'
      }),
      controls() {
        return {
          required: this.props.attributes.hasOwnProperty('requiredField') && this.props.attributes.requiredField === true
        }
      },
      currentValue() {
        return this.props.attributes.hasOwnProperty('currentValue') ? this.props.attributes.currentValue : null
      },
      id() {
        return this.props.attributes.hasOwnProperty('id') ? this.props.attributes.id : ''
      },
      fieldTitle() {
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
      multiple() {
        return this.props.attributes.hasOwnProperty('multiple') ? this.props.attributes.multiple : false
      },
      name() {
        return this.props.attributes.hasOwnProperty('name') ? this.props.attributes.name : ''
      },
      noCurrentSelectedKey() {
        return this.props.attributes.hasOwnProperty('noCurrentSelectedKey') ? this.props.attributes.noCurrentSelectedKey : null
      },
      options() {
        return this.props.attributes.hasOwnProperty('options') ? this.props.attributes.options : ''
      },
      required() {
        return this.controls.required
      },
      rules() {
        let rules = []
        if (this.controls.required) {
          rules.push((v) => !!v || this.uiLabel('required'))
        }
        return rules
      },
      storeForm() {
        return {
          formId: this.props.attributes.formName,
          key: this.props.attributes.name,
          value: this.currentValue || this.noCurrentSelectedKey || (this.multiple ? [''] : '')
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
