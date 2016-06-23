*** Settings ***

Documentation   Suti settings
Resource       ../../common_resource.robot

*** Test Cases ***

Sipoo admin sets suti server address
  Sipoo logs in
  Go to page  backends

  Input text  jquery=.sutiapi input[data-test-id='server-details-url']  http://localhost:8000/dev/suti
  Input text  jquery=.sutiapi input[data-test-id='server-details-username']  sutiuser
  Input text  jquery=.sutiapi input[data-test-id='server-details-password']  sutipassword

  Positive indicator should not be visible
  Click element  jquery=.sutiapi button[data-test-id='server-details-send']
  Positive indicator should be visible

Url and username are set after reload
  Reload Page

  Wait Until  Element Should Be Visible  jquery=.sutiapi button[data-test-id='server-details-send']
  Focus  jquery=.sutiapi button[data-test-id='server-details-send']

  Wait Until  Value Should Be  xpath=//div[@class='sutiapi']//input[@data-test-id='server-details-url']  http://localhost:8000/dev/suti
  Wait Until  Value Should Be  jquery=.sutiapi input[data-test-id='server-details-username']  sutiuser

Password is not echoed
  Value Should Be  jquery=.sutiapi input[data-test-id='server-details-password']  ${EMPTY}
  [Teardown]  Logout
