<template>
  <v-container fluid class="mb-2 ma-0 pa-0" v-if="mustBeDisplayed">
    <v-toolbar dark color="primary" flat height="30px" class="ma-0 pa-0">
      <v-icon color="secondary" left>{{getIcon(icon)}}</v-icon>
      <v-toolbar-title class="text--secondary">
        {{ctmUiLabel('POSTAL_ADDRESS')}}
      </v-toolbar-title>
      <v-spacer></v-spacer>
      <v-btn v-if="editMode && contactMechList.length === 0" small icon sel-label="addPostalAddr" @click="addContactMech">
        <v-icon>
          {{getIcon('mdi-plus-circle')}}
        </v-icon>
      </v-btn>
    </v-toolbar>
    <v-list dense sel-label="postalAddr" class="ma-0 pa-0">
      <v-list-item v-for="postalAddress in filteredContactMechList"
                   :key="postalAddress.contactMech.contactMechId">
        <v-list-item-content :class="postalAddress.partyContactMech.hasOwnProperty('thruDate') && postalAddress.partyContactMech.thruDate ? 'grey--text' : ''" v-if="!editMode">
          <v-list-item-title>
            {{postalAddress.postalAddress.toName}} {{postalAddress.postalAddress.attnName}}
          </v-list-item-title>
          <div>
            {{postalAddress.postalAddress.address1}}
          </div>
          <div>
            {{postalAddress.postalAddress.address2}}
          </div>
          <div>
            {{postalAddress.postalAddress.city}}, {{postalAddress.postalAddress.postalCode}}
          </div>
          <v-list-item-subtitle v-if="postalAddress.partyContactMechPurposes.length > 0 && !editMode">
            <v-row class="ma-0 pa-0">
              <v-chip class="accent mr-1 mb-1" x-small v-for="purpose in postalAddress.partyContactMechPurposes"
                      :key="purpose.contactMechId + '-' + purpose.contactMechPurposeTypeId">
                {{getPurposeDescription(purpose.contactMechPurposeTypeId)}}
              </v-chip>
            </v-row>
          </v-list-item-subtitle>
          <v-list-item-subtitle v-if="postalAddress.partyContactMech.hasOwnProperty('thruDate') && postalAddress.partyContactMech.thruDate">
            <v-chip class="secondary mr-1 mb-1" x-small>
              {{ctmUiLabel('effectiveThru')}}  {{postalAddress.partyContactMech.thruDate}}
            </v-chip>
          </v-list-item-subtitle>
        </v-list-item-content>
        <v-list-item-content v-if="editMode">
          <v-form :lazy-validator="lazy">
              <v-row class="ma-0 pa-0">
                <v-text-field hide-details id="toName" :label="ctmUiLabel('toName')"
                              :rules="rules.toName"
                              v-model="postalAddress.postalAddress.toName" class="mr-4"></v-text-field>
                <v-text-field hide-details id="attentionName" :label="ctmUiLabel('attentionName')"
                              :rules="rules.attentionName"
                              v-model="postalAddress.postalAddress.attnName"></v-text-field>
              </v-row>
              <v-row class="ma-0 pa-0">
                <v-text-field hide-details id="addressLine1" :label="addressLine1"
                              :rules="rules.addressLine1"
                              v-model="postalAddress.postalAddress.address1"></v-text-field>
                <v-text-field hide-details id="addressLine2" :label="ctmUiLabel('addressLine2')"
                              :rules="rules.addressLine2"
                              v-model="postalAddress.postalAddress.address2"></v-text-field>
              </v-row>
              <v-row class="ma-0 pa-0">
                <v-text-field hide-details id="city" :label="city"
                              v-model="postalAddress.postalAddress.city"
                              :rules="rules.city" class="mr-4"></v-text-field>
                <v-text-field hide-details id="zipPostalCode" :label="zipCode"
                              v-model="postalAddress.postalAddress.postalCode"
                              :rules="rules.zipPostalCode" class="mr-4"></v-text-field>
              </v-row>
              <v-list-item-subtitle v-if="editMode && purposeList.length > 0">
                <v-select
                    :label="ctmUiLabel('contactPurposes')"
                    v-model="postalAddress.purposes"
                    :items="purposeList"
                    multiple
                    item-text="description"
                    item-value="contactMechPurposeTypeId">
                </v-select>
              </v-list-item-subtitle>
              <v-list-item-subtitle v-if="editMode" class="d-flex flex-row-reverse">
                <v-btn @click="removeContactMech(postalAddress)" color="error">
                  <v-icon id='mdi-delete'>{{getIcon('mdi-delete')}}</v-icon>
                  {{uiLabel('expire')}}
                </v-btn>
              </v-list-item-subtitle>
          </v-form>
        </v-list-item-content>
      </v-list-item>
      <v-list-item v-if="editMode && contactMechList.length > 0">
        <v-list-item-content>
          <v-list-item-subtitle class="d-flex justify-center">
            <v-btn color="secondary" sel-label="addPostalAddr" @click="addContactMech">
              <v-icon id='mdi-plus-circle' left>{{getIcon('mdi-plus-circle')}}</v-icon>
              {{uiLabel('add')}} {{ctmUiLabel('POSTAL_ADDRESS')}}
            </v-btn>
          </v-list-item-subtitle>
        </v-list-item-content>
      </v-list-item>
    </v-list>
  </v-container>
