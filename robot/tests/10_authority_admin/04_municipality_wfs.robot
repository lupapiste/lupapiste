*** Settings ***

Documentation  Authority admin edits organization WSF
Suite Setup     Sipoo logs in
Suite Teardown  Logout
Resource       ../../common_resource.robot

*** Test Cases ***

Set variables
  Set suite variable   ${VALID_LEGACY}    http://127.0.0.1:8000/dev/krysp
  Set suite variable   ${INVALID_LEGACY}  BROKEN

Inputting invalid wfs gives an error
  Submit wfs  ${INVALID_LEGACY}  R
  There should be error

Legacy can be empty
  Input WFS URL  ${EMPTY}
  Legacy should not be set  R

Setting valid wfs set the wfs
  Submit wfs  ${VALID_LEGACY}  R
  Legacy should be  ${VALID_LEGACY}  R

*** Keywords ***

Submit wfs
  [Arguments]  ${url}  ${permitType}
  Click element  edit-wfs-for-${permitType}
  Wait until  element should be visible  edit-wfs
  Input WFS URL  ${url}

Input WFS URL
  [Arguments]  ${url}
  Input Text  edit-wfs  ${url}
  Click button  wfs-submit

There should be error
  Wait until  element should be visible  wfs-error

Legacy should be
  [Arguments]  ${url}  ${permitType}
  Wait until  Element Text Should Be  wfs-for-${permitType}  ${url}

Legacy should not be set
  [Arguments]  ${permitType}
  Wait until  Element should not be visible  wfs-for-${permitType}
  Element should be visible  wfs-not-set-${permitType}
