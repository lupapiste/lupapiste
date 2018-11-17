*** Settings ***

Documentation   Sonja sends comment to Ronja
Resource        ../../common_resource.robot

*** Test Cases ***

Sonja sets up an application
  Sonja logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${message}  Moi Ronja ${secs}
  Set Suite Variable  ${appname}  notice${secs}
  Set Suite Variable  ${propertyId}  753-0-20-4
  Create application the fast way  ${appname}  ${propertyId}  kerrostalo-rivitalo

Sonja sends message to Ronja
  Open side panel  conversation
  Input text  xpath=//div[@id='conversation-panel']//textarea[@data-test-id='application-new-comment-text']  ${message}
  List Selection Should Be  side-panel-assigneed-authority  ${EMPTY}
  Select From List by id and label  side-panel-assigneed-authority  Sibbo Ronja
  Click by test id  application-new-comment-btn
  Wait until  Element should be visible  xpath=//div[@id='conversation-panel']//div[@data-test-id='comments-table']//span[text()='${message}']
  Page Should Contain Element  xpath=//div[@id='conversation-panel']//div[@data-test-id='comments-table']//span[contains(@class, 'to') and contains(text(), 'Ronja')]

Selection of to has been reset
  List Selection Should Be  side-panel-assigneed-authority  ${EMPTY}

Ronja got email
  Open last email
  Wait Until  Page Should Contain  ronja.sibbo@sipoo.fi
  Page Should Contain  /app/fi/authority

Open the application
  ## Click the first link
  Click link  xpath=//a
  Wait until  Element Text Should Be  xpath=//section[@id='application']//span[@data-test-id='application-property-id']  ${propertyId}

Comment is visible
  Wait until  Element should be visible  xpath=//div[@id='conversation-panel']//div[@data-test-id='comments-table']//span[text()='${message}']
  [Teardown]  Logout
