# Steps to test the initial pass of the SMS gateway integration:

    Build and start the server.
    Reload the fresh data or you can load the data from following XML files.
    - Msg91SeedData.xml
    - Msg91SecurityPermissionSeedData.xml
    - Msg91SecurityGroupDemoData.xml
    - Msg91DemoData.xml

    Sign up to the msg91 services to get the authkey and free SMS quota.
    - http://control.msg91.com/signup/?source=developer-SMS
    Find your authkey after completing the sign up process.

    Alter the system property data to change "telecom.notifications.enabled" flag to "Y".

    Go to "Msg91GatewayConfig" entity. Here you need to set following fields.
    -- country: Set it to 91 if you want to send SMS in India, set it to 1 if you want to send SMS to USA, for rest of countries, it should be set to 0.
    -- authkey: The authkey you got on your API dashboard once you complete registration process.

    After doing all the above mentioned steps, go to webtools and click on run service menu.
    Enter service name as "sendTelecomMessage".
    Put following parameters:
    -- productStoreId: 9000
    -- telecomMsgTypeEnumId: ORDER_SMS
    -- telecomMethodTypeId: Its optional so can be left blank.
    -- numbers: comma seperated numbers. If you are sending SMS in India or USA, then the numbers should be excluding country code. Otherwise numbers should include country code too.
    -- message: message you want to send to.
    After entering the required parameters, run the service.

    Wait for some time to receive message on your phone. If it is taking too much time, go to the delivery section of the msg91 console to see if message is submitted to the API provider or not.