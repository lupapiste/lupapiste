*** Settings ***

Documentation   Mikko creates a new ya applications
Resource        ../../common_resource.robot

*** Test Cases ***


Mikko creates a kaivulupa
  Mikko logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  FOO_${secs}
  Create application  ${appname}  753  753-416-25-23  YA-kaivulupa
  Logout

Mikko creates a kayttolupa
  Mikko logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  FOO_${secs}
  Create application  ${appname}  753  753-416-25-24  YA-kayttolupa
  Logout

Mikko creates a mainostus-viitoitus type of kayttolupa
  Mikko logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  FOO_${secs}
  Create application  ${appname}  753  753-416-25-25  YA-kayttolupa-mainostus-viitoitus
  Logout

Mikko creates a sijoituslupa
  Mikko logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${appname}  FOO_${secs}
  Create application  ${appname}  753  753-416-25-26  YA-sijoituslupa
  Logout