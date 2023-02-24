*** Keywords ***
Authenticate via dummy page
  [Arguments]  ${init-button-id}
  Scroll and click test id  ${init-button-id}
  Sleep  1s
  Fill test id  dummy-login-userid  210281-9988
  Wait test id visible  submit-button
  Submit Form

Start authentication but cancel it
  [Arguments]  ${init-button-id}
  Scroll and click test id  ${init-button-id}
  Wait test id visible  cancel-button
  Scroll and click test id  cancel-button

