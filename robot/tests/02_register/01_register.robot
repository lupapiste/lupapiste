*** Settings ***

Documentation   Mikko registers
Suite Setup     Apply minimal fixture now
Suite teardown  Logout
Resource       ../../common_resource.robot

*** Test Cases ***

Cancelling vetuma return back to register page
  [Tags]  integration  ie8
  Go to register page
  Register button is visible
  Cancel via vetuma
  Wait until page contains element  register-cancel

VTJ-data should be populated from Osuuspankki
  [Tags]  integration  ie8
  Go to login page
  Go to register page
  Register button is visible
  Authenticate via Osuuspankki via Vetuma
  Wait until page contains element  test-register-submit
  Wait until  Element Should Be Disabled  test-register-submit
  Textfield should contain  test-register-street  Sepänkatu 11 A 5
  Textfield should contain  test-register-zip  70100
  Textfield should contain  test-register-city  KUOPIO

Filling register form2
  [Tags]  integration  ie8
  Go to login page
  Go to register page
  Register button is visible
  Authenticate via Nordea via Vetuma
  Wait until page contains element  test-register-personid
  Wait until  Element Should Be Disabled  test-register-submit

  Input Text  test-register-street  Rambokuja 6
  Element Should Be Disabled  test-register-submit

  Input Text  test-register-zip  33800
  Element Should Be Disabled  test-register-submit

  Input Text  test-register-city  sipoo
  Element Should Be Disabled  test-register-submit

  Input Text  test-register-phone  +358554433221
  Element Should Be Disabled  test-register-submit

  Input Text  test-register-email  vetuma@example.com
  Element Should Be Disabled  test-register-submit

  Input Text  test-register-confirmEmail  vetuma@example.com
  Element Should Be Disabled  test-register-submit

  Input Text  test-register-password  vetuma69
  Element Should Be Disabled  test-register-submit

  Input Text  test-register-confirmPassword  vetuma68
  Element Should Be Disabled  test-register-submit

  Input Text  test-register-confirmPassword  vetuma69
  Element Should Be Disabled  test-register-submit

  Checkbox Should Not Be Selected  allowDirectMarketing
  Select Checkbox  allowDirectMarketing
  Checkbox Should Be Selected  allowDirectMarketing

  Checkbox Should Not Be Selected  acceptTerms
  Select Checkbox  acceptTerms
  Checkbox Should Be Selected  acceptTerms
  Element Should Be Enabled  test-register-submit

Submitting form gives confirmation
  [Tags]  integration  ie8
  Click Button  test-register-submit
  Wait until  element should be visible  xpath=//*[@data-bind="ltext: 'register.activation-email-info1'"]
  Element text should be  activation-email  vetuma@example.com

Can not login before activation
  [Tags]  integration  ie8
  Go to login page
  Login fails  vetuma@example.com  vetuma69

Vetuma-guy activates his account
  [Tags]  integration  ie8
  Go to  ${SERVER}/api/last-email
  Wait Until  Page Should Contain  vetuma@example.com
  Page Should Contain  /app/security/activate
  ## Click the first link
  Click link  xpath=//a

Vetuma-guy lands to empty applications page
  [Tags]  integration  ie8
  Login  vetuma@example.com  vetuma69
  User should be logged in  Nordea Demo
  Applications page should be open
  Number of visible applications  0

*** Keywords ***

Go to register page
  Focus  register-button
  Click Button    register-button

Register button is visible
  Wait until page contains element  vetuma-init

Cancel via vetuma
  Click button  vetuma-init
  Click link  << Palaa palveluun
  Click button  Palaa palveluun

Authenticate via Osuuspankki via Vetuma
  Click button   vetuma-init
  Wait Until  Element Should Be Visible  xpath=//img[@alt='Pankkitunnistus']
  Click element  xpath=//img[@alt='Pankkitunnistus']
  Wait Until  Element Should Be Visible  xpath=//a[@class='osuuspankki']
  Click element  xpath=//a[@class='osuuspankki']
  Wait Until  Element Should Be Visible  xpath=//input[@class='login']
  Input text     xpath=//input[@class='login']  123456
  Input text     xpath=//input[@type='PASSWORD']  7890
  Click button   xpath=//input[@name='ktunn']
  Wait Until  Element Should Be Visible  xpath=//input[@name='avainluku']
  Input text     xpath=//input[@name='avainluku']  1234
  Click button   xpath=//input[@name='avainl']
  Wait Until  Element Should Be Visible  xpath=//input[@name='act_hyvaksy']
  Click button   xpath=//input[@name='act_hyvaksy']
  Wait Until  Element Should Be Visible  xpath=//a[contains(text(),'Palaa palveluntarjoajan sivulle')]
  Click link     xpath=//a[contains(text(),'Palaa palveluntarjoajan sivulle')]
  Wait Until  Element Should Be Visible  xpath=//button[@type='submit']
  Click element  xpath=//button[@type='submit']

Authenticate via Nordea via Vetuma
  Click button  vetuma-init
  Wait Until  Element Should Be Visible  xpath=//img[@alt='Pankkitunnistus']
  Click element  xpath=//img[@alt='Pankkitunnistus']
  Wait Until  Element Should Be Visible  xpath=//a[@class='nordea']
  Click element  xpath=//a[@class='nordea']
  Wait Until  Element Should Be Visible  xpath=//input[@name='Ok']
  Click element  xpath=//input[@name='Ok']
  Wait Until  Element Should Be Visible  xpath=//input[@type='submit']
  Click element  xpath=//input[@type='submit']
  Wait Until  Element Should Be Visible  xpath=//button[@type='submit']
  Click element  xpath=//button[@type='submit']
