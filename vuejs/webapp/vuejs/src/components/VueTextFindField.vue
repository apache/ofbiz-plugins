<template>
  <v-row class="ma-0 pa-0" no-gutters>
    <v-col cols="4">
      <v-select class="mt-0 mb-0" :items="items" v-if="opEquals" v-model="valueOp" hide-details dense>
      </v-select>
    </v-col>
    <v-col cols="6">
      <v-tooltip top>
        <template v-slot:activator="{ on }">
          <v-text-field class="mt-0 mb-0"
                        :label="label"
                        v-bind:name="name"
                        v-bind:size="size"
                        v-model="value"
                        v-bind:maxlength="maxlength"
                        v-bind:autocomplete="autocomplete"
                        v-bind:tabindex="tabindex"
                        hide-details
                        dense
                        v-on="fieldHelpText ? on : null"
          />
        </template>
        <span>{{fieldHelpText}}</span>
      </v-tooltip>
    </v-col>
    <v-col cols="2">
      <input v-if="hideIgnoreCase" type="hidden" :name="name + '_ic'" :value="ignCase"/>
      <v-checkbox class="mt-0 mb-0" v-else type="checkbox" label="ignore case" :name="name + '_ic'" v-model="valueIc" hide-details dense/>
    </v-col>
  </v-row>
</template>

<script>
  import {mapGetters} from 'vuex'

  export default {
    name: "VueTextFindField",
    props: ['props', 'updateStore'],
    data() {
      return {
        items: [
          {text: 'egal', value: 'equals'},
          {text: 'débute', value: 'like'},
          {text: 'contient', value: 'contains'},
          {text: 'est vide', value: 'empty'},
          {text: 'Diff', value: 'notEqual'}
          ]
      }
    },
    computed: {
      autocomplete() {
        return this.props.attributes.hasOwnProperty('autocomplete') ? this.props.attributes.autocomplete : ''
      },
      defaultOption() {
        return this.props.attributes.hasOwnProperty('defaultOption') ? this.props.attributes.defaultOption : ''
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
      hideIgnoreCase() {
        return this.props.attributes.hasOwnProperty('hideIgnoreCase') ? this.props.attributes.hideIgnoreCase : false
      },
      ignCase() {
        return this.props.attributes.hasOwnProperty('ignCase') ? 'Y' : ''
      },
      label() {
        return this.required ? this.fieldTitle + ' *' : this.fieldTitle
      },
      maxlength() {
        return this.props.attributes.hasOwnProperty('maxlength') ? this.props.attributes.maxlength : ''
      },
      name() {
        return this.props.attributes.hasOwnProperty('name') ? this.props.attributes.name : ''
      },
      opEquals() {
        return this.props.attributes.hasOwnProperty('opEquals') ? this.props.attributes.opEquals : false
      },
      ...mapGetters({
        getForm: 'form/form',
        getDataFromForm: 'form/fieldInForm'
      }),
      titleStyle() {
        return this.props.attributes.hasOwnProperty('titleStyle') ? this.props.attributes.titleStyle : ''
      },
      size() {
        return this.props.attributes.hasOwnProperty('size') ? this.props.attributes.size : ''
      },
      storeFormIc() {
        return {
          formId: this.formName,
          key: this.name + '_ic',
          value: this.ignCase ? 'Y' : 'N'
        }
      },
      storeForm() {
        return {
          formId: this.formName,
          key: this.name,
          value: this.props.attributes.value ? this.props.attributes.value : ''
        }
      },
      storeFormOp() {
        return {
          formId: this.formName,
          key: this.name + '_op',
          value: this.defaultOption
        }
      },
      tabindex() {
        return this.props.attributes.hasOwnProperty('tabindex') ? this.props.attributes.tabindex : ''
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
      },
      valueOp: {
        get() {
          return this.getDataFromForm(this.storeFormOp)
        },
        set(value) {
          this.$store.dispatch('form/setFieldToForm', {
            formId: this.formName,
            key: this.name + '_op',
            value: value
          })
        }
      },
      valueIc: {
        get() {
          return this.getDataFromForm(this.storeFormIc) === 'Y'
        },
        set(value) {
          this.$store.dispatch('form/setFieldToForm', {
            formId: this.formName,
            key: this.name + '_ic',
            value: value ? 'Y' : 'N'
          })
        }
      }
    },
    created() {
      this.$store.dispatch('form/setFieldToForm', this.storeForm)
      this.$store.dispatch('form/setFieldToForm', this.storeFormOp)
      this.$store.dispatch('form/setFieldToForm', this.storeFormIc)
    },
    watch: {
      props: function () {
        this.$store.dispatch('form/setFieldToForm', this.storeForm)
        this.$store.dispatch('form/setFieldToForm', this.storeFormOp)
        this.$store.dispatch('form/setFieldToForm', this.storeFormIc)
      }
    }
  }
</script>

<style scoped>

</style>
