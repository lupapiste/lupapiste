*** Settings ***

Documentation  Common stuff for the Lupapiste Functional Tests.
Library        Selenium2Library   timeout=10  run_on_failure=Nothing
Library        String
Library        OperatingSystem

*** Variables ***

${SERVER}                       http://localhost:8000
${WAIT_DELAY}                   10
${BROWSER}                      firefox
${DEFAULT_SPEED}                0
${OP_TREE_SPEED}                0.1
${SLOW_SPEED}                   0.2
${SLOWEST_SPEED}                0.5

${LOGIN URL}                    ${SERVER}/app/fi/welcome#!/login
${LOGOUT URL}                   ${SERVER}/app/fi/logout
${BULLETINS URL}                ${SERVER}/app/fi/bulletins
${APPLICATIONS PATH}            /applicant#!/applications
${AUTHORITY APPLICATIONS PATH}  /app/fi/authority#!/applications
${FIXTURE URL}                  ${SERVER}/dev/fixture
${CREATE URL}                   ${SERVER}/dev/create?redirect=true
${CREATE BULLETIN URL}          ${SERVER}/dev/publish-bulletin-quickly
${LAST EMAIL URL}               ${SERVER}/api/last-email?reset=true
${LAST EMAILS URL}              ${SERVER}/api/last-emails?reset=true
${FRONTEND LOG URL}             ${SERVER}/api/frontend-log
${SELENIUM}                     ${EMPTY}
${DB COOKIE}                    test_db_name
${DB PREFIX}                    test_

*** Keywords ***

Set DB cookie
  ${timestamp}=  Get Time  epoch
  ${dbname}=  Set Variable  ${DB PREFIX}${timestamp}
  Add Cookie  ${DB COOKIE}  ${dbname}
  Log To Console  \n Cookie: ${DB COOKIE} = ${dbname} \n
  Log  Cookie: ${DB COOKIE} = ${dbname}

Browser
  [Arguments]
  # Setting cookies on login page fails on IE8, perhaps because of
  # caching headers:
  # https://code.google.com/p/selenium/issues/detail?id=6985
  # Open a static HTML page and set cookie there
  Open browser  ${SERVER}/dev-pages/init.html  ${BROWSER}   remote_url=${SELENIUM}
  Set DB cookie

Open browser to login page
  Browser
  Maximize browser window
  Set selenium speed  ${DEFAULT_SPEED}
  Apply minimal fixture now
  Set integration proxy on
  Disable maps

Go to login page
  Go to  ${LOGIN URL}
  Wait Until  Title should be  Lupapiste
  Wait Until  Page should contain  Haluan kirjautua palveluun

Go to bulletins page
  Go to  ${BULLETINS URL}
  Wait Until  Title should be  Julkipano - Lupapiste
  Wait Until  Page should contain  Kuntien julkipanoilmoitukset

Open last email
  Go to  ${LAST EMAIL URL}
  Wait until  Element should be visible  //*[@data-test-id='subject']

Open all latest emails
  Go to  ${LAST EMAILS URL}
  Wait until  Element should be visible  //*[@data-test-id='subject']

Applications page should be open
  Location should contain  ${APPLICATIONS PATH}
  Title should be  Lupapiste
  Wait Until  Element should be visible  xpath=//*[@data-test-id='own-applications']

Authority applications page should be open
  Location should contain  ${AUTHORITY APPLICATIONS PATH}
  #Title should be  Lupapiste - Viranomainen
  Wait Until  Element should be visible  xpath=//*[@data-test-id='own-applications']

Authority-admin front page should be open
  Wait until  Element should be visible  users

Admin front page should be open
  Wait until  Element should be visible  admin

Number of visible applications
  [Arguments]  ${amount}
  Xpath Should Match X Times  //section[@id='applications']//tr[contains(@class,'application')]  ${amount}

Number of visible inforequests
  [Arguments]  ${amount}
  Xpath Should Match X Times  //section[@id='applications']//tr[contains(@class,'inforequest')]  ${amount}

Wait and click
  [Arguments]  ${element}
  Wait until  Element should be visible  ${element}
  # for IE8
  Wait until  Focus  ${element}
  Wait until  Element should be visible  ${element}
  Wait until  Click element  ${element}

Wait until
  [Arguments]  ${keyword}  @{varargs}
  Wait Until Keyword Succeeds  ${WAIT_DELAY}  0.1  ${keyword}  @{varargs}

Wait for jQuery
  Wait For Condition  return (typeof jQuery !== "undefined") && jQuery.active===0;  15

Kill dev-box
  Execute Javascript  $(".dev-debug").hide();

Resurrect dev-box
  Execute Javascript  $(".dev-debug").show();
  Wait until  Element should be visible  //div[@class="dev-debug"]

Hide nav-bar
  Execute Javascript  $("nav.nav-wrapper").hide();

Show nav-bar
  Execute Javascript  $("nav.nav-wrapper").show();
  Wait until  Element should be visible  //nav[@class="nav-wrapper"]


Language To
  [Arguments]  ${lang}
  Element Should Not Contain  language-select  ${lang}
  Click Link  xpath=//a[@data-test-id='language-link']
  Wait Until  Element Should Be Visible  css=div.language-menu
  Click Element  partial link=${lang}
  Wait Until  Element Should Contain  language-select  ${lang}

Language Is
  [Arguments]  ${lang}
  Wait Until  Element Should Contain  language-select  ${lang}


#
# Navigation
#

Go to page
  [Arguments]  ${page}
  Wait for jQuery
  Execute Javascript  window.location.hash = "!/${page}";
  Wait until  Element should be visible  ${page}

Open tab
  [Arguments]  ${name}
  ${is-visible}=  Run Keyword and Return Status  Element should be visible  application-${name}-tab
  Run keyword unless  ${is-visible}  Run keywords  Click by test id  application-open-${name}-tab  AND  Tab should be visible  ${name}

Tab should be visible
  [Arguments]  ${name}
  Wait until  Element should be visible  application-${name}-tab

Side panel should be visible
  [Arguments]  ${name}
  Wait until  Element should be visible  ${name}-panel

Side panel should not be visible
  [Arguments]  ${name}
  Wait until  Element should not be visible  ${name}-panel

Logout
  Wait for jQuery
  ${secs} =  Get Time  epoch
  Go to  ${LOGOUT URL}?s=${secs}
  Wait until  Element should be visible  xpath=//section[@id='login']//h3[1]
  Wait Until  Element text should be  xpath=//section[@id='login']//h3[1]  Haluan kirjautua palveluun

Open side panel
  [Arguments]  ${name}
  ${sidePanelClosed} =  Run Keyword And Return Status  Element should not be visible  ${name}-panel
  Run keyword If  ${sidePanelClosed}  Click by id  open-${name}-side-panel
  Side panel should be visible  ${name}

Close side panel
  [Arguments]  ${name}
  ${sidePanelOpen} =  Run Keyword And Return Status  Element should be visible  ${name}-panel
  Run keyword If  ${sidePanelOpen}  Click by id  open-${name}-side-panel
  Side panel should not be visible  ${name}

Open accordions
  [Arguments]  ${tab}
  # Open all accordions regardless of tab.
  Execute Javascript  AccordionState.toggleAll( true );
  # # The accordion-toggle class can either be in button or its container.
  # Execute Javascript  $("#application-${tab}-tab button.accordion-toggle.toggled").click();
  # Execute Javascript  $("#application-${tab}-tab div.accordion-toggle.toggled [data-accordion-id]").click();
  # Execute Javascript  $("#application-${tab}-tab button.accordion-toggle").click();
  # Execute Javascript  $("#application-${tab}-tab div.accordion-toggle [data-accordion-id]").click();

Open accordion editors
  Execute Javascript  lupapisteApp.services.accordionService.toggleEditors( true );

Open accordion by test id
  [Arguments]  ${testId}
  ${accordionIsClosed} =  Run Keyword And Return Status  Element should not be visible  xpath=//div[@data-test-id="${testId}"]//div[@data-accordion-state="open"]
  Run keyword If  ${accordionIsClosed}  Execute Javascript  $("div[data-test-id='${testId}'] button.accordion-toggle:not(.toggled)").click();

Positive indicator should be visible
  Wait until  Element should be visible  xpath=//div[@data-test-id="indicator-positive"]

