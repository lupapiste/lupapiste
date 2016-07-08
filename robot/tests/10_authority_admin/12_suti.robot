*** Settings ***

Documentation   Suti settings
Resource       ../../common_resource.robot
Resource       suti_resource.robot

*** Test Cases ***

Sipoo admin sets suti server address
  Sipoo logs in
  Go to page  backends

  Suti server  http://localhost:8000/dev/suti  sutiuser  sutipassword

Url and username are set after reload
  Reload Page

  Scroll to test id  suti-send  
  Test id input is  suti-username  sutiuser
  Test id input is  suti-url  http://localhost:8000/dev/suti

Password is not echoed
  Test id input is  suti-password  ${EMPTY}
  [Teardown]  Logout
