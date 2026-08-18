<template>
  <v-navigation-drawer class="primary text--secondary" id="app-menu" :value="true" mini-variant mini-variant-width="86" mobile-break-point="0" expand-on-hover app>
    <v-list-item>
      <v-list-item-content>
        <v-list-item-title class="title text--secondary">Ofbiz</v-list-item-title>
        <v-list-item-subtitle>application</v-list-item-subtitle>
      </v-list-item-content>
    </v-list-item>
    <v-divider></v-divider>
    <v-list dense nav id="app-navigation" height="auto" v-if="menu.hasOwnProperty('viewScreen')" color="primary" class="text--secondary" link router>
      <v-list-item v-for="(link, id) in menu.viewScreen[0].children" :key="id" link
                   :to="generateRouterLink(link.children[0].attributes.target, link.children[0].attributes.parameterMap)" dense router exact>
        <v-list-item-content>
          <v-list-item-title class="text--secondary">
            {{link.children[0].attributes.text}}
          </v-list-item-title>
        </v-list-item-content>
      </v-list-item>
    </v-list>
    <template v-slot:append>
      <v-list>
        <v-list-item>
          <v-list-item-icon>
            <v-icon>{{getIcon('mdi-theme-light-dark')}}</v-icon>
          </v-list-item-icon>
          <v-list-item-content>
            <v-list-item-title>
              Dark Mode :
            </v-list-item-title>
          </v-list-item-content>
          <v-list-item-action>
            <v-switch v-model="darkMode"></v-switch>
          </v-list-item-action>
        </v-list-item>
        <v-list-group dense :prepend-icon="getIcon('mdi-palette')">
          <template v-slot:activator>
            <v-list-item-title>Colors</v-list-item-title>
          </template>
          <v-list-item dense>
            <v-list-item-title>Primary</v-list-item-title>
            <v-list-item-action>
              <v-dialog v-model="primaryDialog" width="300">
                <template v-slot:activator="{ on }">
                  <v-btn small color="primary" dark v-on="on" class="ms-2">
                    Change
                  </v-btn>
                </template>
                <v-color-picker v-model="primaryColor" width="300" mode="hexa" show-swatches></v-color-picker>
              </v-dialog>
            </v-list-item-action>
          </v-list-item>
          <v-list-item dense>
            <v-list-item-title>Secondary</v-list-item-title>
            <v-list-item-action>
              <v-dialog v-model="secondaryDialog" width="300">
                <template v-slot:activator="{ on }">
                  <v-btn small color="secondary" dark v-on="on" class="ms-2">
                    Change
                  </v-btn>
                </template>
                <v-color-picker v-model="secondaryColor" width="300" mode="hexa" show-swatches></v-color-picker>
              </v-dialog>
            </v-list-item-action>
          </v-list-item>
          <v-list-item dense>
            <v-list-item-title>Accent</v-list-item-title>
            <v-list-item-action>
              <v-dialog v-model="accentDialog" width="300">
                <template v-slot:activator="{ on }">
                  <v-btn small color="accent" dark v-on="on" class="ms-2">
                    Change
                  </v-btn>
                </template>
                <v-color-picker v-model="accentColor" width="300" mode="hexa" show-swatches></v-color-picker>
              </v-dialog>
            </v-list-item-action>
          </v-list-item>
          <v-list-item dense>
            <v-list-item-title>Success</v-list-item-title>
            <v-list-item-action>
              <v-dialog v-model="successDialog" width="300">
                <template v-slot:activator="{ on }">
                  <v-btn small color="success" dark v-on="on" class="ms-2">
                    Change
                  </v-btn>
                </template>
                <v-color-picker v-model="successColor" width="300" mode="hexa" show-swatches></v-color-picker>
              </v-dialog>
            </v-list-item-action>
          </v-list-item>
          <v-list-item dense>
            <v-list-item-title>Error</v-list-item-title>
            <v-list-item-action>
              <v-dialog v-model="errorDialog" width="300">
                <template v-slot:activator="{ on }">
                  <v-btn small color="error" dark v-on="on" class="ms-2">
                    Change
                  </v-btn>
                </template>
                <v-color-picker v-model="errorColor" width="300" mode="hexa" show-swatches></v-color-picker>
              </v-dialog>
            </v-list-item-action>
          </v-list-item>
          <v-list-item dense>
            <v-list-item-title>Warning</v-list-item-title>
            <v-list-item-action>
              <v-dialog v-model="warningDialog" width="300">
                <template v-slot:activator="{ on }">
                  <v-btn small color="warning" dark v-on="on" class="ms-2">
                    Change
                  </v-btn>
                </template>
                <v-color-picker v-model="warningColor" width="300" mode="hexa" show-swatches></v-color-picker>
              </v-dialog>
            </v-list-item-action>
          </v-list-item>
        </v-list-group>
      </v-list>
    </template>
  </v-navigation-drawer>
