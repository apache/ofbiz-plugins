#!/usr/bin/env bash

cd ../../..
patchs="$(find ./plugins/vuejs/ofbizCommit2add/ -name 'OFBIZ*.patch' |sort -n)"
for file in $patchs;
do
git am $file
done