# bsgdprcookies by Aleksander Woźnica
A jQuery based plugin that shows bootstrap modal with cookie info. It is GDPR/RODO compatible. The plugin shows the message on the users first visit and then every 30 days (by default). 

Here is a demo page hosted by [jqueryscript.net](https://www.jqueryscript.net/demo/GDPR-Cookie-Consent-Bootstrap-4-bsgdprcookies/)

You can choose between two options:
* Simple (message with accept button)

![simple_screenshot](https://user-images.githubusercontent.com/33174446/43984662-3e155618-9d02-11e8-8e89-288c3fb27779.png)
* Advanced (message, customize and accept button)

![advanced_screenshot](https://user-images.githubusercontent.com/33174446/43984678-4c82dfe0-9d02-11e8-8330-979f46bbd00c.png)

## Usage
1. Include Bootstrap 
> Here is how: https://getbootstrap.com/docs/4.1/getting-started/introduction/
2. Include plugin script __(must be after jQuery)__
```html
<script src="jquery.bs.gdpr.cookies.js"></script>
```
3. Initialise __bsgdprcookies__
```javascript
// Example with default settings
$(document).ready(function() {
    $('body').bsgdprcookies();
});
```
```javascript
// Example with custom settings
var settings = {
    message: '(...)',
    messageMaxHeightPercent: 30,
    delay: 1000,
    OnAccept : function() {
        console.log('Yay! User accepted your cookies window!');
    }
}

$(document).ready(function() {
    $('body').bsgdprcookies(settings);
});
```

If you want to give the user possibility to choose again, add html button: 
```html
<button id="cookiesBtn" role="button">Choose!</button>
```
and javascript code:
```javascript
//(...)
$('#cookiesBtn').on('click', function(){
    $('body').bsgdprcookies(settings, 'reinit');
});
```

More examples can be found in the repository.

## Settings
Setting | Default Value | Description
------ | ------------- | -----------
id | 'bs-gdpr-cookies-modal' | id of the modal
class | empty | additional modal classes
title | 'Cookies & Privacy Policy' | modal title
backdrop | 'static' |  A click area for dismissing shown modals when clicking outside the modal
message | 'Your cookie message...' | Message
messageScrollBar | false | Attach vertical scroll to the message (requires to specify messageMaxHeightPercent)
messageMaxHeightPercent | 25 | Max message height in %
delay | 1500 | Delay before showing modal (in ms)
expireDays | 30 | Days to show the accept modal again
moreLinkActive | true | Adds more link at the end of the message
moreLinkLabel | 'More informations..' | More link text
moreLinkNewTab | true | Open new tab when user clicks more link
moreLink | 'privacy-policy.php' | More link 
acceptButtonLabel | 'Accept' | Accept button text
allowAdvancedOptions | false | Activate advanced options (customize button)
advancedTitle | 'Select which cookies you want to accept' | Text above advanced options checkboxes
advancedAutoOpenDelay | 1000 | Time (in ms) to automatically show customize options (when "reinit" is set)
advancedButtonLabel | 'Customize' | Customize button text
OnAccept | function() {} | Specify what to do after acceptance
advancedCookiesToSelect | | see below

## How to set custom cookies to select (__advancedCookiesToSelect__)

Setting | Description
------- | -----------
name | Custom cookie name
title | Checkbox label
description | Description displayed when hovering
isFixed | If __true__ the option will always be selected

```javascript
// Default values
advancedCookiesToSelect: [
    {
        name: 'necessary',
        title: 'Necessary',
        description: 'Required for the site to work properly',
        isFixed: true
    },
    {
        name: 'preferences',
        title: 'Site Preferences',
        description: 'Required for saving your site preferences, e.g. remembering your username etc.',
        isFixed: false
    },
    {
        name: 'analytics',
        title: 'Analytics',
        description: 'Required to collect site visits, browser types, etc.',
        isFixed: false
    },
    {
        name: 'marketing',
        title: 'Marketing',
        description: 'Required to marketing, e.g. newsletters, social media, etc',
        isFixed: false
    }
],
```

## Methods

`$.fn.bsgdprcookies.GetUserPreferences` - returns user preferences saved in cookie

`$.fn.bsgdprcookies.PreferenceExists` - check if user preference exists in cookie (returns true/false)

## Styling

I dont include any css files. Feel free to adapt it to your website design!

## Author

[Aleksander Woźnica](https://github.com/Aleksander98)

## License

This plugin is available under the MIT license.

> The plugin has been inspired by [ihavecookies](https://github.com/ketanmistry/ihavecookies)
