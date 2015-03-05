*** Settings ***

Documentation   Kopiolaitos interaction
Suite teardown  Logout
Resource        ../../common_resource.robot
Variables      ../06_attachments/variables.py

*** Test Cases ***


Mikko creates an application
  Mikko logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  approve-app${secs}
  Create application the fast way  ${appname}  753  753-416-25-30  kerrostalo-rivitalo

Mikko adds an attachment
  Open tab  attachments
  Add attachment  ${TXT_TESTFILE_PATH}  ${EMPTY}  Asuinkerrostalon tai rivitalon rakentaminen
  Wait Until  Element should be visible  xpath=//div[@data-test-id='application-pre-attachments-table']//td[@class='attachment-file-info']//a[contains(., '${TXT_TESTFILE_NAME}')]

Mikko submits application
  Submit application
  Logout

Sonja logs in
  Sonja logs in
  Open application  ${appname}  753-416-25-30

Sonja sets attachment to be a verdict attachment
  Open tab  attachments
  Wait Until  Page should contain element  xpath=//select[@data-test-id="attachment-operations-select-lower"]//option[@value='markVerdictAttachments']
  Page should not contain element  xpath=//select[@data-test-id="attachment-operations-select-lower"]//option[@value='orderVerdictAttachments']
  Open attachment details  muut.muu
  Checkbox should not be selected  xpath=//section[@id='attachment']//input[@data-test-id='is-verdict-attachment']
  Select checkbox  xpath=//section[@id='attachment']//input[@data-test-id='is-verdict-attachment']
  Click by test id  back-to-application-from-attachment
  Wait Until  Page should contain element  xpath=//select[@data-test-id="attachment-operations-select-lower"]//option[@value='markVerdictAttachments']
  Page should not contain element  xpath=//select[@data-test-id="attachment-operations-select-lower"]//option[@value='orderVerdictAttachments']

Sonja gives verdict
  Open tab  verdict
  Fetch verdict
  Wait until  Element text should be  xpath=//div[@id='application-verdict-tab']//span[@data-test-id='given-verdict-id-0']  2013-01

An option to order verdict attachments has appeared into the Toiminnot dropdown in the Attachments tab
  Open tab  attachments
  Wait Until  Page should contain element  xpath=//select[@data-test-id="attachment-operations-select-lower"]//option[@value='orderVerdictAttachments']

Sonja checks kopiolaitos infos before order
  Open tab  verdict
  Wait Until  Element should be visible  xpath=//div[@id='application-verdict-tab']
  Click by test id  test-order-attachment-prints
  Wait Until  Element should be visible  dialog-verdict-attachment-prints-order
  Wait Until  Xpath should match x times  //div[@id='dialog-verdict-attachment-prints-order']//tbody[@data-test-id='verdict-attachments-tbody']//tr  1
  Element should be visible  verdict-attachment-prints-order-info

  # Some input values come from organization data set in minimal fixture
  Textfield value should be  xpath=//form[@id='verdict-attachment-prints-order-info']//input[@data-test-id='verdict-attachment-prints-order-orderer-organization']  Sipoon rakennusvalvonta, Sonja Sibbo
  Textfield value should be  xpath=//form[@id='verdict-attachment-prints-order-info']//input[@data-test-id='verdict-attachment-prints-order-orderer-address']  Testikatu 2, 12345 Sipoo
  Textfield value should be  xpath=//form[@id='verdict-attachment-prints-order-info']//input[@data-test-id='verdict-attachment-prints-order-orderer-phone']  0501231234
  Textfield value should be  xpath=//form[@id='verdict-attachment-prints-order-info']//input[@data-test-id='verdict-attachment-prints-order-orderer-email']  tilaaja@example.com

  Textfield value should be  xpath=//form[@id='verdict-attachment-prints-order-info']//input[@data-test-id='verdict-attachment-prints-order-applicant-name']  Intonen Mikko
  Textfield should contain  xpath=//form[@id='verdict-attachment-prints-order-info']//input[@data-test-id='verdict-attachment-prints-order-lupapisteId']  LP-753
  Textfield value should be  xpath=//form[@id='verdict-attachment-prints-order-info']//input[@data-test-id='verdict-attachment-prints-order-kuntalupatunnus']  2013-01
  Textfield should contain  xpath=//form[@id='verdict-attachment-prints-order-info']//input[@data-test-id='verdict-attachment-prints-order-address']  approve-app
  Textfield should contain  xpath=//form[@id='verdict-attachment-prints-order-info']//input[@data-test-id='verdict-attachment-prints-order-propertyId']  753

  Element should be enabled  xpath=//div[@id='dialog-verdict-attachment-prints-order']//button[@data-test-id='test-order-verdict-attachment-prints']
  Click by test id  test-order-verdict-attachment-prints
  Wait until  Element should be visible  xpath=//div[@id='dynamic-ok-confirm-dialog']//button[@data-test-id='confirm-yes']
  Click by test id  confirm-yes
