*** Settings ***

Documentation   Kopiolaitos interaction
Suite Teardown  Logout
Resource        ../../common_resource.robot
Resource        printout_resource.robot
Resource        ../39_pate/pate_resource.robot
Variables      ../06_attachments/variables.py


*** Test Cases ***


Mikko creates an application
  Mikko logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  approve-app${secs}
  Create application the fast way  ${appname}  753-416-25-30  kerrostalo-rivitalo

Mikko adds two attachments
  Open tab  attachments
  Upload attachment  ${PNG_TESTFILE_PATH}  Muu liite  Muu  Asuinkerrostalon tai rivitalon rakentaminen
  Upload attachment  ${PDF_TESTFILE_PATH}  Asemapiirros  Asemapiirros  Asuinkerrostalon tai rivitalon rakentaminen

Mikko submits application
  Submit application
  Logout

Sonja logs in
  Sonja logs in
  Open application  ${appname}  753-416-25-30

Sonja goes to attachments tab
  Open tab  attachments
  Wait until  Element should be visible  jquery=div#application-attachments-tab button[data-test-id=mark-verdict-attachments]
  Wait until  Element should not be visible  jquery=div#application-attachments-tab button[data-test-id=order-attachment-prints]

Sonja sets attachment to be a verdict attachment
  Open attachment details  muut.muu
  Toggle not selected  is-verdict-attachment
  Toggle toggle  is-verdict-attachment
  Positive indicator icon should be visible
  Positive indicator icon should not be visible

Sonja sets contents description for the attachment
  Wait until  Element should be enabled  xpath=//input[@data-test-id='attachment-contents-input']
  Input text by test id  attachment-contents-input  Muu muu muu liite
  Positive indicator icon should be visible
  Positive indicator icon should not be visible
  Return to application
  Wait until  Element should be visible  jquery=div#application-attachments-tab button[data-test-id=mark-verdict-attachments]
  Wait until  Element should be visible  jquery=div#application-attachments-tab button[data-test-id=order-attachment-prints]

Sonja disables verdict attachment using multiselect view, one is selected
  Scroll and click test id  mark-verdict-attachments
  Wait Until  Element should be visible  xpath=//section[@id="verdict-attachments-select"]//h1[1]
  Xpath Should Match X Times  //section[@id="verdict-attachments-select"]//table//tr[contains(@class, 'attachment-row')]  2
  Xpath Should Match X Times  //section[@id="verdict-attachments-select"]//table//tr[contains(@class, 'selected')]  1
  Click element  xpath=//section[@id="verdict-attachments-select"]//table//tr[contains(@class, 'selected')]
  Scroll and click test id  multiselect-action-button

There should be no verdict attachments
  Wait until  Element should not be visible  jquery=div#application-attachments-tab tr[data-test-type='muut.muu'] i[data-test-icon=for-printing-icon]

Sonja marks one attachment as verdict attachment using multiselect view
  Scroll and click test id  mark-verdict-attachments
  Wait Until  Element should be visible  xpath=//section[@id="verdict-attachments-select"]//h1[1]
  Xpath Should Match X Times  //section[@id="verdict-attachments-select"]//table//tr[contains(@class, 'attachment-row')]  2
  Element should be visible  xpath=//section[@id="verdict-attachments-select"]//table//tr[contains(@class, 'attachment-row')]
  Click element  jquery=tr.attachment-row:last
  Wait until  Checkbox should be selected  jquery=tr.attachment-row.selected input
  Scroll and click test id  multiselect-action-button
  Wait for jQuery

There should be now one verdict attachment
  Wait until  Element should be visible  jquery=div#application-attachments-tab tr[data-test-type='muut.muu'] i[data-test-icon=for-printing-icon]

Sonja gives verdict
  Open tab  verdict
  Fetch verdict

Sonja adds RAM attachment to Mikko's attachment
  Sonja adds RAM attachment  paapiirustus.asemapiirros

Sonja marks the RAM attachment as a verdict attachment
  Scroll to top
  Scroll and click test id  mark-verdict-attachments
  Click element  jquery=tr.attachment-row span:contains('(RAM)')
  Scroll and click test id  multiselect-action-button

Order verdict attachments button has appeared into Attachments tab
  Wait until  Element should be visible  jquery=div#application-attachments-tab button[data-test-id=order-attachment-prints]

The print order dialog can be opened by selecting from the dropdown
  Scroll and click test id  order-attachment-prints
  Wait Until  Element should be visible  dialog-verdict-attachment-prints-order

Sonja cancels ordering prints
  Click Link  xpath=//*[@data-test-id='test-order-verdict-attachment-prints-cancel']
  Wait until  Element should not be visible  xpath=//div[@id='dynamic-ok-confirm-dialog']

