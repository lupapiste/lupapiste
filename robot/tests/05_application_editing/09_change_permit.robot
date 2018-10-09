*** Settings ***

Documentation   On R type of application, applying a change permit
Suite Setup     Apply submitted-application fixture now
Suite Teardown  Logout
Resource        ../../common_resource.robot
Resource        ../39_pate/pate_resource.robot

*** Test Cases ***

Sonja opens Pena's application
  Sonja logs in
  Set Suite Variable  ${appname}  Penakuja 3
  Set Suite Variable  ${propertyid}  753-416-55-7
  Set Suite Variable  ${applicationid}  LP-753-2018-90001

  Open application  ${appname}  ${propertyid}
  Wait until  Application state should be  submitted

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

  # Go to the link permit via link
  Click by test id  test-application-link-permit-lupapistetunnus

  Wait Until  Element should not be visible  xpath=//*[@data-test-id='test-application-link-permit-lupapistetunnus']
  Wait Until  Element should be visible  xpath=//*[@data-test-id='test-application-app-linking-to-us']
  Element Text Should Be  xpath=//*[@data-test-id='test-application-app-linking-to-us']  ${newApplicationid} - Asuinkerrostalon tai rivitalon rakentaminen Muutoslupa
