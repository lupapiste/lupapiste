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

Edit target name on an existing inspection summary
  [Arguments]  ${index}  ${newName}
  Click by test id  edit-link-${index}
  Wait test id hidden  new-target-button
  Input text  xpath=//input[@data-test-id="edit-target-field-${index}"]  ${newName}
  Execute javascript  $("[data-test-id='edit-target-field-${index}']").blur();
  Positive indicator should be visible
  Wait until  Element should contain  //tr[@data-test-id="target-${index}"]/td[@class="target-name"]  ${newName}

Add a new target on an existing inspection summary
  [Arguments]  ${targetCountBefore}  ${newName}
  Click by test id  new-target-button
  Wait test id visible  edit-target-field-${targetCountBefore}
  Input text  xpath=//input[@data-test-id="edit-target-field-${targetCountBefore}"]  ${newName}
  Execute javascript  $("[data-test-id='edit-target-field-${targetCountBefore}']").blur();
  Wait until  Element should contain  //tr[@data-test-id="target-${targetCountBefore}"]/td[@class="target-name"]  ${newName}
