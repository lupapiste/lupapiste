*** Settings ***

Documentation   After verdicts, certain documents can be created and sent to backing system
Suite Teardown  Logout
Resource        ../../common_resource.robot
Resource        ../../common_keywords/approve_helpers.robot
Resource        ../39_pate/pate_resource.robot
Variables       ../../common_variables.py

*** Test Cases ***

Mikko creates and submits app
  Mikko logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  PostverdictDocs${secs}
  Create application the fast way  ${appname}  753-416-25-30  kerrostalo-rivitalo

Mikko fills some of the suunnittelija docs
  Open tab  parties
  Input text with jQuery  section[data-doc-type="paasuunnittelija"] input[data-docgen-path="henkilotiedot.etunimi"]  Pää
  Input text with jQuery  section[data-doc-type="paasuunnittelija"] input[data-docgen-path="henkilotiedot.sukunimi"]  Suunnittelija
  Input text with jQuery  section[data-doc-type="suunnittelija"] input[data-docgen-path="henkilotiedot.etunimi"]  DeAngelo
  Input text with jQuery  section[data-doc-type="suunnittelija"] input[data-docgen-path="henkilotiedot.sukunimi"]  Designer
  Submit application
  Logout

Sonja logs in and sends app to backing system
  Sonja logs in
  Open application  ${appname}  753-416-25-30
  Approve application

Sonja fetches verdict from municipality KRYSP service
  Open tab  verdict
  Fetch verdict
  Logout

Mikko can add new designer docs
  As Mikko
  Open application  ${appname}  753-416-25-30
  Open tab  parties
  Xpath should match X times  //section[@data-doc-type="paasuunnittelija"]  1
  Xpath should match X times  //section[@data-doc-type="suunnittelija"]  1
  Add suunnittelija  Herkko  Heraldic  1
  Add suunnittelija  Huge  L  2
  Add suunnittelija  Masan  Jaardit  3