Positive indicator should not be visible
  Wait until  Element should not be visible  xpath=//div[@data-test-id="indicator-positive"]

Negative indicator should be visible
  Wait until  Element should be visible  xpath=//div[@data-test-id="indicator-negative"]

Negative indicator should not be visible
  Wait until  Element should not be visible  xpath=//div[@data-test-id="indicator-negative"]

Negative indicator icon should not be visible
  Wait until  Element should not be visible  xpath=//div[@data-test-id="indicator-icon-negative"]

Positive indicator icon should be visible
  Wait until  Element should be visible  xpath=//div[@data-test-id="indicator-icon-positive"]

Positive indicator icon should not be visible
  Wait until  Element should not be visible  xpath=//div[@data-test-id="indicator-icon-positive"]
#
# Login stuff
#

User should not be logged in
  # Wait for login query to complete
  Wait for jQuery
  Wait Until  User is not logged in

User is not logged in
  Location should be  ${LOGIN URL}
  Page should contain  Haluan kirjautua palveluun
  # test that no data is bind.

Login
  [Arguments]  ${username}  ${password}
  Wait until  Element should be visible  login-username
  Input text  login-username  ${username}
  Input text  login-password  ${password}
  Wait and click  login-button

Login fails
  [Arguments]  ${username}  ${password}
  Login  ${username}  ${password}
  User should not be logged in

User should be logged in
  [Arguments]  ${name}
  Wait Until  Element text should be  user-name  ${name}

User logs in
  [Arguments]  ${login}  ${password}  ${username}
  Login  ${login}  ${password}
  User should be logged in  ${username}
  Kill dev-box

Applicant logs in
  [Arguments]  ${login}  ${password}  ${username}
  User logs in  ${login}  ${password}  ${username}
  User nav menu is visible
  User role should be  applicant
  Applications page should be open

Authority logs in
  [Arguments]  ${login}  ${password}  ${username}  ${showAll}=True
  User logs in  ${login}  ${password}  ${username}
  User nav menu is visible
  User role should be  authority
  Authority applications page should be open
  Run Keyword If  ${showAll}  Show all applications

Authority-admin logs in
  [Arguments]  ${login}  ${password}  ${username}
  User logs in  ${login}  ${password}  ${username}
  User nav menu is visible
  Authority-admin front page should be open

Admin logs in
  [Arguments]  ${login}  ${password}  ${username}
  User logs in  ${login}  ${password}  ${username}
  User nav menu is visible
  User role should be  admin
  Admin front page should be open

Get role
  Run Keyword And Return  Get Element Attribute  user-name@data-test-role

User role should be
  [Arguments]  ${expected-role}
  ${user-role} =  Get role
  Should Be Equal  ${expected-role}  ${user-role}

User nav menu is visible
  Element should be visible  //*[@data-test-id='user-nav-menu']

User nav menu is not visible
  Element should not be visible  //*[@data-test-id='user-nav-menu']

As ${name}
  Go to login page
  Run Keyword  ${name} logs in

Olli logs in
  [Arguments]  ${showAll}=True
  Authority logs in  olli  olli  Olli Ule\u00e5borg  ${showAll}

Olli-ya logs in
  [Arguments]  ${showAll}=True
  Authority logs in  olli-ya  olli  Olli-ya Ule\u00e5borg  ${showAll}

Mikko logs in
  Applicant logs in  mikko@example.com  mikko123  Mikko Intonen

Teppo logs in
  Applicant logs in  teppo@example.com  teppo69  Teppo Nieminen

Sven logs in
  Applicant logs in  sven@example.com  sven  Sven Svensson

Arto logs in
  [Arguments]  ${showAll}=True
  Authority logs in  arto  arto  Arto Viranomainen  ${showAll}

Veikko logs in
  [Arguments]  ${showAll}=True
  Authority logs in  veikko  veikko  Veikko Viranomainen  ${showAll}

Luukas logs in
  [Arguments]  ${showAll}=True
  Authority logs in  luukas  luukas  Luukas Lukija  ${showAll}

Velho logs in
  [Arguments]  ${showAll}=True
  Authority logs in  velho  velho  Velho Viranomainen  ${showAll}

Hannu logs in
  [Arguments]  ${showAll}=True
  Authority logs in  rakennustarkastaja@hel.fi  helsinki  Hannu Helsinki  ${showAll}

Sonja logs in
  [Arguments]  ${showAll}=True
  Authority logs in  sonja  sonja  Sonja Sibbo  ${showAll}

Ronja logs in
  [Arguments]  ${showAll}=True
  Authority logs in  ronja  sonja  Ronja Sibbo  ${showAll}

Pekka logs in
  [Arguments]  ${showAll}=True
  Authority logs in  pekka  pekka  Pekka Borga  ${showAll}

Sipoo logs in
  Authority-admin logs in  sipoo  sipoo  Simo Suurvisiiri

Sipoo-ya logs in
  Authority-admin logs in  sipoo-ya  sipoo  Simo YA-Suurvisiiri

Oulu Ymp logs in
  Authority-admin logs in  ymp-admin@oulu.fi  oulu  Oulu Ymp Admin

Naantali logs in
  Authority-admin logs in  admin@naantali.fi  naantali  Admin Naantali

Kuopio logs in
  Authority-admin logs in  kuopio-r  kuopio  Paakayttaja-R Kuopio

Pena logs in
  Applicant logs in  pena  pena  Pena Panaani

SolitaAdmin logs in
  Admin logs in  admin  admin  Admin Admin
  Wait until  Element should be visible  admin

Jarvenpaa authority logs in
  [Arguments]  ${showAll}=True
  Authority logs in  rakennustarkastaja@jarvenpaa.fi  jarvenpaa  Rakennustarkastaja Järvenpää  ${showAll}

Jussi logs in
  [Arguments]  ${showAll}=True
  Authority logs in  jussi  jussi  Jussi Viranomainen  ${showAll}


#
# Helpers for cases when target element is identified by "data-test-id" attribute:
#

Input text with jQuery
  [Arguments]  ${selector}  ${value}  ${leaveFocus}=${false}
  Wait until page contains element  jquery=${selector}
  Wait until  Element should be visible  jquery=${selector}
  Wait until  Element should be enabled  jquery=${selector}
  Execute Javascript  $('${selector}')[0].scrollIntoView(false);
  Execute Javascript  $('${selector}').focus().val("${value}").change();
  Run Keyword Unless  ${leaveFocus}  Execute Javascript  $('${selector}').blur();

Input text by test id
  [Arguments]  ${id}  ${value}  ${leaveFocus}=${false}
  Input text with jQuery  [data-test-id="${id}"]  ${value}  ${leaveFocus}

Select From List by test id and index
  [Arguments]  ${id}  ${index}
  Wait until page contains element  xpath=//select[@data-test-id="${id}"]
  Select From List By Index  xpath=//select[@data-test-id="${id}"]  ${index}

Select From List by test id
  [Arguments]  ${id}  ${value}
  Wait until page contains element  xpath=//select[@data-test-id="${id}"]
  Select From List  xpath=//select[@data-test-id="${id}"]  ${value}

Select From List by id
  [Arguments]  ${id}  ${value}
  Wait until page contains element  xpath=//select[@id="${id}"]
  Select From List  xpath=//select[@id="${id}"]  ${value}

Select From Autocomplete
  [Arguments]  ${container}  ${value}
  Wait until  Element should be visible  xpath=//${container}//span[contains(@class, "autocomplete-selection")]

  ${autocompleteListNotOpen} =  Run Keyword And Return Status  Element should not be visible  xpath=//${container}//div[@class="autocomplete-dropdown"]
  Run Keyword If  ${autocompleteListNotOpen}  Click Element  xpath=//${container}//span[contains(@class, "autocomplete-selection")]

  Input text  xpath=//${container}//input[@data-test-id="autocomplete-input"]  ${value}
  Wait until  Element should be visible  xpath=//${container}//ul[contains(@class, "autocomplete-result")]//li/span[contains(text(), '${value}')]
  Click Element  xpath=//${container}//ul[contains(@class, "autocomplete-result")]//li/span[contains(text(), '${value}')]
  Wait until  Element should not be visible  xpath=//${container}//ul[contains(@class, "autocomplete-result")]
  Wait for jQuery

