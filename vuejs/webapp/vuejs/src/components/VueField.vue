<template>
  <tr id="vue-field">
    <td class="label">
      <vue-label :props="props.$.title" />
    </td>
    <td v-for="module in Object.keys(props)" :key="module" >
      <div v-if="module !== '$'">
      <div v-for="iteration in Object.keys(props[module])" :key="iteration" v-bind:is="'vue-' + module" v-bind:props="props[module][iteration]" v-bind:name="props.$.name"></div>
      </div>
    </td>
  </tr>
</template>

<script>
  export default {
    name: "VueField",
    props: ['props']
  }
</script>

<style scoped>

</style>
