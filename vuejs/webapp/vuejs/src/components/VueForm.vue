<template>
  <v-form :ref="name" v-bind:id="'vue-form_' + name" :autocomplete="autocomplete" v-model="valid" lazy-validation>
    <div
      v-for="(component, key) in props.children"
      :key="key"
      v-bind:is="constants.components[component.name]"
      :props="component"
      :updateStore="updateStore">
    </div>
  </v-form>
</template>

<script>
  import constants from '../js/constants'

  export default {
    name: "VueForm",
    props: ['props', 'updateStore'],
    data() {
      return {
        constants: constants,
        valid: false
      }
    },
    computed: {
      autocomplete() {
        return this.props.attributes.hasOwnProperty('autocomplete') && this.props.attributes.autocomplete !== '' ? 'on' : 'off'
      },
      defaultEntityName() {
        return this.props.attributes.hasOwnProperty('defaultEntityName') ? this.props.attributes.defaultEntityName : ''
      },
      id() {
        return this.props.attributes.hasOwnProperty('id') ? this.props.attributes.id : ''
      },
      linkUrl() {
        return this.props.attributes.hasOwnProperty('linkUrl') ? this.props.attributes.linkUrl : ''
      },
      name() {
        return this.props.attributes.hasOwnProperty('name') ? this.props.attributes.name : ''
      },
      primaryKey() {
        return this.props.attributes.hasOwnProperty('primaryKey') ? this.props.attributes.primaryKey : ''
      },
      storeData() {
        return {
          id: this.$store.getters['data/currentId'],
          key: this.id,
          value: this.value
        }
      },
      value() {
        return this.props.attributes.hasOwnProperty('value') ? this.props.attributes.value : ''
      },
      viewIndex() {
        return this.props.attributes.hasOwnProperty('viewIndex') ? this.props.attributes.viewIndex : ''
      },
      viewIndexField() {
        return this.props.attributes.hasOwnProperty('viewIndexField') ? this.props.attributes.viewIndexField : ''
      },
      viewSize() {
        return this.props.attributes.hasOwnProperty('viewSize') ? this.props.attributes.viewSize : ''
      },
      viewSizeField() {
        return this.props.attributes.hasOwnProperty('viewSizeField') ? this.props.attributes.viewSizeField : ''
      },
    },
    created() {
      this.$store.dispatch('form/addForm', this.name)
      this.$store.dispatch('form/setFieldToForm', {
        formId: this.name,
        key: 'linkUrl',
        value: this.linkUrl
      })
      this.$store.dispatch('form/setFieldToForm', {
        formId: this.name,
        key: 'viewIndex',
        value: this.viewIndex
      })
      this.$store.dispatch('form/setFieldToForm', {
        formId: this.name,
        key: 'viewIndexField',
        value: this.viewIndexField
      })
      this.$store.dispatch('form/setFieldToForm', {
        formId: this.name,
        key: 'viewSize',
        value: this.viewSize
      })
      this.$store.dispatch('form/setFieldToForm', {
        formId: this.name,
        key: 'viewSizeField',
        value: this.viewSizeField
      })
      if (this.defaultEntityName) {
        this.$store.dispatch('data/setEntity', {
          entityName: this.defaultEntityName,
          list: {},
          primaryKey: this.primaryKey
        })
      }
    },
    mounted() {
      this.$store.dispatch('form/addFormValidate', {formName: this.name, validate: this.$refs[this.name].validate})
    },
    updated() {
      this.$store.dispatch('form/addFormValidate', {formName: this.name, validate: this.$refs[this.name].validate})
    },
    watch: {
      props: function (from, to) {
        this.$store.dispatch('form/addForm', to.name)
        this.$store.dispatch('form/setFieldToForm', {
          formId: this.name,
          key: 'linkUrl',
          value: this.linkUrl
        })
        this.$store.dispatch('form/setFieldToForm', {
          formId: this.name,
          key: 'viewIndex',
          value: this.viewIndex
        })
        this.$store.dispatch('form/setFieldToForm', {
          formId: this.name,
          key: 'viewIndexField',
          value: this.viewIndexField
        })
        this.$store.dispatch('form/setFieldToForm', {
          formId: this.name,
          key: 'viewSize',
          value: this.viewSize
        })
        this.$store.dispatch('form/setFieldToForm', {
          formId: this.name,
          key: 'viewSizeField',
          value: this.viewSizeField
        })
      }
    }
  }
</script>

<style scoped>

</style>
