*** Settings ***

Documentation  Handler roles and handlers
Resource       ../../common_resource.robot

*** Keywords ***

Save indicated
  Positive indicator should be visible

Remove indicated
  Positive indicator should be visible
  Indicator should contain text  Poistettu

Recovery indicated
  Positive indicator should be visible
  Indicator should contain text  Palautettu

Role input is
  [Arguments]  ${tid}  ${txt}  ${enabled}
  Test id input is  ${tid}  ${txt}
  Run keyword if  ${enabled}  Test id enabled  ${tid}
  Run keyword unless  ${enabled}  Test id disabled  ${tid}

Handler role is
  [Arguments]  ${index}  ${fi}  ${sv}  ${en}  ${removable}=True  ${enabled}=True
  Wait until  Role input is  edit-role-${index}-fi  ${fi}  ${enabled}
  Role input is  edit-role-${index}-sv  ${sv}  ${enabled}
  Role input is  edit-role-${index}-en  ${en}  ${enabled}
  Run keyword if  ${removable}  Wait test id visible  remove-role-${index}
  Run keyword unless  ${removable}  No such test id  remove-role-${index}

Disabled handler role is
  [Arguments]  ${index}  ${fi}  ${sv}  ${en}
  Handler role is  ${index}  ${fi}  ${sv}  ${en}  False  False
  Wait test id visible  recover-role-${index}
  Test id disabled  edit-role-${index}-fi
  Test id disabled  edit-role-${index}-sv
  Test id disabled  edit-role-${index}-en

Edit handler role
  [Arguments]  ${index}  ${lang}  ${name}  ${saved}=False
  Fill test id  edit-role-${index}-${lang}  ${name}
  Run keyword if  ${saved}  Save indicated

No required
  [Arguments]  ${index}  ${lang}
  Wait until  Element should not be visible  jquery=input.required[data-test-id=edit-role-${index}-${lang}]

Yes required
  [Arguments]  ${index}  ${lang}
  Scroll to test id  add-handler-role
  Wait until  Element should be visible  jquery=input.required[data-test-id=edit-role-${index}-${lang}]

Warning visible
  Scroll to test id  add-handler-role
  Wait test id visible  handler-roles-warning

Warning not visible
  Scroll to test id  add-handler-role
  No such test id  handler-roles-warning

Handler is
  [Arguments]  ${index}  ${person}  ${role}
  Wait until  Autocomplete selection by test id is  edit-person-${index}  ${person}
  Wait until  Autocomplete selection by test id is  edit-role-${index}  ${role}

Edit handler
  [Arguments]  ${index}  ${person}  ${role}
  Select from autocomplete by test id  edit-person-${index}  ${person}
  Select from autocomplete by test id  edit-role-${index}  ${role}
  Save indicated

Available roles
  [Arguments]  ${index}  @{roles}
  Test id autocomplete options check  edit-role-${index}  true  @{roles}

Unavailable roles
  [Arguments]  ${index}  @{roles}
  Test id autocomplete options check  edit-role-${index}  false  @{roles}

Handler disabled
  [Arguments]  ${index}
  Test id autocomplete disabled  edit-person-${index}
  Test id autocomplete disabled  edit-role-${index}

List handler is
  [Arguments]  ${index}  ${person}  ${role}
  Test id text is  handler-${index}  ${person} (${role})

# Shortcuts for old tests

General handler to
  [Arguments]  ${container}  ${person}
  Wait until  Element should be visible  xpath=//${container}//a[@data-test-id='edit-handlers']
  Click element  xpath=//${container}//a[@data-test-id='edit-handlers']
  Wait until  Element should be visible  xpath=//${container}//button[@data-test-id='add-handler']
  Click element  xpath=//${container}//button[@data-test-id='add-handler']
  Edit handler  0  ${person}  Käsittelijä
  Scroll and click  [data-test-id=edit-handlers-back]:visible

General application handler to
  [Arguments]  ${person}
  General handler to  section[@id='application']  ${person}

General inforequest handler to
  [Arguments]  ${person}
  General handler to  section[@id='inforequest']  ${person}

General handler is
  [Arguments]  ${person}
  Wait until  List handler is  0  ${person}  Käsittelijä

Remove first handler
  Click by test id  edit-handlers
  Click by test id  remove-handler-0
  Remove indicated
  Click by test id  edit-handlers-back
  No such test id  handler-0
