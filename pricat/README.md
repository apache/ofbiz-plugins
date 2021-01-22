<img src="https://camo.githubusercontent.com/b313d4ec52b77b5024e2988aaf76720258233e69/68747470733a2f2f6f6662697a2e6170616368652e6f72672f696d616765732f6f6662697a5f6c6f676f2e706e67" alt="Apache OFBiz" />

# PriCat component
PriCat is the abbreviation of Price and Catalog/Category. The PriCat component is to support importing/parsing excel files with price and catalog/category data. The excel files can be checked by version, header column names, currencyId. Each row can be validated by facility(name, Id and ownership), required fields, string or number and etc.

PriCat component contains two webapps: /pricat/ and /pricatdemo/. In production environment, you SHOULD remove or disable the /pricatdemo/.

## more information
---------------------------------------
PriCat Demos
---------------------------------------
/pricatdemo/control/SamplePricat/: you can use this demo to implement your own excel templates.

/pricatdemo/control/countdownreport and /pricatdemo/control/countupreport: these 2 demos are on html report, you can try this way to display the processing report of rebuilding of lucene index or marchine learning data.