New suunnittelija docs could be deleted but not disabled
  Wait Until  Element should be visible  xpath=(//section[@data-doc-type='suunnittelija'])[2]//button[@data-test-class='delete-schemas.suunnittelija']
  Wait Until  Element should not be visible  xpath=(//section[@data-doc-type='suunnittelija'])[2]//button[@data-test-id='toggle-document-status']
  Wait Until  Element should be visible  xpath=(//section[@data-doc-type='suunnittelija'])[3]//button[@data-test-class='delete-schemas.suunnittelija']
  Wait Until  Element should not be visible  xpath=(//section[@data-doc-type='suunnittelija'])[3]//button[@data-test-id='toggle-document-status']

Old suunnittelija docs could be disabled
  Wait Until  Element should not be visible  xpath=(//section[@data-doc-type='suunnittelija'])[1]//button[@data-test-class='delete-schemas.suunnittelija']
  Wait Until  Element should be visible  xpath=(//section[@data-doc-type='suunnittelija'])[1]//button[@data-test-id='toggle-document-status']
  Element text should be  xpath=(//section[@data-doc-type='suunnittelija'])[1]//button[@data-test-id='toggle-document-status']  Merkitse poistuneeksi

Mikko disables DeAngelo
  Scroll and click  section[data-doc-type='suunnittelija']:eq(0) button[data-test-id='toggle-document-status']
  Confirm yes no dialog
  # Check that disabled class is set
  Document status is disabled  suunnittelija  1

Mikko toggles DeAngelo back to enabled, and eventually to disabled again
  Scroll and click  section[data-doc-type='suunnittelija']:eq(0) button[data-test-id='toggle-document-status']
  Document status is enabled  suunnittelija  1
  Sleep  0.5s
  # stale element error in Roboto from below click, maybe the small sleep helps, maybe not...
  Scroll and click  section[data-doc-type='suunnittelija']:eq(0) button[data-test-id='toggle-document-status']
  Confirm yes no dialog
  Document status is disabled  suunnittelija  1

Mikko does not see send-parties-to-krysp button
  Element should not be visible  xpath=//button[@data-test-id='parties-to-krysp']
  Logout

Sonja logs in and sees DeAngelo is disabled
  As Sonja
  Open application  ${appname}  753-416-25-30
  Open tab  parties
  Wait until  Xpath should match X times  //section[@data-doc-type="suunnittelija"]  4
  Wait until  Element should be visible  xpath=(//section[@data-doc-type='suunnittelija'])[1]//div[contains(@class, 'accordion-toggle')]/button[contains(@class,'disabled')]

Sonja could approve/reject/remove new suunnittelija docs
  Wait until  Element should be visible  xpath=(//section[@data-doc-type='suunnittelija'])[2]//button[@data-test-id='approve-doc-suunnittelija']
  Wait until  Element should be visible  xpath=(//section[@data-doc-type='suunnittelija'])[2]//button[@data-test-id='reject-doc-suunnittelija']
  Wait until  Element should be visible  xpath=(//section[@data-doc-type='suunnittelija'])[2]//button[@data-test-class='delete-schemas.suunnittelija']
  Wait until  Element should be visible  xpath=(//section[@data-doc-type='suunnittelija'])[3]//button[@data-test-id='approve-doc-suunnittelija']
  Wait until  Element should be visible  xpath=(//section[@data-doc-type='suunnittelija'])[3]//button[@data-test-id='reject-doc-suunnittelija']
  Wait until  Element should be visible  xpath=(//section[@data-doc-type='suunnittelija'])[3]//button[@data-test-class='delete-schemas.suunnittelija']
  Wait until  Element should be visible  xpath=(//section[@data-doc-type='suunnittelija'])[4]//button[@data-test-id='approve-doc-suunnittelija']
  Wait until  Element should be visible  xpath=(//section[@data-doc-type='suunnittelija'])[4]//button[@data-test-id='reject-doc-suunnittelija']
  Wait until  Element should be visible  xpath=(//section[@data-doc-type='suunnittelija'])[4]//button[@data-test-class='delete-schemas.suunnittelija']

Sonja adds fifth suunnittelija, but removes it instantly
  Add suunnittelija  Ronja  Piippo  4
  Wait until  Xpath should match X times  //section[@data-doc-type="suunnittelija"]  5
  Scroll and click  section[data-doc-type='suunnittelija']:eq(4) button[data-test-class='delete-schemas.suunnittelija']
  Confirm yes no dialog
  Wait until  Xpath should match X times  //section[@data-doc-type="suunnittelija"]  4

Sonja approves Herkko, but rejects Huge
  Sleep  2s
  Approve accordion  suunnittelija  1
  Reject accordion  suunnittelija  2
  Approve accordion  suunnittelija  3
  Accordion approved  suunnittelija  1
  Accordion rejected  suunnittelija  2
  Accordion approved  suunnittelija  3

Sonja can move new designers to KRYSP, after confirming warning dialog
  Element should be visible  xpath=//button[@data-test-id='parties-to-krysp']
  Scroll and click test id  parties-to-krysp
  Wait until  Element should be visible  xpath=//div[@id='modal-dialog']//div[@data-test-id='yes-no-dialog']
  Wait until  Element should contain  xpath=//div[@id='modal-dialog']//div[@data-test-id='yes-no-dialog']//p  Huge L
  Confirm yes no dialog
  Positive indicator should be visible
  # Two documents have been transferred to backing system
  Indicator should contain text  2
  Close sticky indicator

Sonja removes verdicts and rewinds the application state
  Open tab  verdict
  Click by test id  verdict-delete-0
  Confirm yes no dialog
  Click by test id  verdict-delete-0
  Confirm yes no dialog
  Wait until  Application state should be  sent

Sonja returns the application to the complementNeeded state
  Click by test id  request-for-complement
  Wait until  Application state should be  complementNeeded
  [Teardown]  Logout

Mikko cannot remove the disabled designer document
  Mikko logs in
  Open application  ${appname}  753-416-25-30
  Open tab  parties
  jQuery should match X times  div.accordion-toggle button.disabled  1
  Wait until element is not visible  jquery=div.accordion-toggle button.disabled + div.group-buttons button[data-test-class='delete-schemas.suunnittelija']
  [Teardown]  Logout

Sonja can delete the disabled designer document
  Sonja logs in
  Open application  ${appname}  753-416-25-30
  Open tab  parties
  jQuery should match X times  div.accordion-toggle button.disabled  1
  Scroll and click  div.accordion-toggle button.disabled + div.group-buttons button[data-test-class='delete-schemas.suunnittelija']
  Confirm yes no dialog
  Wait until element is not visible  jquery=div.accordion-toggle button.disabled
  [Teardown]  Logout

*** Keywords ***

Add suunnittelija
  [Arguments]  ${firstname}  ${lastname}  ${idx}=0
  Scroll and click  button[data-test-id='suunnittelija_append_btn']:last
  ${docCount}=  Evaluate  ${idx} + 1
  Wait until  Xpath should match X times  //section[@data-doc-type="suunnittelija"]  ${docCount}
  Sleep  1s
  Open docgen accordion  suunnittelija  ${idx}
  Input text with jQuery  section[data-doc-type="suunnittelija"]:eq(${idx}) input[data-docgen-path="henkilotiedot.etunimi"]  ${firstname}
  Input text with jQuery  section[data-doc-type="suunnittelija"]:eq(${idx}) input[data-docgen-path="henkilotiedot.sukunimi"]  ${lastname}
