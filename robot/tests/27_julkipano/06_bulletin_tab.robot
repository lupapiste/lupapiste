*** Settings ***

Documentation   User authenticates to Julkipano.fi via Vetuma
Suite Setup     Apply minimal fixture now
Suite Teardown  Logout
Resource        ../../common_resource.robot
Resource        ./27_common.robot

*** Test Cases ***

Mikko opens new application
  As Mikko
  Create sent application
  Logout

Olli cannot publish non sent application
  As Olli

