*** Settings ***

Documentation   Automatic assignments are created as new attachments are added
Suite Setup     Apply minimal fixture now
Suite Teardown  Logout
Resource        ../../common_resource.robot
Resource        assignments_common.robot
Resource        ../06_attachments/attachment_resource.robot
Variables       ../06_attachments/variables.py

*** Test Cases ***

Pena logs in, creates and submits application
  Set Suite Variable  ${appname}  To Do
  Set Suite Variable  ${propertyid}  753-416-88-88
  Pena logs in
  Create application with state  ${appname}  ${propertyid}  kerrostalo-rivitalo  submitted
  Open tab  attachments

Pena uploads an application for which there is an automatic assignment trigger in Sipoo
  Add attachment file  tr[data-test-type='paapiirustus.asemapiirros']  ${TXT_TESTFILE_PATH}  oma piirustus
  Scroll and click test id  batch-ready

Pena doesn't see filters 'Ei tehtäviä' and 'Aita ja asema'
  Wait until  Element should be visible  xpath=//label[@data-test-id='other-filter-label']
  Wait until  Element should not be visible  xpath=//label[@data-test-id='assignment-not-targeted-filter-label']
  Wait until  Element should not be visible  xpath=//label[@data-test-id='assignment-dead1111111111111112beef-filter-label']
  Logout

Sonja logs in and opens application
  As Sonja
  Open application  ${appname}  ${propertyid}
  Open tab  attachments

Automatic assignment with the attachment as the target has been created
  Automatic assignment  0
  Wait until  Element should contain  xpath=//div[@data-test-id='automatic-assignment-0']//div[@data-test-id='assignment-text']  käsittelemätön päivitys
  Wait until  Element should contain  xpath=//div[@data-test-id='automatic-assignment-0']//div[@data-test-id='assignment-text']//a  Aita ja asema

Sonja sees filters 'Ei tehtäviä' and 'Aita ja asema'
  Wait until  Checkbox wrapper not selected by test id  assignment-not-targeted-filter
  Wait until  Checkbox wrapper not selected by test id  assignment-dead1111111111111112beef-filter
  Wait until  Total attachments row count is  4
  Toggle toggle  assignment-dead1111111111111112beef-filter
  Wait until  Total attachments row count is  1
  Toggle toggle  assignment-dead1111111111111112beef-filter
  Toggle toggle  assignment-not-targeted-filter
  Wait until  Total attachments row count is  3
  Toggle toggle  assignment-not-targeted-filter

Sonja opens the attachments targeted by the automatic assignment
  Wait until  Total attachments row count is  4
  Click by test id  filter-link-dead1111111111111112beef
  Wait until  Checkbox wrapper selected by test id  assignment-dead1111111111111112beef-filter
  Wait until  Total attachments row count is  1
  Toggle toggle  assignment-dead1111111111111112beef-filter
  Wait until  Total attachments row count is  4

Sonja adds handler to application
  Click by test id  edit-handlers
  Wait test id visible  add-handler
  Click by test id  add-handler
  Edit handler  0  Sibbo Sonja  Käsittelijä
  Logout

Pena uploads two more attachments
  As Pena
  Open application  ${appname}  ${propertyid}
  Open tab  attachments
  Upload attachment  ${TXT_TESTFILE_PATH}  Asemapiirros  oma piirustus  Yleisesti hankkeeseen
  Upload attachment  ${TXT_TESTFILE_PATH}  Asemapiirros  oma piirustus  Yleisesti hankkeeseen
  Logout

