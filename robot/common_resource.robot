*** Settings ***

Documentation  Common stuff for the Lupapiste Functional Tests.
...            More about robot http://code.google.com/p/robotframework/.
Library        Selenium2Library   timeout=15  run_on_failure=Log Source

*** Variables ***

${SERVER}                       http://localhost:8000
${WAIT_DELAY}                   10
${BROWSER}                      firefox
${DEFAULT_SPEED}                0
${SLOW_SPEED}                   0.2
${SLOWEST_SPEED}                0.5

${LOGIN URL}                    ${SERVER}/fi/welcome#!/login
${LOGOUT URL}                   ${SERVER}/fi/logout
${APPLICATIONS PATH}            /fi/applicant#!/applications
${AUTHORITY APPLICATIONS PATH}  /fi/authority#!/applications
${FIXTURE URL}                  ${SERVER}/fixture

${SELENIUM}                     ${EMPTY}

*** Keywords ***

Browser
  [Arguments]  ${url}
  Open browser  ${url}  ${BROWSER}   remote_url=${SELENIUM}

Open browser to login page
  Browser  ${LOGIN URL}
  Maximize browser window
  Set selenium speed  ${DEFAULT_SPEED}
  Title should be  Lupapiste

Go to login page
  Go to  ${LOGIN URL}
  Title should be  Lupapiste

Applications page should be open
  Location should contain  ${APPLICATIONS PATH}
  Title should be  Lupapiste

Authority applications page should be open
  Location should contain  ${AUTHORITY APPLICATIONS PATH}
  #Title should be  Lupapiste - Viranomainen

Authority-admin front page should be open
  Wait until page contains element  admin-header

Admin front page should be open
  Wait until page contains element  admin-header

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
  Focus  ${element}
  Wait until  Element should be visible  ${element}
  Click element  ${element}

Wait until
  [Arguments]  ${keyword}  @{varargs}
  Wait Until Keyword Succeeds  ${WAIT_DELAY}  0.1  ${keyword}  @{varargs}

Wait for jQuery
  Wait For Condition  return (typeof jQuery !== "undefined") && jQuery.active===0;  10

Kill dev-debug
  Execute Javascript  $(".dev-debug").hide();

Show dev-debug
  Execute Javascript  $(".dev-debug").show();

#
# Navigation
#

Go to page
  [Arguments]  ${page}
  Execute Javascript  window.location.hash = "!/${page}";
  Wait until  Element should be visible  ${page}
  Sleep  1

Open tab
  [Arguments]  ${name}
  Click by test id  application-open-${name}-tab
  Wait until  Element should be visible  application-${name}-tab

Logout
  Go to  ${LOGIN URL}
  Wait until page contains element  login-username

#
# Login stuff
#

User should not be logged in
  # Wait for login query to complete
  Wait for jQuery
  Wait Until  User is not logged in

User is not logged in
  Location should be  ${LOGIN URL}
  Title should be  Lupapiste
  # test that no data is bind.

Login
  [Arguments]  ${username}  ${password}
  Input text  login-username  ${username}
  Input text  login-password  ${password}
  # for IE8
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
  Kill dev-debug

Applicant logs in
  [Arguments]  ${login}  ${password}  ${username}
  User logs in  ${login}  ${password}  ${username}
  User role should be  applicant
  Applications page should be open

Authority logs in
  [Arguments]  ${login}  ${password}  ${username}
  User logs in  ${login}  ${password}  ${username}
  User role should be  authority
  Authority applications page should be open

Authority-admin logs in
  [Arguments]  ${login}  ${password}  ${username}
  User logs in  ${login}  ${password}  ${username}
  Authority-admin front page should be open

Admin logs in
  [Arguments]  ${login}  ${password}  ${username}
  User logs in  ${login}  ${password}  ${username}
  User role should be  admin
  Admin front page should be open

User role should be
  [Arguments]  ${role}
  Wait Until   Page should contain element  user-name
  ${found_role} =  Get Element Attribute  user-name@data-test-role
  Should be equal  ${role}  ${found_role}

As Mikko
  Open browser to login page
  Mikko logs in

As Teppo
  Open browser to login page
  Teppo logs in

