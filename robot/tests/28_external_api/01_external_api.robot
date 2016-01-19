*** Settings ***

Documentation   Testing events from 3rd party JS API buttons
Suite Setup     Apply minimal fixture now
Suite Teardown  Logout
Resource        ../../common_resource.robot

*** Variables ***
@{PERMIT_PROPERTIES}   id  location  address  municipality  applicant  type  authority  operation

*** Test Cases ***

# Testing 'LupapisteApi' JS API which supports embedding.
# Testable functions/buttons:
#  - LupapisteApi.showPermitsOnMap
#  - LupapisteApi.openPermit
#  - LupapisteApi.showPermitOnMap
#  - LupapisteApi.integrationSent

JS API is not available for organizations which don't have IPs allowed
  As Sonja
  Create application the fast way  sipoo  753-416-25-32  kerrostalo-rivitalo
  Go to page  applications
  Wait until  Xpath Should Match X Times  //table[@id="applications-list"]//tbody/tr[@class="application-row"]  1
  Element should not be visible  xpath=//section[@id='applications']//button[@data-test-id='external-show-permits-on-map']
  Logout

Porvoo has allowed IP in use and can see Show permits on map button
  As Pekka
  Create application the fast way  Tinatuopintie 3  638-417-1-738  kerrostalo-rivitalo
  Go to page  applications
  Wait until  Xpath Should Match X Times  //table[@id="applications-list"]//tbody/tr[@class="application-row"]  1
  Element should be visible  xpath=//section[@id='applications']//button[@data-test-id='external-show-permits-on-map']

#  - LupapisteApi.showPermitsOnMap
Show permits on map button outputs correct data
  Click by test id  external-show-permits-on-map
  Wait until  Element text should be  xpath=//div[@id='modal-dialog']//div[@class='header']/span  LupapisteApi.showPermitsOnMap
  Permit properties should be visible in dialog

#  - LupapisteApi.openPermit
#  - LupapisteApi.showPermitOnMap
Open permit and show permit on map buttons are visible
  Open application  Tinatuopintie 3  638-417-1-738

  Element should be visible  xpath=//section[@id='application']//button[@data-test-id='external-open-permit']
  Click by test id  external-open-permit
  Wait until  Element text should be  xpath=//div[@id='modal-dialog']//div[@class='header']/span  LupapisteApi.openPermit
  Permit properties should be visible in dialog

  Element should be visible  xpath=//section[@id='application']//button[@data-test-id='external-show-on-map']
  Click by test id  external-show-on-map
  Wait until  Element text should be  xpath=//div[@id='modal-dialog']//div[@class='header']/span  LupapisteApi.showPermitOnMap
  Permit properties should be visible in dialog

#  - LupapisteApi.integrationSent
Successful KRYSP generation emits LupapisteApi.integrationSent function call
  Submit application
  Click enabled by test id  approve-application
  Wait until  Element text should be  xpath=//div[@id='modal-dialog']//div[@class='header']/span  LupapisteApi.integrationSent
  Permit properties should be visible in dialog


*** Keywords ***

Permit properties should be visible in dialog
  :FOR  ${property}  IN  @{PERMIT_PROPERTIES}
  \  Element should contain  xpath=//div[@id='modal-dialog-content-component']//p[@class='dialog-desc']  ${property}
  Confirm notification dialog


