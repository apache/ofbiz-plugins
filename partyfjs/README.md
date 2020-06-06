partyFjs plugin
==================
(partymgr for FrontJs renderer)

This plugin is a Apache OFBiz plugin, part of a group of plugin which intended to enhance OFBiz to work
with the vue.js SPA (Single Page Application).

Group of plugin contain:
- vuejsPortal as the "framework" part
- examplefjs  as example of usage
- flatgreyfjs as a dedicated theme to load only javascript needed (almost nothing)

The 3 are in the ofbizextra/ofbizplugins/ gitlab repository

partyFjs is the fourth plugin, it's the first ofbiz component migrate, currently it's at a WorkInProgress status

FrontJs-Vue.js, is a project which is, at the moment, at the Proof of Concept level. So sometine it's speaking about vue.js, sometime about frontjs
and sometimes about Portal sometine screen.

It's goal is to be a base to a future Apache OFBiz plugin integrated to Apache OFBiz.

The latest about this OFBiz plugin can be found in the documentation (see below)

# Implementation
Have a look to install documentation https://ofbizextra.org/ofbizextra_adocs/docs/asciidoc/developer-manual.html#_poc_vuejsportal_installation

## Summary For developers
1. clone the repo in the plugins folder of your OFBiz location for the 3 plugins
2. apply the ofbiz patch (new tag/attributes in xsd)  (from ofbizFiles vuejsPortal directory)
3. apply patch about Jira waiting validation ((from ofbizCommit2add vuejsPortal directory)
4. copy new files in ofbiz (from ofbizFiles vuejsPortal directory)
5. clone the repo in the plugins folder of your OFBiz location
6. restart your OFBiz implementation
7. load data sets of the plugin via webtools/import (for the portalPage)


# Documentation
All documentation and detail about this plugin is on https://ofbizextra.org/ofbizextra_adocs/docs/asciidoc/developer-manual.html#_frontjs_portal