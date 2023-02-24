*** Settings ***
Resource   ../../common_resource.robot

*** Keywords ***

Create basic template setup
  Go to page  applications
  Add new template  Uusi pohja 1  AA\nBB\nCC\n  AA\nBB\nCC  0
  Add new template  Uusi pohja 2  AA\nDD\n\nCC\n  AA\nDD\nCC  1

Bind template to operation as default
  [Arguments]  ${name}  ${operation}
  Go to page  operations
  Select From List by label   xpath=//select[@data-test-id="select-inspection-summary-template-${operation}"]  ${name}
  Positive indicator should be visible
  Positive indicator should not be visible

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
  Click by test id  target-name-${index}-edit
  # no two way binding currently
  # Wait Until  Test id disabled  new-target-button
  Input text  xpath=//input[@data-test-id="target-name-${index}-input"]  ${newName}
  Press key  xpath=//input[@data-test-id="target-name-${index}-input"]  \\13
  Positive indicator should be visible
  Wait until  Element should contain  //tr[@data-test-id="target-${index}"]/td[contains(@class, 'target-name')]  ${newName}

Add a new target on an existing inspection summary
  [Arguments]  ${targetCountBefore}  ${newName}
  Click by test id  new-target-button
  Wait test id visible  target-name-${targetCountBefore}-input
  Input text  xpath=//input[@data-test-id="target-name-${targetCountBefore}-input"]  ${newName}
  Press key  xpath=//input[@data-test-id="target-name-${targetCountBefore}-input"]  \\13
  Wait until  Element should contain  //tr[@data-test-id="target-${targetCountBefore}"]/td[contains(@class, 'target-name')]  ${newName}
