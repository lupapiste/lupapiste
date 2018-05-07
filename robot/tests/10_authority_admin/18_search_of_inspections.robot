*** Settings ***

Documentation   Authority admin adds handlers and task triggers
Suite Setup     Apply minimal fixture now
Suite Teardown  Logout
Resource        ../../common_resource.robot

*** Test Cases ***

Authority admin goes to the tasks page
  Sipoo logs in
  Go to page  backends

Automatic fetching of inspections is on by default
  Checkbox wrapper selected  automatic-review-fetch-enabled

Selection for only using the inspection from backend is disabled by default
  Checkbox wrapper not selected  automatic-review-generation-enabled

Checkbox for only using the inspection from backend is disabled
  Click label  automatic-review-fetch-enabled
  Checkbox wrapper disabled  automatic-review-generation-enabled
