*** Settings ***

Documentation   Testing events from 3rd party JS API buttons
Suite Setup     Apply minimal fixture now
Suite Teardown  Logout
Resource        ../../common_resource.robot
Resource        ../18_construction/task_resource.robot
Resource        ../common_keywords/approve_helpers.robot
Variables      ../06_attachments/variables.py

*** Variables ***
@{PERMIT_PROPERTIES}   id  location  address  municipality  applicant  type  authority  operation  permitType

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
  Open search tab  all
  Wait until  Xpath Should Match X Times  //table[@id="applications-list"]//tbody/tr[@class="application-row"]  1
  Element should not be visible  xpath=//section[@id='applications']//button[@data-test-id='external-show-permits-on-map']
  Logout

Porvoo has allowed IP in use and can see Show permits on map button
  As Pekka
  Create application the fast way  Tinatuopintie 3  638-417-1-738  kerrostalo-rivitalo
  Go to page  applications
  Open search tab  all
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
  Approve application
  Wait until  Element text should be  xpath=//div[@id='modal-dialog']//div[@class='header']/span  LupapisteApi.integrationSent
  Permit properties should be visible in dialog

Add post verdict attachment
  Open tab  verdict
  Fetch verdict

  Open tab  attachments
  Wait until  Element should not be visible  xpath=//button[@data-test-id='export-attachments-to-backing-system']
  Upload attachment  ${PDF_TESTFILE_PATH}  Muu liite  Muu  Asuinkerrostalon tai rivitalon rakentaminen

Transfering attachments emits LupapisteApi.integrationSent function call
  Scroll to top
  # We have 3 buttons with the same test-id, check & click the first
  Wait Until  Element Should Be Enabled  xpath=//button[@data-test-id='export-attachments-to-backing-system']
  Scroll to test id  export-attachments-to-backing-system
  Click Element  xpath=//button[@data-test-id='export-attachments-to-backing-system']

  Wait Until  Page should contain  Siirrä liitteet taustajärjestelmään

  Click enabled by test id  multiselect-action-button
  Confirm  dynamic-yes-no-confirm-dialog

  Permit properties should be visible in dialog
  Element should not be visible  xpath=//button[@data-test-id='export-attachments-to-backing-system']

Fill review info
  Open tab  tasks
  Open task  Aloituskokous

  Select From List by test id  katselmus.tila  lopullinen
  Execute JavaScript  $(".hasDatepicker").unbind("focus");
  Input text with jQuery  input[data-test-id="katselmus.pitoPvm"]  29.2.2016
  Input text with jQuery  input[data-test-id="katselmus.pitaja"]  Sonja Sibbo
  Input text with jQuery  textarea[data-test-id="katselmus.huomautukset.kuvaus"]  ei

Transfering task emits LupapisteApi.integrationSent function call
  Click enabled by test id  review-done
  Confirm yes no dialog
  Confirm  dynamic-ok-confirm-dialog
  Permit properties should be visible in dialog

Button not visible if parent function isnt implemented
  Go to page  applications
  Open search tab  all
  Wait until  Element should be visible  xpath=//section[@id='applications']//button[@data-test-id='external-show-permits-on-map']
  Execute Javascript  window.LupapisteApi.showPermitsOnMap = null
  Open search tab  application
  Wait until  Xpath Should Match X Times  //table[@id="applications-list"]//tbody/tr[@class="application-row"]  0
  Wait until  Element should not be visible  xpath=//section[@id='applications']//button[@data-test-id='external-show-permits-on-map']
  Open search tab  all
  Wait until  Xpath Should Match X Times  //table[@id="applications-list"]//tbody/tr[@class="application-row"]  1
  Wait until  Element should not be visible  xpath=//section[@id='applications']//button[@data-test-id='external-show-permits-on-map']


*** Keywords ***

Permit properties should be visible in dialog
  Wait Until  Element Should Be Visible  modal-dialog-content-component
  :FOR  ${property}  IN  @{PERMIT_PROPERTIES}
  \  Element should contain  xpath=//div[@id='modal-dialog-content-component']//p[@class='dialog-desc']  ${property}
  Confirm notification dialog
