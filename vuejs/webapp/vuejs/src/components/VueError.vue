<template>
  <div id="vue-error">
    <h1 style="color: red">
      {{component}} is not yet supported !!!
    </h1>
  </div>
</template>

<script>
  export default {
    name: "VueError",
    props: ['component']
  }
</script>

<style scoped>

</style>
