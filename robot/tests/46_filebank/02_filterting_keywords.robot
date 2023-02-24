*** Settings ***

Documentation   Sonja filters filebank files according to filename and keywords
Suite Teardown  Logout
Resource        ../../common_resource.robot
Resource        filebank_resource.robot
Variables       ../06_attachments/variables.py

*** Test Cases ***


Admin enables filebank for organization
  Admin enables filebank for organization  753-R

Sonja goes to empty filebank tab
  [Tags]  attachments
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  attachments${secs}
  Set Suite Variable  ${propertyId}  753-416-6-1
  Sonja logs in
  Create application the fast way  ${appname}  ${propertyId}  kerrostalo-rivitalo
  Open tab  filebank

Sonja uploads files to the filebank
  @{keywords-pdf}  Create list  test1  test2  test3
  @{keywords-png}  Create list  test1  test2
  @{keywords-txt}  Create list  test4
  Upload filebank file with keywords  ${PDF_TESTFILE_PATH}  @{keywords-pdf}
  Upload filebank file with keywords  ${PNG_TESTFILE_PATH}  @{keywords-png}
  Upload filebank file with keywords  ${TXT_TESTFILE_PATH}  @{keywords-txt}

All files are visible
  Page should contain  PDF
  Page should contain  PNG
  Page should contain  TXT

Filter box options should include a sorted list of the keywords
  Sleep  0.5s
  Element text should be  xpath=//div[contains(@class, 'filter-group')]  Valitse kaikki\ntest1test2test3test4

Checking the 'select all' box selects all filter options
  Click by test id  filter-box-show-all

Only PDF file is visible as it has all keywords
  Page should contain  PDF
  Page should not contain  PNG
  Page should not contain  TXT

Unchecking the 'select all' box unselects all filter options
  Click by test id  filter-box-show-all

All files are visible again
  Page should contain  PDF
  Page should contain  PNG
  Page should contain  TXT

Selecting a subset of filter options only shows some files
  Click by test id  filter-box-0
  Click by test id  filter-box-1
  Page should contain  PDF
  Page should contain  PNG
  Page should not contain  TXT

Editing the search bar text filters files by filename
  Input text by test id  search-files-bar  PDF

Only the PNG file is shown
  Page should contain  PDF
  Page should not contain  PNG
  Page should not contain  TXT

Sonja resets the filter options
  Click by test id  filter-box-show-all
  Click by test id  filter-box-show-all
  Clear rum text field by test id  search-files-bar

All files are visible yet again
  Page should contain  PDF
  Page should contain  PNG
  Page should contain  TXT


