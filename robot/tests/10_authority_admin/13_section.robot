*** Settings ***

Documentation   Section requirement for verdicts/operations.
Suite Setup     Apply minimal fixture now
Suite Teardown  Logout
Resource       ../../common_resource.robot

*** Test Cases ***

Sipoo admin logs in
  Sipoo logs in
  Go to page  backends

Section is not enabled
  Checkbox should not be selected  verdict-section-enabled

Section operations not visible
  Go to page  operations
  Element should not be visible  jquery=label[for=section-kiinteistonmuodostus]

Enable section
  Toggle section
  Checkbox should be selected  verdict-section-enabled

Selection survives reload
  Reload page and kill dev-box
  Wait Until  Checkbox should be selected  verdict-section-enabled

Operations can now be selected
  Toggle section operation  kiinteistonmuodostus
  Checkbox should be selected  section-kiinteistonmuodostus

Operation selection survives reload
  Reload page and kill dev-box
  Wait Until  Checkbox should be selected  section-kiinteistonmuodostus

Disable section
  Toggle section

Operations no longer visible
  Go to page  operations
  Element should not be visible  jquery=label[for=section-kiinteistonmuodostus]



*** Keywords ***

Toggle section
  Go to page  backends
  Click label  verdict-section-enabled
  Positive indicator should be visible

Toggle section operation
  [Arguments]  ${operation}
  Go to page  operations
  Click label  section-${operation}