Select From Autocomplete By Test Id
  [Arguments]  ${data-test-id}  ${value}
  Select From Autocomplete  *[@data-test-id="${data-test-id}"]  ${value}

Autocomplete selection is
  [Arguments]  ${container}  ${value}
  Element should contain  xpath=//${container}//span[contains(@class, "autocomplete-selection")]/span[contains(@class, 'caption')]  ${value}

Autocomplete selectable values should not contain
  [Arguments]  ${container}  ${value}
  # Open dropdown if it is not open
  ${autocompleteListNotOpen} =  Run Keyword And Return Status  Element should not be visible  xpath=//div[@data-test-id="operations-filter-component"]//div[@class="autocomplete-dropdown"]
  Run Keyword If  ${autocompleteListNotOpen}  Click Element  xpath=//div[@data-test-id="operations-filter-component"]//span[contains(@class, "autocomplete-selection")]
  Wait until  Element should not be visible  xpath=//${container}//ul[contains(@class, "autocomplete-result")]//li/span[contains(text(), '${value}')]

Autocomplete option list should contain
  [Arguments]  ${data-test-id}  @{options}
  :FOR  ${element}  IN  @{options}
  \  Element should contain  xpath=//div[@data-test-id="${data-test-id}"]//ul[contains(@class, "autocomplete-result")]  ${element}

Autocomplete option list should contain by test id
  [Arguments]  ${data-test-id}  @{options}
  Click Element  xpath=//div[@data-test-id="${data-test-id}"]//span[contains(@class, "autocomplete-selection")]
  Wait until  Element should be visible  xpath=//div[@data-test-id="${data-test-id}"]//div[@class="autocomplete-dropdown"]
  :FOR  ${element}  IN  @{options}
  \  Wait Until  Element should contain  xpath=//div[@data-test-id="${data-test-id}"]//ul[contains(@class, "autocomplete-result")]  ${element}

Click by id
  [Arguments]  ${id}
  ${selector} =   Set Variable  $("[id='${id}']:visible")
  # 'Click Element' is broken in Selenium 2.35/FF 23 on Windows, using jQuery instead
  Wait For Condition  return ${selector}.length===1;  10
  Execute Javascript  ${selector}.click();

Click by test id
  [Arguments]  ${id}
  ${selector} =   Set Variable  $("[data-test-id='${id}']:visible")
  # 'Click Element' is broken in Selenium 2.35/FF 23 on Windows, using jQuery instead
  Wait For Condition  return ${selector}.length===1;  10
  Execute Javascript  ${selector}[0].click();

Element should be visible by test id
  [Arguments]  ${id}
  Wait Until  Element Should Be Visible  xpath=//*[@data-test-id="${id}"]

Element should not be visible by test id
  [Arguments]  ${id}
  Wait Until  Element Should Not Be Visible  xpath=//*[@data-test-id="${id}"]

Click enabled by test id
  [Arguments]  ${id}
  Element should be visible by test id  ${id}
  Wait Until  Element Should Be Enabled  xpath=//*[@data-test-id='${id}']
  Click by test id  ${id}

# Workaround for HTML5 inputs
Value should be
  [Arguments]    ${textfield}    ${expected}
  ${actual}=    Get Value    ${textfield}
  Should Be Equal    ${expected}    ${actual}

#
# The following do not take data-test-id as argument
#

Get identifiers closed
  [Arguments]  ${docId}
  ${identifiersClosed} =  Run Keyword And Return Status  Element should not be visible  xpath=//div[@id='application-info-tab']//section[@data-doc-id='${docId}']//div[@data-test-id='identifier-editors']
  [Return]  ${identifiersClosed}

Primary operation is
  [Arguments]  ${opId}
  Element should be visible  xpath=//span[@data-test-primary-operation-id="${opId}"]

Edit operation description
  [Arguments]  ${doc}  ${text}  ${idx}=1
  Wait until   Element should be visible  xpath=//div[@id='application-info-tab']//section[@data-doc-type='${doc}'][${idx}]//button[@data-test-id='toggle-identifiers-${doc}']
  ${docId}=  Get Element Attribute  xpath=//div[@id='application-info-tab']//section[@data-doc-type='${doc}'][${idx}]@data-doc-id
  ${identifiersClosed} =  Get identifiers closed  ${docId}
  # for jQuery ${idx}-1 because xpath indeces start from 1!
  Run keyword If  ${identifiersClosed}  Execute Javascript  $('div#application-info-tab [data-test-id=toggle-identifiers-${doc}]')[${idx}-1].click();
  ${opDescriptionXpath}=  Set Variable  //div[@id='application-info-tab']//section[@data-doc-id='${docId}']//input[@data-test-id='op-description-editor-${doc}']
  Wait until element is visible  ${opDescriptionXpath}
  Input text   ${opDescriptionXpath}  ${text}
  # Blur
  Focus  xpath=//div[@id='application-info-tab']//section[@data-doc-id='${docId}']//button[@data-test-id='toggle-identifiers-${doc}']
  # Close the input
  Execute Javascript  $('div#application-info-tab [data-test-id=toggle-identifiers-${doc}]')[${idx}-1].click();
  Wait until  Element should not be visible  ${opDescriptionXpath}

# This only works if there is only one applicable document.
Operation description is
  [Arguments]  ${doc}  ${text}
  Wait until  Element Should Contain  xpath=//div[@id='application-info-tab']//span[@data-test-id='${doc}-accordion-description-text']  ${text}

Input building identifier
  [Arguments]  ${doc}  ${text}  ${idx}=1
  Wait until   Element should be visible  xpath=//div[@id='application-info-tab']//section[@data-doc-type='${doc}'][${idx}]//button[@data-test-id='toggle-identifiers-${doc}']
  ${docId}=  Get Element Attribute  xpath=//div[@id='application-info-tab']//section[@data-doc-type='${doc}'][${idx}]@data-doc-id
  ${identifiersClosed} =  Get identifiers closed  ${docId}
  # for jQuery ${idx}-1 because xpath indeces start from 1!
  Run keyword If  ${identifiersClosed}  Execute Javascript  $('div#application-info-tab [data-test-id=toggle-identifiers-${doc}]')[${idx}-1].click();
  Wait until element is visible  jquery=div#application-info-tab input[data-test-id=${docId}-identifier-input]
  Input text by test id  ${docId}-identifier-input  ${text}
  # Close the input
  Execute Javascript  $('div#application-info-tab [data-test-id=toggle-identifiers-${doc}]')[${idx}-1].click();
  Wait until element is not visible  jquery=div#application-info-tab input[data-test-id=${docId}-identifier-input]

Table with id should have rowcount
  [Arguments]  ${id}  ${expectedRowcount}
  ${rowcount}=  Get Matching XPath Count  //table[@id='${id}']/tbody/tr
  Should be equal  ${rowcount}  ${expectedRowcount}

#
# Helper for inforequest and application crud operations:
#

Create application the fast way
  [Arguments]  ${address}  ${propertyId}  ${operation}
  Go to  ${CREATE URL}&address=${address}&propertyId=${propertyId}&operation=${operation}&x=360603.153&y=6734222.95
  Wait until  Element Text Should Be  xpath=//section[@id='application']//span[@data-test-id='application-property-id']  ${propertyId}
  Kill dev-box

Create application with state
  [Arguments]  ${address}  ${propertyId}  ${operation}  ${state}
  Go to  ${CREATE URL}&address=${address}&propertyId=${propertyId}&operation=${operation}&state=${state}&x=360603.153&y=6734222.95
  Wait until  Element Text Should Be  xpath=//section[@id='application']//span[@data-test-id='application-property-id']  ${propertyId}
  Kill dev-box

Create bulletins the fast way
  [Arguments]  ${count}
  Go to  ${CREATE BULLETIN URL}?count=${count}

Create inforequest the fast way
  [Arguments]  ${address}  ${x}  ${y}   ${propertyId}  ${operation}  ${message}
  Go to  ${CREATE URL}&infoRequest=true&address=${address}&propertyId=${propertyId}&operation=${operation}&x=${x}&y=${y}&message=${message}
  Wait until  Element Text Should Be  xpath=//section[@id='inforequest']//span[@data-test-id='inforequest-property-id']  ${propertyId}
  Kill dev-box