Sonja opens the kopiolaitos order dialog on Verdict tab
  Open tab  verdict
  Wait Until  Element should be visible  xpath=//div[@id='application-verdict-tab']
  Element should not be visible  xpath=//div[@id="application-verdict-tab"]//a[@data-test-id='test-open-prints-order-history']
  Scroll and click test id  test-order-attachment-prints
  Wait Until  Element should be visible  dialog-verdict-attachment-prints-order

There are two attachment marked as verdict attachments
  Wait Until  Xpath should match x times  //div[@id='dialog-verdict-attachment-prints-order']//tbody[@data-test-id='verdict-attachments-tbody']//tr  2
  Element should be visible  verdict-attachment-prints-order-info

Attachment data is visible
  Element should contain  jquery=div#dialog-verdict-attachment-prints-order tr[data-test-id=order-prints-attachment-row-muut-muu] td[data-test-col=type]  Muu liite
  Element should contain  jquery=div#dialog-verdict-attachment-prints-order tr[data-test-id=order-prints-attachment-row-muut-muu] td[data-test-col=contents]  Muu muu muu liite
  Element should contain  jquery=div#dialog-verdict-attachment-prints-order tr[data-test-id=order-prints-attachment-row-muut-muu] td[data-test-col=filename]  robotframework-testfile-06_attachments_PNG.png
  Textfield value should be  jquery=div#dialog-verdict-attachment-prints-order tr[data-test-id=order-prints-attachment-row-muut-muu] td[data-test-col=amount] input  2

RAM attachment is marked
  Element should contain  jquery=tr[data-test-id=order-prints-attachment-row-paapiirustus-asemapiirros] td[data-test-col=type]  Asemapiirros (RAM)

Let's not order RAM attachment
  Input text with jQuery  tr[data-test-id=order-prints-attachment-row-paapiirustus-asemapiirros] td[data-test-col=amount] input  0

Sonja checks the kopiolaitos order
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

Sonja sends the kopiolaitos order
  Element should be enabled  xpath=//div[@id='dialog-verdict-attachment-prints-order']//button[@data-test-id='test-order-verdict-attachment-prints']
  Scroll and click test id  test-order-verdict-attachment-prints
  Wait until  Element should not be visible  dialog-verdict-attachment-prints-order
  Confirm  dynamic-ok-confirm-dialog
  Wait until  Element should not be visible  xpath=//div[@id='dynamic-ok-confirm-dialog']

Sonja checks that email was sent
  Open last email
  Page Should Contain  Sipoon rakennusvalvonta, Sonja Sibbo
  Page Should Contain  tilaaja@example.com
  Page Should Contain  ${PNG_TESTFILE_NAME}
  [Teardown]  Go Back

Sonja opens the kopiolaitos order history dialog
  Wait test id visible  test-open-prints-order-history
  Scroll and click test id  test-open-prints-order-history
  Wait Until  Element should be visible  dialog-verdict-attachment-prints-order-history
  Element should be visible  //div[@id='dialog-verdict-attachment-prints-order-history']//button[@data-test-id='verdict-attachment-prints-history-ok']

The history dialog includes the order item
  Xpath should match x times  //div[@id='dialog-verdict-attachment-prints-order-history']//div[@class='history-item']  1
  Element should contain  xpath=//div[@id='dialog-verdict-attachment-prints-order-history']//div[@class='history-item']//span[@data-test-id='print-order-ordererOrganization']  Sipoon rakennusvalvonta, Sonja Sibbo
  Element should be visible  //div[@id='dialog-verdict-attachment-prints-order-history']//div[@class='history-item']//span[@data-test-id='print-order-timestamp']

  Element should contain  xpath=//div[@id='dialog-verdict-attachment-prints-order-history']//div[@class='history-item']//td[@data-test-id='test-order-attachment-type']  Muu liite
  Element should contain  xpath=//div[@id='dialog-verdict-attachment-prints-order-history']//div[@class='history-item']//td[@data-test-id='test-order-attachment-contents']  Muu muu muu liite
  Element should contain  xpath=//div[@id='dialog-verdict-attachment-prints-order-history']//div[@class='history-item']//td[@data-test-id='test-order-attachment-filename']  ${PNG_TESTFILE_NAME}
  Textfield value should be  xpath=//div[@id='dialog-verdict-attachment-prints-order-history']//div[@class='history-item']//input[@data-test-id='test-order-amount']  2

Sonja closes the order history dialog
  Scroll and click test id  verdict-attachment-prints-history-ok
  Wait until  Element should not be visible  dialog-verdict-attachment-prints-order-history
  Logout

Mikko still does not see the prints history link
  Mikko logs in
  Open application  ${appname}  753-416-25-30
  Open tab  verdict
  Wait Until  Element should not be visible  xpath=//div[@id="application-verdict-tab"]//a[@data-test-id='test-open-prints-order-history']
