<template>
  <v-container fluid class="mb-2 ma-0 pa-0" v-if="mustBeDisplayed">
    <v-toolbar dark color="primary" flat height="30px" class="ma-0 pa-0">
      <v-icon color="secondary" left>{{getIcon(icon)}}</v-icon>
      <v-toolbar-title class="text--secondary">
        {{ctmUiLabel('FTP_ADDRESS')}}
      </v-toolbar-title>
      <v-spacer></v-spacer>
      <v-btn v-if="editMode && contactMechList.length === 0" small icon sel-label="addFtpAddr" @click="addContactMech">
        <v-icon>
          {{getIcon('mdi-plus-circle')}}
        </v-icon>
      </v-btn>
    </v-toolbar>
    <v-list dense class="ma-0 pa-0" sel-label="ftpAddr">
      <v-list-item v-for="ftpAddress in filteredContactMechList"
                   :key="ftpAddress.contactMech.contactMechId">
        <v-list-item-content :class="ftpAddress.partyContactMech.hasOwnProperty('thruDate') && ftpAddress.partyContactMech.thruDate ? 'grey--text' : ''" v-if="!editMode">
          <v-list-item-title>
            {{ftpAddress.ftpAddress.hostname}}:{{ftpAddress.ftpAddress.port}}
          </v-list-item-title>
          <div>
            user: {{ftpAddress.ftpAddress.username}} - pass: {{ftpAddress.ftpAddress.ftpPassword}}
          </div>
          <div>
            {{ftpAddress.ftpAddress.filePath}} - {{ftpAddress.ftpAddress.defaultTimeout}}ms
          </div>
          <div>
            <v-row class="ma-0 pa-0" justify="space-between">
              <v-checkbox class="ma-0 mr-1" hide-details small label="binary" :disabled="!editMode"
                          v-model="ftpAddress.ftpAddress.binaryTransfer"
                          true-value="Y" false-value="N"></v-checkbox>
              <v-checkbox class="ma-0 mr-1" hide-details small label="zip" :disabled="!editMode"
                          v-model="ftpAddress.ftpAddress.zipFile"
                          true-value="Y" false-value="N"></v-checkbox>
              <v-checkbox class="ma-0 mr-1" hide-details small label="passive" :disabled="!editMode"
                          v-model="ftpAddress.ftpAddress.passiveMode"
                          true-value="Y" false-value="N"></v-checkbox>
            </v-row>
          </div>
          <v-list-item-subtitle v-if="ftpAddress.partyContactMechPurposes.length > 0">
            <v-row class="ma-0 pa-0">
              <v-chip class="accent mr-1 mb-1" x-small v-for="purpose in ftpAddress.partyContactMechPurposes"
                      :key="purpose.contactMechId + '-' + purpose.contactMechPurposeTypeId">
                {{getPurposeDescription(purpose.contactMechPurposeTypeId)}}
              </v-chip>
            </v-row>
          </v-list-item-subtitle>
          <v-list-item-subtitle v-if="ftpAddress.partyContactMech.hasOwnProperty('thruDate') && ftpAddress.partyContactMech.thruDate">
            <v-chip class="secondary mr-1 mb-1" x-small>
              {{ctmUiLabel('effectiveThru')}}  {{ftpAddress.partyContactMech.thruDate}}
            </v-chip>
          </v-list-item-subtitle>
        </v-list-item-content>
        <v-list-item-content v-if="editMode">
          <v-form class="ml-3" :lazy-validator="lazy">
            <v-row class="ma-0 pa-0">
              <v-tooltip top>
                <template v-slot:activator="{ on }">
                  <v-text-field hide-details id="hostname" v-on="on" :label="ctmUiLabel('hostname')" class="mr-4"
                            :rules="rules.hostname"
                            v-model="ftpAddress.ftpAddress.hostname"></v-text-field>
                </template>
                <span>{{ctmUiLabel('hostnameMustContainProtocol')}} (ftp://, sftp://, ftps://...)</span>
              </v-tooltip>
              <v-text-field hide-details id="port" :label="ctmUiLabel('port')" class="" :rules="rules.port"
                            v-model="ftpAddress.ftpAddress.port"></v-text-field>
            </v-row>
            <v-row class="ma-0 pa-0">
              <v-text-field hide-details id="username" :label="ctmUiLabel('username')" class="mr-4"
                            :rules="rules.username"
                            v-model="ftpAddress.ftpAddress.username"></v-text-field>
              <v-text-field hide-details id="ftpPassword" :label="ctmUiLabel('password')"
                            :rules="rules.ftpPassword"
                            v-model="ftpAddress.ftpAddress.ftpPassword"></v-text-field>
            </v-row>
            <v-row class="ma-0 pa-0">
              <v-text-field hide-details id="filePath" :label="ctmUiLabel('path')" class="mr-4"
                            :rules="rules.filePath"
                            v-model="ftpAddress.ftpAddress.filePath"></v-text-field>
              <v-text-field hide-details id="defaultTimeout" :label="ctmUiLabel('defaultTimeout')" class=""
                            :rules="rules.defaultTimeout"
                            v-model="ftpAddress.ftpAddress.defaultTimeout"></v-text-field>
            </v-row>
            <v-row class="ma-0 pa-0">
              <v-checkbox class="ma-0 mr-1" id="binaryTransfer" :label="ctmUiLabel('binaryTransfer')" trueValue="Y"
                          falseValue="N"
                          :rules="rules.binaryTransfer"
                          v-model="ftpAddress.ftpAddress.binaryTransfer"></v-checkbox>
              <v-checkbox class="ma-0 mr-1" id="zipFile" :label="ctmUiLabel('zipFile')" trueValue="Y" falseValue="N"
                          :rules="rules.zipFile"
                          v-model="ftpAddress.ftpAddress.zipFile"></v-checkbox>
              <v-checkbox class="ma-0 mr-1" id="passiveMode" :label="ctmUiLabel('passiveMode')" trueValue="Y" falseValue="N"
                          :rules="rules.passiveMode"
                          v-model="ftpAddress.ftpAddress.passiveMode"></v-checkbox>
            </v-row>
          </v-form>
          <v-list-item-subtitle v-if="editMode && purposeList.length > 0">
            <v-select
                :label="ctmUiLabel('contactPurposes')"
                v-model="ftpAddress.purposes"
                :items="purposeList"
                multiple
                item-text="description"
                item-value="contactMechPurposeTypeId">
            </v-select>
          </v-list-item-subtitle>
          <v-list-item-subtitle v-if="editMode" class="d-flex flex-row-reverse">
            <v-btn @click="removeContactMech(ftpAddress)" color="error">
              <v-icon id='mdi-delete'>{{getIcon('mdi-delete')}}</v-icon>
              {{uiLabel('expire')}}
            </v-btn>
          </v-list-item-subtitle>
        </v-list-item-content>
      </v-list-item>
      <v-list-item v-if="editMode && contactMechList.length > 0">
        <v-list-item-content>
          <v-list-item-subtitle  class="d-flex justify-center">
            <v-btn color="secondary" sel-label="addFtpAddr" @click="addContactMech">
              <v-icon left>{{getIcon('mdi-plus-circle')}}</v-icon>
              {{uiLabel('add')}} {{ctmUiLabel('FTP_ADDRESS')}}
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
    name: "FtpAddress",
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
