*** Settings ***

Documentation   Mikko adds an sensitive attachment to application, and hides it from other parties
Suite Teardown  Logout
Resource        ../../common_resource.robot
Variables       variables.py

*** Test Cases ***

Mikko uploads attachment
  [Tags]  attachments
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  attachments${secs}
  Set Suite Variable  ${propertyId}  753-416-6-1
  Mikko logs in
  Create application the fast way  ${appname}  ${propertyId}  kerrostalo-rivitalo
  Open tab  attachments
  Add attachment  application  ${PNG_TESTFILE_PATH}  ${EMPTY}  osapuolet.cv
  Wait Until  Element should be visible  jquery=div[id=application-attachments-tab] tr[data-test-type='osapuolet.cv']

Mikko sets CV to be visible only to himself and authorities
  Open attachment details  osapuolet.cv
  # By default attachment is public
  List selection should be  xpath=//section[@id='attachment']//select[@data-test-id='attachment-visibility']  Julkinen
  Select From List By Value  xpath=//section[@id='attachment']//select[@data-test-id='attachment-visibility']  viranomainen
  Positive indicator icon should be visible
  Scroll to top
  Click element  xpath=//section[@id="attachment"]//a[@data-test-id="back-to-application-from-attachment"]
  Wait Until  Tab should be visible  attachments

Mikko uploads attachment to placeholder asemapiirros
  Open attachment details  paapiirustus.asemapiirros
  Wait Until  Element Should Be Disabled  xpath=//section[@id='attachment']//select[@data-test-id='attachment-visibility']
  Add attachment version  ${PNG_TESTFILE_PATH}
  Wait Until  Element Should Be Enabled  xpath=//section[@id='attachment']//select[@data-test-id='attachment-visibility']
  Click element  xpath=//section[@id="attachment"]//a[@data-test-id="back-to-application-from-attachment"]
  Wait Until  Tab should be visible  attachments

Mikko invites Teppo and Pena
  Invite teppo@example.com to application
  Invite pena@example.com to application
  Logout

Teppo logs in, doesn't see Mikko's CV
  Teppo logs in
  Wait until  Click element  xpath=//div[@class='invitation']//a[@data-test-id='open-application-button']
  Confirm yes no dialog
  Open tab  attachments
  Wait Until  Element should not be visible  jquery=div[id=application-attachments-tab] tr[data-test-type='osapuolet.cv']

Teppo uploads new version to asemapiirros
  # When Teppo uploads version to attachment, he is authed to attachment and can see contents even if visibility is set to only-authority level
  Open attachment details  paapiirustus.asemapiirros
  Add attachment version  ${PNG_TESTFILE_PATH}
  Wait Until  Click button  id=show-attachment-versions
  Wait Until  Xpath Should Match X Times  //section[@id='attachment']//div[@class='attachment-file-versions-content']//table/tbody/tr  2

Teppo sets asemapiirros to only-authority visibility
  List selection should be  xpath=//section[@id='attachment']//select[@data-test-id='attachment-visibility']  Julkinen
  Select From List By Value  xpath=//section[@id='attachment']//select[@data-test-id='attachment-visibility']  viranomainen
  Positive indicator icon should be visible

  Logout

Pena logs in, doesn't see Mikko's CV, nor asemapiirros
  Pena logs in
  Wait until  Click element  xpath=//div[@class='invitation']//a[@data-test-id='open-application-button']
  Confirm yes no dialog
  Open tab  attachments
  Wait Until  Element should be visible  jquery=div[id=application-attachments-tab] tr[data-test-type='paapiirustus.pohjapiirustus']
  Element should not be visible  jquery=div[id=application-attachments-tab] tr[data-test-type='osapuolet.cv']
  Element should not be visible  jquery=div[id=application-attachments-tab] tr[data-test-type='paapiirustus.asemapiirros']

  Logout

Mikko logs in, sets asemapiirros's visibility to 'parties'
  Mikko logs in
  Open application  ${appname}  ${propertyId}
  Open tab  attachments
  Open attachment details  paapiirustus.asemapiirros
  ${selection}=  Get Selected List Value  xpath=//section[@id='attachment']//select[@data-test-id='attachment-visibility']
  Should Be Equal As Strings  ${selection}  viranomainen
  Select From List By Value  xpath=//section[@id='attachment']//select[@data-test-id='attachment-visibility']  asiakas-ja-viranomainen
  Positive indicator icon should be visible

  Logout

Pena logs in and now sees asemapiirros, as he has auth to application
  Pena logs in
  Open application  ${appname}  ${propertyId}
  Open tab  attachments
  Wait Until  Element should be visible  jquery=div[id=application-attachments-tab] tr[data-test-type='paapiirustus.pohjapiirustus']
  Element should be visible  jquery=div[id=application-attachments-tab] tr[data-test-type='paapiirustus.asemapiirros']
  # CV is still hidden
  Element should not be visible  jquery=div[id=application-attachments-tab] tr[data-test-type='osapuolet.cv']
