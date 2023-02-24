*** Settings ***

Documentation  Authority admin edits organization WSF
Suite Setup     Sipoo logs in
Suite Teardown  Logout
Resource       ../../common_resource.robot

*** Test Cases ***

Open backend settings tab
  Go to page  backends

Set variables
  Set suite variable   ${VALID_LEGACY}    http://127.0.0.1:8000/dev/krysp
  Set suite variable   ${INVALID_LEGACY}  BROKEN

Inputting invalid wfs for rakval gives an error
  Submit wfs  ${INVALID_LEGACY}  R
  There should be error

Rakval WFS can be empty
  Input WFS URL  ${EMPTY}
  There should not be error
  Legacy should not be set  R

Setting valid rakval wfs set the wfs
  Submit wfs  ${VALID_LEGACY}  R
  Legacy should be  ${VALID_LEGACY}  R

Inputting invalid wfs for osoitteet gives an error
  Submit wfs  ${INVALID_LEGACY}  osoitteet
  There should be error

Osoitteet WFS can be empty
  Input WFS URL  ${EMPTY}
  There should not be error
  Legacy should not be set  osoitteet

Setting valid osoitteet wfs set the wfs
  Submit wfs  ${VALID_LEGACY}  osoitteet
  Legacy should be  ${VALID_LEGACY}  osoitteet

*** Keywords ***

There should not be error
  Wait until  element should not be visible  wfs-error

Submit wfs
  [Arguments]  ${url}  ${permitType}

  Click element  edit-wfs-for-${permitType}
  Wait until  element should be visible  edit-wfs
  There should not be error
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