Create inforequest with state
  [Arguments]  ${address}  ${propertyId}  ${operation}  ${state}
  Go to  ${CREATE URL}&infoRequest=true&address=${address}&propertyId=${propertyId}&operation=${operation}&state=${state}&x=360603.153&y=6734222.95
  Wait until  Element Text Should Be  xpath=//section[@id='inforequest']//span[@data-test-id='inforequest-property-id']  ${propertyId}
  Kill dev-box

Create application
  [Arguments]  ${address}  ${municipality}  ${propertyId}  ${permitType}
  Prepare new request  ${address}  ${municipality}  ${propertyId}  ${permitType}
  Click by test id  create-application
  Wait Until  Element should be visible  application
  Wait Until  Element Text Should Be  xpath=//span[@data-test-id='application-property-id']  ${propertyId}

Create first application
  [Arguments]  ${address}  ${municipality}  ${propertyId}  ${permitType}
  Prepare first request  ${address}  ${municipality}  ${propertyId}  ${permitType}
  Click by test id  create-application
  Wait Until  Element should be visible  application
  Wait Until  Element Text Should Be  xpath=//span[@data-test-id='application-property-id']  ${propertyId}

Create inforequest
  [Arguments]  ${address}  ${municipality}  ${propertyId}  ${message}  ${permitType}
  Do create inforequest  false  ${address}  ${municipality}  ${propertyId}  ${message}  ${permitType}

Create first inforequest
  [Arguments]  ${address}  ${municipality}  ${propertyId}  ${message}  ${permitType}
  Do create inforequest  true  ${address}  ${municipality}  ${propertyId}  ${message}  ${permitType}

Do create inforequest
  [Arguments]  ${isFirstInforequest}  ${address}  ${municipality}  ${propertyId}  ${message}  ${permitType}
  Run Keyword If  '${isFirstInforequest}' == 'true'  Prepare first request  ${address}  ${municipality}  ${propertyId}  ${permitType}
  ...  ELSE  Prepare new request  ${address}  ${municipality}  ${propertyId}  ${permitType}
  Click by test id  create-proceed-to-inforequest
  # Needed for animation to finish.
  Wait until page contains element  xpath=//textarea[@data-test-id="create-inforequest-message"]
  Wait until  Element should be visible  xpath=//textarea[@data-test-id="create-inforequest-message"]
  Input text  xpath=//textarea[@data-test-id="create-inforequest-message"]  ${message}
  Click by test id  create-inforequest
  Confirm  dynamic-ok-confirm-dialog
  Wait Until  Element should be visible  inforequest
  Wait Until  Element Text Should Be  xpath=//span[@data-test-id='inforequest-property-id']  ${propertyId}

Prepare new request
  [Arguments]  ${address}  ${municipality}  ${propertyId}  ${permitType}
  Go to page  applications
  Click by test id  applications-create-new-application
  Do prepare new request  ${address}  ${municipality}  ${propertyId}  ${permitType}

Prepare first request
  [Arguments]  ${address}  ${municipality}  ${propertyId}  ${permitType}
  Go to page  applications
  Click by test id  applications-create-new-inforequest
  Do prepare new request  ${address}  ${municipality}  ${propertyId}  ${permitType}

Selected Municipality Is
  [Arguments]  ${municipality}
  ${selectedMuni} =  Get Element Attribute  xpath=//div[@id="popup-id"]//span[@data-test-id='create-municipality-select']@data-test-value
  Should Be Equal  ${selectedMuni}  ${municipality}

Address is not blank
  ${address} =  Get Element Attribute  xpath=//div[@id="popup-id"]//input[@data-test-id='create-address']@value
  Should Not Be Equal As Strings  ${address}  ${EMPTY}

Do prepare new request
  [Arguments]  ${address}  ${municipality}  ${propertyId}  ${permitType}
  Input Text  create-search  ${propertyId}
  Click enabled by test id  create-search-button
  Wait until  Element should be visible  xpath=//div[@id='popup-id']//input[@data-test-id='create-property-id']
  Textfield Value Should Be  xpath=//div[@id='popup-id']//input[@data-test-id='create-property-id']  ${propertyId}
  Wait Until  Selected Municipality Is  ${municipality}
  Wait Until  Address is not blank
  Execute Javascript  $("div[id='popup-id'] input[data-test-id='create-address']").val("${address}").change();
  Set animations off

  ${path} =   Set Variable  xpath=//div[@id="popup-id"]//button[@data-test-id="create-continue"]
  Wait until  Element should be enabled  ${path}
  Click element  ${path}

  Select operation path by permit type  ${permitType}
  Wait until  Element should be visible  xpath=//section[@id="create-part-2"]//div[@class="tree-content"]//*[@data-test-id="create-application"]
  Set animations on

Add empty attachment template
  [Arguments]  ${templateName}  ${topCategory}  ${subCategory}
  Click enabled by test id  add-attachment-templates
  Wait Until Element Is Visible  jquery=div#dialog-add-attachment-templates-v2 input[data-test-id=selectm-filter-input]
  Input Text  jquery=div#dialog-add-attachment-templates-v2 input[data-test-id=selectm-filter-input]  ${templateName}
  List Should Have No Selections  jquery=div#dialog-add-attachment-templates-v2 select[data-test-id=selectm-source-list]
  Click Element  jquery=div#dialog-add-attachment-templates-v2 select[data-test-id=selectm-source-list] option:contains('${templateName}')
  Click Element  jquery=div#dialog-add-attachment-templates-v2 button[data-test-id=selectm-add]
  Click Element  jquery=div#dialog-add-attachment-templates-v2 button[data-test-id=selectm-ok]
  Wait Until  Element Should Not Be Visible  jquery=div#dialog-add-attachment-templates-v2 input[data-test-id=selectm-filter-input]
  Wait Until Element Is Visible  jquery=div#application-attachments-tab tr[data-test-type="${topCategory}.${subCategory}"]

Add attachment
  [Arguments]  ${kind}  ${path}  ${description}  ${type}=muut.muu  ${operation}=
  Run Keyword If  '${kind}' == 'application'  Click enabled by test id  add-attachment
  Run Keyword If  '${kind}' == 'inforequest'  Click enabled by test id  add-inforequest-attachment
  Run Keyword If  '${kind}' == 'verdict'  Click enabled by test id  add-targetted-attachment
  Run Keyword If  '${kind}' == 'statement'  Click enabled by test id  add-statement-attachment

  Wait until  Element should be visible  upload-dialog

  Select Frame      uploadFrame
  Wait until        Element should be visible  test-save-new-attachment

  Run Keyword If  '${kind}' == 'application'  Set attachment type for upload  ${type}
  Run Keyword If  '${kind}' == 'application' and $operation  Wait until  Page should contain element  xpath=//form[@id='attachmentUploadForm']//option[text()='${operation}']
  Run Keyword If  '${kind}' == 'application' and $operation  Select From List  attachmentOperation  ${operation}

  Input text        text  ${description}
  Wait until        Page should contain element  xpath=//form[@id='attachmentUploadForm']/input[@type='file']
  Focus             xpath=//form[@id='attachmentUploadForm']/input[@type='file']
  Choose File       xpath=//form[@id='attachmentUploadForm']/input[@type='file']  ${path}
  Click element     test-save-new-attachment
  Unselect Frame
  Wait until  Element should not be visible  upload-dialog
  Run Keyword If  '${kind}' == 'application'  Wait Until  Element Should Be Visible  jquery=section[id=attachment] a[data-test-id=back-to-application-from-attachment]
  Run Keyword If  '${kind}' == 'inforequest'  Wait Until Page Contains  ${description}

Return to application
  Wait Until  Click by test id  back-to-application-from-attachment

Delete attachment
  [Arguments]  ${type}
  Scroll to  tr[data-test-type='${type}'] button[data-test-icon='delete-button']
  Click element  jquery=tr[data-test-type='${type}'] button[data-test-icon='delete-button']
  Confirm yes no dialog

Set attachment type for upload
  [Arguments]  ${type}
  Wait until  Page should contain element  xpath=//form[@id='attachmentUploadForm']//option[@value='${type}']
  Select From List  attachmentType  ${type}

