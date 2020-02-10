/*!
 * Name: Bootstrap GDPR Cookies
 * Description: jQuery based plugin that shows bootstrap modal with cookie info
 * Version: v1.0
 *
 * Copyright (c) 2018 Aleksander Woźnica
 * Licensed under the MIT license: http://www.opensource.org/licenses/mit-license.php
 * Inspired by: https://github.com/ketanmistry/ihavecookies
 * Cookies Create/Read/Delete from https://www.quirksmode.org/js/cookies.html
 */

(function($) {

    $.fn.bsgdprcookies = function(options, event) {

        var $element = $(this);
        var cookieShow = ReadCookie('CookieShow');
        var cookiePreferences = ReadCookie('CookiePreferences');

        // Set default settings
        var settings = $.extend({
            id: 'bs-gdpr-cookies-modal',
            class: '',
            title: 'Cookies & Privacy Policy',
            backdrop: 'static',
            message: 'Your cookie message...',
            messageScrollBar: false,
            messageMaxHeightPercent: 25,
            delay: 1500,
            expireDays: 30,
            moreLinkActive: true,
            moreLinkLabel: 'More informations..',
            moreLinkNewTab: true,
            moreLink: 'privacy-policy.php',
            acceptButtonLabel: 'Accept',
            allowAdvancedOptions: false,
            advancedTitle: 'Select which cookies you want to accept',
            advancedAutoOpenDelay: 1000,
            advancedButtonLabel: 'Customize',
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
            OnAccept: function() {}
        }, options);

        if(!cookieShow || !cookiePreferences || event == 'reinit') {

            // Make sure that other instances are gone
            DisposeModal(settings.id);

            var modalBody = '';
            var modalButtons = '';
            var modalBodyStyle = '';
            var moreLink = '';

            // Generate more link
            if(settings.moreLinkActive == true) {
                if(settings.moreLinkNewTab == true) {
                    moreLink = '<a href="' + settings.moreLink + '" target="_blank" rel="noopener noreferrer" id="' + settings.id + '-more-link">' + settings.moreLinkLabel + '</a>';
                }
                else {
                    moreLink = '<a href="' + settings.moreLink + '" id="' + settings.id + '-more-link">' + settings.moreLinkLabel + '</a>';
                }
            }


            if(settings.allowAdvancedOptions === true) {
                modalButtons = '<button id="' + settings.id + '-advanced-btn" type="button" class="btn btn-secondary">' + settings.advancedButtonLabel + '</button><button id="' + settings.id + '-accept-btn" type="button" class="btn btn-primary" data-dismiss="modal">' + settings.acceptButtonLabel + '</button>';

                // Generate list of available advanced settings
                var advancedCookiesToSelectList = '';

                preferences = JSON.parse(cookiePreferences);
                $.each(settings.advancedCookiesToSelect, function(index, field) {
                    if (field.name !== '' && field.title !== '') {

                        var cookieDisabledText = '';
                        if(field.isFixed == true) {
                            cookieDisabledText = ' checked="checked" disabled="disabled"';
                        }       

                        var cookieDescription = '';
                        if (field.description !== false) {
                            cookieDescription = ' title="' + field.description + '"';
                        }

                        var fieldID = settings.id + '-option-' + field.name;

                        advancedCookiesToSelectList += '<li><input type="checkbox" id="' + fieldID + '" name="bsgdpr[]" value="' + field.name + '" data-auto="on" ' + cookieDisabledText + '> <label name="bsgdpr[]" data-toggle="tooltip" data-placement="right" for="' + fieldID + '"' + cookieDescription + '>' + field.title + '</label></li>';
                    }
                });

                modalBody = '<div id="' + settings.id + '-message">' + settings.message + moreLink + '</div>' + '<div id="' + settings.id + '-advanced-types" style="display:none; margin-top: 10px;"><h5 id="' + settings.id + '-advanced-title">' + settings.advancedTitle + '</h5>' + advancedCookiesToSelectList + '</div>';
            }
            else {
                modalButtons = '<button id="' + settings.id + '-accept-btn" type="button" class="btn btn-primary" data-dismiss="modal">' + settings.acceptButtonLabel + '</button>';

                modalBody ='<div id="' + settings.id + '-message">' + settings.message + moreLink + '</div>';
            }
            
            if(settings.messageScrollBar == true) {
                modalBodyStyle = 'style="overflow-y: scroll; max-height: ' + settings.messageMaxHeightPercent + '%"';
            }

            var modal = '<div class="modal fade ' + settings.class + '" id="' + settings.id + '" tabindex="-1" role="dialog" aria-labelledby="' + settings.id + '-title" aria-hidden="true"><div class="modal-dialog modal-dialog-centered" role="document"><div class="modal-content"><div class="modal-header"><h5 class="modal-title" id="' + settings.id + '-title">' + settings.title + '</h5></div><div id="' + settings.id + '-body" class="modal-body" ' + modalBodyStyle + '>' + modalBody + '</div><div class="modal-footer">' + modalButtons + '</div></div></div></div>';

            // Show Modal
            setTimeout(function() {
                $($element).append(modal);

                $('#' + settings.id).modal({keyboard: false, backdrop: settings.backdrop});

                if (event === 'reinit' && settings.allowAdvancedOptions === true) {

                    setTimeout(function(){
                        $('#' + settings.id + '-advanced-btn').trigger('click');
                        $.each(preferences, function(index, field) {
                            $('#' + settings.id + '-option-' + field).prop('checked', true);
                        });
                    }, settings.advancedAutoOpenDelay)
                }
            }, settings.delay);

            // When user clicks accept set cookie and close modal
            $('body').on('click','#' + settings.id + '-accept-btn', function(){

                // Set show cookie
                CreateCookie('CookieShow', true, settings.expireDays);
                DisposeModal(settings.id);

                // If 'data-auto' is set to ON, tick all checkboxes because the user has not chosen any option
                $('input[name="bsgdpr[]"][data-auto="on"]').prop('checked', true);

                // Clear user preferences cookie
                DeleteCookie('CookiePreferences');

                // Set user preferences cookie
                var preferences = [];
                $.each($('input[name="bsgdpr[]"]').serializeArray(), function(i, field){
                    preferences.push(field.value);
                });
                CreateCookie('CookiePreferences', JSON.stringify(preferences), 365);

                // Run callback function
                settings.OnAccept.call(this);
            });

            // Show advanced options
            $('body').on('click', '#' + settings.id + '-advanced-btn', function(){
                // Uncheck all checkboxes except for the disabled ones
                $('input[name="bsgdpr[]"]:not(:disabled)').attr('data-auto', 'off').prop('checked', false);
                
                $('label[name="bsgdpr[]"]').tooltip({offset: '0, 10'});

                // Show advanced checkboxes
                $('#' + settings.id + '-advanced-types').slideDown('fast', function(){
                    $('#' + settings.id + '-advanced-btn').prop('disabled', true);
                });

                // Scroll content to bottom if scrollbar option is active
                if(settings.messageScrollBar == true) {
                    setTimeout(function() {
                        bodyID = settings.id + '-body';
                        var div = document.getElementById(bodyID);
                        $('#' + bodyID).animate({
                            scrollTop: div.scrollHeight - div.clientHeight
                        }, 800);
                    }, 500);
                }
            });
        }
        else {
            var cookieValue = true;
            if (cookieShow == 'false') {
                cookieValue = false;
            }
            CreateCookie('CookieShow', cookieValue, settings.expireDays);
            DisposeModal(settings.id);
        }
    }

    /**
     * Returns user preferences saved in cookie
     */
    $.fn.bsgdprcookies.GetUserPreferences = function() {
        var preferences = ReadCookie('CookiePreferences');
        return JSON.parse(preferences);
    };

    /**
     * Check if user preference exists in cookie
     * 
     * @param {string} pref Preference to check
     */
    $.fn.bsgdprcookies.PreferenceExists = function(pref) {
        var preferences = $.fn.bsgdprcookies.GetUserPreferences();

        if (ReadCookie('CookieShow') === false) {
            return false;
        }
        if (preferences === false || preferences.indexOf(pref) === -1) {
            return false;
        }

        return true;
    };


    /**
     * Hide then delete bs modal
     * 
     * @param {string} id Modal ID without '#'
     */
    function DisposeModal(id) {
        id = '#' + id;
        $(id).modal('hide');
        $(id).on('hidden.bs.modal', function (e) {
            $(this).modal('dispose');
            $(id).remove();
        });
    }

    /**
     * Sets Cookie
     * 
     * @param {string} name Name of the cookie which you want to create
     * @param {boolean} value Value for the created cookie
     * @param {number} days Expire days
     */
    function CreateCookie(name, value, days) {
        if (days) {
            var date = new Date();
            date.setTime(date.getTime() + (days*24*60*60*1000));
            var expires = "; expires=" + date.toGMTString();
        }
        else var expires = "";
        document.cookie = name + "=" + value + expires + "; path=/";
    }
    

    /**
     * Gets Cookie called 'name'
     * 
     * @param {string} name Name of the cookie to read
     */
    function ReadCookie(name) {
        var nameEQ = name + "=";
        var ca = document.cookie.split(';');
        for(var i=0; i < ca.length; i++) {
            var c = ca[i];
            while (c.charAt(0) == ' ') c = c.substring(1, c.length);
            if (c.indexOf(nameEQ) == 0) return c.substring(nameEQ.length, c.length);
        }
        return null;
    }
    
    /**
     * Deletes Cookie called 'name;
     * 
     * @param {string} name Name of the cookie which you want to delete
     */
    function DeleteCookie(name) {
        CreateCookie(name, "", -1);
    }    

}(jQuery));