*** Settings ***

Documentation   After verdicts, certain documents can be created and sent to backing system
Suite Teardown  Logout
Resource        ../../common_resource.robot
Resource        ../common_keywords/approve_helpers.robot
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
  Click enabled by test id  approve-application
  # Deny warning about designers
  Wait Until  Page should contain  Suunnittelijoiden tietoja hyväksymättä
  Deny yes no dialog
  # Approve desingers
  Open tab  parties
  Approve accordion  paasuunnittelija
  Approve accordion  suunnittelija
  Accordion approved  paasuunnittelija
  Accordion approved  suunnittelija
  Click enabled by test id  approve-application
  Wait until  Application state should be  sent

Sonja fetches verdict from municipality KRYSP service
  Open tab  verdict
  Fetch verdict
  Element text should be  xpath=//div[@data-test-id='given-verdict-id-1-content']//span[@data-bind='text: lupamaaraykset.autopaikkojaEnintaan']  10
  Element text should be  xpath=//div[@data-test-id='given-verdict-id-1-content']//span[@data-bind='text: lupamaaraykset.kokonaisala']  110
  No such test id  verdict-requirements-0
  Wait test id visible  verdict-requirements-1
  Logout

Mikko can add new suunnitelija docs
  As Mikko
  Open application  ${appname}  753-416-25-30
  Open tab  parties
  Xpath should match X times  //section[@data-doc-type="paasuunnittelija"]  1
  Xpath should match X times  //section[@data-doc-type="suunnittelija"]  1
  Add suunnittelija  Herkko  Heraldic  1
  Add suunnittelija  Huge  L  2

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
  Scroll and click  section[data-doc-type='suunnittelija']:eq(0) button[data-test-id='toggle-document-status']
  Confirm yes no dialog
  Document status is disabled  suunnittelija  1
  Logout

Sonja logs in and sees DeAngelo is disabled
  As Sonja
  Open application  ${appname}  753-416-25-30
  Open tab  parties
  Wait until  Xpath should match X times  //section[@data-doc-type="suunnittelija"]  3
  Wait until  Element should be visible  xpath=(//section[@data-doc-type='suunnittelija'])[1]//div[contains(@class, 'accordion-toggle')]/button[contains(@class,'disabled')]

Sonja could approve/reject/remove new suunnittelija docs
  Wait until  Element should be visible  xpath=(//section[@data-doc-type='suunnittelija'])[2]//button[@data-test-id='approve-doc-suunnittelija']
  Wait until  Element should be visible  xpath=(//section[@data-doc-type='suunnittelija'])[3]//button[@data-test-id='approve-doc-suunnittelija']
  Wait until  Element should be visible  xpath=(//section[@data-doc-type='suunnittelija'])[2]//button[@data-test-id='reject-doc-suunnittelija']
  Wait until  Element should be visible  xpath=(//section[@data-doc-type='suunnittelija'])[3]//button[@data-test-id='reject-doc-suunnittelija']
  Wait until  Element should be visible  xpath=(//section[@data-doc-type='suunnittelija'])[2]//button[@data-test-class='delete-schemas.suunnittelija']
  Wait until  Element should be visible  xpath=(//section[@data-doc-type='suunnittelija'])[3]//button[@data-test-class='delete-schemas.suunnittelija']

Sonja adds fourth suunnittelija, but removes it instantly
  Add suunnittelija  Ronja  Piippo  3
  Scroll and click  section[data-doc-type='suunnittelija']:eq(3) button[data-test-class='delete-schemas.suunnittelija']
  Confirm  dynamic-yes-no-confirm-dialog
  Wait until  Xpath should match X times  //section[@data-doc-type="suunnittelija"]  3

Sonja approves Herkko, but rejects Huge
  Approve accordion  suunnittelija  1
  Reject accordion  suunnittelija  2
  Accordion approved  suunnittelija  1
  Accordion rejected  suunnittelija  2
  Logout


*** Keywords ***

Add suunnittelija
  [Arguments]  ${firstname}  ${lastname}  ${idx}=0
  Scroll and click  button[data-test-id='suunnittelija_append_btn']:last
  ${docCount}=  Evaluate  ${idx} + 1
  Wait until  Xpath should match X times  //section[@data-doc-type="suunnittelija"]  ${docCount}
  Open docgen accordion  suunnittelija  ${idx}
  Input text with jQuery  section[data-doc-type="suunnittelija"]:eq(${idx}) input[data-docgen-path="henkilotiedot.etunimi"]  ${firstname}
  Input text with jQuery  section[data-doc-type="suunnittelija"]:eq(${idx}) input[data-docgen-path="henkilotiedot.sukunimi"]  ${lastname}

Document status is disabled
  [Arguments]  ${docType}  ${xpathIdx}
  Wait until  Element should be visible  xpath=(//section[@data-doc-type='${docType}'])[${xpathIdx}]//div[contains(@class, 'accordion-toggle')]/button[contains(@class,'disabled')]
  Wait until  Element text should be  xpath=(//section[@data-doc-type='${docType}'])[${xpathIdx}]//button[@data-test-id='toggle-document-status']/span  Palauta aktiiviseksi

Document status is enabled
  [Arguments]  ${docType}  ${xpathIdx}
  Wait until  Element should not be visible  xpath=(//section[@data-doc-type='${docType}'])[${xpathIdx}]//div[contains(@class, 'accordion-toggle')]/button[contains(@class,'disabled')]
  Wait until  Element text should be  xpath=(//section[@data-doc-type='${docType}'])[${xpathIdx}]//button[@data-test-id='toggle-document-status']/span  Merkitse poistuneeksi