Open attachment details
  [Arguments]  ${type}  ${nth}=0
  Open tab  attachments
  ${selector} =  Set Variable  $("div#application-attachments-tab tr[data-test-type='${type}'] a[data-test-id=open-attachment]:visible")
  # 'Click Element' is broken in Selenium 2.35/FF 23 on Windows, using jQuery instead
  Wait For Condition  return ${selector}.length>0;  10
  Execute Javascript  ${selector}[${nth}].click();
  Wait Until  Element Should Be Visible  jquery=section[id=attachment] a[data-test-id=back-to-application-from-attachment]

Click not needed
  [Arguments]  ${type}
  ${selector} =  Set Variable  div#application-attachments-tab tr[data-test-type='${type}'] label[data-test-id=not-needed-label]
  Wait until  Element should be visible  jquery=${selector}
  Scroll to  ${selector}
  Click element  jquery=${selector}

Attachment indicator icon should be visible
  [Arguments]  ${icon}  ${type}
  Element should be visible  jquery=table.attachments-table tr[data-test-type='${type}'] i[data-test-icon=${icon}-icon]

Attachment indicator icon should not be visible
  [Arguments]  ${icon}  ${type}
  Element should not be visible  jquery=table.attachments-table tr[data-test-type='${type}'] i[data-test-icon=${icon}-icon]

Assert file latest version
  [Arguments]  ${name}  ${versionNumber}
  Wait Until  Element Should Be Visible  test-attachment-file-name
  Wait Until Page Contains  ${PNG_TESTFILE_NAME}
  Element Text Should Be  test-attachment-file-name  ${name}
  Element Text Should Be  test-attachment-version  ${versionNumber}

Attachment file upload
  [Arguments]  ${path}
  Wait Until     Element should be visible  uploadFrame
  Select Frame   uploadFrame
  Wait until     Element should be visible  test-save-new-attachment
  Wait until     Page should contain element  xpath=//form[@id='attachmentUploadForm']/input[@type='file']
  Focus          xpath=//form[@id='attachmentUploadForm']/input[@type='file']
  Choose File    xpath=//form[@id='attachmentUploadForm']/input[@type='file']  ${path}
  # Had to use 'Select Frame' another time to be able to use e.g. 'Element Should Be Enabled'
  # Select Frame   uploadFrame
  # Wait Until     Element Should Be Enabled  test-save-new-attachment
  Click element  test-save-new-attachment
  Unselect Frame
  ${path}  ${filename}=  Split Path  ${path}
  Wait until     Element Text Should Be  xpath=//section[@id='attachment']//span[@id='test-attachment-file-name']/a  ${filename}

# Add file version from attachment details
Add attachment version
  [Arguments]  ${path}
  Wait Until     Element should be visible  xpath=//button[@id="add-new-attachment-version"]
  Click Element  xpath=//button[@id="add-new-attachment-version"]
  Attachment file upload  ${path}

# Add the first file to template from attachments view
Add attachment file
  [Arguments]  ${row}  ${path}
  Wait Until     Element should be visible  jquery=${row}
  Scroll and click  ${row} a[data-test-id=add-attachment-file]
  Attachment file upload  ${path}


Open attachments tab and unselect post verdict filter
  Open tab  attachments
  Checkbox wrapper selected by test id  postVerdict-filter-checkbox
  Scroll and click test id  postVerdict-filter-label

Select operation path by permit type
  [Arguments]  ${permitType}
  Run Keyword If  '${permitType}' == 'R'  Select operations path R
  ...  ELSE IF  '${permitType}' == 'YA-kaivulupa'  Select operations path YA kayttolupa kaivu
  ...  ELSE IF  '${permitType}' == 'YA-kayttolupa'  Select operations path YA kayttolupa
  ...  ELSE IF  '${permitType}' == 'YA-kayttolupa-mainostus-viitoitus'  Select operations path YA kayttolupa mainostus-viitoitus
  ...  ELSE IF  '${permitType}' == 'YA-sijoituslupa'  Select operations path YA sijoituslupa
  ...  ELSE  Select operations path R

Select operations path R
  Click tree item by text  "Rakentaminen, purkaminen tai maisemaan vaikuttava toimenpide"
  Click tree item by text  "Uuden rakennuksen rakentaminen"
  Click tree item by text  "Asuinkerrostalon tai rivitalon rakentaminen"

Select operations path YA kayttolupa
  Click tree item by text  "Yleiset alueet (Sijoittamissopimus, katulupa, alueiden käyttö)"
  Click tree item by text  "Yleisten alueiden tai muiden kunnan omistamien alueiden käyttö (tapahtumat, mainokset, yms.)"
  Click tree item by text  "Terassin sijoittaminen"

Select operations path YA kayttolupa kaivu
  Click tree item by text  "Yleiset alueet (Sijoittamissopimus, katulupa, alueiden käyttö)"
  Click tree item by text  "Työskentely yleisellä alueella (Katulupa)"
  Click tree item by text  "Kaivaminen yleisellä alueella"
  Click tree item by text  "Vesi- ja viemäritöiden tekeminen"

Select operations path YA kayttolupa mainostus-viitoitus
  Click tree item by text  "Yleiset alueet (Sijoittamissopimus, katulupa, alueiden käyttö)"
  Click tree item by text  "Yleisten alueiden tai muiden kunnan omistamien alueiden käyttö (tapahtumat, mainokset, yms.)"
  Click tree item by text  "Mainoslaitteiden ja opasteviittojen sijoittaminen"

Select operations path YA sijoituslupa
  Click tree item by text  "Yleiset alueet (Sijoittamissopimus, katulupa, alueiden käyttö)"
  Click tree item by text  "Rakenteiden sijoittaminen yleiselle alueelle (Sijoittamissopimus)"
  Click tree item by text  "Pysyvien maanalaisten rakenteiden sijoittaminen"
  Click tree item by text  "Vesi- ja viemärijohtojen sijoittaminen"

Click tree item by text
  [Arguments]  ${itemName}
  Wait and click  //section[@id="create-part-2"]//div[@class="tree-content"]//*[text()=${itemName}]


# Cancel application or inforequest

Close current inforequest
  Wait Until  Element Should Be Enabled  xpath=//button[@data-test-id="inforequest-cancel-btn"]
  Click enabled by test id  inforequest-cancel-btn
  Confirm  dynamic-yes-no-confirm-dialog

Cancel current application
  [Arguments]  ${reason}=${EMPTY}
  Wait Until  Element Should Be Enabled  xpath=//button[@data-test-id="application-cancel-btn"]
  Click enabled by test id  application-cancel-btn
  Fill test id  cancel-application-reason  ${reason}
  Confirm  dialog-cancel-application

Cancel current application as authority
  [Arguments]  ${reason}=${EMPTY}
  Wait Until  Element Should Be Enabled  xpath=//button[@data-test-id="application-cancel-authority-btn"]
  Click enabled by test id  application-cancel-authority-btn
  Fill test id  cancel-application-reason  ${reason}
  Confirm  dialog-cancel-application

# New yes no modal dialog
Confirm yes no dialog
  Wait until  Element should be visible  xpath=//div[@id="modal-dialog"]//button[@data-test-id="confirm-yes"]
  Focus  xpath=//div[@id="modal-dialog"]//button[@data-test-id="confirm-yes"]
  Click Element  xpath=//div[@id="modal-dialog"]//button[@data-test-id="confirm-yes"]
  Wait Until  Element Should Not Be Visible  xpath=//div[@id="modal-dialog"]//button[@data-test-id="confirm-yes"]

Deny yes no dialog
  Wait until  Element should be visible  xpath=//div[@id="modal-dialog"]//button[@data-test-id="confirm-no"]
  Focus  xpath=//div[@id="modal-dialog"]//button[@data-test-id="confirm-no"]
  Click Element  xpath=//div[@id="modal-dialog"]//button[@data-test-id="confirm-no"]
  Wait Until  Element Should Not Be Visible  xpath=//div[@id="modal-dialog"]//button[@data-test-id="confirm-no"]

Confirm ok dialog
  Wait until  Element should be visible  xpath=//div[@id="modal-dialog"]//button[@data-test-id="ok-button"]
  Focus  xpath=//div[@id="modal-dialog"]//button[@data-test-id="ok-button"]
  Click Element  xpath=//div[@id="modal-dialog"]//button[@data-test-id="ok-button"]
  Wait Until  Element Should Not Be Visible  xpath=//div[@id="modal-dialog"]//button[@data-test-id="ok-button"]

