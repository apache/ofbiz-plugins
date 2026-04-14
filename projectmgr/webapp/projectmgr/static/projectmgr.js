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

const ganttItemsJson = document.getElementById("ofbizGantItemsJson").value;
const ganttItems = JSON.parse(ganttItemsJson);

const g = new JSGantt.GanttChart(document.getElementById('GanttChartDIV'), 'day');
g.setShowRes(1); // Show/Hide Resource (0/1)
g.setShowDur(1); // Show/Hide Duration (0/1)
g.setShowComp(1); // Show/Hide % Complete(0/1)
g.setShowTaskInfoLink(1);
g.setShowTaskInfoNotes(0)

g.setDateTaskTableDisplayFormat('dd mon yyyy');
g.setDayMajorDateDisplayFormat('dd mon');

for (t of ganttItems) {
    g.AddTaskItem(
        new JSGantt.TaskItem(t.pID, t.pName, t.pStart, t.pEnd, t.pClass, t.pLink, t.pMile, t.pRes, t.pComp,
            t.pGroup, t.pParent, t.pOpen, t.pDepend, "", "", g)
    );
}

g.Draw();
