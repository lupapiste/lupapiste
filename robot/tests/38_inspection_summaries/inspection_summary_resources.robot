*** Settings ***
Resource   ../../common_resource.robot

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
