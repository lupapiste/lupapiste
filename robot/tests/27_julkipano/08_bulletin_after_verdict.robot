*** Settings ***

Documentation   User authenticates to Julkipano.fi via Vetuma
Suite Setup     Apply minimal fixture now
Suite Teardown  Logout
Resource        ../../common_resource.robot
Resource        ./julkipano_common.robot
Resource        ../39_pate/pate_resource.robot

*** Test Cases ***

Mikko opens new application
  As Mikko
  Create sent application  Vaalantie 540  564-404-26-102
  Logout

Olli gives verdict without proclaiming bulletin
  As Olli
  Open application  Vaalantie 540  564-404-26-102
  Submit empty verdict

No bulletins yet, can be sent as verdict given
  Bulletin not proclaimed but can be moved to verdict given

Olli moves bulletin to verdict given
  Move bulletin to verdict given
  Bulletin shows as verdict given and ce be moved to final

Olli moves bulletin to final
  Move bulletin to final
  Bulletin shows as final