Confirm
  [Arguments]  ${modalId}
  Wait until  Element should be visible  xpath=//div[@id="${modalId}"]//button[@data-test-id="confirm-yes"]
  Focus  xpath=//div[@id="${modalId}"]//button[@data-test-id="confirm-yes"]
  Click Element  xpath=//div[@id="${modalId}"]//button[@data-test-id="confirm-yes"]
  Wait Until  Element Should Not Be Visible  ${modalId}

Deny
  [Arguments]  ${modalId}
  Wait until  Element should be visible  xpath=//div[@id="${modalId}"]//button[@data-test-id="confirm-no"]
  Focus  xpath=//div[@id="${modalId}"]//button[@data-test-id="confirm-no"]
  Click Element  xpath=//div[@id="${modalId}"]//button[@data-test-id="confirm-no"]
  Wait Until  Element Should Not Be Visible  xpath=//div[@id="${modalId}"]//button[@data-test-id="confirm-no"]

It is possible to add operation
  Wait until  Element should be visible  xpath=//button[@data-test-id="add-operation"]

Submit application
  Open tab  requiredFieldSummary
  Test id enabled  application-submit-btn
  Scroll and click test id  application-submit-btn
  Confirm  dynamic-yes-no-confirm-dialog
  Wait until  Application state should be  submitted

Submit application errors count is
  [Arguments]  ${count}
  Wait until  Xpath Should Match X Times  //div[@data-test-id='submit-errors-container']//div[contains(@class,'info-line')]  ${count}

