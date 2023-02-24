*** Settings ***

Documentation  Sonja adds and removes files to the filebank. Sonja also adds and removes keywords for the files.
Suite Teardown  Logout
Resource       ../../common_resource.robot
Variables      ../06_attachments/variables.py
Resource       filebank_resource.robot

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

Sonja uploads file to the filebank
  Upload filebank file  ${PNG_TESTFILE_PATH}

Sonja adds keywords
  Add keyword  0  important file
  Add keyword  0  you can delete this file
  Add keyword  0  hello

Sonja sees the added keywords before uploading
  Wait until  page should contain  hello
  Check that text of a keyword is same before uploading  0  0  important file
  Check that text of a keyword is same before uploading  0  1  you can delete this file
  Check that text of a keyword is same before uploading  0  2  hello

Sonja finishes uploading
  Click by test id  finish-upload

Sonja sees the added file in the file list
  Element should be visible by test id  filebank-row-0-download
  Page should contain  ${PNG_TESTFILE_NAME}

Sonja adds new keyword after uploading
  Add keyword after upload  0  mars jupiter venus

Sonja sees the added keyword
  Wait until  page should contain  mars jupiter venus
  Check that text of a keyword is same  0  3  mars jupiter venus

Sonja removes keyword after uploading
  Remove keyword after upload  0  0

Sonja sees the right keywords after removing
  Wait until  page should not contain  important file
  Check that text of a keyword is same  0  0  you can delete this file
  Check that text of a keyword is same  0  1  hello
  Check that text of a keyword is same  0  2  mars jupiter venus

Sonja uploads another file to the filebank
  Upload filebank file  ${PDF_TESTFILE_PATH}

Sonja adds keywords for the new file
  Add keyword  0  very important
  Add keyword  0  not so important

Sonja finishes uploading the second file
  Click by test id  finish-upload

Sonja sees the second added file in the file list
  Element should be visible by test id  filebank-row-1-download
  Page should contain  ${PDF_TESTFILE_NAME}

Sonja sees the keywords of the second file
  Wait until  page should contain  not so important
  Check that text of a keyword is same  1  0  very important
  Check that text of a keyword is same  1  1  not so important

Sonja removes two keywords from the second file
  Remove keyword after upload  1  1
  Remove keyword after upload  1  0

The second file does not any keywords
  Element should not be visible by test id  filebank-row-1-keywords-0-edit

Sonja adds third file in to the filebank
  Upload filebank file  ${TXT_TESTFILE_PATH}

Sonja finishes uploading the third file
  Click by test id  finish-upload

Sonja removes the first file
  Click by test id  filebank-row-0-remove-file

Sonja does not see the first file
  Wait until  Page should not contain  ${PNG_TESTFILE_NAME}
  Element should not be visible by test id  filebank-row-2-filebank-download

Sonja sees the second and third file after removing the first
  Page should contain  ${PDF_TESTFILE_NAME}
  Page should contain  ${TXT_TESTFILE_NAME}

Sonja removes the third file
  Remove file  1

Sonja does not see the third file but does see the second file
  Wait until  Page should not contain  ${TXT_TESTFILE_NAME}
  Element should not be visible by test id  filebank-row-1-filebank-download
  Page should contain  ${PDF_TESTFILE_NAME}
