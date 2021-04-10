<img src="https://camo.githubusercontent.com/b313d4ec52b77b5024e2988aaf76720258233e69/68747470733a2f2f6f6662697a2e6170616368652e6f72672f696d616765732f6f6662697a5f6c6f676f2e706e67" alt="Apache OFBiz" />

# Ecommerce component

## More information
How to use Janrain Engage Social Login.

1.Get API Key from http://www.janrain.com/products/engage/social-login.
2.Configure file setting : ecommerce.properties you can put it 
          Example:
          --------------------------------------------------------------------------------
            # -- Enable janrain engage (Y/ N) default N
            janrain.enabled=N
            
            # -- Janrain api key (secret)
            janrain.apiKey=exampleKey
            
            # -- Janrain application domain
            janrain.baseUrl=https://example.rpxnow.com
            
            # -- Janrain application name
            janrain.appName=exampleAppName
          --------------------------------------------------------------------------------
3.Restart the server.


How to test Janrain Engage Social Login.
=======================================

1. Go to Login screen.
2. Look the Social Login Widget and you can use these existing accounts to sign-in to your website.
3. First time if account does not exists then system will create new account.
4. After account existing in the system you can use "Social Login Widget" to login account.

===================================================================================================