Submit application error should be
  [Arguments]  ${errorText}
  Wait until  Element should be visible  //div[@data-test-id='submit-errors-container']
  ${attrValue}=  Get Element Attribute  xpath=(//div[@data-test-id='submit-errors-container']//span)@data-submit-error
  Should Be Equal As Strings  ${errorText}  ${attrValue}

Approve application ok
  Click enabled by test id  approve-application
  Confirm ok dialog
  Wait until  Application state should be  sent

Approve application yes
  Click enabled by test id  approve-application
  Confirm yes no dialog
  Wait until  Application state should be  sent

Notification dialog should be open
  Wait until  Element should be visible  xpath=//div[@id="modal-dialog"]//button[@data-test-id="ok-button"]

Confirm notification dialog
  Wait until  Element should be visible  xpath=//div[@id="modal-dialog"]//button[@data-test-id="ok-button"]
  Focus  xpath=//div[@id="modal-dialog"]//button[@data-test-id="ok-button"]
  Click Element  xpath=//div[@id="modal-dialog"]//button[@data-test-id="ok-button"]
  Wait Until  Element Should Not Be Visible  xpath=//div[@id="modal-dialog"]//button[@data-test-id="ok-button"]

#
# Jump to application or inforequest:
#

Open the request
  [Arguments]  ${address}  ${tab}=all
  Go to page  applications
  Click by test id  search-tab-${tab}
  Wait until  Click element  xpath=//table[@id='applications-list']//tr[@data-test-address='${address}']
  Wait for jQuery

Open the request at index
  [Arguments]  ${address}  ${index}
  Go to page  applications
  Wait until  Click element  xpath=//table[@id='applications-list']//tr[@data-test-address='${address}'][${index}]
  Wait for jQuery

Open application
  [Arguments]  ${address}  ${propertyId}
  Open the request  ${address}
  Wait until  Element Should Be Visible  application
  Wait until  Element Text Should Be  xpath=//section[@id='application']//span[@data-test-id='application-property-id']  ${propertyId}

Open canceled application
  [Arguments]  ${address}  ${propertyId}
  Open the request  ${address}  canceled
  Wait until  Element Should Be Visible  application
  Wait until  Element Text Should Be  xpath=//section[@id='application']//span[@data-test-id='application-property-id']  ${propertyId}

Open inforequest
  [Arguments]  ${address}  ${propertyId}
  Open the request  ${address}
  Wait until  Element Should Be Visible  inforequest
  Wait until  Element Text Should Be  xpath=//section[@id='inforequest']//span[@data-test-id='inforequest-property-id']  ${propertyId}

Request should be visible
  [Arguments]  ${address}
  Wait Until  Element should be visible  xpath=//table[@id='applications-list']//tr[@data-test-address='${address}']

Request should not be visible
  [Arguments]  ${address}
  Wait Until  Element should not be visible  xpath=//table[@id='applications-list']//tr[@data-test-address='${address}']

Active search tab is
  [Arguments]  ${tab}
  Wait until  Element should be visible  jquery=li.active[data-test-id=search-tab-${tab}]

Open search tab
  [Arguments]  ${tab}
  Wait until  Element should be visible  xpath=//section[@id='applications']//li[@data-test-id='search-tab-${tab}']
  Click by test id  search-tab-${tab}

Show all applications
  ${tab}=  Run Keyword and Return Status  Wait test id visible  search-tab-all
  Run Keyword If  ${tab}  Scroll and click test id  search-tab-all


#
# Comments:
#

Add comment
  [Arguments]  ${message}
  Open side panel  conversation
  Input text  xpath=//div[@id='conversation-panel']//textarea[@data-test-id='application-new-comment-text']  ${message}
  Click by test id  application-new-comment-btn
  Wait until  Element should be visible  xpath=//div[@id='conversation-panel']//div[@data-test-id='comments-table']//span[text()='${message}']
  Close side panel  conversation

Check comment
  [Arguments]  ${message}
  Open side panel  conversation
  Wait until  Element should be visible  xpath=//div[@id='conversation-panel']//div[@data-test-id='comments-table']//span[contains(., '${message}')]
  Close side panel  conversation

Open to authorities
  [Arguments]  ${message}
  Open side panel  conversation
  Input text  xpath=//div[@id='conversation-panel']//textarea[@data-test-id='application-new-comment-text']  ${message}
  Click by test id  application-open-application-btn
  Wait until  Element should be visible  xpath=//div[@id='conversation-panel']//div[@data-test-id='comments-table']//span[text()='${message}']
  Close side panel  conversation

Input comment
  [Arguments]  ${message}
  Open side panel  conversation
  Wait Until  Input text  xpath=//div[@id='conversation-panel']//textarea[@data-test-id='application-new-comment-text']  ${message}
  Wait Until  Click element  xpath=//div[@id='conversation-panel']//button[@data-test-id='application-new-comment-btn']
  Wait until  Element should be visible  xpath=//div[@id='conversation-panel']//div[contains(@class,'is-comment')]//span[text()='${message}']
  Close side panel  conversation

Input inforequest comment
  [Arguments]  ${message}
  Input text  xpath=//section[@id='inforequest']//textarea[@data-test-id='application-new-comment-text']  ${message}
  Click element  xpath=//section[@id='inforequest']//button[@data-test-id='application-new-comment-btn']
  Wait until  Element should be visible  xpath=//section[@id='inforequest']//div[contains(@class,'is-comment')]//span[text()='${message}']

Input comment and open to authorities
  [Arguments]  ${message}
  Open side panel  conversation
  Input text  xpath=//div[@id='conversation-panel']//textarea[@data-test-id='application-new-comment-text']  ${message}
  Click element  xpath=//div[@id='conversation-panel']//button[@data-test-id='application-open-application-btn']
  Wait until  Element should be visible  xpath=//div[@id='conversation-panel']//div[contains(@class,'is-comment')]//span[text()='${message}']
  Close side panel  conversation

Input comment and mark answered
  [Arguments]  ${message}
  Input text  xpath=//section[@id='inforequest']//textarea[@data-test-id='application-new-comment-text']  ${message}
  Click element  xpath=//section[@id='inforequest']//button[@data-test-id='comment-request-mark-answered']
  Confirm ok dialog
  Wait until  Element should be visible  xpath=//section[@id='inforequest']//div[contains(@class,'is-comment')]//span[text()='${message}']

Mark answered
  Click element  xpath=//section[@id='inforequest']//button[@data-test-id='comment-request-mark-answered']
  Confirm notification dialog

Comment count is
  [Arguments]  ${amount}
  Open side panel  conversation
  Wait until  Xpath Should Match X Times  //div[@id='conversation-panel']//div[contains(@class,'is-comment')]  ${amount}
  Close side panel  conversation

#
# Invites
#

Is authorized party
  # Party can be either email or username
  [Arguments]  ${party}
  Wait Until  Element Should Be Visible  xpath=//section[@id='application']//div[@class='parties-list']//table/tbody//td[contains(., '${party}')]

Fill application person invite bubble
  [Arguments]  ${email}  ${message}
  Element should be visible  xpath=//button[@data-test-id='application-invite-person']
  Click by test id  application-invite-person
  Test id disabled  person-invite-bubble-dialog-ok
  Fill test id  person-invite-email  ${email}
  Fill test id  person-invite-message  Tervetuloa muokkaamaan hakemusta
  Test id enabled  person-invite-bubble-dialog-ok

Invite ${email} to application
  Open tab  parties
  ${invites_count}=  Get Matching Xpath Count  //div[@class='parties-list']/table//tr[@class='party']
  Fill application person invite bubble  ${email}  Tervetuloa muokkaamaan hakemusta
  Scroll and click test id  person-invite-bubble-dialog-ok
  Wait test id hidden  person-invite-bubble-dialog-ok
  Wait Until  Element Should Be Visible  xpath=//div[@class='parties-list']//tr[@class='party'][${invites_count} + 1]
  ${email_found}=  Run Keyword And Return Status  Is authorized party  ${email}
  # If specified email was not found from auths, try to parse username from the email and test if username exists (case of pena)
  ${username}=  Fetch From Left  ${email}  @
  Run Keyword Unless  ${email_found}  Is authorized party  ${username}

Invite count is
  [Arguments]  ${amount}
  Wait Until  Xpath Should Match X Times  //*[@class='user-invite']  ${amount}

#
# Authority admin
#

Invite company to application
  [Arguments]  ${company}
  Open tab  parties
  Scroll and click test id  application-invite-company
  Wait test id visible  company-invite-bubble-dialog-ok
  Select From Autocomplete  div[@data-test-id="company-invite-companies"]  ${company}
  Scroll and click test id  company-invite-bubble-dialog-ok
  Is authorized party  ${company}

#
# Tasks
#

Task count is
  [Arguments]  ${type}  ${amount}
  Wait until  Xpath Should Match X Times  //table//tbody/tr[@data-test-type="${type}"]  ${amount}

Task state count is
  [Arguments]  ${type}  ${state}  ${amount}
  Wait until  Xpath Should Match X Times  //table//tbody/tr[@data-test-type="${type}"]//i[@data-test-state="${state}"]  ${amount}

Foreman count is
  [Arguments]  ${amount}
  Wait until  Xpath Should Match X Times  //table[@class="tasks-foreman"]/tbody/tr  ${amount}

#
# Quick, jettison the db...
#

Apply minimal fixture now
  Go to  ${FIXTURE URL}/minimal
  Wait until  Page should contain  true
  Go to login page

#
# Application state check:
#

Application state should be
  [Arguments]  ${state}
  ${s} =  Get Element Attribute  xpath=//div[@data-test-id='application-state']@data-test-state
  Should be equal  ${s}  ${state}

Permit type should be
  [Arguments]  ${type}
  Element Text Should Be  xpath=//span[@data-bind='ltext: permitType']  ${type}

Application address should be
  [Arguments]  ${address}
  ${a} =  Convert To Uppercase  ${address}
  Wait Until  Element Should Be Visible  xpath=//section[@id='application']//span[@data-test-id='application-title']
  Wait Until  Element text should be  xpath=//section[@id='application']//span[@data-test-id='application-title']  ${a}

Neighbor application address should be
  [Arguments]  ${address}
  ${a} =  Convert To Uppercase  ${address}
  Wait Until  Element Should Be Visible  xpath=//section[@id='neighbor-show']//span[@data-test-id='application-title']
  Wait Until  Element text should be  xpath=//section[@id='neighbor-show']//span[@data-test-id='application-title']  ${a}


#
# Proxy control:
#

Enable maps
  Execute Javascript  ajax.command("set-feature",{feature:"maps-disabled",value:false}).call();
  Wait for jQuery

Set integration proxy on
  Execute Javascript  ajax.post("/api/proxy-ctrl/on").call();
  Wait for jQuery
  Execute Javascript  ajax.command("set-feature", {feature: "disable-ktj-on-create", value:false}).call();
  Wait for jQuery

Disable maps
  Execute Javascript  ajax.command("set-feature", {feature: "maps-disabled", value:true}).call();
  Wait for jQuery

Set integration proxy off
  Execute Javascript  ajax.post("/api/proxy-ctrl/off").call();
  Wait for jQuery
  Execute Javascript  ajax.command("set-feature", {feature: "disable-ktj-on-create", value:true}).call();
  Wait for jQuery

#
# Animations control:
#

Set animations on
  Execute Javascript  tree.animation(true);

Set animations off
  Execute Javascript  tree.animation(false);

#
# Neighbor
#

Add neighbor
  [Arguments]  ${propertyId}  ${name}  ${email}
  Click enabled by test id  manager-neighbors-add
  Wait Until   Element Should Be Visible  xpath=//*[@data-test-id='modal-dialog-content']
  Input text by test id  neighbors.edit.propertyId  ${propertyId}
  Input text by test id  neighbors.edit.name  ${name}
  Input text by test id  neighbors.edit.email  ${email}
  Click by test id  modal-dialog-submit-button
  Wait Until  Element Should Not Be Visible  xpath=//*[@data-test-id='modal-dialog-content']
  Wait Until  Page Should Contain  ${email}

#
# Verdict
#

Go to give new verdict
  Open tab  verdict
  Click enabled by test id  give-verdict
  Wait Until  Element Should Be Visible  backend-id
  Wait Until  Element Should Be Enabled  backend-id

Input verdict
  [Arguments]  ${backend-id}  ${verdict-type-select-value}  ${verdict-given-date}  ${verdict-official-date}  ${verdict-giver-name}
  ## Disable date picker
  Execute JavaScript  $(".hasDatepicker").unbind("focus");
  Input text  backend-id  ${backend-id}
  Select From List By Value  verdict-type-select  ${verdict-type-select-value}
  Input text  verdict-given  ${verdict-given-date}
  Input text  verdict-official  ${verdict-official-date}
  Input text  verdict-name  ${verdict-giver-name}
  Select Checkbox  verdict-agreement
  ## Trigger change manually
  Execute JavaScript  $("#backend-id").change();
  Execute JavaScript  $("#verdict-type-select").change();
  Execute JavaScript  $("#verdict-given").change();
  Execute JavaScript  $("#verdict-official").change();
  Execute JavaScript  $("#verdict-name").change();

Submit empty verdict
  [Arguments]  ${targetState}=verdictGiven  ${targetStatus}=6
  Go to give new verdict
  Input verdict  -  ${targetStatus}  01.05.2018  01.06.2018  -
  Click enabled by test id  verdict-publish
  Confirm  dynamic-yes-no-confirm-dialog
  Wait for jQuery
  Wait until  Application state should be  ${targetState}

Do fetch verdict
  [Arguments]  ${fetchConfirmationText}
  Click enabled by test id  fetch-verdict
  Wait for jQuery
  Wait Until  Element Should Be Visible  dynamic-ok-confirm-dialog
  Element Text Should Be  xpath=//div[@id='dynamic-ok-confirm-dialog']//div[@class='dialog-user-content']/p  ${fetchConfirmationText}
  Confirm  dynamic-ok-confirm-dialog

Fetch verdict
  Do fetch verdict  Taustajärjestelmästä haettiin 2 kuntalupatunnukseen liittyvät tiedot. Tiedoista muodostettiin 9 uutta vaatimusta Rakentaminen-välilehdelle.
  Verdict is given  2013-01  0

Fetch YA verdict
  Do fetch verdict  Taustajärjestelmästä haettiin 1 kuntalupatunnukseen liittyvät tiedot. Tiedoista muodostettiin 2 uutta vaatimusta Rakentaminen-välilehdelle.
  Verdict is given  523  0

Verdict is given
  [Arguments]  ${kuntalupatunnus}  ${i}
  Wait until  Element should be visible  application-verdict-details
  Wait until  Element text should be  //div[@id='application-verdict-tab']//h2//*[@data-test-id='given-verdict-id-${i}']  ${kuntalupatunnus}


# User management

Fill in new password
  [Arguments]  ${section}  ${password}
  Wait Until  Page Should Contain  Salasanan vaihtaminen
  Input text  xpath=//section[@id='${section}']//input[@placeholder='Uusi salasana']  ${password}
  Element Should Be Disabled  xpath=//section[@id='${section}']//button
  Input text  xpath=//section[@id='${section}']//input[@placeholder='Salasana uudelleen']  ${password}
  Wait Until  Element Should Be Enabled  xpath=//section[@id='${section}']//button
  Click Element  xpath=//section[@id='${section}']//button
  Wait Until  Page should contain  Salasana asetettu.
  Confirm notification dialog

Open company user listing
  Click Element  user-name
  Open accordion by test id  mypage-company-accordion
  Wait Until  Element should be visible  //div[@data-test-id='my-company']
  Click by test id  company-edit-users
  Wait until  Element should be visible  company

Open company details
  Click Element  user-name
  Open accordion by test id  mypage-company-accordion
  Wait Until  Element should be visible  //div[@data-test-id='my-company']
  Click by test id  company-edit-info
  Wait until  Element should be visible  company


#
# Mock Ajax calls: jquery.mockjax
#

Mock query
  [Arguments]  ${name}  ${jsonResponse}
  Execute Javascript  $.mockjax({url:'/api/query/${name}', dataType:'json', responseText: ${jsonResponse}});

Mock query error
  [Arguments]  ${name}
  Execute Javascript  $.mockjax({url:'/api/query/${name}', dataType:'json', responseText: {"ok":false, "text":"error.unknown"}});

Mock command
  [Arguments]  ${name}  ${jsonResponse}
  Execute Javascript  $.mockjax({url:'/api/command/${name}', type: 'POST', dataType:'json', responseText: ${jsonResponse}});

Mock datatcommandables error
  [Arguments]  ${name}
  Execute Javascript  $.mockjax({url:'/api/command/${name}', type: 'POST', dataType:'json', responseText: {"ok":false, "text":"error.unknown"}});

Mock datatables
  [Arguments]  ${name}  ${jsonResponse}
  Execute Javascript  $.mockjax({url:'/api/datatables/${name}', type: 'POST', dataType:'json', responseText: ${jsonResponse}});

Mock datatables error
  [Arguments]  ${name}
  Execute Javascript  $.mockjax({url:'/api/datatables/${name}', type: 'POST', dataType:'json', responseText: {"ok":false, "text":"error.unknown"}});

Mock proxy
  [Arguments]  ${name}  ${jsonResponse}
  Execute Javascript  $.mockjax({url:'/proxy/${name}', dataType:'json', responseText: ${jsonResponse}});

Mock proxy error
  [Arguments]  ${name}
  Execute Javascript  $.mockjax({url:'/proxy/${name}', dataType:'json', status:500});

Clear mocks
  Execute Javascript  $.mockjaxClear();

# -------------------------------
# Selector arguments for scroll keywords are jQuery selector without jquery= part.
# -------------------------------

Scroll to
  [Arguments]  ${selector}
  Wait Until  Execute Javascript  $("${selector}")[0].scrollIntoView(false);

Scroll to top
  Execute javascript  window.scrollTo(0,0)

Scroll to bottom
  Execute javascript  window.scrollTo(0,888888)

Scroll to test id
  [Arguments]  ${id}
  Scroll to  [data-test-id=${id}]

Scroll and click
  [Arguments]  ${selector}
  Scroll to  ${selector}
  Click Element  jquery=${selector}

Scroll and click input
  [Arguments]  ${selector}
  Scroll to  ${selector}
  Element should be enabled  jquery=${selector}
  Click Element  jquery=${selector}

Scroll and click test id
  [Arguments]  ${id}
  Scroll to  [data-test-id=${id}]
  Click by test id  ${id}

Wait test id visible
  [Arguments]  ${id}
  Scroll to test id  ${id}
  Wait Until Element Is Visible  jquery=[data-test-id=${id}]

Wait test id hidden
  [Arguments]  ${id}
  Scroll to test id  ${id}
  Wait Until Element Is Not Visible  jquery=[data-test-id=${id}]

Test id empty
  [Arguments]  ${id}
  Wait test id visible  ${id}
  Textfield Value Should Be  jquery=[data-test-id=${id}]  ${EMPTY}

Test id disabled
  [Arguments]  ${id}
  Scroll to test id  ${id}
  Wait Until  Element should be disabled  jquery=[data-test-id=${id}]

Test id enabled
  [Arguments]  ${id}
  Scroll to test id  ${id}
  Wait Until  Element should be enabled  jquery=[data-test-id=${id}]

Fill test id
  [Arguments]  ${id}  ${text}
  Wait test id visible  ${id}
  Element Should Be Enabled  jquery=[data-test-id=${id}]
  Input text by test id  ${id}  ${text}

Focus test id
  [Arguments]  ${id}
  Focus  jquery=[data-test-id=${id}]

No such test id
  [Arguments]  ${id}
  Wait until  Element should not be visible  jquery=[data-test-id=${id}]

Test id should contain
  [Arguments]  ${id}  ${text}
  Wait until  Element should contain  jquery=[data-test-id=${id}]  ${text}

Test id input is
  [Arguments]  ${id}  ${text}
  Wait until  Value should be  jquery=[data-test-id=${id}]  ${text}

Test id text is
  [Arguments]  ${id}  ${text}
  Wait until  Element text should be  jquery=[data-test-id=${id}]  ${text}

Javascript? helper
  [Arguments]  ${expression}
  ${result}=  Execute JavaScript  return ${expression};
  Should be true  ${result}

Javascript?
  [Arguments]  ${expression}
  Wait Until  Javascript? helper  ${expression}

# Alternative to Wait Test Id Visible. Does not scroll
Test id visible
  [Arguments]  ${id}
  Wait Until  Element should be visible  jquery=[data-test-id=${id}]:visible

Checkbox wrapper selected by test id
  [Arguments]  ${data-test-id}
  Javascript?  $("input[data-test-id=${data-test-id}]:checked").length === 1

Checkbox wrapper not selected by test id
  [Arguments]  ${data-test-id}
  Javascript?  $("input[data-test-id=${data-test-id}]:checked").length === 0

Click label
  [Arguments]  ${for}
  Scroll to  label[for=${for}]
  Click element  jquery=label[for=${for}]

Checkbox wrapper selected
  [Arguments]  ${id}
  Javascript?  $("input#${id}:checked").length === 1

Checkbox wrapper not selected
  [Arguments]  ${id}
  Javascript?  $("input#${id}:checked").length === 0

Checkbox wrapper disabled
  [Arguments]  ${id}
  Javascript?  $("input#${id}:disabled").length === 1

Select from test id
  [Arguments]  ${id}  ${value}
  Select from list  jquery=select[data-test-id=${id}]  ${value}

Test id select is
  [Arguments]  ${id}  ${value}
  List selection should be  jquery=select[data-test-id=${id}]  ${value}


# Frontend error log

Print frontend error texts
  [Arguments]  ${ROW_XPATH}  ${ERROR_LEVEL}
  # Print 10 first rows if there are errors
  :FOR  ${elem_idx}  IN RANGE  1  10
  \  ${ELEM_COUNT}=  Get Matching Xpath Count  ${ROW_XPATH}[${elem_idx}]
  \  Exit for loop if  ${ELEM_COUNT} == 0
  \  ${VAL}=  Get Text  ${ROW_XPATH}[${elem_idx}]
  \  Log To Console  ${ERROR_LEVEL}: ${VAL}

Open frontend log
  Go to  ${FRONTEND LOG URL}
  Wait until  Element text should be  xpath=//h1  Frontend log

There are no frontend errors
  Open frontend log
  Set test variable  ${FATAL_LOG_XPATH}  //div[@data-test-level='fatal']
  Set test variable  ${ERROR_LOG_XPATH}  //div[@data-test-level='error']
  ${FATAL_COUNT}=  Get Matching Xpath Count  ${FATAL_LOG_XPATH}
  ${ERR_COUNT}=    Get Matching Xpath Count  ${ERROR_LOG_XPATH}
  Print frontend error texts  ${FATAL_LOG_XPATH}  FATAL
  Print frontend error texts  ${ERROR_LOG_XPATH}  ERROR
  Go to  ${LOGIN URL}
  Logout
  # These test cases will fail if errors exist
  Javascript?  ${FATAL_COUNT} === 0
  Javascript?  ${ERR_COUNT} === 0
