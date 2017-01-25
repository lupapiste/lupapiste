*** Settings ***

Documentation   Asianhallinta transfer
Resource        ../../common_resource.robot
Variables      ../06_attachments/variables.py

*** Test Cases ***

Mikko creates an application, permit-type R
  Mikko logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  approve-app${secs}
  Create application the fast way  ${appname}  297-34-107-10  kerrostalo-rivitalo

Mikko sets himself the applicant
  Open tab  parties
  Open accordions  parties
  Select From List  //section[@data-doc-type="hakija-r"]//select[@name="henkilo.userId"]  Intonen Mikko
  Wait Until  Textfield Value Should Be  //section[@data-doc-type="hakija-r"]//input[@data-docgen-path="henkilo.henkilotiedot.etunimi"]  Mikko

Mikko can't approve application
  Wait Until  Element should be disabled  xpath=//section[@id="application"]//button[@data-test-id='to-asianhallinta']

Mikko decides to submit application
  Submit application

Mikko still can't move to asianhallinta because he is applicant
  Wait Until  Element should be disabled  xpath=//section[@id="application"]//button[@data-test-id='to-asianhallinta']
  [Teardown]  logout

Velho logs in for approval
  Velho logs in
  Open application  ${appname}  297-34-107-10

Velho does not see move to asianhallinta button because application is of wrong type
  Element should not be visible  xpath=//section[@id="application"]//button[@data-test-id='to-asianhallinta']
  [Teardown]  logout


Mikko creates an application, permit-type P
  Mikko logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  approve-app${secs}
  Create application the fast way  ${appname}  297-34-107-10  poikkeamis

Mikko sets himself the applicant and submits, doesn't see asianhallinta button and logsout
  Open tab  parties
  Open accordions  parties
  Select From List  //section[@data-doc-type="hakija"]//select[@name="henkilo.userId"]  Intonen Mikko
  Wait Until  Textfield Value Should Be  //section[@data-doc-type="hakija"]//input[@data-docgen-path="henkilo.henkilotiedot.etunimi"]  Mikko
  Submit application
  Wait Until  Element should be disabled  xpath=//section[@id="application"]//button[@data-test-id='to-asianhallinta']
  [Teardown]  logout

Velho logs in again for approval
  Velho logs in
  Open application  ${appname}  297-34-107-10

Velho now sees asianhallinta button
  Element should be visible  xpath=//section[@id="application"]//button[@data-test-id='to-asianhallinta']

Velho also does not see approve-button
  Element should not be visible  xpath=//section[@id="application"]//button[@data-test-id='approve-application']

Velho sees asianhallinta buttons in requiredFieldSummary tab but no approve button
  Open tab  requiredFieldSummary
  Element should be visible  xpath=//button[@data-test-id="to-asianhallinta-summaryTab"]
  Element should not be visible  xpath=//button[@data-test-id="approve-application-summaryTab"]
  Element should not be visible  xpath=//button[@data-test-id="approve-application"]

Velho moves application to asianhallinta, error should pop up
  Click by test id  to-asianhallinta
  Confirm  integration-error-dialog
  Logout

Kuopio admin sets asianhallinta disabled
  Kuopio logs in
  Go to page  backends
  Wait until  Element should be visible  xpath=//section[@data-test-id="asianhallinta"]//input[@data-test-id="enable-asianhallinta"]
  Scroll to  input[data-test-id=enable-asianhallinta]
  Unselect checkbox  xpath=//section[@data-test-id="asianhallinta"]//input[@data-test-id="enable-asianhallinta"]
  [Teardown]  Logout

Velho logs in to check if he can move application to asianhallinta
  Velho logs in
  Open application  ${appname}  297-34-107-10
  Wait until  Element should not be visible  xpath=//section[@id="application"]//button[@data-test-id='to-asianhallinta']
  Logout

Kuopio admin enabled asianhallinta again
  Kuopio logs in
  Go to page  backends
  Wait until  Element should be visible  xpath=//section[@data-test-id="asianhallinta"]//input[@data-test-id="enable-asianhallinta"]
  Scroll to  input[data-test-id=enable-asianhallinta]
  Select checkbox  xpath=//section[@data-test-id="asianhallinta"]//input[@data-test-id="enable-asianhallinta"]
  [Teardown]  Logout

Velho logs in, sets Mikko as maksaja and moves application to asianhallinta
  Velho logs in
  Open application  ${appname}  297-34-107-10
  Open tab  parties
  Open accordions  parties
  Select From List  //section[@data-doc-type="maksaja"]//select[@name="henkilo.userId"]  Intonen Mikko
  Wait Until  Javascript?  $("section[data-doc-type=maksaja] input[data-docgen-path='henkilo.henkilotiedot.etunimi']").val() == "Mikko"
  Click by test id  to-asianhallinta
  Wait until  Application state should be  sent
  [Teardown]  logout
