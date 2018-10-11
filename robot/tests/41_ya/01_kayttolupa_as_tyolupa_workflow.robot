*** Settings ***

Documentation   YA kayttolupa uses tyolupa workflow, thus constructionStarted is possible
Suite Setup     Apply minimal fixture now
Resource        ../../common_resource.robot
Resource        ../common_keywords/construction_time_helpers.robot
Resource        ../39_pate/pate_resource.robot
Variables       ../../common_variables.py
Library         DateTime

*** Test Cases ***

Mikko needs to place roll-off on the street
  Mikko logs in
  ${secs} =  Get Time  epoch
  ${TODAY_DD_MM_YYYY} =  Convert Date  ${CURRENT_DATETIME}  %d.%m.%Y
  Set Suite Variable  ${appname}  Nostotyöt-${secs}
  Set Suite Variable  ${today}  ${TODAY_DD_MM_YYYY}
  Create application  ${appname}  753  753-416-25-30  YA-sijoituslupa-tyolupa
  Wait until  Permit subtype is  Käyttölupa
  # Applicant can't change permit subtype
  Element should be visible by test id  permit-subtype-text
  Open accordions  info
  Fill tyoaika fields
  Open tab  parties
  Open accordions  parties
  Fill required fields for the parties
  Submit application
  Logout

Sonja moves YA to verdictGiven state
  ${TOMORROW} =     Add time to date  ${CURRENT_DATETIME}  1 days  %d.%m.%Y
  Sonja logs in
  Open application  ${appname}  753-416-25-30
  Wait until  Application state should be  submitted
  Wait until  Permit subtype is  Käyttölupa
  Element should be visible by test id  permit-subtype-text

  Give legacy verdict  321  Sopija-Sonja  Myönnetty  ${today}
  Click back
  Wait until  Application state should be  verdictGiven
  Wait until  Permit subtype is  Käyttölupa
  Logout

Mikko can inform construction started
  Mikko logs in
  Open application  ${appname}  753-416-25-30
  Wait until  Application state should be  verdictGiven
  Open tab  tasks
  Element should be visible by test id  application-inform-construction-started-btn
  Element should not be visible by test id  application-inform-construction-ready-btn

Mikko informs construction started
  Sets construction started/ready via modal datepicker dialog  application-inform-construction-started-btn  ${today}
  Wait until  Application state should be  constructionStarted

Mikko can't inform construction finalized, as only authority can
  Element should not be visible by test id  application-inform-construction-ready-btn
  Logout

Sonja puts consturction finalized
  ${THE_DAY_AFTER_TOMORROW}=  Add time to date  ${CURRENT_DATETIME}  2 days  %d.%m.%Y
  Sonja logs in
  Open application  ${appname}  753-416-25-30
  Wait until  Application state should be  constructionStarted
  Open tab  tasks
  Element should be visible by test id  application-inform-construction-ready-btn
  Sets construction started/ready via modal datepicker dialog  application-inform-construction-ready-btn  ${THE_DAY_AFTER_TOMORROW}
  Wait until  Application state should be  closed

Frontend errors check
  There are no frontend errors
