*** Settings ***

Library        Collections

*** Keywords ***

Initialize
  ${applicationIds} =  Create List
  Set Suite Variable  ${applicationIds}

Mikko creates new application
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  foreman-app${secs}
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

Foreman applies personal information to the application
  Teppo logs in
  Open application at index  ${appname}  753-416-25-22  1
  Wait until  Click by test id  accept-invite-button
  Open tab  parties
  Wait until  Click by test id  fill-info-button
  [Teardown]  logout

Foreman can see the first construction info on the application
  Teppo logs in
  Open application at index  ${appname}  753-416-25-22  1
  Open tab  parties
  ${permitId} =   Get From List  ${applicationIds}  0
  Wait until  Textfield Value Should Be  xpath=//input[@data-test-id='muutHankkeet.0.luvanNumero']  ${permitId}
