*** Settings ***

Documentation   Inforequest state handling
Suite Teardown  Logout
Resource        ../../common_resource.robot
Resource       assignment_resource.robot

*** Test Cases ***

Mikko creates two new inforequests
  Mikko logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${inforequest-handling}  ir-h${secs}
  Set Suite Variable  ${inforequest-cancelling}  ir-c${secs}
  Set Suite Variable  ${newName}  ${inforequest-cancelling}-edit
  Set Suite Variable  ${propertyId}  753-416-25-30
  Create inforequest the fast way  ${inforequest-handling}  360603.153  6734222.95  ${propertyId}  kerrostalo-rivitalo  Jiihaa
  Create inforequest the fast way  ${inforequest-cancelling}  360603.153  6734222.95  ${propertyId}  kerrostalo-rivitalo  Jiihaa
  [Teardown]  Logout

Sonja sees comment indicator on applications list
  Sonja logs in
  Wait Until  Element text should be  xpath=//table[@id='applications-list']//tr[@data-test-address='${inforequest-handling}']//div[@data-test-id='unseen-comments']  1

Authority assigns an inforequest to herself
  Inforequest is not assigned  ${inforequest-handling}
  Open inforequest  ${inforequest-handling}  ${propertyId}
  Assign inforequest to  Sibbo Sonja

Comment indicator is no longer visible (LPK-454)
  Go to page  applications
  Wait Until  Element text should be  xpath=//table[@id='applications-list']//tr[@data-test-address='${inforequest-cancelling}']//div[@data-test-id='unseen-comments']  1
  Page should not contain element  xpath=//table[@id='applications-list']//tr[@data-test-address='${inforequest-handling}']//div[@data-test-id='unseen-comments']

Sonja is marked as authority
  Inforequest is assigned to  ${inforequest-handling}  Sibbo Sonja
  [Teardown]  Logout

Mikko sees Sonja as authority
  Mikko logs in
  Inforequest is assigned to  ${inforequest-handling}  Sibbo Sonja

Mikko should be able to cancel the inforequest but not mark it as answered
  Open inforequest  ${inforequest-handling}  ${propertyId}

  Element should not be visible  //*[@data-test-id='inforequest-mark-answered']
  Element should be visible  //*[@data-test-id='inforequest-cancel-btn']

Mikko sees that inforequest is assigned to Sonja
  Application assignee span is  Sibbo Sonja

... even after reload
  Reload page and kill dev-box
  Application assignee span is  Sibbo Sonja

Mikko should be able to add attachment
  Element should be visible  //*[@data-test-id='add-inforequest-attachment']

Mikko opens inforequest for renaming and cancellation
  Open inforequest  ${inforequest-cancelling}  ${propertyId}

Mikko changes inforequest address
  Page should contain  ${inforequest-cancelling}
  Page should not contain  ${newName}
  Element should be visible  xpath=//section[@id='inforequest']//a[@data-test-id='change-location-link']
  Click element  xpath=//section[@id='inforequest']//a[@data-test-id='change-location-link']
  Textfield Value Should Be  xpath=//input[@data-test-id="application-new-address"]  ${inforequest-cancelling}
  Input text by test id  application-new-address  ${newName}
  Click enabled by test id  change-location-save
  Wait Until  Page should contain  ${newName}

Mikko cancels an inforequest
  Wait Until  Element should be enabled  xpath=//*[@data-test-id='inforequest-cancel-btn']
  Click enabled by test id  inforequest-cancel-btn
  Confirm  dynamic-yes-no-confirm-dialog

Mikko does not see the cancelled inforequest
  Wait until  Element should be visible  applications-list
  Wait Until  Inforequest is not visible  ${inforequest-cancelling}
  Wait Until  Inforequest is not visible  ${newName}

Mikko waits until the first inforequest is answered
  Logout

Authority can convert the inforequest to application
  Sonja logs in
  Open inforequest  ${inforequest-handling}  ${propertyId}
  Wait until  Inforequest state is  Avoin
  Element should be visible  //*[@data-test-id='inforequest-convert-to-application']

Authority checks property owners
  Click enabled by test id  inforequest-property-owners-btn
  # Local dummy endpoint returns 2 results
  Wait Until  Xpath Should Match X Times  //tbody[@data-test-id="owner-query-results"]/tr  2
  Click enabled by test id  ok-button

Authority adds a comment marking inforequest answered
  Wait until  Page should contain element  //section[@id='inforequest']//button[@data-test-id='comment-request-mark-answered']
  Input inforequest comment  oletko miettinyt tuulivoimaa?
  Mark answered
  Wait until  Inforequest state is  Vastattu
  Logout

Mikko sees the inforequest answered
  Mikko logs in
  Open inforequest  ${inforequest-handling}  ${propertyId}
  Wait until  Inforequest state is  Vastattu

Mikko should still be able to add attachment
  Element should be visible  //*[@data-test-id='add-inforequest-attachment']

When Mikko adds a comment inforequest goes back to Avoin
  Input inforequest comment  tuulivoima on ok.
  Wait until  Inforequest state is  Avoin
  Logout

Authority cancels the inforequest
  Sonja logs in
  Open inforequest  ${inforequest-handling}  ${propertyId}
  Wait Until  Element should be enabled  xpath=//*[@data-test-id='inforequest-cancel-btn']
  Click enabled by test id  inforequest-cancel-btn
  Confirm  dynamic-yes-no-confirm-dialog


*** Keywords ***

Inforequest state is
  [Arguments]  ${state}
  Wait until   Element Text Should Be  test-inforequest-state  ${state}

Inforequest is not visible
  [Arguments]  ${address}
  Wait until  Page Should Not Contain Element  xpath=//table[@id='applications-list']//tr[@data-test-address='${address}']

Inforequest is not assigned
  [Arguments]  ${address}
  Wait until  Element text should be  xpath=//table[@id='applications-list']//tr[@data-test-address='${address}']/td[@data-test-col-name='authority']  ${EMPTY}

Inforequest is assigned to
  [Arguments]  ${address}  ${name}
  Wait until  Element text should be  xpath=//table[@id='applications-list']//tr[@data-test-address='${address}']/td[@data-test-col-name='authority']  ${name}