Assignments are assigned to Sonja
  As Sonja
  Open assignments search
  Open search tab  all
  Scroll and click test id  toggle-advanced-filters
  Wait until  Element should be visible by test id  recipient-filter-component
  Autocomplete selection is  div[@data-test-id="recipient-filter-component"]  Omat tehtäväni
  Xpath Should Match X Times  //table[@id="assignments-list"]//tbody/tr[contains(@class, 'assignment-row')]  1
  Element text should be  xpath=(//table[@id="assignments-list"]//tbody/tr[contains(@class, 'assignment-row')])[1]/td[@data-test-col-name='description']  Aita ja asema

Sonja can see that there are more attachments in the same assignment
  Open applications search
  Open application  ${appname}  ${propertyid}
  Open tab  attachments
  Wait until  Element should contain  xpath=//div[@data-test-id='automatic-assignment-0']//div[@data-test-id='assignment-text']  3 käsittelemätöntä päivitystä
  Wait until  Element should contain  xpath=//div[@data-test-id='automatic-assignment-0']//div[@data-test-id='assignment-text']//a  Aita ja asema

Sonja completes the automatic assignment
  Click by test id  mark-assignment-complete
  Wait until  Element should not be visible  xpath=//div[@data-test-id='automatic-assignment']
  Logout

Pena uploads two more attachments belonging in different automatic assignment trigger groups
  As Pena
  Open application  ${appname}  ${propertyid}
  Open tab  attachments
  Upload attachment  ${TXT_TESTFILE_PATH}  Asemapiirros  oma piirustus  Yleisesti hankkeeseen
  Upload attachment  ${TXT_TESTFILE_PATH}  ELY:n tai kunnan poikkeamapäätös  poikkeamapäätös  Yleisesti hankkeeseen
  Logout

Sonja opens attachments tab and sees 'ELY ja naapuri' filter
  As Sonja
  Open application  ${appname}  ${propertyid}
  Open tab  attachments
  Wait until  Checkbox wrapper not selected by test id  assignment-not-targeted-filter
  Wait until  Checkbox wrapper not selected by test id  assignment-dead1111111111111112beef-filter
  Wait until  Checkbox wrapper not selected by test id  assignment-dead1111111111111111beef-filter
  Wait until  Total attachments row count is  8
  Click by test id  filter-link-dead1111111111111112beef
  Wait until  Total attachments row count is  1
  Toggle toggle  assignment-dead1111111111111112beef-filter
  Scroll and click test id  filter-link-dead1111111111111111beef
  Wait until  Total attachments row count is  1
  Toggle toggle  assignment-dead1111111111111111beef-filter
  Wait until  Total attachments row count is  8

Sonja has two assignments
  Open assignments search
  Open search tab  all
  Scroll and click test id  toggle-advanced-filters
  Wait until  Element should be visible by test id  recipient-filter-component
  Autocomplete selection is  div[@data-test-id="recipient-filter-component"]  Omat tehtäväni
  Xpath Should Match X Times  //table[@id="assignments-list"]//tbody/tr[contains(@class, 'assignment-row')]  2
  Element text should be  xpath=(//table[@id="assignments-list"]//tbody/tr[contains(@class, 'assignment-row')])[1]/td[@data-test-col-name='description']  Aita ja asema
  Element text should be  xpath=(//table[@id="assignments-list"]//tbody/tr[contains(@class, 'assignment-row')])[1]/td[@data-test-col-name='description']  Aita ja asema

Sonja can see two automatic assignments
  Open applications search
  Open application  ${appname}  ${propertyid}
  Open tab  attachments
  Wait until  Element should contain  xpath=//div[@data-test-id='automatic-assignment-0']//div[@data-test-id='assignment-text']  käsittelemätön päivitys
  Wait until  Element should contain  xpath=//div[@data-test-id='automatic-assignment-0']//div[@data-test-id='assignment-text']//a  Aita ja asema
  Wait until  Element should contain  xpath=//div[@data-test-id='automatic-assignment-1']//div[@data-test-id='assignment-text']  käsittelemätön päivitys
  Wait until  Element should contain  xpath=//div[@data-test-id='automatic-assignment-1']//div[@data-test-id='assignment-text']//a  ELY ja naapuri

Sonja deletes ELY:n tai kunnan poikkeamapäätös attachment, and the associated automatic assignment is deleted
  Wait Until  Delete attachment  ennakkoluvat_ja_lausunnot.elyn_tai_kunnan_poikkeamapaatos
  Wait until  Element should not be visible  xpath=//div[@data-test-id='automatic-assignment-1']

Sonja completes the Aita ja asema assignment
  Scroll and click test id  mark-assignment-complete
  Wait until  Element should not be visible  xpath=//div[@data-test-id='automatic-assignment-1']
  Logout

Pena logs in and changes the type of aitapiirros to aitapiirustus
  As Pena
  Open application  ${appname}  ${propertyid}
  Open tab  attachments
  Open attachment details  paapiirustus.asemapiirros
  Click enabled by test id  change-attachment-type
  Select from list  attachment-type-select  paapiirustus.aitapiirustus
  Wait Until  Element Should Not Be Visible  attachment-type-select-loader
  Click enabled by test id  confirm-yes
  Positive indicator should be visible
  Positive indicator should not be visible
  Wait until  Element should be visible  jquery=a[data-test-id=back-to-application-from-attachment]
  Scroll to test id  back-to-application-from-attachment
  Click element  jquery=[data-test-id=back-to-application-from-attachment]
  Wait Until  Tab should be visible  attachments
  Logout

Sonja opens the application and sees that changing the type has created a new automatic assignment
  As Sonja
  Open application  ${appname}  ${propertyid}
  Open tab  attachments
  Wait until  Element should contain  xpath=//div[@data-test-id='automatic-assignment-0']//div[@data-test-id='assignment-text']  käsittelemätön päivitys
  Wait until  Element should contain  xpath=//div[@data-test-id='automatic-assignment-0']//div[@data-test-id='assignment-text']//a  Aita ja asema

The assignment targets the 'Aitapiirustus' attachment
  Scroll and click test id  filter-link-dead1111111111111112beef
  Wait until  Total attachments row count is  1
  Wait until  Element should be visible  xpath=//tr[@data-test-type='paapiirustus.aitapiirustus']

Sonja changes handler
  Automatic assignment  0  1  Sonja Sibbo
  Scroll and click test id  edit-handlers
  Edit handler  0  Sibbo Ronja  Käsittelijä
  Positive indicator should be visible
  Positive indicator should not be visible
  Scroll and click test id  edit-handlers-back

Automatic assignment recipient has changed
  Automatic assignment  0  1  Ronja Sibbo

Sonja has no open assignments
  Open assignments search
  Scroll and click test id  toggle-advanced-filters
  Wait until  Element should be visible by test id  recipient-filter-component
  Autocomplete selection is  div[@data-test-id="recipient-filter-component"]  Omat tehtäväni
  Xpath Should Match X Times  //table[@id="assignments-list"]//tbody/tr[contains(@class, 'assignment-row')]  0
  Logout

Ronja has one assignment
  As Ronja
  Open assignments search
  Open search tab  all
  Scroll and click test id  toggle-advanced-filters
  Wait until  Element should be visible by test id  recipient-filter-component
  Autocomplete selection is  div[@data-test-id="recipient-filter-component"]  Omat tehtäväni
  Xpath Should Match X Times  //table[@id="assignments-list"]//tbody/tr[contains(@class, 'assignment-row')]  1
  Element text should be  xpath=(//table[@id="assignments-list"]//tbody/tr[contains(@class, 'assignment-row')])[1]/td[@data-test-col-name='description']  Aita ja asema


Ronja opens the application
  Open applications search
  Open application  ${appname}  ${propertyid}
  Open tab  attachments
  Automatic assignment  0  1  Ronja Sibbo

Ronja uploads Asemapiirros
  Upload attachment  ${TXT_TESTFILE_PATH}  Asemapiirros  Doodle  Yleisesti hankkeeseen

Automatic assignment is updated
  Automatic assignment  0  2  Ronja Sibbo
  [Teardown]  Logout

Frontend error
  There are no frontend errors
