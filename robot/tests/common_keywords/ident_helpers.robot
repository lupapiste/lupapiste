*** Keywords ***
Authenticate via dummy page
  [Arguments]  ${init-button-id}
  Click by test id  ${init-button-id}
  Fill test id  dummy-login-userid  210281-9988
  Wait test id visible  submit-button
  Click by test id  submit-button

Start authentication but cancel it
  [Arguments]  ${init-button-id}
  Click by test id  ${init-button-id}
  Wait test id visible  cancel-button
  Click by test id  cancel-button

