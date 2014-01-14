*** Settings ***

Documentation  Authority admin edits organization WSF
Suite setup     Sipoo logs in
Suite teardown  Logout
Resource       ../../common_resource.robot

*** Test Cases ***

Set variables
  Set suite variable   ${VALID_LEGACY}    ${SERVER}/dev/krysp
  Set suite variable   ${INVALID_LEGACY}  BROKEN

Inputting invalid wfs gives an error
  Submit wfs  ${INVALID_LEGACY}
  There should be error

Legacy can be empty
  Input wfs  ${EMPTY}
  Legacy should not be set

Setting valid wfs set the wfs
  Submit wfs  ${VALID_LEGACY}
  Legacy should be  ${VALID_LEGACY}

*** Keywords ***

Submit wfs
  [Arguments]  ${wfs}
  Click element  edit-wfs-link
  Wait until  element should be visible  edit-wfs
  Input Legacy  ${wfs}

Input wfs
  [Arguments]  ${wfs}
  Input Text  edit-wfs  ${wfs}
  Click button  wfs-submit

There should be error
  Wait until  element should be visible  wfs-error

Legacy should be
  [Arguments]  ${wfs}
  Wait until  Element Text Should Be  wfs  ${wfs}

Legacy should not be set
  Wait until  Element should not be visible  wfs
  Element should be visible  wfs-not-set
