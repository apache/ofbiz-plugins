/*
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
*/
package org.apache.ofbiz.scrum

String partyId = parameters.partyId

// get existing task that no assign
projectSprintBacklogAndTaskList = []
List projectAndTaskList = from('ProjectSprintBacklogAndTask')
        .where(sprintTypeId: 'SCRUM_SPRINT', taskCurrentStatusId: 'STS_CREATED')
        .orderBy('-taskId')
        .queryList()
projectAndTaskList.each { projectAndTaskMap ->
    String projectId = projectAndTaskMap.projectId
    List partyAssignmentProjectList = from('WorkEffortPartyAssignment')
            .where(workEffortId: projectId, partyId: partyId)
            .queryList()

    // if this userLoginId is a member of project
    if (partyAssignmentProjectList) {
        String sprintId = projectAndTaskMap.sprintId
        List partyAssignmentSprintList = from('WorkEffortPartyAssignment').where(workEffortId: sprintId, partyId: partyId).queryList()

        // if this userLoginId is a member of sprint
        if (partyAssignmentSprintList) {
            String workEffortId = projectAndTaskMap.taskId
            List partyAssignmentTaskList = from('WorkEffortPartyAssignment').where(workEffortId: workEffortId).queryList()

            // if the task do not assigned or  if the task do not assigned and assigned with custRequestTypeId = RF_SCRUM_MEETINGS
            if (!partyAssignmentTaskList
                    || (projectAndTaskMap.custRequestTypeId == 'RF_SCRUM_MEETINGS'
                    && projectAndTaskMap.backlogStatusId == 'CRQ_REVIEWED')) {
                projectSprintBacklogAndTaskList << projectAndTaskMap
            }
        }
    }
}
context.projectSprintBacklogAndTaskList = projectSprintBacklogAndTaskList
