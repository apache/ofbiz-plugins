<template>
  <v-row id="vue-date-time-field">
    <v-tooltip top>
      <template v-slot:activator="{ on }">
        <label class="font-weight-medium ma-2" v-on="fieldHelpText ? on : null">{{label}}</label>
      </template>
      <span>{{fieldHelpText}}</span>
    </v-tooltip>
    <v-menu
        ref="dateMenu"
        v-model="dateMenu"
        :close-on-content-click="false"
        :return-value.sync="date"
        transition="scale-transition"
        offset-overflow
        offset-y
        min-width="290px"
    >
      <template v-slot:activator="{ on }">
        <v-text-field
            v-model="date"
            :label="uiLabel('date')"
            prepend-icon="mdi-event"
            :rules="rules"
            :hide-details="noRules"
            v-on="on"
        ></v-text-field>
      </template>
      <v-date-picker v-model="date" scrollable @change="$refs.dateMenu.save(date)" :locale="language">
      </v-date-picker>
    </v-menu>
    <v-menu
        ref="timeMenu"
        v-model="timeMenu"
        :close-on-content-click="false"
        :return-value.sync="time"
        transition="scale-transition"
        offset-overflow
        offset-y
        min-width="290px"
    >
      <template v-slot:activator="{ on }">
        <v-text-field
            v-model="time"
            :label="uiLabel('time')"
            prepend-icon="mdi-event"
            :rules="rules"
            :hide-details="noRules"
            v-on="on"
        ></v-text-field>
      </template>
      <v-time-picker v-model="time" :format="config.timeFormat" scrollable use-seconds @change="$refs.timeMenu.save(time)">
      </v-time-picker>
    </v-menu>
    <v-btn class="primary mt-5 ml-2" x-small  @click="setToNow">{{uiLabel('now')}}</v-btn>
  </v-row>
</template>

<script>
  import {mapGetters} from 'vuex'

  export default {
    name: "VueDateTimeField",
    props: ['props', 'updateStore'],
    data() {
      return {
        date: '',
        time: '',
        onDate: false,
        onTime: false,
        dateMenu: false,
        timeMenu: false
      }
    },
    computed: {
      ...mapGetters({
        getForm: 'form/form',
        getDataFromForm: 'form/fieldInForm',
        uiLabel: 'ui/uiLabel',
        locale: 'ui/locale'
      }),
      config() {
        return {
          allowInput: true,
          enableTime: true,
          enableSeconds: true,
          timeFormat: this.isTwelveHour ? 'ampm' : '24hr'
        }
      },
      controls() {
        return {
          required: this.props.attributes.hasOwnProperty('required') && this.props.attributes.required.hasOwnProperty('requiredField') && this.props.attributes.required.requiredField === "true"
        }
      },
      datetime() {
        return this.date + ' ' + this.time
      },
      dateType() {  // not yet used but will be
        return this.props.attributes.hasOwnProperty('datetype') ? this.props.attributes.datetype : 'default'
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
      isTwelveHour() {
        return this.props.attributes.hasOwnProperty('isTwelveHour') && this.props.attributes.isTwelveHour === 'Y' ? this.props.attributes.isTwelveHour : false
      },
      label() {
        return this.required ? this.fieldTitle + ' *' : this.fieldTitle
      },
      language() {
        return this.locale.language
      },
      name() {
        return this.props.attributes.hasOwnProperty('name') ? this.props.attributes.name : ''
      },
      noRules() {
        return this.controls.required === false
      },
      required() {
        return this.props.attributes.hasOwnProperty('required') && this.props.attributes.required.hasOwnProperty('requiredField') && this.props.attributes.required.requiredField === "true"
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
          formId: this.formName,
          key: this.name,
          value: this.props.attributes.value ? this.props.attributes.value : ''
        }
      },
      value() {
        return this.props.attributes.hasOwnProperty('value') ? this.props.attributes.value.split('.')[0] : ''
      }
    },
    methods: {
      setToNow() {
        let now = new Date(Date.now())
        this.date = `${now.getFullYear()}-${now.getMonth() < 9 ? '0' : ''}${now.getMonth() + 1}-${now.getDate() < 10 ? '0' : ''}${now.getDate()}`
        this.time = `${now.getHours() < 10 ? '0' : ''}${now.getHours()}:${now.getMinutes() < 10 ? '0' : ''}${now.getMinutes()}:${now.getSeconds() < 10 ? '0' : ''}${now.getSeconds()}`
      }
    }
    ,
    mounted() {
      if (this.props.attributes.hasOwnProperty('value') && this.props.attributes.value !== '') {
        this.date = this.props.attributes.value.split(' ')[0]
        this.time = this.props.attributes.value.split(' ')[1].split('.')[0]
      }
    },
    created() {
      this.$store.dispatch('form/setFieldToForm', this.storeForm)
    },
    watch: {
      datetime: function (newValue) {
        this.$store.dispatch('form/setFieldToForm', {
          formId: this.formName,
          key: this.name,
          value: newValue
        })
      },
      props: function () {
        this.$store.dispatch('form/setFieldToForm', this.storeForm)
        if (this.props.attributes.hasOwnProperty('value') && this.props.attributes.value !== '') {
          this.date = this.props.attributes.value.split(' ')[0]
          this.time = this.props.attributes.value.split(' ')[1].split('.')[0]
        }
      }
    }
  }
</script>

<style scoped>

</style>
