*** Settings ***

Documentation   On R type of application, applying a change permit
Suite Setup     Apply minimal fixture now
Suite Teardown  Logout
Resource        ../../common_resource.robot
Resource        ../39_pate/pate_resource.robot

*** Variables ***

${appname}        Penakuja 3
${propertyid}     753-416-55-7

*** Test Cases ***

Pena creates application and submits it
  Pena logs in
  Create application the fast way  ${appname}  ${propertyId}  kerrostalo-rivitalo
  ${applicationId}=  Get text  jquery=span[data-test-id=application-id]
  Set suite variable  ${applicationId}
  Submit application
  [Teardown]  Logout

Sonja opens Pena's application
  Sonja logs in
  Open application  ${appname}  ${propertyid}
  Wait until  Application state should be  submitted

No change permit premises note
  Open tab  info
  Open accordions  info
  No such test id  change-permit-premises-note

Sonja approves application, no dialog shown
  Open tab  requiredFieldSummary
  Approve application no dialogs

Sonja gives it a verdict
  Element should not be visible  xpath=//*[@data-test-id='change-permit-create-btn']
  Open tab  verdict
  Fetch verdict

Sonja creates a change permit
  Wait Until  Element should be visible  xpath=//*[@data-test-id='change-permit-create-btn']
  Element Text Should Be  xpath=//span[@data-test-id='application-id']  ${applicationid}
  Element Text Should Be  xpath=//span[@data-test-id='application-property-id']  ${propertyid}
  Element Should Be Visible  xpath=//span[@data-test-id='application-submitted-date']
  Element Should Not Be Visible  xpath=//*[@data-test-id='test-application-app-linking-to-us']

  # Create and open the change permit by pressing button
  Click enabled by test id  change-permit-create-btn
  Confirm  dynamic-yes-no-confirm-dialog

  Wait until  Application state should be  open
  Element Should Not Be Visible  xpath=//*[@data-test-id='change-permit-create-btn']

  ${newApplicationid} =  Get Text  xpath=//span[@data-test-id='application-id']
  Set Suite Variable  ${newApplicationid}
  Should Not Be Equal As Strings  ${newApplicationid}  ${applicationid}

  Application address should be  ${appname}
  Element Text Should Be  xpath=//span[@data-test-id='application-property-id']  ${propertyid}
  Element Should Not Be Visible  xpath=//span[@data-test-id='application-submitted-date']

  Element Should Not Be Visible  xpath=//*[@data-test-id='test-application-app-linking-to-us']
  Element Should Be Visible  xpath=//*[@data-test-id='test-application-link-permit-lupapistetunnus']
  Element Text Should Be  xpath=//*[@data-test-id='test-application-link-permit-lupapistetunnus']  ${applicationid} - Asuinkerrostalon tai rivitalon rakentaminen

Change permit premises note
  Open tab  info
  Open accordions  info
  Wait test id visible  change-permit-premises-note

Submit and approve the change permit, dialog is shown
  Submit application
  Fill test id  bulletin-op-description-summaryTab  Metamorphosis
  Click enabled by test id  approve-application-summaryTab
  Wait until element is visible  jquery=div.yes-no-dialog div.content p.dialog-desc:contains("Olet siirtämässä muutoslupaa käsittelyyn.")
  Test id text is  confirm-yes  SIIRRÄ KÄSITTELYYN
  Test id text is  confirm-no   Peruuta
  Confirm yes no dialog
  Wait until  Application state should be  sent

No more change permit premises note
  Open tab  info
  Open accordions  info
  No such test id  change-permit-premises-note

Go to the base application
  # Go to the link permit via link
  Click by test id  test-application-link-permit-lupapistetunnus

  Wait Until  Element should not be visible  xpath=//*[@data-test-id='test-application-link-permit-lupapistetunnus']
  Wait Until  Element should be visible  xpath=//*[@data-test-id='test-application-app-linking-to-us']
  Element Text Should Be  xpath=//*[@data-test-id='test-application-app-linking-to-us']  ${newApplicationid} - Asuinkerrostalon tai rivitalon rakentaminen Muutoslupa
