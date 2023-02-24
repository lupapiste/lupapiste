*** Settings ***

Documentation   Mikko creates property formation (kiinteistönmuodostus) app based on plot allocation (tonttijako) app
Resource        ../../common_resource.robot
Library         DateTime
Suite Setup     Apply minimal fixture now
Suite Teardown  Logout

*** Variables ***

${property-id}  753-416-25-30

*** Test Cases ***

Create-property-formation-app-btn is hidden for kerrostalo-rivitalo application
  ${date}=         Get Current Date  result_format=datetime
  Set Suite Variable  ${year}  ${date.year}
  Pena logs in
  Create application the fast way  Katu 12  ${property-id}  kerrostalo-rivitalo
  Submit application
  Element should not be visible by test id  application-create-property-formation-app-btn
  Logout

Mikko creates and submits plot allocation (tonttijako) application
  Mikko logs in
  Create application the fast way  Katu 12  ${property-id}  tonttijako
  Element should be visible by test id  application-create-property-formation-app-btn

Mikko invites Pena and Teppo and submits the application
  Invite pena@example.com to application
  Invite teppo@example.com to application
  Submit application

Mikko starts to create property formation app (kiinteistonmuodostus) app based on tonttijako app
  Click by test id  application-create-property-formation-app-btn
  Test id text is  location-for-new-app  ${property-id}, Katu 12, Sipoo

Mikko invites Pena to the new app
  ${auth-path}=  Set variable  //section[@id='property-formation-app']//div[@id='auths-to-invite']
  Element text should be  ${auth-path}/div[1]//label  Pena Panaani, Kirjoitusoikeus
  Element text should be  ${auth-path}/div[2]//label  Teppo Nieminen, Kirjoitusoikeus
  Click element  xpath=${auth-path}/div[1]//label
  Click by test id  create-property-formation-app-button
  # there seems to be several confirm-yes buttons in DOM
  Click element  xpath=//div[@id='dynamic-yes-no-confirm-dialog']//button[@data-test-id='confirm-yes']

Mikko is directed to the new application
  Wait until  Element Should Be Visible  application
  Wait until  Application state should be  draft
  Wait until  Application address should be  Katu 12
  # FIXME hardocded year
  Test id text is  application-id  LP-753-${year}-90003
  Test id text is  test-application-primary-operation  Kiinteistönmuodostus
  Test id text is  application-property-id  ${property-id}

Mikko sees an indicator informing that creating new application was successful
  Positive indicator should be visible
  Indicator should contain text  Kiinteistönmuodostushakemus luotiin onnistuneesti.

Pena is invited to the new application but Teppo is not
  Open tab  parties
  Is authorized party  pena
  Is not authorized party  teppo@example.com

Applications are linked
  Click by test id  test-application-link-permit-lupapistetunnus
  Test id text is  application-id  LP-753-${year}-90002
  Click by test id  test-application-app-linking-to-us
  Test id text is  application-id  LP-753-${year}-90003

Pena receives invitation email to the new Application
  Open last email
  Wait until   Page should contain  Sinut halutaan valtuuttaa kirjoitusoikeudella osapuoleksi Lupapisteessä olevaan hankkeeseen sähköpostiosoitteella pena@example.com. Valtuutus koskee hanketta Kiinteistönmuodostus osoitteessa Katu 12, Sipoo.
  Click link  xpath=//a
