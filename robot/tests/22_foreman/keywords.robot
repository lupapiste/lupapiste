*** Settings ***

Library        Collections

*** Keywords ***

Initialize foreman
  Apply minimal fixture now
  ${applicationIds} =  Create List
  Set Suite Variable  ${applicationIds}
  ${applications} =  Create List
  Set Suite Variable  ${applications}
  ${foremanApps} =  Create List
  Set Suite Variable  ${foremanApps}

Create project application
  [Arguments]  ${state}=draft
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  foreman-app${secs}
  Append To List  ${applications}  ${appname}
  Create application with state  ${appname}  753-416-25-22  kerrostalo-rivitalo  ${state}
  ${newApplicationid} =  Get Text  xpath=//span[@data-test-id='application-id']
  Append To List  ${applicationIds}  ${newApplicationId}

Go back to project application
  Scroll and click test id  test-application-link-permit-lupapistetunnus
  Wait until  Element should be visible  //section[@id='application']//span[@data-test-primary-operation-id='kerrostalo-rivitalo']

Open foreman application
  [Arguments]  ${index}
  ${foremanAppId} =  Get From List  ${foremanApps}  ${index}
  Open application by id  ${foremanAppId}
  Wait until  jQuery should match X times  span[data-test-primary-operation-id=tyonjohtajan-nimeaminen-v2]:visible  1

Open foreman app from list
  [Arguments]  ${index}
  ${foremanAppId} =  Get From List  ${foremanApps}  ${index}
  Click element  //div[contains(@class,"nav-top")]//div[contains(@class,"header-box")]//a[@title="Hankkeet"]
  Wait until  Element should be visible  //table[@id="applications-list"]//td[@data-test-col-name="operation"]
  Click element  //table[@id="applications-list"]//tr[@data-id="${foremanAppId}"]//td[@data-test-col-name="operation"]
  Wait until  Element should be visible  xpath=//section[@id='application']//span[@data-test-primary-operation-id='tyonjohtajan-nimeaminen-v2']

Open foreman accordions
  Open accordions  parties
  ${toggled}=  Run Keyword And Return Status  Element should be visible  xpath=//button[@data-test-id='accordion-application-foreman-header' and contains(@class,'toggled')]
  Run keyword unless  ${toggled}  Scroll to and click xpath results  //button[@data-test-id='accordion-application-foreman-header' and not(contains(@class,'toggled'))]
  Wait until  Element should be visible  xpath=//section[@id='accordion-application-foreman']//div[@data-test-id='application-foreman-template']

Sonja invites foreman to application
  Open tab  parties
  Open foreman accordions
  Wait until  Click by test id  invite-foreman-button
  Wait until  Element should be visible  invite-foreman-email
  Wait until  Input Text  invite-foreman-email  teppo@example.com
  Click by test id  application-invite-foreman
  Wait until  Click by test id  application-invite-foreman-close-dialog
  Wait until  Element should be visible  //section[@id='application']//span[@data-test-primary-operation-id='tyonjohtajan-nimeaminen-v2']
  ${foremanAppId} =  Get Text  xpath=//section[@id='application']//span[@data-test-id='application-id']
  Append To List  ${foremanApps}  ${foremanAppId}

Foreman accepts invitation
  [Arguments]  ${index}
  Open foreman app from list  ${index}
  Wait until  Confirm yes no dialog
  Open tab  parties
  Wait until  Page should contain  Hyväksynyt valtuutuksen

Foreman disables attachment import checkbox
  Unselect checkbox  fill-attachments-checkbox

Foreman applies personal information to the foreman application
  Wait until  Click by test id  fill-info-button

Foreman personal information has been applied
  Wait until  Textfield value should be  xpath=//section[@data-doc-type='tyonjohtaja-v2']//input[@data-docgen-path='henkilotiedot.etunimi']  Teppo

Foreman personal attachments have been copied
  Open tab  attachments
  Wait Until  Element should be visible  jquery=div#application-attachments-tab a:contains('${PDF_TESTFILE_NAME}')
  Open tab  parties

Foreman personal attachments have not been copied
  Open tab  attachments
  Sleep  2s
  Element should not be visible  jquery=div#application-attachments-tab a:contains('${PDF_TESTFILE_NAME}')
  Open tab  parties

Submit foreman base app
  [Arguments]  ${index}
  Open foreman application  ${index}
  Scroll and click test id  test-application-link-permit-lupapistetunnus
  Wait until  Primary operation is  kerrostalo-rivitalo
  Submit application

Foreman accepts invitation and fills info
  Wait until  Click visible test id  accept-invite-button
  Wait until  Element should not be visible  xpath=//section[@id='application']//button[@data-test-id='accept-invite-button']
  Wait for jQuery
  Wait until  Scroll and click test id  fill-info-button
  Wait for jQuery

Set foreman role
  [Arguments]  ${role}
  Wait until  Select From List by test id  kuntaRoolikoodi  ${role}

Foreman sets role and difficulty to foreman application
  [Arguments]  ${index}  ${role}  ${difficulty}
  Open foreman app from list  ${index}
  Test id visible  confirm-no
  Deny yes no dialog
  Open tab  parties
  Foreman accepts invitation and fills info
  Wait until  Select From List by test id  kuntaRoolikoodi  ${role}
  Wait until  Select From List by test id  patevyysvaatimusluokka  ${difficulty}

Foreman submit application
  [Arguments]  ${index}
  Open foreman app from list  ${index}
  Sleep  1s
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
  Go to  ${SERVER}/app/fi/${user-role}
  Execute Javascript  window.location.hash = "!/application/${appId}";
  Wait until  Element text should be  xpath=//span[@data-test-id='application-id']  ${appId}

Project application is open
  ${appId} =   Get From List  ${applicationIds}  0
  Wait until  Element Text Should Be  xpath=//section[@id='application']//span[@data-test-id='application-id']  ${appId}

Open project application
  ${appId} =   Get From List  ${applicationIds}  0
  Open application by id  ${appId}
  Project application is open

Open project app from list
  ${appId} =   Get From List  ${applicationIds}  0
  Click element  //div[contains(@class,"nav-top")]//div[contains(@class,"header-box")]//a[@title="Hankkeet"]
  Wait until  Element should be visible  //table[@id="applications-list"]//td[@data-test-col-name="operation"]
  Click element  //table[@id="applications-list"]//tr[@data-id="${appId}"]//td[@data-test-col-name="operation"]
  Project application is open

Foreman history should have text X times
  [Arguments]  ${text}  ${times}
  Xpath Should Match X Times  //foreman-history//td[contains(., '${text}')]  ${times}

Foreman logs in
  Logout
  Teppo logs in

Add työnjohtaja task to current application
  [Arguments]  ${role}
  Open tab  tasks
  Click enabled by test id  application-new-task
  Wait until  Element should be visible  dialog-create-task
  Wait until  Element should be visible  choose-task-type
  Select From List By Value  choose-task-type   task-vaadittu-tyonjohtaja
  Input text  create-task-name  ${role}
  Click enabled by test id  create-task-save
  Wait until  Element should not be visible  dialog-create-task

Required foreman state is
  [Arguments]  ${role}  ${state}
  ${s} =  Get Element Attribute  xpath=//table[contains(@class, 'tasks-foreman')]//tr[@data-test-name="${role}"]//td[@data-test-state]  data-test-state
  Should be equal  ${s}  ${state}
