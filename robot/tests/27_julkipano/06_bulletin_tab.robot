*** Settings ***

Documentation   User authenticates to Julkipano.fi via Vetuma
Suite Setup     Apply minimal fixture now
Suite Teardown  Logout
Resource        ../../common_resource.robot
Resource        ./julkipano_common.robot

*** Test Cases ***

Mikko opens new application
  As Mikko
  Create sent application  Vaalantie 540  564-404-26-102
  Logout

Olli publishes sent application
  As Olli
  Open application  Vaalantie 540  564-404-26-102
  Publish bulletin
  Bulletin shows as proclaimed

Olli fetches verdict for application
  Submit empty verdict
  Bulletin shows as proclaimed and can be moved to verdict given

Olli moves bulletin to verdict given
  Move bulletin to verdict given
  Bulletin shows as verdict given and ce be moved to final

Olli moves bulletin to final
  Move bulletin to final
  Bulletin shows as final
