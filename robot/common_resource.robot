*** Settings ***

Documentation  Common stuff for the Lupapiste Functional Tests.
Library        CustomSeleniumLibrary.py  timeout=12  run_on_failure=Nothing
Library        String
Library        Collections
Library        OperatingSystem
Library        DebugLibrary

*** Variables ***

${SERVER}                       http://localhost:8000
${WAIT_DELAY}                   10
${BROWSER}                      firefox
${DEFAULT_SPEED}                0
${OP_TREE_SPEED}                0.1
${SLOW_SPEED}                   0.2
${SLOWEST_SPEED}                0.5

${LOGIN URL}                    ${SERVER}/app/fi/welcome
${LOGOUT URL}                   ${SERVER}/app/fi/logout
${BULLETINS URL}                ${SERVER}/app/fi/bulletins
${APPLICATIONS PATH}            /applicant#!/applications
${AUTHORITY APPLICATIONS PATH}  /app/fi/authority#!/applications
${FIXTURE URL}                  ${SERVER}/dev/fixture
${CREATE URL}                   ${SERVER}/dev/create?redirect=true
${CREATE BULLETIN URL}          ${SERVER}/dev/publish-bulletin-quickly
${LAST EMAIL URL}               ${SERVER}/api/last-email
${LAST EMAILS URL}              ${SERVER}/api/last-emails
${FRONTEND LOG URL}             ${SERVER}/api/frontend-log
${SELENIUM}                     ${EMPTY}
${DB COOKIE}                    test_db_name
${DB PREFIX}                    test_

*** Keywords ***

# Convenience replacements of deprecated keywords

Focus
  [Arguments]  ${locator}
  Set Focus To Element  ${locator}

Get Matching Xpath Count
  [Arguments]  ${xpath}
  ${count}=  Get Element Count  xpath:${xpath}
  [Return]  ${count}

Xpath Should Match X Times
  [Arguments]  ${xpath}  ${times}
  Page Should Contain Element  xpath:${xpath}  limit=${times}

Set DB cookie
  ${timestamp}=  Get Time  epoch
  ${random post fix}=  Evaluate  random.randint(0, 999999999999)  modules=random
  ${dbname}=  Set Variable  ${DB PREFIX}${timestamp}_${random post fix}
  Add Cookie  ${DB COOKIE}  ${dbname}  /
  Log To Console  \n Cookie: ${DB COOKIE} = ${dbname} \n
  Log  Cookie: ${DB COOKIE} = ${dbname}

Browser
  [Arguments]
  # Setting cookies on login page fails on IE8, perhaps because of
  # caching headers:
  # https://code.google.com/p/selenium/issues/detail?id=6985
  # Open a static HTML page and set cookie there
  Open custom browser  ${SERVER}/dev-pages/init.html  ${BROWSER}   remote_url=${SELENIUM}
  Set DB cookie

Reload page and kill dev-box
  Reload page
  Kill dev-box

Open browser to login page
  Browser
  Run Keyword And Ignore Error  Maximize browser window
  Set selenium speed  ${DEFAULT_SPEED}
  Apply minimal fixture now
  Set integration proxy on
  Disable maps
  Disable KTJ

Go to login page
  Go to  ${LOGIN URL}
  Wait Until  Title should be  Lupapiste
  Wait Until  Page should contain  Haluan kirjautua palveluun

Go to bulletins page
  Go to  ${BULLETINS URL}
  Wait Until  Title should be  Julkipano - Lupapiste
  Wait Until  Page should contain  Kuntien julkipanoilmoitukset

Open last email
  [Arguments]  ${reset}=True
  Run keyword if  ${reset}  Go to  ${LAST EMAIL URL}?reset=true
  Run keyword unless  ${reset}  Go to  ${LAST EMAIL URL}
  Wait until  Element should be visible  //*[@data-test-id='subject']

Open all latest emails
  [Arguments]  ${reset}=True
  Run keyword if  ${reset}  Go to  ${LAST EMAILS URL}?reset=true
  Run keyword unless  ${reset}  Go to  ${LAST EMAILS URL}
  Wait until  Element should be visible  //*[@data-test-id='subject']

Applications page should be open
  Location should contain  ${APPLICATIONS PATH}
  Wait Until  Title should be  Lupapiste
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
  Wait until  Element should be visible  jquery=.dev-debug
  Execute Javascript  $(".dev-debug").hide();

Resurrect dev-box
  Execute Javascript  $(".dev-debug").show();
  Wait until  Element should be visible  //div[contains(@class, 'dev-debug')]

Hide nav-bar
  Execute Javascript  $("nav.nav-wrapper").hide();

Show nav-bar
  Execute Javascript  $("nav.nav-wrapper").show();
  Wait until  Element should be visible  //nav[contains(@class, 'nav-wrapper')]


Language To
  [Arguments]  ${lang}
  Element Should Not Contain  language-select  ${lang}
  Click Link  xpath=//a[@data-test-id='language-link']
  Wait Until  Element Should Be Visible  css=div.language-menu
  Click Element  partial link=${lang}
  Wait Until  Element Should Contain  language-select  ${lang}
  Kill dev-box

Language Is
  [Arguments]  ${lang}
  Wait Until  Element Should Contain  language-select  ${lang}

#
# Accordions
#

Current page id
  ${page_xpath}=  Set Variable  //section[contains(@class, 'visible')]
  Xpath should match X times  ${page_xpath}  1
  ${page_id}=  Get Element Attribute  ${page_xpath}  id
  [return]  ${page_id}

Current tab id
  ${current_page_id}=  Current page id
  ${tab_xpath}=  Set Variable  //section[@id='${current_page_id}']//div[contains(@class, 'tab-content')][contains(@style, 'display: block')]
  Xpath should match X times  ${tab_xpath}  1
  ${current_tab_id}=  Get Element Attribute  ${tab_xpath}  id
  [return]  ${current_tab_id}

Visible accordions Xpath
  ${tab_id}=  Current tab id
  ${visible_accordions_xpath}=  Set Variable  //div[@id='${tab_id}']//section[contains(@class,'accordion')]
  ${count}=  Get Matching Xpath Count  ${visible_accordions_xpath}
  Should Be True  0 < ${count}
  [return]  ${visible_accordions_xpath}

Visible accordion count
  ${path}=  Visible accordions Xpath
  ${count}=  Get Matching Xpath Count  ${path}
  [return]  ${count}

All visible accordions should be
  [Arguments]  ${state}
  ${path}=  Visible accordions Xpath
  ${visible_count}=  Visible accordion count
  ${matching_accordions_path}=  Set Variable  ${path}//div[@data-accordion-state='${state}']
  Xpath Should Match X Times  ${matching_accordions_path}  ${visible_count}

All visible accordions should be open
  All visible accordions should be  open

All visible accordions should be closed
  All visible accordions should be  closed

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
  Scroll to top
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
  Wait for jQuery
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

