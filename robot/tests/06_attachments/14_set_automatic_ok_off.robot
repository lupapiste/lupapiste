*** Settings ***

Documentation   Sipoo sets automatic ok off and Sonja tries to add an attachment
Suite Teardown  Logout
Resource        ../../common_resource.robot
Resource        attachment_resource.robot
Variables       variables.py

*** Test Cases ***
Sonja creates an application and goes to attachments tab
    [Tags]  attachments
    ${secs} =  Get Time  epoch
    Set Suite Variable  ${appname}  attachments${secs}
    Set Suite Variable  ${propertyId}  753-416-6-331
    Sonja logs in
    Create application the fast way  ${appname}  ${propertyId}  kerrostalo-rivitalo
    Open tab  attachments

Sonja adds an attachment and it is ok
    [Tags]  attachments
    Upload attachment  ${PNG_TESTFILE_PATH}  Muu liite  Miumau  Asuinkerrostalon tai rivitalon rakentaminen
    Scroll to  tr[data-test-type='muut.muu']
    Element should be visible  xpath=//button[contains(@class, 'btn-icon-only positive no-events')]

Sonja deletes the attachment and logs out
    [Tags]  attachments
    Delete attachment  muut.muu
    [Teardown]  Logout

Sipoo logs in goes to admin page and disables automatic ok
    [Tags]  attachments
    Sipoo logs in
    Go to page  applications
    Unselect set automatic ok for attachments checkbox
    [Teardown]  Logout

Sonja adds an attachment and it's not ok
    [Tags]  attachments
    Sonja logs in
    Open application  ${appname}  ${propertyId}
    Open tab  attachments
    Upload attachment  ${PNG_TESTFILE_PATH}  Muu liite  Maumiu  Asuinkerrostalon tai rivitalon rakentaminen
    Scroll to  tr[data-test-type='muut.muu']
    Element should not be visible  xpath=//button[contains(@class, 'btn-icon-only positive no-events')]

Sonja deletes the file and logs out
    [Tags]  attachments
    Delete attachment  muut.muu
    [Teardown]  Logout

Sipoo goes enables automatic ok
    [Tags]  attachments
    Sipoo logs in
    Go to page  applications
    Select set automatic ok for attachments checkbox
    [Teardown]  Logout


Sonja adds an attachment and it's ok she likes doing it a lot
    [Tags]  attachments
    Sonja logs in
    Open application  ${appname}  ${propertyId}
    Open tab  attachments
    Upload attachment  ${PNG_TESTFILE_PATH}  Muu liite  Miumiu  Asuinkerrostalon tai rivitalon rakentaminen
    Wait until  Element should be visible  xpath=//button[contains(@class, 'btn-icon-only positive no-events')]

*** Keywords ***

Unselect set automatic ok for attachments checkbox
    Element should be visible by test id  automatic-ok-wrapper
    Checkbox Should Be Selected  automatic-ok-for-attachments-enabled
    Click label  automatic-ok-for-attachments-enabled
    Checkbox Should not Be Selected  automatic-ok-for-attachments-enabled

Select set automatic ok for attachments checkbox
    Element should be visible by test id  automatic-ok-wrapper
    Checkbox Should Not Be Selected  automatic-ok-for-attachments-enabled
    Click label  automatic-ok-for-attachments-enabled
    Checkbox Should Be Selected  automatic-ok-for-attachments-enabled
