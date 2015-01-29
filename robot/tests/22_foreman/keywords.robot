*** Settings ***

Library        Collections

*** Keywords ***

Initialize
  ${applicationIds} =  Create List
  Set Suite Variable  ${applicationIds}
  ${applications} =  Create List
  Set Suite Variable  ${applications}

Mikko creates new application
  ${secs} =  Get Time  epoch
  # appname contains always the last created application
  Set Suite Variable  ${appname}  foreman-app${secs}
  Append To List  ${applications}  ${appname}
  Create application the fast way  ${appname}  753  753-416-25-22  kerrostalo-rivitalo
  ${newApplicationid} =  Get Text  xpath=//span[@data-test-id='application-id']
  Append To List  ${applicationIds}  ${newApplicationId}
  Set Suite Variable  ${applicationIds}

Mikko invites foreman to application
  Open tab  parties
  Click by test id  invite-foreman-button
  Input Text  invite-foreman-email  teppo@example.com
  Click by test id  application-invite-foreman
  Wait until  Click by test id  application-invite-foreman-close-dialog
  Wait until  Element should be visible  //section[@id='application']//span[@data-test-operation-id='tyonjohtajan-nimeaminen-v2']

Foreman applies personal information to the foreman application
  [Arguments]  ${index}
  ${name} =  Get From List  ${applications}  ${index}
  Open application at index  ${name}  753-416-25-22  1
  Wait until  Click by test id  accept-invite-button
  Open tab  parties
  Wait until  Click by test id  fill-info-button

Foreman can see the first related construction info on the second foreman application
  Open application at index  ${appname}  753-416-25-22  1
  Open tab  parties
  ${permitId} =   Get From List  ${applicationIds}  0
  Wait until  Textfield Value Should Be  xpath=//input[@data-test-id='muutHankkeet.0.luvanNumero']  ${permitId}

Foreman logs in
  Logout
  Teppo logs in
