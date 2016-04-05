*** Settings ***

Documentation   Application statements are managed
Suite Setup     Apply minimal fixture now
Suite Teardown  Logout
Resource        ../../common_resource.robot

*** Test Cases ***

Sonja creates an application with statement
  Sonja logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  Mikontalo${secs}
  Set Suite Variable  ${appPropertyId}  753-416-25-22
  Create application with state  ${appname}  ${appPropertyId}  kerrostalo-rivitalo  open
  Open application  ${appname}  ${appPropertyId}
  Open tab  statement
  Wait and click   xpath=//button[@data-test-id="add-statement"]
  Wait until  Select Checkbox  statement-giver-checkbox-0
  Wait until  Element should be enabled  xpath=//*[@data-test-id='add-statement-giver']
  Wait and click  xpath=//*[@data-test-id='add-statement-giver']
  Wait until  Element should be disabled  xpath=//*[@data-test-id='add-statement-giver']
  Scroll to top
  Wait and Click  xpath=//div[@id='application-statement-tab']//a[@data-test-id='open-statement-0']
  Wait until  Element text should be  xpath=//div[@data-test-id='header-statement-edit']//span[@data-bind='text: person.text']  PALOVIRANOMAINEN
  Input text  statement-text  do it
  Select From List By Value  statement-type-select  puoltaa
  Wait and click  statement-submit
  Confirm  dynamic-yes-no-confirm-dialog
  Wait Until  Element should be visible  xpath=//div[@id='application-statement-tab']//table[@data-test-id='application-statements']

Attachment is generated
  Open tab  attachments
  Wait until  Element should be visible  //table[@data-test-id='attachments-template-table']//tr[@id='attachment-row-ennakkoluvat_ja_lausunnot-lausunto']

Attachement is not removable from list view
  Element should not be visible  //table[@data-test-id='attachments-template-table']//tr[@data-test-icon='delete-ennakkoluvat_ja_lausunnot.lausunto']

Attachement is not removable from attachment view
  Wait and click  //table[@data-test-id='attachments-template-table']//a[@data-test-type='ennakkoluvat_ja_lausunnot.lausunto']
  Wait until  Element should contain  //span[@id='test-attachment-file-name']/a  Lausunto.pdf
  Element should not be visible  //button[@data-test-id='delete-attachment']

Attachment version is not removable
  Wait and click  show-attachment-versions
  Wait until  Element should be visible  //tr[@data-test-id='version-row-0.1']
  Element should not be visible  //tr[@data-test-id='version-row-0.1']//a[@data-test-id='delete-version']
  Logout
