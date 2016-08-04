*** Settings ***

Library        Collections

*** Keywords ***

Initialize
  ${applicationIds} =  Create List
  Set Suite Variable  ${applicationIds}
  ${applications} =  Create List
  Set Suite Variable  ${applications}
  ${foremanApps} =  Create List
  Set Suite Variable  ${foremanApps}

Create project application
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  foreman-app${secs}
  Append To List  ${applications}  ${appname}
  Create application the fast way  ${appname}  753-416-25-22  kerrostalo-rivitalo
  ${newApplicationid} =  Get Text  xpath=//span[@data-test-id='application-id']
  Append To List  ${applicationIds}  ${newApplicationId}

Go back to project application
  Click by test id  test-application-link-permit-lupapistetunnus
  Wait until  Element should be visible  //section[@id='application']//span[@data-test-primary-operation-id='kerrostalo-rivitalo']

Open foreman application
  [Arguments]  ${index}
  ${foremanAppId} =  Get From List  ${foremanApps}  ${index}
  Open application by id  ${foremanAppId}
  Wait Until  Page should contain  Työnjohtajan nimeäminen

Open foreman accordions
  Open accordions  parties
  Execute Javascript  $("button[data-test-id=accordion-application-foreman-header]:not(.toggled)").click();

Sonja invites foreman to application
  Open tab  parties
  Open foreman accordions
  Click by test id  invite-foreman-button
  Input Text  invite-foreman-email  teppo@example.com
  Click by test id  application-invite-foreman
  Wait until  Click by test id  application-invite-foreman-close-dialog
  Wait until  Element should be visible  //section[@id='application']//span[@data-test-primary-operation-id='tyonjohtajan-nimeaminen-v2']
  ${foremanAppId} =  Get Text  xpath=//section[@id='application']//span[@data-test-id='application-id']
  Append To List  ${foremanApps}  ${foremanAppId}

Foreman applies personal information to the foreman application
  [Arguments]  ${index}
  Open foreman application  ${index}
  Wait until  Confirm yes no dialog
  Open tab  parties
  Wait until  Page should contain  Hyväksynyt valtuutuksen
  Open foreman accordions
  Wait until  Click by test id  fill-info-button
  Wait for jQuery

Submit foreman base app
  [Arguments]  ${index}
  Open foreman application  ${index}
  Click by test id  test-application-link-permit-lupapistetunnus
  Wait until  Primary operation is  kerrostalo-rivitalo
  Submit application


Foreman accepts invitation and fills info
  Wait until  Click by test id  accept-invite-button
  Wait until  Element should not be visible  xpath=//section[@id='application']//button[@data-test-id='accept-invite-button']
  Wait for jQuery
  Open foreman accordions
  Wait until  Click by test id  fill-info-button
  Wait for jQuery
  Open foreman accordions

Foreman sets role and difficulty to foreman application
  [Arguments]  ${index}  ${role}  ${difficulty}
  Open foreman application  ${index}
  Deny yes no dialog
  Open tab  parties
  Open foreman accordions
  Foreman accepts invitation and fills info
  Wait until  Select From List by test id  kuntaRoolikoodi  ${role}
  Wait until  Select From List by test id  patevyysvaatimusluokka  ${difficulty}

Foreman submit application
  [Arguments]  ${index}
  Open foreman application  ${index}
  Select From List By Value  permitSubtypeSelect  tyonjohtaja-hakemus
  Positive indicator should be visible
  Submit application

Verdict for foreman application
  [Arguments]  ${index}
  Open foreman application  ${index}
  Submit empty verdict  foremanVerdictGiven

Open application by id
  [Arguments]  ${appId}
  ${user-role} =  Get role
  Go to  ${SERVER}/app/fi/${user-role}#!/application/${appId}
  Wait until  Element Should Be Visible  xpath=//section[@id='application']

Project application is open
  ${appId} =   Get From List  ${applicationIds}  0
  Wait until  Element Text Should Be  xpath=//section[@id='application']//span[@data-test-id='application-id']  ${appId}

Open project application
  ${appId} =   Get From List  ${applicationIds}  0
  Open application by id  ${appId}
  Project application is open

Foreman history should have text X times
  [Arguments]  ${text}  ${times}
  Xpath Should Match X Times  //foreman-history//td[contains(., '${text}')]  ${times}

Check related project
  [Arguments]  ${foreman-app-index}
  ${permitId} =   Get From List  ${applicationIds}  ${foreman-app-index}
  Test id input is  'muutHankkeet.0.luvanNumero'  ${permitId}

Foreman can see the first related construction info on the second foreman application
  Open foreman application  1
  Open tab  parties

  ${rows} =  Get Matching Xpath Count  //table[@data-test-id="foreman-other-applications-table"]/tbody[1]/tr
  ${lastRowIndex} =  Evaluate  ${rows} - 1
  ${permitId} =   Get From List  ${applicationIds}  0

  Wait until  Textfield Value Should Be  xpath=//input[@data-test-id='muutHankkeet.${lastRowIndex}.luvanNumero']  ${permitId}

Foreman logs in
  Logout
  Teppo logs in

Add työnjohtaja task to current application
  [Arguments]  ${role}
  Open tab  tasks
  Click enabled by test id  application-new-task
  Wait until  Element should be visible  dialog-create-task
  Select From List By Value  choose-task-type   task-vaadittu-tyonjohtaja
  Input text  create-task-name  ${role}
  Click enabled by test id  create-task-save
  Wait until  Element should not be visible  dialog-create-task

Required foreman state is
  [Arguments]  ${role}  ${state}
  ${s} =  Get Element Attribute  xpath=//table[@class="tasks-foreman"]//tr[@data-test-name="${role}"]//td[@data-test-state]@data-test-state
  Should be equal  ${s}  ${state}
