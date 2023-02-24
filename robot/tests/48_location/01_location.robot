*** Settings ***

Documentation   Structures tab functionality
Suite Setup     Apply minimal fixture now
Suite Teardown  Logout
Resource       ../../common_resource.robot
Resource       ../45_notice_forms/notice_forms_resource.robot

*** Variables ***

${property-id}              753-416-25-41
${address}                  Structure Street 16
${pientalo}                 Asuinpientalon rakentaminen (enintään kaksiasuntoinen erillispientalo)
${pientalo-schema}          uusiRakennus
${pientalo-op}              pientalo
${pientalo-tag}             ABC
${pientalo-desc}            Fortress
${aita-branch}              Rakennelman rakentaminen (vaja, katosrakennelma, aita, maalämpö, mainoslaite, jätevesijärjestelmä yms.)
${aita}                     Aidan rakentaminen
${aita-schema}              kaupunkikuvatoimenpide-ei-tunnusta
${aita-op}                  aita
${aita-desc}                Build that wall!
${kaivaminen-branch}        Maisemaan tai asuinympäristöön vaikuttava toimenpide
${kaivaminen}               Kaivaminen, louhiminen tai maan täyttäminen omalla kiinteistöllä
${kaivaminen-op}            kaivuu
${aita-x}                   410000
${aita-y}                   6680000

*** Test Cases ***

#-----------------------
# Pena (applicant)
#-----------------------

Pena logs in and creates application
  Pena logs in
  Create application with state  ${address}  ${property-id}  pientalo  submitted
  Input building identifier  ${pientalo-schema}  ${pientalo-tag}
  Sleep  1s
  Add operation description  ${pientalo-schema}  ${pientalo-desc}  True
  Sleep  1s

Pena adds aita operation
  Add operation  ${aita}  ${aita-branch}
  Sleep  1s
  Add operation description  ${aita-schema}  ${aita-desc}  True
  Sleep  1s

Pena adds kaivaminen operation
  Add operation  ${kaivaminen}  ${kaivaminen-branch}

Pena does not see Structures tab
  Structures tab not visible
  [Teardown]  Logout

#-----------------------
# Sonja (authority)
#-----------------------

Sonja logs in and opens Structures tab
  Sonja logs in
  Open application  ${address}  ${property-id}
  Open tab  structures

Pientalo information
  Row operation is  ${pientalo-op}  ${pientalo}
  Row description is  ${pientalo-op}  ${pientalo-tag}: ${pientalo-desc}
  Row building id is  ${pientalo-op}  ${EMPTY}
  No row location  ${pientalo-op}

Aita information
  Row operation is  ${aita-op}  ${aita}
  Row description is  ${aita-op}  ${aita-desc}
  Row building id is  ${aita-op}  ${EMPTY}
  No row location  ${aita-op}

Kaivuu is not listed
  No such test id  ${kaivaminen-op}-operation

Sonja edits location for aita
  Click by test id  ${aita-op}-edit
  Test id disabled  edit-location-save
  Test id input is  edit-location-x  ${EMPTY}
  Test id input is  edit-location-y  ${EMPTY}
  Fill test id  edit-location-x  ${aita-x}
  Fill test id  edit-location-y  ${aita-y}
  Test id enabled  edit-location-save

Sonja cancels edit
  Click by test id  edit-location-cancel
  No such test id  edit-location-cancel
  No row location  ${aita-op}

Sonja edits aita location again
  Click by test id  ${aita-op}-edit
  Test id required  edit-location-x
  Test id required  edit-location-y
  Test id not invalid  edit-location-x
  Test id not invalid  edit-location-y

Incorrect inpus
  Incorrect input  edit-location-x  bad
  Incorrect input  edit-location-x  9900
  Incorrect input  edit-location-x  810000

  Incorrect input  edit-location-y  bad
  Incorrect input  edit-location-y  6600000
  Incorrect input  edit-location-y  7789999
  Test id disabled  edit-location-save

Correct inputs
  Fill test id  edit-location-x  ${aita-x}
  Test id OK  edit-location-x
  Fill test id  edit-location-y  ${aita-y}
  Test id OK  edit-location-y

Save the changed location
  Click by test id  edit-location-save
  Row location is  ${aita-op}  ${aita-x}  ${aita-y}

Edit again and cancel
  Click by test id  ${aita-op}-edit
  Test id input is  edit-location-x  ${aita-x}
  Test id input is  edit-location-y  ${aita-y}
  Test id enabled  edit-location-save
  Click by test id  edit-location-cancel

Edit pientalo and cancel
  Click by test id  ${pientalo-op}-edit
  Test id input is  edit-location-x  ${EMPTY}
  Test id input is  edit-location-y  ${EMPTY}
  Test id disabled  edit-location-save
  Click by test id  edit-location-cancel

Fake buildings
  Fetch fake buildings and logout

#---------------------------
# Luukas (reader authority)
#---------------------------

Luukas logs in and opens Structures tab
  Luukas logs in
  Open application  ${address}  ${property-id}
  Open tab  structures

Pientalo information has changed
  Row operation is  ${pientalo-op}  ${pientalo}
  Row description is  ${pientalo-op}  ${pientalo-tag}: ${pientalo-desc}
  Row building id is  ${pientalo-op}  VTJ-PRT-1
  No such test id  ${pientalo-op}-edit
  Has row location  ${pientalo-op}

Aita information has changed
  Row operation is  ${aita-op}  ${aita}
  Row description is  ${aita-op}  ${aita-desc}
  Row building id is  ${aita-op}  VTJ-PRT-2
  No such test id  ${aita-op}-edit
  ${passed}=  Run keyword and return status  Row location is  ${aita-op}  ${aita-x}  ${aita-y}
  Run keyword if  ${passed}  Fail
  [Teardown]  Logout


*** Keywords ***

Structures tab not visible
  Element should not be visible  application-structures-tab

Structures tab visible
  Tab should be visible  structures

Row operation is
  [Arguments]  ${op}  ${text}
  Test id text is  ${op}-operation  ${text}

Row description is
  [Arguments]  ${op}  ${text}
  Test id text is  ${op}-description  ${text}

Row building id is
  [Arguments]  ${op}  ${text}
  Test id text is  ${op}-building-id  ${text}

Row location is
  [Arguments]  ${op}  ${x}  ${y}
  Test id text is  ${op}-x  ${x}
  Test id text is  ${op}-y  ${y}

No row location
  [Arguments]  ${op}
  Wait test id visible  ${op}-no-location

Has row location
  [Arguments]  ${op}
  No such test id  ${op}-no-location

Test id has classes
  [Arguments]  ${tid}  ${cls}
  Wait test id visible  ${tid}
  Element should be visible  jquery=input[data-test-id=${tid}].${cls}

Test id does not have classes
  [Arguments]  ${tid}  ${cls}
  Wait test id visible  ${tid}
  Element should not be visible  jquery=input[data-test-id=${tid}].${cls}

Incorrect input
  [Arguments]  ${tid}  ${value}
  Fill test id  ${tid}  ${value}
  Test id invalid  ${tid}
  Test id not required  ${tid}
