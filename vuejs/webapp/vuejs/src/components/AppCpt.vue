<template>
  <input type="hidden" id="updateCpt" :value="updateCpt">
</template>

<script>
  import {mapGetters} from 'vuex'

  export default {
    name: "AppCpt",
    computed: {
      ...mapGetters({
        updateCpt: 'ui/updateCpt'
      })
    },
  }
</script>

<style scoped>

</style>