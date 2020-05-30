// TODO compare size and description.size and tronque
<template>
  <div id="vue-display-field">
    <table v-if="fieldTitle">
      <tr>
        <td>
          <v-tooltip top>
            <template v-slot:activator="{ on }">
              <label class="font-weight-medium ma-2" v-on="fieldHelpText ? on : null">{{fieldTitle}}</label>
            </template>
            <span>{{fieldHelpText}}</span>
          </v-tooltip>
        </td>
        <td>
          <div v-if="inPlaceEditor">
            <div v-if="editing">
              <v-text-field class="d-inline-flex ma-1" v-model="editValue" v-on:keydown.enter.prevent="save"></v-text-field><span>
            <v-btn class="d-inline-flex ma-1 primary dark" @click.prevent="save">Save</v-btn>
            <v-btn class="d-inline-flex ma-1" @click="toggleEdit">Cancel</v-btn>
          </span>
            </div>
            <div v-else>
              <v-hover v-slot:default="{ hover }">
                <label :class="hover ? 'font-weight-bold' : ''" :idname="idName" class="ma-1" @click="toggleEdit">{{value}}</label>
              </v-hover>
            </div>
          </div>
            <div v-else>
              <label :idname="idName" class="ma-1">{{value}}</label>
          </div>
        </td>
      </tr>
    </table>
    <div v-else>
      <div v-if="inPlaceEditor">
        <div v-if="editing">
          <v-text-field class="d-inline-flex ma-1" v-model="editValue" v-on:keydown.enter.prevent="save"></v-text-field><span>
        <v-btn class="d-inline-flex ma-1 primary dark" @click.prevent="save">Save</v-btn>
        <v-btn class="d-inline-flex ma-1" @click="toggleEdit">Cancel</v-btn>
      </span>
        </div>
        <div v-else>
          <v-hover v-slot:default="{ hover }">
            <label :class="hover ? 'font-weight-bold' : ''" :idname="idName" class="ma-1" @click="toggleEdit">{{value}}</label>
          </v-hover>
        </div>
      </div>
      <div v-else>
        <label :idname="idName" class="ma-1">{{value}}</label>
      </div>
    </div>


  </div>
</template>

<script>
  import {mapGetters} from 'vuex'

  export default {
    name: "VueDisplayField",
    props: [
      'props'
    ],
    data() {
      return {
        pointer: {
          entityName: this.getNestedObject(this.props, ['stPointer', 'stEntityName']),
          id: this.getNestedObject(this.props, ['stPointer', 'id']),
          attribute: this.getNestedObject(this.props, ['stPointer', 'field'])
        },
        editing: false,
        editValue: '',
        edited: false,
        newValue: ''
      }
    },
    computed: {
      ...mapGetters({
        getData: 'data/entityRowAttribute',
        getDataFromForm: 'form/fieldInForm'
      }),
      title() {
        return this.props.attributes.hasOwnProperty('title') ? this.props.attributes.title : ''
      },
      description() {
        return this.props.attributes.hasOwnProperty('description') ? this.props.attributes.description : ''
      },
      name() {
        return this.props.attributes.hasOwnProperty('name') ? this.props.attributes.name : ''
      },
      havePointer() {
        return this.pointer.entityName !== '' && this.pointer.entityName !== undefined
      },
      getPointer() {
        return this.$store.getters['data/entityRowAttribute'](this.pointer);
      },
      formName() {
        return this.props.attributes.hasOwnProperty('formName') ? this.props.attributes.formName : ''
      },
      idName() {
        return this.props.attributes.hasOwnProperty('idName') ? this.props.attributes.idName : ''
      },
      hasEntity() {
        return this.pointer.hasOwnProperty('entityName')
      },
      storeForm() {
        return {
          formId: this.formName,
          key: this.name,
          value: this.hasEntity ? this.getPointer : this.description
        }
      },
      inPlaceEditor() {
        return this.props.attributes.hasOwnProperty('inPlaceEditor') ? this.props.attributes.inPlaceEditor : false
      },
      url() {
        return this.props.attributes.hasOwnProperty('url') ? this.props.attributes.url : ''
      },
      fieldMap() {
        return this.props.attributes.hasOwnProperty('fieldMap') ? this.props.attributes.fieldMap : {}
      },
      params() {
        return {...this.fieldMap, ...{[this.name]: this.editValue}}
      },
      value() {
        if (this.havePointer) {
          return this.getPointer
        } else {
          if (!this.edited) {
            return this.title !== '' ? this.title : this.description
          } else {
            return this.newValue
          }
        }
      },
      fieldTitle() {
        return this.props.attributes.hasOwnProperty('fieldTitle') ? this.props.attributes.fieldTitle : ''
      },
      fieldHelpText() {
        return this.props.attributes.hasOwnProperty('fieldHelpText') ? this.props.attributes.fieldHelpText : ''
      }
    },
    methods: {
      toggleEdit() {
        if (!this.edited) this.editValue = this.value
        this.editing = !this.editing
      },
      save() {
        this.$store.dispatch('backOfficeApi/doRequest', {uri: this.url, mode: 'post', params: this.params}).then(() => {
          if (this.havePointer) {
            this.$store.dispatch('data/setEntityRow', {
              entityName: this.getNestedObject(this.props, ['stPointer', 'stEntityName']),
              primaryKey: this.getNestedObject(this.props, ['stPointer', 'id']),
              data: {[this.name]: this.editValue}
            })
          } else {
            this.newValue = this.editValue
            this.edited = true
          }
          this.toggleEdit()
        },() => {
          this.toggleEdit()
        })
      }
    },
    watch: {
      props: function () {
        this.pointer = {
          entityName: this.getNestedObject(this.props, ['stPointer', 'stEntityName']),
          id: this.getNestedObject(this.props, ['stPointer', 'id']),
          attribute: this.getNestedObject(this.props, ['stPointer', 'field'])
        }
      }
    }
  }
</script>

<style scoped>

</style>
