<img src="https://camo.githubusercontent.com/b313d4ec52b77b5024e2988aaf76720258233e69/68747470733a2f2f6f6662697a2e6170616368652e6f72672f696d616765732f6f6662697a5f6c6f676f2e706e67" alt="Apache OFBiz" />

# ProjectMgr component
This component enables organisations to manage projects, project phases and project tasks.


The Project Management application enables organisations to manage their internal and external projects in a professional manner. It allows them to collaborate with their customers on projects. Approved time spent on external (time/material) projects will generate invoices in the Accounting application of OFBiz.

- [ ] We need to either remove the information below or extend it to all other components. Accounting is another such case...

## Features

    Resource assignement to projects
    Resource allocation to tasks
    Approval of time spent on tasks
    Gantt charts for projects, phases and tasks
    Time registration with different rates
    Generate invoice from a project
    Project copy
    Project, project phases and tasks show planned and actual time spent
    Project templates

## Data sets

### seed	
Needs to be loaded first

    generic configuration: https://svn.apache.org/repos/asf/ofbiz/trunk/specialpurpose/projectmgr/data/ProjectMgrTypeData.xml
    permissions configuration: https://svn.apache.org/repos/asf/ofbiz/trunk/specialpurpose/projectmgr/data/ProjectMgrSecurityPermissionSeedData.xml
    portlet configuration: https://svn.apache.org/repos/asf/ofbiz/trunk/specialpurpose/projectmgr/data/ProjectMgrPortletData.xml
    help screens configuration: https://svn.apache.org/repos/asf/ofbiz/trunk/specialpurpose/projectmgr/data/ProjectMgrHelpData.xml

### seed-initial
none	 

### extseed	
none	

###demo	
Loaded after extseed. For security reason don't load credentials in production!

    generic demo data: https://svn.apache.org/repos/asf/ofbiz/trunk/specialpurpose/projectmgr/data/ProjectMgrDemoData.xml
    demo permissions: https://svn.apache.org/repos/asf/ofbiz/trunk/specialpurpose/projectmgr/data/ProjectMgrSecurityGroupDemoData.xml
    demo user passwords: https://svn.apache.org/repos/asf/ofbiz/trunk/specialpurpose/projectmgr/data/ProjectMgrDemoPasswordData.xml



    
    
    