</template>

<script>
  import icons from '../../../js/icons'
  import {mapGetters} from 'vuex'

  export default {
    name: "PostalAddress",
    props: ['contactMechList', 'editMode', 'icon', 'uiLabels', 'contactMechTypeId', 'showMore', 'purposeList', 'rules', 'lazy', 'showLessList'],
    computed: {
      ...mapGetters({
        commonUiLabel: 'ui/uiLabel'
      }),
      filteredContactMechList() {
        if (this.showLessList.mode === 'never') {
          return []
        } else if (this.showLessList.mode === 'none' && !this.showMore && !this.editMode) {
          return []
        } else if (this.showLessList.mode === 'purposes' && !this.showMore && !this.editMode) {
          return this.contactMechList.filter(contactMech => contactMech.partyContactMechPurposes.find(purpose => this.showLessList.purposes.includes(purpose.contactMechPurposeTypeId)))
        } else {
          return !this.showMore && this.showLessList.mode === 'count' && this.showLessList.count > 0 && !this.editMode ? this.contactMechList.slice(0, this.showLessList.count) : this.contactMechList
        }
      },
      mustBeDisplayed() {
        return (this.editMode && this.showLessList.mode !== 'never') || this.filteredContactMechList.length > 0
      },
      addressLine1(){
          return this.ctmUiLabel('addressLine1') + " *"
      },
      city(){
          return this.ctmUiLabel('city') + " *"
      },
      zipCode(){
          return this.ctmUiLabel('zipCode') + " *"
      }
    },
    methods: {
      addContactMech() {
        this.$emit('addContactMech')
      },
      removeContactMech(contactMech) {
        this.$emit('removeContactMech', contactMech)
      },
      getIcon(icon) {
        return icons.hasOwnProperty(icon) ? icons[icon] : null
      },
      uiLabel(label){
          return this.commonUiLabel(label)
      },
      ctmUiLabel(label) {
        return this.uiLabels.hasOwnProperty(label) ? this.uiLabels[label] : label
      },
      getPurposeDescription(contactMechPurposeTypeId) {
        return this.purposeList.filter(purpose => purpose.contactMechPurposeTypeId === contactMechPurposeTypeId).length > 0 ? this.purposeList.filter(purpose => purpose.contactMechPurposeTypeId === contactMechPurposeTypeId)[0].description : ''
      },
      // currently not use, because date is formated in ofbiz
      parseDate(timestamp) {
        return new Date(parseInt(timestamp)).toLocaleDateString() + ' - ' + new Date(parseInt(timestamp)).toLocaleTimeString()
      }
    }
  }
</script>

<style scoped>

</style>