Open docgen accordion
  [Arguments]  ${doctype}  ${idx}=0
  ${xpathIndex}=  Evaluate  ${idx} + 1
  ${accordionIsClosed} =  Run Keyword And Return Status  Element should not be visible  xpath=(//section[@data-doc-type="${doctype}"])[${xpathIndex}]//div[contains(@class,'accordion-toggle')]/button[contains(@class, 'toggled')]
  Run keyword If  ${accordionIsClosed}  Execute Javascript  $("section[data-doc-type='${doctype}']:eq(${idx}) div.accordion-toggle button:first-child").click();

Check accordion text
  [Arguments]  ${id}  ${title}  ${text}
  Test id text is  ${id}-accordion-title-text  ${title}
  Test id text is  ${id}-accordion-description-text  ${text}

Edit party name
  [Arguments]  ${party}  ${firstname}  ${lastname}  ${henkilotiedot-path}=henkilotiedot
  Input text with jQuery  section[data-doc-type='${party}'] input[data-docgen-path='${henkilotiedot-path}.etunimi']  ${firstname}
  Input text with jQuery  section[data-doc-type='${party}'] input[data-docgen-path='${henkilotiedot-path}.sukunimi']  ${lastname}

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

Indicator should contain text
  [Arguments]  ${text}
  Wait until  Element should contain  xpath=//div[@id='indicator']//div[contains(@class, 'indicator-message')]  ${text}

Close sticky indicator
  Click element  xpath=//div[@id='indicator']//div[contains(@class, 'indicator-close')]
  Wait until  Element should not be visible  xpath=//div[@id="indicator"]//div[contains(@class,'indicator-bar')]

#
# Login stuff
#

User should not be logged in
  # Wait for login query to complete
  Wait for jQuery
  Wait Until  User is not logged in

User is not logged in
  Location should be  ${LOGIN URL}#!/login
  Page should contain  Haluan kirjautua palveluun
  # test that no data is bind.

Login
  [Arguments]  ${username}  ${password}
  Wait until  Element should be visible  login-username
  Input text  login-username  ${username}
  Input text  login-password  ${password}
  Submit form  welcome-login-form

Login fails
  [Arguments]  ${username}  ${password}
  Login  ${username}  ${password}
  User should not be logged in

User should be logged in
  [Arguments]  ${name}
  # Give some time for scripts to run
  Sleep  0.5s
  Run Keyword And Ignore Error  Maximize browser window
  Scroll to top
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
  Run Keyword And Return  Get Element Attribute  user-name  data-test-role

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

Kuopio-ya logs in
  Authority-admin logs in  kuopio-ya  kuopio  Paakayttaja-YA Kuopio

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

Jarvenpaa admin logs in
  Authority-admin logs in  admin@jarvenpaa.fi  jarvenpaa  Admin Järvenpää

Jussi logs in
  [Arguments]  ${showAll}=True
  Authority logs in  jussi  jussi  Jussi Viranomainen  ${showAll}

Financial logs in
  User logs in  financial  admin  ARA-käsittelijä


#
# Helpers for cases when target element is identified by "data-test-id" attribute:
#


# Return quoted (or rather ticked)  string
# Quote  hello   -> "hello"
# Quote  "hello" -> "hello"
# Quote  hei "hou" ->  "hei 'hou'"
Quote
  [Arguments]  ${s}
  ${s}=  Convert to string  ${s}
  ${s}=  Strip string  ${s}
  ${s}=  Replace String  ${s}  "  '  # " Fix highlight
  ${quoted}=  Execute Javascript  return '"' + (("${s}".charAt(0) === "'" && "${s}".charAt("${s}".length-1) === "'") ? "${s}".substr(1, "${s}".length-2) : "${s}") + '"';
  [Return]  ${quoted}

Input text with jQuery
  [Arguments]  ${selector}  ${value}  ${leaveFocus}=False
  Wait until  Element should be visible  jquery=${selector}
  Wait until  Element should be enabled  jquery=${selector}
  ${q}=  Quote  ${selector}
  Execute Javascript  $(${q})[0].scrollIntoView(false);
  Execute Javascript  $(${q}).focus().val("${value}").change();
  Run Keyword Unless  ${leaveFocus}  Execute Javascript  $(${q}).blur();

Input text by test id
  [Arguments]  ${id}  ${value}  ${leaveFocus}=False
  Element should be visible by test id  ${id}
  Test id enabled  ${id}
  Set Focus To Element  xpath=//*[@data-test-id="${id}"]
  Input Text  xpath=//*[@data-test-id="${id}"]  ${value}
  Run Keyword Unless  ${leaveFocus}  Execute Javascript  document.querySelector('[data-test-id="${id}"]').blur();

Input text to visible section test id
  [Arguments]  ${id}  ${value}
  ${xp}=  Set Variable  //section[contains(@class,"visible")]//*[@data-test-id="${id}"]
  Wait Until  Element Should Be Visible  ${xp}
  Wait Until  Element Should Be Enabled  ${xp}
  Set Focus To Element  ${xp}
  Input Text  ${xp}  ${value}

Select From List by test id and index
  [Arguments]  ${id}  ${index}
  Wait until page contains element  xpath=//select[@data-test-id="${id}"]
  Select From List By Index  xpath=//select[@data-test-id="${id}"]  ${index}

Select From List by test id
  [Arguments]  ${id}  ${value}
  Wait until page contains element  xpath=//select[@data-test-id="${id}"]
  Select From List by value  xpath=//select[@data-test-id="${id}"]  ${value}

Select From List by id
  [Arguments]  ${id}  ${value}
  Wait until page contains element  xpath=//select[@id="${id}"]
  Select From List by value  xpath=//select[@id="${id}"]  ${value}

Select From Autocomplete
  [Arguments]  ${container}  ${value}
  Wait until  Element should be visible  jquery=${container} span.autocomplete-selection

  ${autocompleteListNotOpen} =  Run Keyword And Return Status  Element should not be visible  jquery=${container} div.autocomplete-dropdown
  Run Keyword If  ${autocompleteListNotOpen}  Scroll and click  ${container} div.autocomplete-selection-wrapper
  Scroll by  400
  Input text  jquery=${container} input[data-test-id="autocomplete-input"]  ${value}
  Wait until  Element should be visible  jquery=${container} ul.autocomplete-result li span:contains('${value}')
  Click element  jquery=${container} ul.autocomplete-result li span:contains('${value}')
  Wait until  Element should not be visible  jquery=${container} ul.autocomplete-result
  Wait for jQuery

Select From Autocomplete By Test Id
  [Arguments]  ${data-test-id}  ${value}
  Select From Autocomplete  [data-test-id="${data-test-id}"]:visible  ${value}

Clear autocomplete selections by test id
  [Arguments]  ${data-test-id}
  Element should be visible  jquery=[data-test-id="${data-test-id}"]
  :FOR  ${i}  IN RANGE  99999
  \  ${selection-empty}=  Run keyword and return status  Element should not be visible  jquery=[data-test-id="${data-test-id}"] .tag-remove
  \  Exit for loop if  ${selection-empty}
  \  Click element  jquery=[data-test-id="${data-test-id}"] ul.tags .tag-remove

Autocomplete selection is
  [Arguments]  ${container}  ${value}
  Element should contain  xpath=//${container}//span[contains(@class, "autocomplete-selection")]/span[contains(@class, 'caption')]  ${value}

Autocomplete selection by test id is
  [Arguments]  ${tid}  ${value}
  Element should contain  jquery=div[data-test-id=${tid}] span.autocomplete-selection span.caption  ${value}

Autocomplete selection by test id contains
  [Arguments]  ${tid}  ${value}
  Element should contain  jquery=div[data-test-id=${tid}] span.autocomplete-selection  ${value}

Autocomplete selection by test id is empty
  [Arguments]  ${tid}
  Element should not be visible  jquery=div[data-test-id=${tid}] span.autocomplete-selection span.caption
  Element should not be visible  jquery=div[data-test-id=${tid}] span.autocomplete-selection ul.tags .tag

Autocomplete selectable values should not contain
  [Arguments]  ${container}  ${value}
  # Open dropdown if it is not open
  ${autocompleteListNotOpen} =  Run Keyword And Return Status  Element should not be visible  xpath=//div[@data-test-id="operations-filter-component"]//div[contains(@class, 'autocomplete-dropdown')]
  Run Keyword If  ${autocompleteListNotOpen}  Click Element  xpath=//div[@data-test-id="operations-filter-component"]//span[contains(@class, "autocomplete-selection")]
  Wait until  Element should not be visible  xpath=//${container}//ul[contains(@class, "autocomplete-result")]//li/span[contains(text(), '${value}')]

Autocomplete option list should contain
  [Arguments]  ${data-test-id}  @{options}
  :FOR  ${element}  IN  @{options}
  \  Element should contain  xpath=//div[@data-test-id="${data-test-id}"]//ul[contains(@class, "autocomplete-result")]  ${element}

Autocomplete option list should contain by test id
  [Arguments]  ${data-test-id}  @{options}
  Click Element  xpath=//div[@data-test-id="${data-test-id}"]//span[contains(@class, "autocomplete-selection")]
  Wait until  Element should be visible  xpath=//div[@data-test-id="${data-test-id}"]//div[contains(@class, 'autocomplete-dropdown')]
  :FOR  ${element}  IN  @{options}
  \  Wait Until  Element should contain  xpath=//div[@data-test-id="${data-test-id}"]//ul[contains(@class, "autocomplete-result")]  ${element}

Click by id
  [Arguments]  ${id}
  ${selector} =   Set Variable  $("[id='${id}']:visible")
  # 'Click Element' is broken in Selenium 2.35/FF 23 on Windows, using jQuery instead
  Wait For Condition  return ${selector}.length===1;  10
  Execute Javascript  ${selector}.click();

Element should be visible by test id
  [Arguments]  ${id}
  Wait Until  Element Should Be Visible  xpath=//*[@data-test-id="${id}"]

Element should not be visible by test id
  [Arguments]  ${id}
  Wait Until  Element Should Not Be Visible  xpath=//*[@data-test-id="${id}"]

Click by test id
  [Arguments]  ${id}
  Wait until  Element should be visible by test id  ${id}
  Click element  xpath=//*[@data-test-id="${id}"]

Click enabled by test id
  [Arguments]  ${id}
  Wait until  Element should be visible by test id  ${id}
  Wait Until  Element Should Be Enabled  xpath=//*[@data-test-id="${id}"]
  Scroll and click test id  ${id}

# Workaround for HTML5 inputs
Value should be
  [Arguments]    ${textfield}    ${expected}
  ${actual}=    Get Value    ${textfield}
  Should Be Equal    ${expected}    ${actual}

#
# The following do not take data-test-id as argument
#

Get document id
  [Arguments]  ${doc}  ${idx}=1
    ${docId}=  Get Element Attribute  xpath=//section[@data-doc-type='${doc}'][${idx}]  data-doc-id
   [Return]  ${docId}

Get identifiers closed
  [Arguments]  ${doc}  ${idx}
  ${docId} =  Get document id  ${doc}  ${idx}
  ${identifiersClosed} =  Run Keyword And Return Status  Element should not be visible  xpath=//section[@data-doc-id='${docId}']//div[@data-test-id='identifier-editors']
  [Return]  ${identifiersClosed}

Open identifiers
  [Arguments]  ${doc}  ${idx}=1
  Wait until   Element should be visible  xpath=//section[@data-doc-type='${doc}'][${idx}]//button[@data-test-id='toggle-identifiers-${doc}']
  ${docId}=  Get document id  ${doc}  ${idx}
  ${identifiersClosed} =  Get identifiers closed  ${doc}  ${idx}
  # for jQuery ${idx}-1 because xpath indeces start from 1!
  Run keyword If  ${identifiersClosed}  Execute Javascript  $('[data-test-id=toggle-identifiers-${doc}]')[${idx}-1].click();
  Wait test id visible  ${docId}-identifier-input

Close identifiers
  [Arguments]  ${doc}  ${idx}
  ${identifiersClosed} =  Get identifiers closed  ${doc}  ${idx}
  Run keyword unless  ${identifiersClosed}  Execute Javascript  $('[data-test-id=toggle-identifiers-${doc}]')[${idx}-1].click();

Primary operation is
  [Arguments]  ${opId}
  Element should be visible  xpath=//span[@data-test-primary-operation-id="${opId}"]

Edit operation description
  [Arguments]  ${doc}  ${text}  ${idx}=1
  ${docId}=  Get document id  ${doc}  ${idx}
  Open identifiers  ${doc}  ${idx}

  ${path}=  Set Variable  section[data-doc-id='${docId}'] input[data-test-id='op-description-editor-${doc}']
  Wait Until  Element Should Be Visible  jquery=${path}
  Input text with jQuery  ${path}  ${text}
  Close identifiers  ${doc}  ${idx}

# This only works if there is only one applicable document.
Operation description is
  [Arguments]  ${doc}  ${text}
  Wait until  Element Should Contain  xpath=//span[@data-test-id='${doc}-accordion-description-text']  ${text}

Operation description disabled
  [Arguments]  ${doc}  ${idx}=1
  ${docId}=  Get document id  ${doc}  ${idx}
  Open identifiers  ${doc}  ${idx}

  ${path}=  Set Variable  section[data-doc-id='${docId}'] input[data-test-id='op-description-editor-${doc}']
  Wait Until  Element Should Be Disabled  jquery=${path}
  Close identifiers  ${doc}  ${idx}

Input building identifier
  [Arguments]  ${doc}  ${text}  ${idx}=1
  Open identifiers  ${doc}  ${idx}
  ${docId}=  Get document id  ${doc}  ${idx}
  Input text by test id  ${docId}-identifier-input  ${text}
  Close identifiers  ${doc}  ${idx}

Building identifier is
  [Arguments]  ${doc}  ${text}  ${idx}=1
  Open identifiers  ${doc}  ${idx}
  ${docId}=  Get document id  ${doc}  ${idx}
  Test id input is  ${docId}-identifier-input  ${text}
  Close identifiers  ${doc}  ${idx}

Building identifier disabled
  [Arguments]  ${doc}  ${idx}=1
  Open identifiers  ${doc}  ${idx}
  ${docId}=  Get document id  ${doc}  ${idx}
  Test id disabled  ${docId}-identifier-input
  Close identifiers  ${doc}  ${idx}

Document status is disabled
  [Arguments]  ${docType}  ${xpathIdx}=1
  Wait until  Element should be visible  xpath=(//section[@data-doc-type='${docType}'])[${xpathIdx}]//div[contains(@class, 'accordion-toggle')]/button[contains(@class,'disabled')]
  Wait until  Element text should be  xpath=(//section[@data-doc-type='${docType}'])[${xpathIdx}]//button[@data-test-id='toggle-document-status']/span  Palauta aktiiviseksi

Document status is enabled
  [Arguments]  ${docType}  ${xpathIdx}=1
  Wait until  Element should not be visible  xpath=(//section[@data-doc-type='${docType}'])[${xpathIdx}]//div[contains(@class, 'accordion-toggle')]/button[contains(@class,'disabled')]
  Wait until  Element text should be  xpath=(//section[@data-doc-type='${docType}'])[${xpathIdx}]//button[@data-test-id='toggle-document-status']/span  Merkitse poistuneeksi


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
  ${selectedMuni} =  Get Element Attribute  xpath=//div[@id="popup-id"]//span[@data-test-id='create-municipality-select']  data-test-value
  Should Be Equal  ${selectedMuni}  ${municipality}

Address is not blank
  ${address} =  Get Element Attribute  xpath=//div[@id="popup-id"]//input[@data-test-id='create-address']  value
  Should Not Be Equal As Strings  ${address}  ${EMPTY}

Do prepare new request
  [Arguments]  ${address}  ${municipality}  ${propertyId}  ${permitType}
  Input Text  create-search  ${propertyId}
  Click enabled by test id  create-search-button
  Wait until  Element should be visible  xpath=//div[@id='popup-id']//input[@data-test-id='create-property-id']
  Textfield Value Should Be  xpath=//div[@id='popup-id']//input[@data-test-id='create-property-id']  ${propertyId}
  Wait Until  Selected Municipality Is  ${municipality}
  Wait Until  Address is not blank
  Sleep  1s
  Input Text  //div[@id='popup-id']//input[@data-test-id='create-address']  ${address}
  Set animations off
  ${path} =   Set Variable  xpath=//div[@id="popup-id"]//button[@data-test-id="create-continue"]
  Wait until  Element should be enabled  ${path}
  Click element  ${path}

  Select operation path by permit type  ${permitType}
  Wait until  Element should be visible  xpath=//section[@id="create-part-2"]//div[contains(@class, 'tree-content')]//*[@data-test-id="create-application"]
  Set animations on

Add empty attachment template
  [Arguments]  ${templateName}  ${topCategory}  ${subCategory}
  Click enabled by test id  add-attachment-templates
  Select From Autocomplete  div[data-test-id="attachment-type-autocomplete"]  ${templateName}
  Wait Until  Element Should Be Visible  jquery=div.selected-attachment-types-container div[data-test-id=selected-attachment-${topCategory}-${subCategory}]
  Click by test id  require-attachments-bubble-dialog-ok
  Wait Until  Element Should Not Be Visible  jquery=div.selected-attachment-types-container
  Wait Until  Element Should Be Visible  jquery=div#application-attachments-tab tr[data-test-type="${topCategory}.${subCategory}"]

Expose file input
  [Arguments]  ${jQuerySelector}
  Execute Javascript  $("${jQuerySelector}").css( "display", "block").toggleClass( "hidden", false )
  Wait until element is visible  jquery=${jQuerySelector}

Hide file input
  [Arguments]  ${jQuerySelector}
  Execute Javascript  $("${jQuerySelector}").css( "display", "none").toggleClass( "hidden", true )

Upload with hidden input
  [Arguments]  ${jquerySelector}  ${path}
  Expose file input  ${jquerySelector}
  Choose file  jquery=${jquerySelector}  ${path}
  Hide file input  ${jquerySelector}

Upload via button or link
  [Arguments]  ${uploadContainer}  ${path}
  Upload with hidden input  input[data-test-id=${uploadContainer}-input]  ${path}

Upload batch file
  [Arguments]  ${index}  ${path}  ${type}  ${contents}  ${grouping}  ${testId}=add-attachments-input
  Expose file input  input[data-test-id=${testId}]
  Choose file  jquery=input[data-test-id=${testId}]  ${path}
  Hide file input  input[data-test-id=${testId}]
  Wait Until  Element should be visible  jquery=div.upload-progress--finished
  Wait test id visible  batch-ready
  Scroll to bottom
  ${alreadySelected}=  Run keyword and return status  Autocomplete selection is  div[contains(@class, 'batch-autocomplete') and @data-test-id='batch-type-${index}']  ${type}
  Run keyword unless  ${alreadySelected}  Select from autocomplete  div.batch-autocomplete[data-test-id=batch-type-${index}]  ${type}
  Run keyword unless  '${contents}' == '${EMPTY}'  Fill test id  batch-contents-${index}  ${contents}
  ${group-is-selected}=  Run Keyword and Return Status  Autocomplete selection by test id contains  batch-grouping-${index}  ${grouping}
  Run keyword unless  ${group-is-selected}  Clear autocomplete selections by test id  batch-grouping-${index}
  Run keyword unless  ${group-is-selected} or '${grouping}' == 'Yleisesti hankkeeseen'  Wait until  Select from autocomplete  [data-test-id=batch-grouping-${index}] [data-test-id=attachment-group-autocomplete]  ${grouping}

Upload verdict or task attachment
  [Arguments]  ${path}  ${type}  ${contents}  ${grouping}
  Test id visible  upload-button-label
  Scroll to top
  Upload batch file  0  ${path}  ${type}  ${contents}  ${grouping}  upload-button-input
  Click enabled by test id  batch-ready
  Wait until  No such test id  batch-ready

Upload attachment with default type
  [Arguments]  ${path}  ${testId}=upload-button-input
  Test id visible  upload-button-label
  Expose file input  input[data-test-id=${testId}]
  Choose file  jquery=input[data-test-id=${testId}]  ${path}
  Hide file input  input[data-test-id=${testId}]
  Wait Until  Element should be visible  jquery=div.upload-progress--finished
  Wait test id visible  batch-ready
  Scroll to bottom
  Click enabled by test id  batch-ready
  Wait until  No such test id  batch-ready

Upload attachment
  [Arguments]  ${path}  ${type}  ${contents}  ${grouping}
  Test id visible  add-attachments-label
  Scroll to top
  Upload batch file  0  ${path}  ${type}  ${contents}  ${grouping}
  Scroll and click test id  batch-ready
  Wait Until Keyword Succeeds  20s  0.4  Element Should Not Be Visible  xpath=//*[@data-test-id="batch-ready"]

Add attachment
  [Arguments]  ${kind}  ${path}  ${description}  ${type}=muut.muu  ${operation}=
  Run Keyword If  '${kind}' == 'application'  Fail  Use Upload Attachment instead
  Run Keyword If  '${kind}' == 'inforequest'  Click enabled by test id  add-inforequest-attachment
  Run Keyword If  '${kind}' == 'verdict'  Click enabled by test id  add-targetted-attachment
  Run Keyword If  '${kind}' == 'statement'  Click enabled by test id  add-statement-attachment

  Wait until  Element should be visible  upload-dialog

  Select Frame      uploadFrame
  Wait until        Element should be visible  test-save-new-attachment

  Run Keyword If  '${kind}' == 'application'  Set attachment type for upload  ${type}
  Run Keyword If  '${kind}' == 'application' and $operation  Wait until  Page should contain element  xpath=//form[@id='attachmentUploadForm']//option[text()='${operation}']
  Run Keyword If  '${kind}' == 'application' and $operation  Select From List by value  attachmentOperation  ${operation}

  Input text        text  ${description}
  Wait until        Page should contain element  xpath=//form[@id='attachmentUploadForm']/input[@type='file']
  Focus             xpath=//form[@id='attachmentUploadForm']/input[@type='file']
  Choose File       xpath=//form[@id='attachmentUploadForm']/input[@type='file']  ${path}
  Execute Javascript  $('#test-save-new-attachment')[0].click();
  Unselect Frame
  Wait until  Element should not be visible  upload-dialog
  Run Keyword If  '${kind}' == 'application'  Wait Until  Element Should Be Visible  jquery=section[id=attachment] a[data-test-id=back-to-application-from-attachment]
  Run Keyword If  '${kind}' == 'inforequest'  Wait Until Page Contains  ${description}

Return to application
  Wait Until  Scroll and click test id  back-to-application-from-attachment

Delete attachment
  [Arguments]  ${type}
  Scroll to  tr[data-test-type='${type}'] button[data-test-icon='delete-button']
  Click element  jquery=tr[data-test-type='${type}'] button[data-test-icon='delete-button']
  Confirm yes no dialog

Set attachment type for upload
  [Arguments]  ${type}
  Wait until  Page should contain element  xpath=//form[@id='attachmentUploadForm']//option[@value='${type}']
  Select From List by value  attachmentType  ${type}

Open attachment details
  [Arguments]  ${type}  ${nth}=0
  Open tab  attachments
  ${selector} =  Set Variable  div#application-attachments-tab tr[data-test-type='${type}'] a[data-test-id=open-attachment]:visible
  Wait until  Element should be visible  jquery=${selector}
  Scroll and click link  ${selector}:eq(${nth})
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
  Wait Until Page Contains  ${name}
  Element Text Should Be  test-attachment-file-name  ${name}
  Element Text Should Be  test-attachment-version  ${versionNumber}

# Add file version from attachment details
Add attachment version
  [Arguments]  ${path}
  Wait Until  Element should be visible  jquery=label[data-test-id=upload-button-label]
  Upload via button or link  upload-button  ${path}
  Positive indicator should be visible

# Add the first file to template from attachments view
Add attachment file
  [Arguments]  ${row}  ${path}  ${contents}
  Wait Until     Element should be visible  jquery=${row} label[data-test-id=add-attachment-file-label]
  Scroll to  ${row} label[data-test-id=add-attachment-file-label]
  Upload with hidden input  ${row} input[data-test-id=add-attachment-file-input]  ${path}
  Wait Until  Element should not be visible  jquery=${row} label[data-test-id=add-attachment-file-label]
  Fill test id  batch-contents-0  ${contents}
  Wait Until  Test id enabled  batch-ready

Attachment is
  [Arguments]  ${approvalStatus}
  Wait until  Element should be visible  xpath=//div[@data-test-id='approval-component']//i[contains(@class, '${approvalStatus}')]

Select operation path by permit type
  [Arguments]  ${permitType}
  Run Keyword If  '${permitType}' == 'R'  Select operations path R
  ...  ELSE IF  '${permitType}' == 'YA-kaivulupa'  Select operations path YA kayttolupa kaivu
  ...  ELSE IF  '${permitType}' == 'YA-kayttolupa'  Select operations path YA kayttolupa
  ...  ELSE IF  '${permitType}' == 'YA-kayttolupa-mainostus-viitoitus'  Select operations path YA kayttolupa mainostus-viitoitus
  ...  ELSE IF  '${permitType}' == 'YA-sijoituslupa'  Select operations path YA sijoituslupa
  ...  ELSE IF  '${permitType}' == 'YA-sijoituslupa-tyolupa'  Select operations path YA sijoituslupa-tyolupa
  ...  ELSE  Select operations path R

Select operations path R
  Click tree item by text  "Rakentaminen, purkaminen tai maisemaan vaikuttava toimenpide"
  Click tree item by text  "Uuden rakennuksen rakentaminen"
  Click tree item by text  "Asuinkerrostalon tai rivitalon rakentaminen"

Select operations path R in Swedish
  Click tree item by text  "Byggande, rivning eller åtgärd som inverkar på landskapet"
  Click tree item by text  "Byggande av ny byggnad"
  Click tree item by text  "Byggande av flervåningshus eller radhus"

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

Select operations path YA sijoituslupa-tyolupa
  Click tree item by text  "Yleiset alueet (Sijoittamissopimus, katulupa, alueiden käyttö)"
  Click tree item by text  "Työskentely yleisellä alueella (Katulupa)"
  Click tree item by text  "Liikennealueen rajaaminen työkäyttöön"
  Click tree item by text  "Nostotyöt"

Click tree item by text
  [Arguments]  ${itemName}
  Wait and click  //section[@id="create-part-2"]//div[contains(@class, 'tree-content')]//*[text()=${itemName}]


# Cancel application or inforequest

Close current inforequest
  Wait Until  Element Should Be Enabled  xpath=//button[@data-test-id="inforequest-cancel-btn"]
  Click enabled by test id  inforequest-cancel-btn
  Confirm  dynamic-yes-no-confirm-dialog

Cancel current application
  [Arguments]  ${reason}=${EMPTY}
  Wait Until  Element Should Be Enabled  xpath=//button[@data-test-id="application-cancel-btn"]
  Click enabled by test id  application-cancel-btn
  Fill test id  modal-dialog-textarea  ${reason}
  Confirm yes no dialog

Cancel current application as authority
  [Arguments]  ${reason}=${EMPTY}
  Wait Until  Element Should Be Enabled  xpath=//button[@data-test-id="application-cancel-btn"]
  Click enabled by test id  application-cancel-btn
  Fill test id  modal-dialog-textarea  ${reason}
  Confirm yes no dialog

# New yes no modal dialog

New modal mask is invisible
  Element should not be visible  xpath=//div[@id='modal-dialog-content-container']/div[contains(@class, 'mask')]

Confirm yes no dialog
  Wait until  Element should be visible  xpath=//div[@id="modal-dialog"]//button[@data-test-id="confirm-yes"]
  Wait until  Element should be enabled  xpath=//div[@id="modal-dialog"]//button[@data-test-id="confirm-yes"]
  Focus  xpath=//div[@id="modal-dialog"]//button[@data-test-id="confirm-yes"]
  Click Element  xpath=//div[@id="modal-dialog"]//button[@data-test-id="confirm-yes"]
  Wait Until  Element Should Not Be Visible  xpath=//div[@id="modal-dialog"]//button[@data-test-id="confirm-yes"]

Deny yes no dialog
  Wait until  Element should be visible  xpath=//div[@id="modal-dialog"]//button[@data-test-id="confirm-no"]
  Focus  xpath=//div[@id="modal-dialog"]//button[@data-test-id="confirm-no"]
  Click Element  xpath=//div[@id="modal-dialog"]//button[@data-test-id="confirm-no"]
  Wait Until  Element Should Not Be Visible  xpath=//div[@id="modal-dialog"]//button[@data-test-id="confirm-no"]
  Wait Until  New modal mask is invisible

Confirm ok dialog
  Wait until  Element should be visible  xpath=//div[@id="modal-dialog"]//button[@data-test-id="ok-button"]
  Focus  xpath=//div[@id="modal-dialog"]//button[@data-test-id="ok-button"]
  Click Element  xpath=//div[@id="modal-dialog"]//button[@data-test-id="ok-button"]
  Wait Until  Element Should Not Be Visible  xpath=//div[@id="modal-dialog"]//button[@data-test-id="ok-button"]
  Wait Until  New modal mask is invisible

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
  ${attrValue}=  Get Element Attribute  xpath=(//div[@data-test-id='submit-errors-container']//span)  data-submit-error
  Should Be Equal As Strings  ${errorText}  ${attrValue}

Approve application no dialogs
  Open tab  requiredFieldSummary
  Wait until  Element should be visible  //div[@id='application-requiredFieldSummary-tab']
  ${BULLETIN_DESCR_VISIBLE}=  Run Keyword And Return Status  Test id visible  bulletin-op-description-summaryTab
  Run Keyword If  ${BULLETIN_DESCR_VISIBLE}  Fill test id  bulletin-op-description-summaryTab  Toimenpideotsikko julkipanoon
  Wait test id visible  approve-application-summaryTab
  Click enabled by test id  approve-application-summaryTab
  Wait until  Application state should be  sent

Approve application ok
  Open tab  requiredFieldSummary
  Click enabled by test id  approve-application-summaryTab
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
  Run Keyword And Ignore Error  Click by test id  search-tab-${tab}
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
  Run Keyword And Ignore Error  Scroll and click test id  search-tab-${tab}

Show all applications
  ${tab}=  Run Keyword and Return Status  Wait test id visible  search-tab-all
  Run Keyword If  ${tab}  Run Keyword And Ignore Error  Scroll and click test id  search-tab-all


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
  Wait Until  Element Should Be Visible  xpath=//section[@id='application']//div[contains(@class, 'parties-list')]//table/tbody//td[contains(., '${party}')]

Is not authorized party
  # Party can be either email or username
  [Arguments]  ${party}
  Wait Until  Element Should Not Be Visible  xpath=//section[@id='application']//div[contains(@class, 'parties-list')]//table/tbody//td[contains(., '${party}')]

Fill application person invite bubble
  [Arguments]  ${email}  ${message}
  Scroll to test id  application-invite-person
  Element should be visible  xpath=//button[@data-test-id='application-invite-person']
  Click by test id  application-invite-person
  Test id disabled  person-invite-bubble-dialog-ok
  Fill test id  person-invite-email  ${email}
  Fill test id  person-invite-message  Tervetuloa muokkaamaan hakemusta
  Test id enabled  person-invite-bubble-dialog-ok

Invite ${email} to application
  Open tab  parties
  ${invites_count}=  Get Matching Xpath Count  //div[contains(@class, 'parties-list')]/table//tr[contains(@class, 'party')]
  Fill application person invite bubble  ${email}  Tervetuloa muokkaamaan hakemusta
  Scroll and click test id  person-invite-bubble-dialog-ok
  Wait until  Element should not be visible  jquery=[data-test-id=person-invite-bubble-dialog-ok]
  Wait Until  Element Should Be Visible  xpath=//div[contains(@class, 'parties-list')]//tr[contains(@class, 'party')][${invites_count} + 1]
  ${email_found}=  Run Keyword And Return Status  Is authorized party  ${email}
  # If specified email was not found from auths, try to parse username from the email and test if username exists (case of pena)
  ${username}=  Fetch From Left  ${email}  @
  Run Keyword Unless  ${email_found}  Is authorized party  ${username}

Invite count is
  [Arguments]  ${amount}
  Wait Until  Xpath Should Match X Times  //*[contains(@class, 'user-invite')]  ${amount}

#
# Authority admin
#

Invite company to application
  [Arguments]  ${company}
  Open tab  parties
  Sleep  0.5s
  Scroll and click test id  application-invite-company
  Wait test id visible  company-invite-bubble-dialog-ok
  Select From Autocomplete  div[data-test-id="company-invite-companies"]  ${company}
  Sleep  0.5s
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
  Wait until  Xpath Should Match X Times  //table[contains(@class, 'tasks-foreman')]/tbody/tr  ${amount}

#
# Quick, jettison the db...
#

Apply ${fixture-name} fixture now
  Go to  ${FIXTURE URL}/${fixture-name}
  Wait until  Page should contain  true
  Go to login page

#
# Application state check:
#

Application state should be
  [Arguments]  ${state}
  ${s} =  Get Element Attribute  xpath=//div[@data-test-id='application-state']  data-test-state
  Should be equal  ${s}  ${state}

Permit type should be
  [Arguments]  ${type}
  Element Text Should Be  xpath=//span[@data-bind='ltext: permitType']  ${type}

Application address should be
  [Arguments]  ${address}
  Wait Until  Element Should Be Visible  xpath=//section[@id='application']//span[@data-test-id='application-title']
  Wait Until  Element text should be  xpath=//section[@id='application']//span[@data-test-id='application-title']  ${address}

Neighbor application address should be
  [Arguments]  ${address}
  Wait Until  Element Should Be Visible  xpath=//section[@id='neighbor-show']//span[@data-test-id='application-title']
  Wait Until  Element text should be  xpath=//section[@id='neighbor-show']//span[@data-test-id='application-title']  ${address}

#
# Proxy control:
#

Enable maps
  Execute Javascript  ajax.command("set-feature",{feature:"maps-disabled",value:false}).call();
  Wait for jQuery

Set integration proxy on
  Execute Javascript  ajax.post("/api/proxy-ctrl/on").call();
  Wait for jQuery

Disable maps
  Execute Javascript  ajax.command("set-feature", {feature: "maps-disabled", value:true}).call();
  Wait for jQuery

Set integration proxy off
  Execute Javascript  ajax.post("/api/proxy-ctrl/off").call();
  Wait for jQuery

Disable KTJ
  Execute Javascript  ajax.command("set-feature", {feature: "disable-ktj-on-create", value:true}).call();
  Wait for jQuery

Enable KTJ
  Execute Javascript  ajax.command("set-feature", {feature: "disable-ktj-on-create", value:false}).call();
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
  Wait test id visible  manager-neighbors-add
  Scroll to bottom
  Click by test id  manager-neighbors-add
  Wait Until   Element Should Be Visible  xpath=//*[@data-test-id='modal-dialog-content']
  Input text by test id  neighbors.edit.propertyId  ${propertyId}
  Input text by test id  neighbors.edit.name  ${name}
  Input text by test id  neighbors.edit.email  ${email}
  Click by test id  modal-dialog-submit-button
  Wait Until  Element Should Not Be Visible  xpath=//*[@data-test-id='modal-dialog-content']
  Wait Until  Page Should Contain  ${email}

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

Fill in new company password
  [Arguments]  ${section}  ${password}
  Wait Until  Element should be visible  xpath=//section[@id='${section}']//h3[@data-test-id='company-setpw-header']
  Input text  xpath=//section[@id='${section}']//input[@data-test-id='password1']  ${password}
  Wait test id visible  password1-message
  Test id disabled  testCompanyUserSubmitPassword
  Input text  xpath=//section[@id='${section}']//input[@data-test-id='password2']  ${password}
  Test id enabled  testCompanyUserSubmitPassword
  Click by test id  testCompanyUserSubmitPassword
  Confirm notification dialog


Open company user listing
  Click Element  user-name
  Wait until  Element should be visible  xpath=//div[@data-test-id="mypage-company-accordion"]
  Open accordion by test id  mypage-company-accordion
  Wait Until  Element should be visible  //div[@data-test-id='my-company']//button[@data-test-id='company-edit-users']
  Click by test id  company-edit-users
  Wait until  Element should be visible  company

Open company details
  Click Element  user-name
  Open accordion by test id  mypage-company-accordion
  Wait Until  Element should be visible  //div[@data-test-id='my-company']//button[@data-test-id='company-edit-info']
  Click by test id  company-edit-info
  Wait until  Element should be visible  company

# At company registration page
Company ${type} billing is selected
  Wait until  Element should be visible  xpath=//button[@data-test-id='${type}-billing' and contains(@class, 'selected')]


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
  [Arguments]  ${selector}  ${index}=0
  ${q}=  Quote  ${selector}
  Wait Until  Execute Javascript  $(${q})[${index}].scrollIntoView(false);

Scroll to top
  Execute javascript  window.scrollTo(0,0)

Scroll to bottom
  Execute javascript  window.scrollTo(0,888888)

Scroll by
  [Arguments]  ${deltaY}
  Execute javascript  window.scrollBy(0, ${deltaY})

Scroll to test id
  [Arguments]  ${id}
  Wait Until  Page Should Contain Element  xpath=//*[@data-test-id='${id}']
  Execute Javascript  document.body.querySelector("[data-test-id='${id}']").scrollIntoView(false);

Scroll to xpath
  [Arguments]  ${xpath}
  ${q}=  Quote  ${xpath}
  ${xfn}=  Set Variable  document.evaluate(${q}, document, null, XPathResult.ORDERED_NODE_ITERATOR_TYPE, null)
  Execute javascript  var result = ${xfn}; var node = result.iterateNext(); if (node) node.scrollIntoView(false);

Scroll and click
  [Arguments]  ${selector}
  Scroll to  ${selector}
  Click Element  jquery=${selector}

Scroll and click link
  [Arguments]  ${selector}
  Scroll to  ${selector}
  # Just in case
  Scroll by  40
  Click Link  jquery=${selector}

Scroll and click input
  [Arguments]  ${selector}
  Scroll to  ${selector}
  Element should be enabled  jquery=${selector}
  Click Element  jquery=${selector}

Scroll and click test id
  [Arguments]  ${id}
  Scroll to test id  ${id}
  Click by test id  ${id}

Scroll to and click xpath results
  [Arguments]  ${xpath}
  Sleep  2s
  ${xfn}=  Set Variable  document.evaluate("${xpath}", document, null, XPathResult.ORDERED_NODE_ITERATOR_TYPE, null)
  Execute javascript  var result = ${xfn}; var node = result.iterateNext(); while (node) { node.scrollIntoView(false); node.click(); try {node = result.iterateNext();} catch(e){node = null;}}

Wait test id visible
  [Arguments]  ${id}  ${timeout}=None
  Scroll to test id  ${id}
  ${q}=  Quote  ${id}
  Wait Until Element is visible  xpath=//*[@data-test-id=${q}]  ${timeout}

Wait test id hidden
  [Arguments]  ${id}
  Wait Until  Element should not be visible  jquery=[data-test-id=${id}]

Test id empty
  [Arguments]  ${id}
  Wait test id visible  ${id}
  Textfield Value Should Be  jquery=[data-test-id=${id}]  ${EMPTY}

Textarea is empty
  [Arguments]  ${test-id}
  Wait test id visible  ${test-id}
  Textarea Value Should Be  jquery=[data-test-id=${test-id}]  ${EMPTY}

Test id disabled
  [Arguments]  ${id}
  Scroll to test id  ${id}
  Wait Until  Element should be disabled  xpath=//*[@data-test-id='${id}']

Test id enabled
  [Arguments]  ${id}
  Scroll to test id  ${id}
  Wait Until  Element should be enabled  xpath=//*[@data-test-id='${id}']

Fill test id
  [Arguments]  ${id}  ${text}
  Wait test id visible  ${id}
  Element Should Be Enabled  xpath=//*[@data-test-id='${id}']
  Input text by test id  ${id}  ${text}

Focus test id
  [Arguments]  ${id}
  Wait until  Focus  jquery=[data-test-id=${id}]

No such test id
  [Arguments]  ${id}
  Wait Until  Element Should Not Be Visible  xpath=//*[@data-test-id="${id}"]

Test id should contain
  [Arguments]  ${id}  ${text}
  Wait until  Element should contain  jquery=[data-test-id=${id}]:visible  ${text}

Test id should not contain
  [Arguments]  ${id}  ${text}
  Wait until  Element should not contain  jquery=[data-test-id=${id}]:visible  ${text}

Test id input is
  [Arguments]  ${id}  ${text}
  Wait until  Value should be  xpath=//*[@data-test-id='${id}']  ${text}

Test id text is
  [Arguments]  ${id}  ${text}
  Wait until  Element text should be  jquery=[data-test-id=${id}]:visible  ${text}

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

Click visible test id
  [Arguments]  ${id}
  Test id visible  ${id}
  Click element  jquery=[data-test-id=${id}]:visible

Click label
  [Arguments]  ${for}
  Scroll to  label[for=${for}]
  Click element  jquery=label[for=${for}]

Click label by test id
  [Arguments]  ${tid}
  Scroll to  label[data-test-id=${tid}]
  Click element  jquery=label[data-test-id=${tid}]

Checkbox wrapper selected by test id
  [Arguments]  ${data-test-id}
  Wait until  Element should be visible  xpath=//label[@data-test-id='${data-test-id}-label']
  Wait until  Checkbox should be selected  xpath=//input[@data-test-id='${data-test-id}-input']

Checkbox wrapper not selected by test id
  [Arguments]  ${data-test-id}
  Wait until  Element should be visible  xpath=//label[@data-test-id='${data-test-id}-label']
  Wait until  Checkbox should not be selected  xpath=//input[@data-test-id='${data-test-id}-input']

Checkbox wrapper selected
  [Arguments]  ${id}
  Javascript?  $("input#${id}:checked").length === 1

Checkbox wrapper not selected
  [Arguments]  ${id}
  Javascript?  $("input#${id}:checked").length === 0

Checkbox wrapper disabled
  [Arguments]  ${id}
  Javascript?  $("input#${id}:disabled").length === 1

Toggle selected
  [Arguments]  ${tid}
  Checkbox wrapper selected by test id  ${tid}

Toggle not selected
  [Arguments]  ${tid}
  Checkbox wrapper not selected by test id  ${tid}

Toggle disabled
  [Arguments]  ${tid}
  Test id disabled  ${tid}-input

Toggle enabled
  [Arguments]  ${tid}
  Test id enabled  ${tid}-input

Toggle visible
  [Arguments]  ${tid}
  Wait until  Element should be visible  xpath=//label[@data-test-id='${tid}-label']

No such toggle
  [Arguments]  ${tid}
  Wait until  Element should not be visible  xpath=//label[@data-test-id='${tid}-label']

Toggle toggle
  [Arguments]  ${tid}
  Toggle visible  ${tid}
  Click label by test id  ${tid}-label

Select from test id
  [Arguments]  ${id}  ${value}
  Select from list by value  jquery=select[data-test-id=${id}]  ${value}

Select from test id by text
  [Arguments]  ${id}  ${text}
  Select from list by label  jquery=select[data-test-id=${id}]  ${text}

Test id select is
  [Arguments]  ${id}  ${value}
  Wait until  List selection should be  jquery=select[data-test-id=${id}]  ${value}

Test id select text is
  [Arguments]  ${id}  ${text}
  ${label}=  Get Selected List Label  jquery=select[data-test-id=${id}]
  Should be true  '${label}' == '${text}'

Test id select values are
  [Arguments]  ${tid}  @{values}
  Wait test id visible  ${tid}
  @{vals}=  Get list items  jquery=[data-test-id=${tid}]  values=True
  Should be true  @{vals} == @{values}

Test id select texts are
  [Arguments]  ${tid}  @{texts}
  Wait test id visible  ${tid}
  @{vals}=  Get list items  jquery=[data-test-id=${tid}]  values=False
  Should be true  @{vals} == @{texts}


jQuery should match X times
  [Arguments]  ${selector}  ${count}
  Wait until  Javascript?  $("${selector}").length === ${count}

Test id autocomplete options check
  [Arguments]  ${tid}  ${included}  @{options}
  :FOR  ${text}  IN  @{options}
  \  Javascript?  _.includes($("div[data-test-id='${tid}'] div.autocomplete-dropdown .autocomplete-result-item span").map( function() {return this.innerText;}).get(), "${text}") === ${included}

Test id autocomplete disabled
  [Arguments]  ${tid}
  jQuery should match X times  div[data-test-id='${tid}'] .autocomplete-selection-wrapper.disabled:visible  1


Press key test id
  [Arguments]  ${tid}  ${key}
  Press key  jquery=[data-test-id=${tid}]:visible  ${key}

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
  Go to  ${LOGOUT URL}
  Wait until  Element should be visible  xpath=//section[@id='login']//h3[1]
  # These test cases will fail if errors exist
  Should be equal as integers  ${FATAL_COUNT}  0  Fatal frontend errors
  Should be equal as integers  ${ERR_COUNT}  0  Frontend errors

#
# YA
#

Fill tyoaika fields
  [Arguments]  ${startDate}=1.5.2014  ${endDate}=2.5.2014
  Wait until  Element should be visible  //section[@id='application']//div[@id='application-info-tab']
  Execute JavaScript  $(".hasDatepicker").unbind("focus");

  Wait until  Element should be visible  //input[contains(@id,'tyoaika-alkaa-ms')]
  Execute Javascript  $("input[id*='tyoaika-alkaa-ms']").val("${startDate}").change();
  Wait Until  Textfield Value Should Be  //input[contains(@id,'tyoaika-alkaa-ms')]  ${startDate}

  Wait until  Element should be visible  //input[contains(@id,'tyoaika-paattyy-ms')]
  Execute Javascript  $("input[id*='tyoaika-paattyy-ms']").val("${endDate}").change();
  Wait Until  Textfield Value Should Be  //input[contains(@id,'tyoaika-paattyy-ms')]  ${endDate}

Fill in yritys info
  [Arguments]  ${dataDocType}
  ## NOTE: When using another variable (i.e. ${dataDocType}) to set the value of a variable, the keyword "Set Variable" must be used.
  ${docSectionPath} =  Set Variable  //div[@id='application-parties-tab']//section[@data-doc-type='${dataDocType}']
  ${docjQuery}=  Set Variable  \#application-parties-tab section[data-doc-type="${dataDocType}"]
  Element should be visible  ${docSectionPath}//input[@data-docgen-path='yritys.yhteyshenkilo.henkilotiedot.sukunimi']

  Input text with jQuery  ${docjQuery} input[data-docgen-path="yritys.yhteyshenkilo.henkilotiedot.etunimi"]  John
  Input text with jQuery  ${docjQuery} input[data-docgen-path="yritys.yhteyshenkilo.henkilotiedot.sukunimi"]  Rambo
  Input text with jQuery  ${docjQuery} input[data-docgen-path="yritys.yhteyshenkilo.yhteystiedot.puhelin"]  0401234567
  Input text with jQuery  ${docjQuery} input[data-docgen-path="yritys.yritysnimi"]  Rambol Oy
  Input text with jQuery  ${docjQuery} input[data-docgen-path="yritys.liikeJaYhteisoTunnus"]  1234567-1
  Input text with jQuery  ${docjQuery} input[data-docgen-path="yritys.osoite.katu"]  Katu
  Input text with jQuery  ${docjQuery} input[data-docgen-path="yritys.osoite.postinumero"]  98765
  Input text with jQuery  ${docjQuery} input[data-docgen-path="yritys.osoite.postitoimipaikannimi"]  Sipoo
  Focus  ${docSectionPath}
  Wait until  Textfield Value Should Be  ${docSectionPath}//input[@data-docgen-path='yritys.yhteyshenkilo.henkilotiedot.sukunimi']  Rambo
  Wait for jQuery


Fill required fields for the parties
  Wait until  Element should be visible  //section[@id='application']//div[@id='application-parties-tab']
  Execute Javascript  $("input[value='yritys']").click();
  # Maksaja's default is Henkilo, that is why we have to wait its type has changed to Yritys.
  Wait until  Element should be visible  //div[@id='application-parties-tab']//section[@data-doc-type='yleiset-alueet-maksaja']//input[@data-docgen-path='yritys.yhteyshenkilo.henkilotiedot.etunimi']
  Fill in yritys info  hakija-ya
  Fill in yritys info  yleiset-alueet-maksaja

Permit subtype is
  [Arguments]  ${localizedPermitSubtype}
  ${SELECT_VISIBLE}=  Run Keyword And Return Status  Element should be visible  permitSubtypeSelect
  Run keyword If  ${SELECT_VISIBLE}  List Selection Should Be  permitSubtypeSelect  ${localizedPermitSubtype}
  Run keyword unless  ${SELECT_VISIBLE}  Element text should be  xpath=//section[@id='application']//span[@data-test-id='permit-subtype-text']  ${localizedPermitSubtype}

Check expected required fields and warnings
  [Arguments]  ${EXPECTED-TEST-IDS}
  Wait for jQuery
  ${ELEMENTS}=  Get Webelements  xpath=//div[contains(@class,'info-line')]//span[contains(@class, 'required-field-error-element-name')]
  ${FOUND-TEST-IDS}=  Create List
  ${NOT-FOUND-TEST-IDS}=  Create List
  :FOR  ${elem}  IN  @{ELEMENTS}
  \  ${TEST-ID}=  Get Element Attribute  ${elem}  data-test-id
  \  ${EXPECTED-IDX}=  Get Index From List  ${EXPECTED-TEST-IDS}  ${TEST-ID}
  \  Run keyword if  ${EXPECTED-IDX} >= 0  Remove From List  ${EXPECTED-TEST-IDS}  ${EXPECTED-IDX}
  \  Run keyword if  ${EXPECTED-IDX} < 0  Append to list  ${NOT-FOUND-TEST-IDS}  ${TEST-ID}

  ${EXPECTED-LENGTH}=  Get Length  ${EXPECTED-TEST-IDS}
  ${NOT-FOUND-LENGTH}=  Get Length  ${NOT-FOUND-TEST-IDS}
  Should Be Equal As Integers  0  ${EXPECTED-LENGTH}  Expected errors were not found: ${EXPECTED-TEST-IDS}
  Should Be Equal As Integers  0  ${NOT-FOUND-LENGTH}  Errors were not expected: ${NOT-FOUND-TEST-IDS}
