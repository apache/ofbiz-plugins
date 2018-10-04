# Using a flexible report - User #
## Introduction ##
>Note: this feature will be officially available with the R17.12 branch first release 

A flexible report is an OFBiz content of FLEXIBLE_REPORT type which allows the final user to obtain reports using the Birt reporting module. It will be produced at a specific time of your choosing, with your chosen output format, filtering the data with a few parameters defined during report design creation.

## Pre-requisite ##
- OFBiz
- The Birt plugin
- Pre-published reports created from report masters 

## Using the report ##
1. go to the Birt component or to another page harboring reports.
2. In the Birt component, click "Use a report".
3. Select your report and hit "Send".
4. The next screen will allow you to filter your data through a set of pre-defined criteria. Should you leave it empty, you will retrieve unfiltered data. 
5. Select the desired export format
6. Upon validation, your report is now loaded and can be saved. 

>_Note_: Report loading can be a bit long depending on the data treatment