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
  Submit wfs  ${INVALID_LEGACY}
  There should be error

Legacy can be empty
  Input WFS URL  ${EMPTY}
  Legacy should not be set

Setting valid wfs set the wfs
  Submit wfs  ${VALID_LEGACY}
  Legacy should be  ${VALID_LEGACY}

*** Keywords ***

Submit wfs
  [Arguments]  ${url}
  Click element  edit-wfs-for-R
  Wait until  element should be visible  edit-wfs
  Input WFS URL  ${url}

Input WFS URL
  [Arguments]  ${url}
  Input Text  edit-wfs  ${url}
  Click button  wfs-submit

There should be error
  Wait until  element should be visible  wfs-error

Legacy should be
  [Arguments]  ${url}
  Wait until  Element Text Should Be  wfs-for-R  ${url}

Legacy should not be set
  Wait until  Element should not be visible  wfs-for-R
  Element should be visible  wfs-not-set-R