</template>

<script>
  import {mapGetters} from 'vuex'
  import constants from '../js/constants'
  import icons from '../js/icons'

  export default {
    name: "AppMenu",
    data() {
      return {
        menu: {},
        primaryDialog: false,
        secondaryDialog: false,
        accentDialog: false,
        successDialog: false,
        errorDialog: false,
        warningDialog: false,
      }
    },
    computed: {
      ...mapGetters({
        currentApi: 'backOfficeApi/currentApi'
      }),
      darkMode: {
        get() {
          return this.$vuetify.theme.dark
        },
        set(value) {
          this.$vuetify.theme.dark = value
          this.$store.dispatch('backOfficeApi/doRequest', {
            uri: constants.hostUrl + this.currentApi + '/ajaxSetUserPreference',
            mode: 'post',
            params : {
              userPrefTypeId: 'DARK_MODE',
              userPrefValue: value,
              userPrefGroupTypeId: 'VUEJS_PREFERENCES'
            }})
        }
      },
      primaryColor: {
        get() {
          return this.$vuetify.theme.themes.dark.primary
        },
        set(value) {
          this.$vuetify.theme.themes.dark.primary = value
          this.$vuetify.theme.themes.light.primary = value
          this.$store.dispatch('backOfficeApi/doRequest', {
            uri: constants.hostUrl + this.currentApi + '/ajaxSetUserPreference',
            mode: 'post',
            params : {
              userPrefTypeId: 'PRIMARY_COLOR',
              userPrefValue: value,
              userPrefGroupTypeId: 'VUEJS_PREFERENCES'
            }})
        }
      },
      secondaryColor: {
        get() {
          return this.$vuetify.theme.themes.dark.secondary
        },
        set(value) {
          this.$vuetify.theme.themes.dark.secondary = value
          this.$vuetify.theme.themes.light.secondary = value
          this.$store.dispatch('backOfficeApi/doRequest', {
            uri: constants.hostUrl + this.currentApi + '/ajaxSetUserPreference',
            mode: 'post',
            params : {
              userPrefTypeId: 'SECONDARY_COLOR',
              userPrefValue: value,
              userPrefGroupTypeId: 'VUEJS_PREFERENCES'
            }})
        }
      },
      accentColor: {
        get() {
          return this.$vuetify.theme.themes.dark.accent
        },
        set(value) {
          this.$vuetify.theme.themes.dark.accent = value
          this.$vuetify.theme.themes.light.accent = value
          this.$store.dispatch('backOfficeApi/doRequest', {
            uri: constants.hostUrl + this.currentApi + '/ajaxSetUserPreference',
            mode: 'post',
            params : {
              userPrefTypeId: 'ACCENT_COLOR',
              userPrefValue: value,
              userPrefGroupTypeId: 'VUEJS_PREFERENCES'
            }})
        }
      },
      successColor: {
        get() {
          return this.$vuetify.theme.themes.dark.success
        },
        set(value) {
          this.$vuetify.theme.themes.dark.success = value
          this.$vuetify.theme.themes.light.success = value
          this.$store.dispatch('backOfficeApi/doRequest', {
            uri: constants.hostUrl + this.currentApi + '/ajaxSetUserPreference',
            mode: 'post',
            params : {
              userPrefTypeId: 'SUCCESS_COLOR',
              userPrefValue: value,
              userPrefGroupTypeId: 'VUEJS_PREFERENCES'
            }})
        }
      },
      errorColor: {
        get() {
          return this.$vuetify.theme.themes.dark.error
        },
        set(value) {
          this.$vuetify.theme.themes.dark.error = value
          this.$vuetify.theme.themes.light.error = value
          this.$store.dispatch('backOfficeApi/doRequest', {
            uri: constants.hostUrl + this.currentApi + '/ajaxSetUserPreference',
            mode: 'post',
            params : {
              userPrefTypeId: 'ERROR_COLOR',
              userPrefValue: value,
              userPrefGroupTypeId: 'VUEJS_PREFERENCES'
            }})
        }
      },
      warningColor: {
        get() {
          return this.$vuetify.theme.themes.dark.warning
        },
        set(value) {
          this.$vuetify.theme.themes.dark.warning = value
          this.$vuetify.theme.themes.light.warning = value
          this.$store.dispatch('backOfficeApi/doRequest', {
            uri: constants.hostUrl + this.currentApi + '/ajaxSetUserPreference',
            mode: 'post',
            params : {
              userPrefTypeId: 'WARNING_COLOR',
              userPrefValue: value,
              userPrefGroupTypeId: 'VUEJS_PREFERENCES'
            }})
        }
      }
    },
    methods: {
      generateRouterLink(url, params) {
        let pathname = url.split('?')[0]
        let webapp = pathname.substring(pathname.lastIndexOf('/') + 1, pathname.length)
        if (webapp === 'showPortalPage') {
          return `/screen/showPortalPage/${params.portalPageId}`
        } else {
          return `/screen/${pathname}`
        }
      },
      getIcon(icon) {
        return icons.hasOwnProperty(icon) ? icons[icon] : null
      }
    },
    mounted() {
      this.$store.dispatch('backOfficeApi/doRequest', {uri: 'applicationMenu', mode: 'post', params: {}}).then(result => {
        this.menu = result.body
      })
      this.$store.dispatch('backOfficeApi/doRequest', {uri: 'getUserPreference', mode: 'post', params: {userPrefTypeId: 'DARK_MODE', userPrefGroupTypeId: 'VUEJS_PREFERENCES'}}).then(result => {
        if (result.body.hasOwnProperty('userPrefValue')) {
          this.$vuetify.theme.dark = result.body.userPrefValue === "true"
        }
      })
      this.$store.dispatch('backOfficeApi/doRequest', {uri: 'getUserPreference', mode: 'post', params: {userPrefTypeId: 'PRIMARY_COLOR', userPrefGroupTypeId: 'VUEJS_PREFERENCES'}}).then(result => {
        if (result.body.hasOwnProperty('userPrefValue')) {
          this.$vuetify.theme.themes.dark.primary = result.body.userPrefValue
          this.$vuetify.theme.themes.light.primary = result.body.userPrefValue
        }
      })
      this.$store.dispatch('backOfficeApi/doRequest', {uri: 'getUserPreference', mode: 'post', params: {userPrefTypeId: 'SECONDARY_COLOR', userPrefGroupTypeId: 'VUEJS_PREFERENCES'}}).then(result => {
        if (result.body.hasOwnProperty('userPrefValue')) {
          this.$vuetify.theme.themes.dark.secondary = result.body.userPrefValue
          this.$vuetify.theme.themes.light.secondary = result.body.userPrefValue
        }
      })
      this.$store.dispatch('backOfficeApi/doRequest', {uri: 'getUserPreference', mode: 'post', params: {userPrefTypeId: 'ACCENT_COLOR', userPrefGroupTypeId: 'VUEJS_PREFERENCES'}}).then(result => {
        if (result.body.hasOwnProperty('userPrefValue')) {
          this.$vuetify.theme.themes.dark.accent = result.body.userPrefValue
          this.$vuetify.theme.themes.light.accent = result.body.userPrefValue
        }
      })
      this.$store.dispatch('backOfficeApi/doRequest', {uri: 'getUserPreference', mode: 'post', params: {userPrefTypeId: 'SUCCESS_COLOR', userPrefGroupTypeId: 'VUEJS_PREFERENCES'}}).then(result => {
        if (result.body.hasOwnProperty('userPrefValue')) {
          this.$vuetify.theme.themes.dark.success = result.body.userPrefValue
          this.$vuetify.theme.themes.light.success = result.body.userPrefValue
        }
      })
      this.$store.dispatch('backOfficeApi/doRequest', {uri: 'getUserPreference', mode: 'post', params: {userPrefTypeId: 'ERROR_COLOR', userPrefGroupTypeId: 'VUEJS_PREFERENCES'}}).then(result => {
        if (result.body.hasOwnProperty('userPrefValue')) {
          this.$vuetify.theme.themes.dark.error = result.body.userPrefValue
          this.$vuetify.theme.themes.light.error = result.body.userPrefValue
        }
      })
      this.$store.dispatch('backOfficeApi/doRequest', {uri: 'getUserPreference', mode: 'post', params: {userPrefTypeId: 'WARNING_COLOR', userPrefGroupTypeId: 'VUEJS_PREFERENCES'}}).then(result => {
        if (result.body.hasOwnProperty('userPrefValue')) {
          this.$vuetify.theme.themes.dark.warning = result.body.userPrefValue
          this.$vuetify.theme.themes.light.warning = result.body.userPrefValue
        }
      })
    },
    watch: {
      currentApi: function (newValue) {
        this.$store.dispatch('backOfficeApi/doRequest', {uri: constants.hostUrl + newValue + '/applicationMenu', mode: 'post', params: {}}).then(result => {
          this.menu = result.body
        })
        this.$store.dispatch('backOfficeApi/doRequest', {uri: 'getUserPreference', mode: 'post', params: {userPrefTypeId: 'DARK_MODE', userPrefGroupTypeId: 'VUEJS_PREFERENCES'}}).then(result => {
          if (result.body.hasOwnProperty('userPrefValue')) {
            this.$vuetify.theme.dark = result.body.userPrefValue === "true"
          }
        })
        this.$store.dispatch('backOfficeApi/doRequest', {uri: 'getUserPreference', mode: 'post', params: {userPrefTypeId: 'PRIMARY_COLOR', userPrefGroupTypeId: 'VUEJS_PREFERENCES'}}).then(result => {
          if (result.body.hasOwnProperty('userPrefValue')) {
            this.$vuetify.theme.themes.dark.primary = result.body.userPrefValue
            this.$vuetify.theme.themes.light.primary = result.body.userPrefValue
          }
        })
        this.$store.dispatch('backOfficeApi/doRequest', {uri: 'getUserPreference', mode: 'post', params: {userPrefTypeId: 'SECONDARY_COLOR', userPrefGroupTypeId: 'VUEJS_PREFERENCES'}}).then(result => {
          if (result.body.hasOwnProperty('userPrefValue')) {
            this.$vuetify.theme.themes.dark.secondary = result.body.userPrefValue
            this.$vuetify.theme.themes.light.secondary = result.body.userPrefValue
          }
        })
        this.$store.dispatch('backOfficeApi/doRequest', {uri: 'getUserPreference', mode: 'post', params: {userPrefTypeId: 'ACCENT_COLOR', userPrefGroupTypeId: 'VUEJS_PREFERENCES'}}).then(result => {
          if (result.body.hasOwnProperty('userPrefValue')) {
            this.$vuetify.theme.themes.dark.accent = result.body.userPrefValue
            this.$vuetify.theme.themes.light.accent = result.body.userPrefValue
          }
        })
        this.$store.dispatch('backOfficeApi/doRequest', {uri: 'getUserPreference', mode: 'post', params: {userPrefTypeId: 'SUCCESS_COLOR', userPrefGroupTypeId: 'VUEJS_PREFERENCES'}}).then(result => {
          if (result.body.hasOwnProperty('userPrefValue')) {
            this.$vuetify.theme.themes.dark.success = result.body.userPrefValue
            this.$vuetify.theme.themes.light.success = result.body.userPrefValue
          }
        })
        this.$store.dispatch('backOfficeApi/doRequest', {uri: 'getUserPreference', mode: 'post', params: {userPrefTypeId: 'ERROR_COLOR', userPrefGroupTypeId: 'VUEJS_PREFERENCES'}}).then(result => {
          if (result.body.hasOwnProperty('userPrefValue')) {
            this.$vuetify.theme.themes.dark.error = result.body.userPrefValue
            this.$vuetify.theme.themes.light.error = result.body.userPrefValue
          }
        })
        this.$store.dispatch('backOfficeApi/doRequest', {uri: 'getUserPreference', mode: 'post', params: {userPrefTypeId: 'WARNING_COLOR', userPrefGroupTypeId: 'VUEJS_PREFERENCES'}}).then(result => {
          if (result.body.hasOwnProperty('userPrefValue')) {
            this.$vuetify.theme.themes.dark.warning = result.body.userPrefValue
            this.$vuetify.theme.themes.light.warning = result.body.userPrefValue
          }
        })
      }
    }
  }
</script>

<style scoped>

</style>
