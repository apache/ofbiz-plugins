<img src="https://camo.githubusercontent.com/b313d4ec52b77b5024e2988aaf76720258233e69/68747470733a2f2f6f6662697a2e6170616368652e6f72672f696d616765732f6f6662697a5f6c6f676f2e706e67" alt="Apache OFBiz" />

# Scrum component
This component enables organisations to manage agile project, product backlogs and sprints.


## How to install the revision of task function. 
# Deprecated this needs to be adapted to Git #

- [ ] We need to adapt the information below from Svn to Git


### Server requirements
1. Git

### Installation (On server)

1. Install Git
2. Hook script setting
    2.1 post-commit file is hook script file that will work when users commit source code to subversion repository.
          Copy post-commit file from "scrum/data/hookscripts/post-commit" to hooks folder of repository and then edit file following :
          Example : python /usr/share/subversion/hook-scripts/commit.py "$REPOS" "$REV"
    2.2 commit.py file is python file which will send revision information to Scrum web service.
          Copy commit.py from "scrum/data/hookscripts/commit.py" to "/usr/share/subversion/hook-scripts/" and then edit file following :
          ---------------------------------------------------------------------------------
            CONFIG_PATH = ""    // the path of the revision.properties should begin from home directory.
            Example : CONFIG_PATH = "/home/ofbiz/ofbiz/plugins/scrum/config/revision.properties"
          --------------------------------------------------------------------------------
3. Configure file setting : The original configure file is in scrum component (/scrum/config/revision.properties) you can put it 
          anywhere that you wish but should be set the path of the file in commit.py file ("CONFIG_PATH=").
          Example:
          --------------------------------------------------------------------------------
            revision.url =www.example.com/svn/
            ofbiz.webservice.url =http://www.example.com/webtools/control/SOAPService
            host.name =www.example.com
            host.port =80
            
            #-- subversion admin and password
            svn.user=demoUser
            svn.password=demoPassword
          --------------------------------------------------------------------------------
4. Change the location path of the updateScrumRevision service in .../scrum/servicedef/services.xml file.
          Example:
          --------------------------------------------------------------------------------
          <service name="updateScrumRevision" engine="soap" export="true"
            location="http://www.example.com/webtools/control/SOAPService" invoke="updateScrumRevisionChange">
            <implements service="updateScrumRevisionChange"/>
          </service>
          --------------------------------------------------------------------------------
5. Restart the server.
