*** Settings ***

Documentation   Review 'debugger', loops through list of applications and takes screenshots
Resource        common_resource.robot

*** Variables ***
${SERVER}         https://www.lupapiste.fi
${BROWSER}        chrome
${APP_PATH}       ${SERVER}/app/fi/authority#!/application

*** Test Cases ***

Start
  Set Screenshot Directory  /tmp/screenshots
  Open browser  ${SERVER}  ${BROWSER}
  Maximize browser window
  Debug
  ${contents}=  Get File  /tmp/applications.txt
  @{lines}=  Split to lines  ${contents}
  FOR  ${LP}  IN  @{lines}
    Go to  ${APP_PATH}/${LP}
    Wait until  Element Text Should Be  xpath=//span[@data-test-id='application-id']  ${LP}
    Open tab  tasks
    Wait until  Element should be visible  xpath=//table[@class="reviews-table"]//tbody/tr
    Sleep  0.1s
    Wait until  Scroll to  div[data-test-id='reviews-table-end']
    Capture Page Screenshot  ${LP}.png
    Sleep  0.5s
  Debug
  Close all browsers
