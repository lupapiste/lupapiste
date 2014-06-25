*** Settings ***

Documentation   Mikko creates a new ya applications
Resource        ../../common_resource.robot

*** Test Cases ***

Setting maps enabled for these tests
  Set integration proxy on

Mikko creates a kaivulupa
  Mikko logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  FOO_${secs}
  Create application  ${appname}  753  753-423-2-162  YA-kaivulupa
  [Teardown]  logout

Mikko creates a kayttolupa
  Mikko logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  FOO_${secs}
  Create application  ${appname}  753  753-423-2-163  YA-kayttolupa
  [Teardown]  logout

Mikko creates a mainostus-viitoitus type of kayttolupa
  Mikko logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  FOO_${secs}
  Create application  ${appname}  753  753-423-2-159  YA-kayttolupa-mainostus-viitoitus
  [Teardown]  logout

Mikko creates a sijoituslupa
  Mikko logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  FOO_${secs}
  Create application  ${appname}  753  753-423-2-160  YA-sijoituslupa
  [Teardown]  logout

Setting maps disabled again after the tests
  Set integration proxy off
