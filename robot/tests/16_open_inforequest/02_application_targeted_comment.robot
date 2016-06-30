*** Settings ***

Documentation   Targeted comment emails
Suite Teardown  Logout
Resource        ../../common_resource.robot

*** Test Cases ***

Sonja creates an info request to Sipoo
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  Latokuja 1, Sipoo

  Sonja logs in
  User role should be  authority
  Create inforequest the fast way  ${appname}  404335.789  6693783.426  753-416-55-7  kerrostalo-rivitalo  Jiihaa
  Wait until  Element text should be  //section[@id='inforequest']//span[@data-test-id='inforequest-application-applicant']  Sibbo Sonja

Sonja sends comment to Kosti
  Select From List  xpath=//*[@id="side-panel-assigneed-authority"]  Kommentoija Kosti
  Input Text  application-new-comment-text  Kommentoiva kommentti
  Click by test id  application-new-comment-btn
  Sleep  5s

Kosti receives an email notification of the comment
  Open last email
  Wait until  Page should contain  Moi Kosti
  Go to login page
