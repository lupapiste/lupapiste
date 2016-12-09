*** Settings ***

Documentation   Old school organization links
Suite Setup     Apply minimal fixture now
Resource        ../../common_resource.robot


*** Variables ***
${propertyid}   753-423-13-12
${sipoo-url}    http://sipoo.fi

*** Test cases ***

Pena logs in and starts creating inforequest
  Pena logs in
  Open inforequest tree
  Select operations path R

Organization links on the last tree page
  Check link  0  Sipoo  ${sipoo-url}

Organization links on the inforequest
  Finish creation
  Check link  0  Sipoo  ${sipoo-url}

Pena does the same things in Swedish
  Go to page  applications
  Language to  SV
  Open inforequest tree
  Select operations path R in Swedish

Organization links on the last tree page again
  Check link  0  Sibbo  ${sipoo-url}

Organization links on the inforequest again
  Finish creation
  Check link  0  Sibbo  ${sipoo-url}
  [Teardown]  Logout


*** Keywords ***

Check link
  [Arguments]  ${index}  ${text}  ${url}
  Wait Until  Javascript?  $("div.organization-links a[data-test-id=org-link-${index}]").attr("href") === "${url}"
  Test id text is  org-link-${index}  ${text}

Open inforequest tree
  Scroll and click test id  applications-create-new-inforequest
  Input text  create-search  ${propertyId}
  Click enabled by test id  create-search-button
  Wait until  Element should be visible  xpath=//div[@id='popup-id']//input[@data-test-id='create-property-id']
  Textfield Value Should Be  xpath=//div[@id='popup-id']//input[@data-test-id='create-property-id']  ${propertyId}
  Wait Until  Selected Municipality Is  753
  Wait until element is enabled  create-location-continue
  Click by test id  create-continue
  Set animations off
  Wait until  Element should be visible  xpath=//section[@id='create-part-2']//div[@class='tree-page']


Finish creation
  Scroll and click test id  create-proceed-to-inforequest
  Wait test id visible  create-inforequest-message
  Press key  jquery=textarea[data-test-id=create-inforequest-message]  Hello
  Scroll and click test id  create-inforequest
  Confirm  dynamic-ok-confirm-dialog
