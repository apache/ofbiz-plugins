<template>
  <div id="vue-message-list">
    <v-snackbar v-if="firstMessage" value="true" :timeout="0" :color="color" top>
      <div id="snackbarContent">
        {{firstMessage.messageContent}}
      </div>
      <div id="dismissSnackbar">
        <v-btn v-if="messageListSize === 1" @click="dismiss(firstMessage)" dark :color="color" class="darken-1">
          Close
          <v-icon>mdi-close</v-icon>
        </v-btn>
        <v-btn v-else @click="dismiss(firstMessage)" dark :color="color" class="darken-1">Next ({{messageListSize - 1}}
          more)
          <v-icon>mdi-arrow-right</v-icon>
        </v-btn>
      </div>
    </v-snackbar>
  </div>
</template>

<script>
  import {mapGetters} from 'vuex'

  export default {
    name: "VueMessageList",
    computed: {
      ...mapGetters({
        messageList: 'backOfficeApi/messageList'
      }),
      firstMessage() {
        return this.messageList.length > 0 ? this.messageList[0] : null
      },
      messageListSize() {
        return this.messageList.length
      },
      color() {
        return this.firstMessage.messageType === 'error' ? 'error' : 'success'
      }
    },
    methods: {
      dismiss(message) {
        this.$store.dispatch('backOfficeApi/deleteMessage', {message})
      },
      click() {
        this.flash('hello world !!!', 'success', 2000)
      }
    },
    watch: {
      firstMessage(message) {
        if (message && message.messageType === 'event') {
          setTimeout(() => {
            if (message === this.firstMessage) {
              this.dismiss(message)
            }
          }, 9000)
        }
      }
    }
  }
</script>

<style scoped>
</style>
