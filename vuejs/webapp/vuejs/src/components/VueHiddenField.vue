<template>
  <div id="vue-hidden-field">
    <input v-if="conditionGroup" type="hidden" :name="name + '_grp'" v-bind:value="value" />
    <input v-else type="hidden" v-model="storeValue"/>
  </div>
</template>

<script>
  import { mapGetters } from 'vuex'

  export default {
    name: "VueHiddenField",
    props: ['props', 'updateStore'],
    computed: {
      ...mapGetters({
        dataFromExample: 'data/dataFromExample',
        currentId: 'data/currentId',
        getForm: 'form/form',
        getDataFromForm: 'form/fieldInForm'
      }),
      conditionGroup() {
        return this.props.attributes.hasOwnProperty('conditionGroup') ? this.props.attributes.conditionGroup : ''
      },
      formName() {
        return this.props.attributes.hasOwnProperty('formName') ? this.props.attributes.formName : ''
      },
      id() {
        return this.props.attributes.hasOwnProperty('id') ? this.props.attributes.id : ''
      },
      name() {
        return this.props.attributes.hasOwnProperty('name') ? this.props.attributes.name : ''
      },
      storeForm() {
        return {
          formId: this.formName,
          key: this.name,
          value: this.value
        }
      },
      storeValue: {
        get() {
          return this.getDataFromForm(this.storeForm)
        },
        set(value) {
          this.$store.dispatch('form/setFieldToForm', {
            formId: this.formName,
            key: this.id,
            value: value
          })
        }
      },
      value() {
        return this.props.attributes.hasOwnProperty('value') ? this.props.attributes.value : ''
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
