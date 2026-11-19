<template>
  <div id="vue-login">
    <v-dialog v-model="dialogStatus" name="login" :reset="true" height="auto" width="600px">
      <div class="screenlet login-screenlet">
        <div class="screenlet-body">
          <v-card>
            <v-card-title>
              {{uiLabel('registred')}}
            </v-card-title>
            <v-card-text>
              <v-form name="loginform">
                <v-text-field name="USERNAME" :label="uiLabel('userName')" v-model="username" size="20" type="text" />
                <v-text-field name="PASSWORD" :label="uiLabel('password')" autocomplete="off" v-model="password" size="20" type="password" />
                  <v-row justify="center">
                    <v-btn text @click.prevent="singIn">{{uiLabel('login')}}</v-btn>
                  </v-row>
                  <v-row>
                    <v-spacer></v-spacer>
                    <a href="https://localhost:8443/exampleapi/control/forgotPassword_step1">{{uiLabel('forgotYourPassword')}}?</a>
                  </v-row>
              </v-form>
            </v-card-text>
          </v-card>
        </div>
      </div>
    </v-dialog>
  </div>
</template>

<script>
  import {mapGetters} from 'vuex'

  export default {
    name: "VueLogin",
    data() {
      return {
        username: '',
        password: '',
      }
    },
    computed: {
      ...mapGetters({
        getDialogStatus: 'ui/dialogStatus',
        uiLabel: 'ui/uiLabel'
      }),
      credential() {
        return {username: this.username, password: this.password}
      },
      dialogStatus: {
        get() {
          return this.getDialogStatus('loginDialog')
        },
        set(value) {
          this.$store.dispatch('ui/setDialogStatus', {
            dialogId: 'loginDialog',
            dialogStatus: value
          })
        }
      }
    },
    methods: {
      singIn() {
        this.$store.dispatch('login/login', this.credential)
          .then(() => {
            this.$store.dispatch('ui/setDialogStatus', {
              dialogId: 'loginDialog',
              dialogStatus: false
            })
          })
      }
    }
  }
</script>

<style scoped>

</style>
