*** Settings ***

Documentation   Mikko creates a new application
Resource        ../../common_resource.robot
Suite Setup     Apply minimal fixture now

*** Test Cases ***

#Setting maps enabled for these tests
#  Set integration proxy on

Mikko creates a new application
  [Tags]  ie8  firefox
  Mikko logs in
  Create first application  create-app  753  753-423-2-41  R
  Wait until  Title Should Be  create-app - Lupapiste
  It is possible to add operation

Mikko sees application in list
  [Tags]  ie8  firefox
  Go to page  applications
  Request should be visible  create-app

Mikko creates a new inforequest
  [Tags]  firefox
  Create inforequest  create-info  753  753-416-25-22  Hoblaa  R
  Wait until  Element text should be  //section[@id='inforequest']//span[@data-test-id='inforequest-application-applicant']  Intonen Mikko
  Wait until  Element should be visible  //section[@id='inforequest']//span[@data-test-primary-operation-id='kerrostalo-rivitalo']
  Element should not be visible  //h2[@data-test-id='wanna-join']
  Wait until  Title Should Be  create-info - Lupapiste

Mikko sees one application and one inforequest
  [Tags]  firefox
  Go to page  applications
  Request should be visible  create-app
  Request should be visible  create-info

Mikko is really hungry and runs to Selvi for some delicious liha-annos
  [Tags]  ie8  firefox
  Logout

Teppo should not see Mikko's application
  [Tags]  firefox
  Teppo logs in
  Request should not be visible  create-app
  Request should not be visible  create-info
  Logout

Mikko comes back and sees his application and inforequest
  [Tags]  firefox
  Mikko logs in
  Request should be visible  create-app
  Request should be visible  create-info

Mikko inspects inforequest and sees his initial comments
  [Tags]  firefox
  Open inforequest  create-info  753-416-25-22
  Wait until  Xpath Should Match X Times  //section[@id='inforequest']//div[@data-test-id='comments-table']//span[text()='Hoblaa']  1

#LUPA-585
The contents of unsent inforequest's message field is resetted properly when moving to another inforequest
  [Tags]  firefox
  Input text  xpath=//section[@id='inforequest']//textarea[@data-test-id='application-new-comment-text']  roskaa
  # XXX 'Element Should Contain' or 'Textfield Value Should Be' do not work for some reason
  Wait For Condition  return $("#inforequest").find("textarea[data-test-id='application-new-comment-text']").val() == "roskaa";

  Create inforequest the fast way  create-info-2  360603.153  6734222.95  753-416-25-22  kerrostalo-rivitalo  init-comment-2
  Wait For Condition  return $("#inforequest").find("textarea[data-test-id='application-new-comment-text']").val() == "";

Mikko cancels the initial inforequest
  Open inforequest  create-info  753-416-25-22
  Close current inforequest
  Wait until  Applications page should be open
  Request should not be visible  create-info

Mikko creates new application
  [Tags]  firefox
  Wait until  Applications page should be open
  Create application the fast way  create-app-2  753-416-25-22  kerrostalo-rivitalo
  Go to page  applications
  Request should be visible  create-app
  Request should be visible  create-app-2

Mikko closes application at Latokuja 3 and logs out
  [Tags]  firefox
  Open application  create-app-2  753-416-25-22
  Close current application  Zaijian
  Wait Until  Request should be visible  create-app
  Wait Until  Request should not be visible  create-app-2
  Open canceled application  create-app-2  753-416-25-22
  Check comment  Zaijian
  [Teardown]  logout

# LUPA-23
Authority (Veikko) can create an application
  [Tags]  ie8  firefox
  Veikko logs in
  Create application the fast way  create-veikko-auth-app  837-111-172-1  kerrostalo-rivitalo
  Wait until  Application state should be  open
  It is possible to add operation

# LUPA-23
Veikko can submit the application he created
  [Tags]  ie8  firefox
  Open tab  requiredFieldSummary
  Wait Until  Element should be visible  //*[@data-test-id='application-submit-btn']

Veikko sees application in list
  [Tags]  ie8  firefox
  Go to page  applications
  Request should be visible  create-veikko-auth-app
  [Teardown]  logout



#Setting maps disabled again after the tests
#  Set integration proxy off

