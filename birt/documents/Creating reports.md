# Report creation - Admin/Super-User #

## Introduction ##
A flexible report is an OFBiz content of FLEXIBLE\_REPORT type that allows a final user to make use of the reporting module to obtain flexible reports, ie reports which are created from an entity or view definition and even from a service. A flexible report is created from a Report Master (content REPORT_MASTER), and an optional XML override of the parent form.

## Pre-requisite ##
- OFBiz framework
- The Birt plugin
- The BIRT Report Designer

## Report creation ##
1. Get to Birt component in OFBiz ![Birt menus](https://cwiki.apache.org/confluence/download/attachments/68720496/Birt%20Menus.png?api=v2)
2. Click on the "Flexible Report" menu (varies depending on themes)
3. Click on the "Generate report" button, you get to this screen:
4. Fill the form: 
![Example Report](https://cwiki.apache.org/confluence/download/attachments/68720496/Example%20Report.png?api=v2) 
 * The list "Choose report topic" will let you choose among predefined report masters your topic of interest.
 * The report name is a simple short name from which the file name will be generated. 
 * The description is a short description which will allow you to recognise the report and its topic. 
 * The box "Generate filters in design" will add in the design the visualisation of the filters filled in the filtering form.  
 
Finally, if you don't find what you want, you will need to create a new Report Master...


Once the form is validated, OFBiz will show you the "Edit Report" screen. ![Edit Report screen](https://cwiki.apache.org/confluence/download/attachments/68720496/Edit%20Report%20screen.png?api=v2)

## Report information ##
This first panel allows you to change the report description and status. Actually it does not make sense changing the status to published before having downloaded the .rptdesign file (Birt Report Designer file) from the server (in database), edited and uploaded it back to the server. This is explained in the section below. Changing the status allows users to use your reports. But if you publish without any change the report will render as empty.

## The .rptdesign report file: donwload, edit, upload and publish it ##
To really use the report you need to download the .rptdesign file from the server in a location from where you can edit it with the BIRT Report Designer. So you need to install first the BIRT Report Designer. Then you can edit the .rptdesign file in the BIRT Report Designer. For that refer to the ["Using the Birt Report Designer.md.html" file](https://svn.apache.org/repos/asf/ofbiz/ofbiz-plugins/trunk/birt/documents/Using%20the%20Birt%20Report%20Designer.md.html). You can also find the Markdonw version in the same directory: ofbiz-framework/plugins/birt/documents
###  Editing the downloaded file ###
Once you installed the BIRT Report Designer and have downloaded the .rptdesign file, you can edit it with the Birt Report Designer. When you have edited it suiting your needs you must upload it to the server for your changes to be taken into account by OFBiz.

<span style="color:red">**This is when things begin to be really interesting**.</span> You can then test your report using the "Preview" panel. There you can temporarily filter the result, and use the export format you prefer, once done click "Send". You can then decide to change the report content in the Birt Report Designer again or keep your changes. Once done in the Birt Report Designer, simply select the changed file to upload, and upload it again. You can re-test your changes and continue until you really get what you want! You can then publish the report to allow users to select and use it. There are 2 ways to publish a report from the "Manage reports" screen or directly in the "Report information" panel. We will see the "Manage reports" screen below. 

## Filters Overriding  ##
You may want to overide the default filters. You can then use the Xml "Override filters" panel to override and personnalize the form, once done click "Save". If you ignore that step, it will **NOT** prevent the report creation, **it is already done**, it will just be with the generic filtering form inherited from the master from.
>_Note_: if no preview is available, it is usually due to a mistake in the master form code. You can edit it in the database.

## Manage reports ##
The "Manage reports" button get you to a screen which allows to edit (get back to current page), publish or delete a report.

## Use a report ##
Users can select and use any published report from that screen. When they select a report they then get the same "Preview" Panel and can do the same things than in the "Edit Report" screen. Refer users to  



