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

New suunnitelija docs can be added
  Open tab  parties
  Xpath should match X times  //section[@data-doc-type="paasuunnittelija"]  1
  Xpath should match X times  //section[@data-doc-type="suunnittelija"]  1
  Click by test id  suunnittelija_append_btn
  Wait until  Xpath should match X times  //section[@data-doc-type="suunnittelija"]  2

Suunnittelija docs could be deleted and approved
  Wait Until  Element should be visible  xpath=//section[@data-doc-type='paasuunnittelija']//button[@data-test-class='delete-schemas.paasuunnittelija']
  Wait Until  Element should be visible  xpath=//section[@data-doc-type='paasuunnittelija']//button[@data-test-id='reject-doc-paasuunnittelija']
  Wait Until  Element should be visible  xpath=(//section[@data-doc-type='suunnittelija'])[2]//button[@data-test-class='delete-schemas.suunnittelija']
  Wait Until  Element should be visible  xpath=(//section[@data-doc-type='suunnittelija'])[2]//button[@data-test-id='approve-doc-suunnittelija']

