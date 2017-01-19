*** Settings ***

Documentation   Authority admin administers inspection summary templates
Suite Setup     Apply minimal fixture now
Resource       ../../common_resource.robot


*** Test Cases ***


Feature not in use in Sipoo
  Sipoo logs in
  Go to page  applications
  Element should not be visible by test id  inspection-summary-templates-list
  Go to page  operations
  Element should not be visible by test id  select-inspection-summary-template
  Logout

Feature in use in Jarvenpaa
  Jarvenpaa admin logs in
  Go to page  applications
  Element should be visible by test id  inspection-summary-templates-list
  Go to page  operations
  Element should be visible by test id  select-inspection-summary-template

Create new templates
  Go to page  applications
  Add new template  Uusi pohja 1  AA\nBB\nCC\n  AA\nBB\nCC  0
  Add new template  Uusi pohja 2  AA\nDD\n\nCC\n  AA\nDD\nCC  1

Template editor opens and closes properly
  Open template editor  0
  Close template editor  0

Template name can be edited
  Change template name  0  Uusi nimi pohjalle


*** Keywords ***

Add new template
  [Arguments]  ${name}  ${content}  ${savedContent}  ${index}
  Click enabled by test id  show-new-template-bubble
  Template bubble OK is disabled
  Input text  inspection-summary-template-name  ${name}
  Template bubble OK is disabled
  Input text  inspection-summary-template-items  ${content}
  Click enabled by test id  inspection-summary-template-new-bubble-dialog-ok
  Wait Until  Test id should contain  edit-template-${index}  ${name}
  Element should be visible by test id  remove-template-${index}

Open template editor
  [Arguments]  ${index}
  Click by test id  edit-template-${index}
  Wait Until  Element should be visible by test id  template-editor-${index}

Close template editor
  [Arguments]  ${index}
  Click enabled by test id  inspection-summary-template-new-bubble-dialog-cancel
  Wait Until  Element should not be visible by test id  template-editor-${index}

Change template name
  [Arguments]  ${index}  ${newName}
  Open template editor  ${index}
  Input text  inspection-summary-template-name  ${newName}
  Click enabled by test id  inspection-summary-template-new-bubble-dialog-ok
  Wait Until  Element should not be visible by test id  template-editor-${index}
  Wait Until  Test id should contain  edit-template-${index}  ${newName}


Template bubble OK is disabled
  Wait Until  Element Should Be Disabled  xpath=//button[@data-test-id='inspection-summary-template-new-bubble-dialog-ok']
