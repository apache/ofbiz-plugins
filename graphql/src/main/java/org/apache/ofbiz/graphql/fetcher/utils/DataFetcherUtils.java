/*******************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *******************************************************************************/
package org.apache.ofbiz.graphql.fetcher.utils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.condition.EntityComparisonOperator;
import org.apache.ofbiz.entity.condition.EntityCondition;
import org.apache.ofbiz.entity.condition.EntityFunction;
import org.apache.ofbiz.entity.condition.EntityOperator;
import org.apache.ofbiz.entity.model.ModelEntity;
import graphql.language.Field;
import graphql.language.FragmentSpread;
import graphql.language.InlineFragment;
import graphql.language.Selection;
import graphql.language.SelectionSet;

public class DataFetcherUtils {
    static Selection getGraphQLSelection(SelectionSet selectionSet, String name) {
        if (selectionSet == null) {
            return null;
        }
        for (Selection selection : selectionSet.getSelections()) {
            if (selection instanceof Field) {
                if (((Field) (selection)).getName().equals(name)) {
                    return selection;
                }
            } else if (selection instanceof FragmentSpread) {
                // Do nothing since FragmentSpread has no way to find selectionSet
            } else if (selection instanceof InlineFragment) {
                getGraphQLSelection(((InlineFragment) (selection)).getSelectionSet(), name);
            }
        }
        return null;
    }

    static SelectionSet getGraphQLSelectionSet(Selection selection) {
        if (selection == null) {
            return null;
        }
        if (selection instanceof Field) {
            return (((Field) selection)).getSelectionSet();
        }
        if (selection instanceof InlineFragment) {
            return ((InlineFragment) (selection)).getSelectionSet();
        }
        return null;
    }

    static SelectionSet getConnectionNodeSelectionSet(SelectionSet selectionSet) {
        SelectionSet finalSelectionSet;

        Selection edgesSS = getGraphQLSelection(selectionSet, "edges");
        finalSelectionSet = getGraphQLSelectionSet(edgesSS);
        if (finalSelectionSet == null) {
            return null;
        }

        Selection nodeSS = getGraphQLSelection(finalSelectionSet, "node");
        finalSelectionSet = getGraphQLSelectionSet(nodeSS);

        return finalSelectionSet;
    }

    static boolean matchParentByRelKeyMap(Map<String, Object> sourceItem, Map<String, Object> self,
                                          Map<String, String> relKeyMap) {
        int found = -1;
        for (Map.Entry<String, String> entry : relKeyMap.entrySet()) {
            found = (found == -1) ? (sourceItem.get(entry.getKey()) == self.get(entry.getValue()) ? 1 : 0)
                    : (found == 1 && sourceItem.get(entry.getKey()) == self.get(entry.getValue()) ? 1 : 0);
        }
        return found == 1;
    }

    public static List<EntityCondition> addEntityConditions(List<EntityCondition> entityConditions,
                                                            Map<String, Object> inputFieldsMap, ModelEntity entity) {
        if (inputFieldsMap == null || inputFieldsMap.size() == 0) {
            return entityConditions;
        }

        for (String fieldName : entity.getAllFieldNames()) {
            if (inputFieldsMap.containsKey(fieldName) || inputFieldsMap.containsKey(fieldName + "_op")) {
                String value = (String) inputFieldsMap.get(fieldName);
                String op = UtilValidate.isNotEmpty(inputFieldsMap.get(fieldName + "_op"))
                        ? (String) inputFieldsMap.get(fieldName + "_op")
                        : "equals";
                boolean not = ("Y").equals(inputFieldsMap.get(fieldName + "_not"))
                        || "true".equals(inputFieldsMap.get(fieldName + "_not"));
                boolean ic = "Y".equals(inputFieldsMap.get(fieldName + "_ic"))
                        || "true".equals(inputFieldsMap.get(fieldName + "_ic"));
                boolean isValEmpty = UtilValidate.isEmpty(value);
                switch (op) {
                case "equals":
                    if (!isValEmpty) {
                        EntityComparisonOperator<?, ?> eq_operator = not ? EntityOperator.NOT_EQUAL
                                : EntityOperator.EQUALS;
                        if (ic) {
                            entityConditions.add(EntityCondition.makeCondition(EntityFunction.UPPER_FIELD(fieldName),
                                    eq_operator, EntityFunction.UPPER(value.trim())));
                        } else {
                            entityConditions.add(EntityCondition.makeCondition(fieldName, eq_operator, value.trim()));
                        }

                    }
                    break;
                case "like":
                    if (!isValEmpty) {
                        EntityComparisonOperator<?, ?> eq_operator = not ? EntityOperator.NOT_LIKE
                                : EntityOperator.LIKE;
                        if (ic) {
                            entityConditions.add(EntityCondition.makeCondition(EntityFunction.UPPER_FIELD(fieldName),
                                    eq_operator, EntityFunction.UPPER(value)));
                        } else {
                            entityConditions.add(EntityCondition.makeCondition(fieldName, eq_operator, value));
                        }

                    }
                    break;
                case "contains":
                    if (!isValEmpty) {
                        EntityComparisonOperator<?, ?> eq_operator = not ? EntityOperator.NOT_LIKE
                                : EntityOperator.LIKE;
                        if (ic) {
                            entityConditions.add(EntityCondition.makeCondition(EntityFunction.UPPER_FIELD(fieldName),
                                    eq_operator, EntityFunction.UPPER("%" + value + "%")));
                        } else {
                            entityConditions
                                    .add(EntityCondition.makeCondition(fieldName, eq_operator, "%" + value + "%"));
                        }
                    }
                    break;
                case "begins":
                    if (!isValEmpty) {
                        EntityComparisonOperator<?, ?> eq_operator = not ? EntityOperator.NOT_LIKE
                                : EntityOperator.LIKE;
                        if (ic) {
                            entityConditions.add(EntityCondition.makeCondition(EntityFunction.UPPER_FIELD(fieldName),
                                    eq_operator, EntityFunction.UPPER(value + "%")));
                        } else {
                            entityConditions.add(EntityCondition.makeCondition(fieldName, eq_operator, value + "%"));
                        }
                    }
                    break;
                case "in":
                    if (!isValEmpty) {
                        List<String> valueList = Arrays.asList(value.split(","));
                        EntityComparisonOperator<?, ?> eq_operator = not ? EntityOperator.NOT_IN : EntityOperator.IN;
                        entityConditions.add(EntityCondition.makeCondition(fieldName, eq_operator, valueList));
                    }
                    break;
                }

            }
        }
        return entityConditions;
    }
}