As Veikko
  Open browser to login page
  Veikko logs in

As Sonja
  Open browser to login page
  Sonja logs in

As Sipoo
  Open browser to login page
  Sipoo logs in

As Solitaadmin
  Open browser to login page
  Solitaadmin logs in

Mikko logs in
  Applicant logs in  mikko@example.com  mikko69  Mikko Intonen

Teppo logs in
  Applicant logs in  teppo@example.com  teppo69  Teppo Nieminen

Veikko logs in
  Authority logs in  veikko  veikko  Veikko Viranomainen

Sonja logs in
  Authority logs in  sonja  sonja  Sonja Sibbo

Sipoo logs in
  Authority-admin logs in  sipoo  sipoo  Simo Suurvisiiri

SolitaAdmin logs in
  Admin logs in  admin  admin  Admin Admin
  Wait until page contains element  admin-header

#
# Helpers for cases when target element is identified by "data-test-id" attribute:
#

Input text by test id
  [Arguments]  ${id}  ${value}
  Wait until page contains element  xpath=//input[@data-test-id="${id}"]
  Input text  xpath=//input[@data-test-id="${id}"]  ${value}

Select From List by test id
  [Arguments]  ${id}  ${value}
  Wait until page contains element  xpath=//select[@data-test-id="${id}"]
  Select From List  xpath=//select[@data-test-id="${id}"]  ${value}

Click by test id
  [Arguments]  ${id}
  Wait until  Page should contain element  xpath=//*[@data-test-id='${id}']
  Wait until  Element should be visible  xpath=//*[@data-test-id='${id}']
  # Make sure the element is visible on browser view before clicking. Take header heigth into account.
  Execute Javascript  window.scrollTo(0, $("[data-test-id='${id}']").position().top - 130);
  # IE8
  Focus  xpath=//*[@data-test-id='${id}']
  Wait until  Element should be visible  xpath=//*[@data-test-id='${id}']
  Click element  xpath=//*[@data-test-id='${id}']

Click enabled by test id
  [Arguments]  ${id}
  Wait until  Page should contain element  xpath=//*[@data-test-id="${id}"]
  Wait Until  Element should be enabled  xpath=//*[@data-test-id="${id}"]
  Wait Until  Element should be visible  xpath=//*[@data-test-id="${id}"]
  Click element  xpath=//*[@data-test-id="${id}"]

#
# Helpser for inforequest and application crud operations:
#

Create application the fast way
  [Arguments]  ${address}  ${municipality}  ${propertyId}
  Execute Javascript  ajax.command("create-application", {"infoRequest":false,"operation":"asuinrakennus","y":0,"x":0,"address":"${address}","propertyId":"${propertyId}","messages":[],"municipality":"${municipality}"}).success(function(){window.location.hash = "!/applications";}).call();
  Reload Page
  Open application  ${address}  ${propertyId}

Create inforequest the fast way
  [Arguments]  ${address}  ${municipality}  ${propertyId}  ${message}
  Execute Javascript  ajax.command("create-application", {"infoRequest":true,"operation":"asuinrakennus","y":0,"x":0,"address":"${address}","propertyId":"${propertyId}","messages":["${message}"],"municipality":"${municipality}"}).success(function(){window.location.hash = "!/applications";}).call();
  Reload Page
  Open inforequest  ${address}  ${propertyId}

Create application
  [Arguments]  ${address}  ${municipality}  ${propertyId}  ${button}
  Go to page  applications
  Prepare new request  ${address}  ${municipality}  ${propertyId}  ${button}
  Click by test id  create-application
  Wait Until  Element should be visible  application
  Wait Until  Element Text Should Be  xpath=//span[@data-test-id='application-property-id']  ${propertyId}

Create inforequest
  [Arguments]  ${address}  ${municipality}  ${propertyId}  ${message}  ${button}
  Prepare new request  ${address}  ${municipality}  ${propertyId}  ${button}
  Click by test id  create-proceed-to-inforequest
  # Needed for animation to finish.
  Sleep  1
  Wait until page contains element  xpath=//textarea[@data-test-id="create-inforequest-message"]
  Wait until  Element should be visible  xpath=//textarea[@data-test-id="create-inforequest-message"]
  Input text  xpath=//textarea[@data-test-id="create-inforequest-message"]  ${message}
  Click by test id  create-inforequest
  Wait Until  Element should be visible  inforequest
  Wait Until  Element Text Should Be  xpath=//span[@data-test-id='inforequest-property-id']  ${propertyId}

