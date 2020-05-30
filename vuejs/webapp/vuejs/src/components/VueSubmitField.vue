<template>
  <v-row id="vue-submit-field" text-center class="ma-2">
    <v-btn
        raised
        color="primary"
        class="secondary--text"
        type="submit"
        :label="title"
        v-on:click.prevent="resolveEvents">{{title}}
    </v-btn>
  </v-row>
</template>

<script>
  import {mapGetters} from 'vuex'

  export default {
    name: "VueSubmitField",
    props: ['props'],
    computed: {
      ...mapGetters({
        getForm: 'form/form',
        getDataFromForm: 'form/fieldInForm',
        formValidate: 'form/formValidate'
      }),
      haveUpdateAreas() {
        return this.props.attributes.hasOwnProperty('updateAreas') && !this.props.attributes.updateAreas.empty
      },
      form() {
        return this.getForm(this.formName)
      },
      formName() {
        return this.props.attributes.hasOwnProperty('formName') ? this.props.attributes.formName : ''
      },
      title() {
        return this.props.attributes.hasOwnProperty('title') ? this.props.attributes.title : ''
      },
      updateAreas() {
        return this.haveUpdateAreas ? this.props.attributes.updateAreas : []
      }
    },
    methods: {
      resolveEvent(updateArea) {
        switch (updateArea.eventType) {
          case 'close-modal':
            if (updateArea.hasOwnProperty('areaId') && updateArea.areaId !== '') {
              this.$store.dispatch('ui/setDialogStatus', {
                dialogId: updateArea.areaId,
                dialogStatus: false
              })
            } else {
              this.$store.dispatch('ui/closeAllDialogs')
            }
            return new Promise((resolve) => {
              setTimeout(() => {
                resolve()
              }, 0)
            })
          case 'collapse':
            switch (updateArea.areaTarget) {
              case 'collapse':
                this.$store.dispatch('ui/setCollapsibleStatus', {
                  areaId: updateArea.areaId,
                  areaTarget: true
                })
                break
              case 'expand':
                this.$store.dispatch('ui/setCollapsibleStatus', {
                  areaId: updateArea.areaId,
                  areaTarget: false
                })
                break
              case 'toggle':
                this.$store.dispatch('ui/setCollapsibleStatus', {
                  areaId: updateArea.areaId,
                  areaTarget: !this.$store.getters['ui/collapsibleStatus'](updateArea.areaId)
                })
                break
              default:
                this.$store.dispatch('ui/setCollapsibleStatus', {
                  areaId: updateArea.areaId,
                  areaTarget: !this.$store.getters['ui/collapsibleStatus'](updateArea.areaId)
                })
                break
            }
            return new Promise((resolve) => {
              setTimeout(() => {
                resolve()
              }, 0)
            })
          case 'set-area':
            return this.setArea(updateArea)
          case 'set-watcher':
            this.setWatcher(updateArea)
            return new Promise((resolve) => {
              setTimeout(() => {
                resolve()
              }, 0)
            })
          case 'submit':
            return this.submit()
          case 'refresh-area':
            return this.refreshArea(updateArea)
          case 'refresh-watcher':
            return this.refreshWatcher(updateArea)
          default:
            return new Promise((resolve) => {
              setTimeout(() => {
                resolve()
              }, 0)
            })
        }
      },
      resolveEvents() {
        if (this.formValidate(this.props.attributes.formName)()) {
          if (this.haveUpdateAreas) {
            this.updateAreas.reduce((accumulatorPromise, nextUpdateArea) => {
              return accumulatorPromise.then(() => {
                return this.resolveEvent(nextUpdateArea)
              })
            }, Promise.resolve())
          } else {
            this.submit()
          }
        }
      },
      setArea(updateArea) {
        return this.$store.dispatch('ui/setArea', {
          areaId: updateArea.areaId,
          targetUrl: `${this.$store.getters['backOfficeApi/currentApi']}/${updateArea.areaTarget}`,
          params: (updateArea.hasOwnProperty('parameterMap') && Object.keys(updateArea.parameterMap).length > 0) ? updateArea.parameterMap : this.form
        })
      },
      setWatcher(updateArea) {
        this.$store.dispatch('data/setWatcher', {
          watcherName: updateArea.areaId,
          params: (updateArea.hasOwnProperty('parameterMap') && Object.keys(updateArea.parameterMap).length > 0) ? updateArea.parameterMap : this.form
        })
      },
      submit() {
        let uri = this.parseUrl(this.getDataFromForm({formId: this.props.attributes.formName, key: 'linkUrl'}), this.form)
        return this.$store.dispatch('backOfficeApi/doRequest', {
          uri: uri,
          mode: 'post',
          params: this.form
        })
      },
      refreshArea(updateArea) {
        return new Promise((resolve) => {
          setTimeout(() => {
            this.$store.dispatch('ui/refreshArea', updateArea.areaId)
            resolve()
          }, 0)
        })
      },
      refreshWatcher(updateArea) {
        return new Promise((resolve) => {
          setTimeout(() => {
            this.$store.dispatch('ui/refreshWatcher', updateArea.areaId)
            resolve()
          }, 0)
        })
      }
    }
  }
</script>

<style scoped>

</style>
