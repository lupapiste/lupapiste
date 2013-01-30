*** Settings ***

Documentation  Common stuff for the Lupapiste Functional Tests.
...            More about robot http://code.google.com/p/robotframework/.
Library        Selenium2Library   timeout=15  run_on_failure=Log Source

*** Variables ***

${SERVER}                       http://localhost:8000
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
  Focus      ${element}
  Click element  ${element}

Wait until
  [Arguments]  ${keyword}  @{varargs}
  Wait Until Keyword Succeeds  30  0.1  ${keyword}  @{varargs}

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
  Wait Until  User is not logged in

User is not logged in
  Location should be  ${LOGIN URL}
  Title should be  Lupapiste
  # test that no data is bind.

Login
  [Arguments]  ${username}  ${password}
  Input text  login-username  ${username}
  Input text  login-password  ${password}
  Click button  login-button

User should be logged in
  [Arguments]  ${name}
  Wait Until  Element text should be  user-name  ${name}

User logs in
  [Arguments]  ${login}  ${password}  ${username}
  Login  ${login}  ${password}
  User should be logged in  ${username}

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

Number of requests on page
  [Arguments]  ${request-type}  ${amount}
  Xpath Should Match X Times  //section[@id='applications']//tr[contains(@class,'${request-type}')]  ${amount}

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
  Click element  xpath=//*[@data-test-id='${id}']

Click enabled by test id
  [Arguments]  ${id}
  Wait until page contains element  xpath=//*[@data-test-id="${id}"]
  Wait Until  Element should be enabled  xpath=//*[@data-test-id="${id}"]
  Click element  xpath=//*[@data-test-id="${id}"]

#
# Helpser for creating new inforequest and application:
#

Create application
  [Arguments]  ${address}  ${municipality}  ${propertyId}
  Go to page  applications
  Prepare new request  ${address}  ${municipality}  ${propertyId}
  Click by test id  create-application
  Wait Until  Element should be visible  application
  Wait Until  Element should contain  xpath=//span[@data-test-id='application-title']  ${address}

Create inforequest
  [Arguments]  ${address}  ${municipality}  ${propertyId}  ${message}
  Prepare new request  ${address}  ${municipality}  ${propertyId}
  Click by test id  create-proceed-to-inforequest
  Wait until page contains element  xpath=//textarea[@data-test-id="create-inforequest-message"]
  Input text  xpath=//textarea[@data-test-id="create-inforequest-message"]  ${message}
  Click by test id  create-inforequest
  Wait Until  Element should be visible  inforequest
  Wait Until  Element should contain  xpath=//span[@data-test-id='inforequest-title']  ${address}

Prepare new request
  [Arguments]  ${address}  ${municipality}  ${propertyId}
  Execute Javascript  window.location.hash = "!/applications";
  Click by test id  applications-create-new
  Input text by test id  create-address  ${address}
  Select From List by test id  create-municipality-select  ${municipality}  
  Input text by test id  create-property-id  ${propertyId}
  Click by test id  create-continue
  Wait and click  xpath=//div[@class="tree-magic"]/a[text()="Rakentaminen ja purkaminen"]
  Wait and click  xpath=//div[@class="tree-magic"]/a[text()="Uuden rakennuksen rakentaminen"]
  Wait and click  xpath=//div[@class="tree-magic"]/a[text()="Asuinrakennus"]

#
# Jump to application or inforequest:
#

Open the request
  [Arguments]  ${address}
  Go to page  applications
  Wait until  Click element  xpath=//table[@id='applications-list']//tr[@data-test-address='${address}']/td

Open the application
  [Arguments]  ${address}
  Open the request  ${address}
  Wait until  Element should contain  xpath=//span[@data-test-id='application-title']  ${address}

Open the inforequest
  [Arguments]  ${address}
  Open the request  ${address}
  Wait until  Element should contain  xpath=//span[@data-test-id='inforequest-title']  ${address}

Request should be visible
  [Arguments]  ${address}
  Element should be visible  xpath=//table[@id='applications-list']//tr[@data-test-address='${address}']

Request should not be visible
  [Arguments]  ${address}
  Element should not be visible  xpath=//table[@id='applications-list']//tr[@data-test-address='${address}']

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
  Click element  debug-apply-minimal
  Wait until  Element should be visible  debug-apply-done

#
# Application state check:
#

Application state should be
  [Arguments]  ${state}
  ${s} =  Get Element Attribute  xpath=//span[@data-test-id='application-state']@data-test-state
  Should be equal  ${s}  ${state}