Prepare new request
  [Arguments]  ${address}  ${municipality}  ${propertyId}  ${button}
  Execute Javascript  window.location.hash = "!/applications";
  Click by test id  ${button}
  Wait and click  xpath=//button[@data-test-id="create-search-button"]
  # for IE8
  Focus  xpath=//input[@data-test-id="create-address"]
  Input text by test id  create-address  ${address}
  Select From List by test id  create-municipality-select  ${municipality}
  Input text by test id  create-property-id  ${propertyId}
  Sleep  1
  Click by test id  create-continue
  # Going too fast causes negative margins
  Set Selenium Speed  ${SLOW_SPEED}
  Wait and click  xpath=//div[@class="tree-magic"]/a[text()="Rakentaminen ja purkaminen"]
  Wait and click  xpath=//div[@class="tree-magic"]/a[text()="Uuden rakennuksen rakentaminen"]
  Wait and click  xpath=//div[@class="tree-magic"]/a[text()="Asuinrakennuksen rakentaminen"]
  Wait until  Element should be visible  xpath=//div[@class='tree-result']
  Set Selenium Speed  ${DEFAULT_SPEED}

# Closes the application that is currently open by clicking cancel button
Close current application
  Wait Until  Element Should Be Enabled  xpath=//button[@data-test-id="application-cancel-btn"]
  Click by test id  application-cancel-btn
  Confirm  dialog-confirm-cancel

Confirm
  [Arguments]  ${modalId}
  Wait until  Element should be visible  xpath=//div[@id="${modalId}"]//button[@data-test-id="confirm-yes"]
  Click Element  xpath=//div[@id="${modalId}"]//button[@data-test-id="confirm-yes"]
  Wait Until  Element Should Not Be Visible  ${modalId}

It is possible to add operation
  Wait until  Element should be visible  xpath=//button[@data-test-id="add-operation"]
#
# Jump to application or inforequest:
#

Open the request
  [Arguments]  ${address}
  Go to page  applications
  Wait until  Click element  xpath=//table[@id='applications-list']//tr[@data-test-address='${address}']/td

Open application
  [Arguments]  ${address}  ${propertyId}
  Open the request  ${address}
  Wait until  Element Text Should Be  xpath=//span[@data-test-id='application-property-id']  ${propertyId}

Open inforequest
  [Arguments]  ${address}  ${propertyId}
  Open the request  ${address}
  Wait until  Element Text Should Be  xpath=//span[@data-test-id='inforequest-property-id']  ${propertyId}

Request should be visible
  [Arguments]  ${address}
  Wait Until  Element should be visible  xpath=//table[@id='applications-list']//tr[@data-test-address='${address}']

Request should not be visible
  [Arguments]  ${address}
  Wait Until  Element should not be visible  xpath=//table[@id='applications-list']//tr[@data-test-address='${address}']

#
# Comments:
#

Add comment
  [Arguments]  ${message}
  Open tab  conversation
  Input text  xpath=//textarea[@data-test-id='application-new-comment-text']  ${message}
  Click by test id  application-new-comment-btn
  Wait until  Element should be visible  xpath=//table[@data-test-id='application-comments-table']//td[text()='${message}']

#
# Quick, jettison the db...
#

Apply minimal fixture now
  Show dev-debug
  Click element  debug-apply-minimal
  Wait until  Element should be visible  debug-apply-done
  Kill dev-debug

#
# Application state check:
#

Application state should be
  [Arguments]  ${state}
  ${s} =  Get Element Attribute  xpath=//span[@data-test-id='application-state']@data-test-state
  Should be equal  ${s}  ${state}

Permit type should be
  [Arguments]  ${type}
  Element Text Should Be  xpath=//span[@data-bind='ltext: permitType']  ${type}
