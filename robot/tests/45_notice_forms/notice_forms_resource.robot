*** Keywords ***

Add operation
  [Arguments]  ${operation}  ${branch}=Uuden rakennuksen rakentaminen
  Scroll and click test id  add-operation
  Wait Until  Element Should Be Visible  add-operation
  Wait Until  Title Should Be  ${address} - Lupapiste
  Wait and click  jquery:button.operation-tree-button span:contains(Rakentaminen, purkaminen tai maisemaan vaikuttava toimenpide)
  Wait and click  jquery:button.operation-tree-button span:contains(${branch})
  Wait and click  jquery:button.operation-tree-button span:contains(${operation})
  Click enabled by test id  add-operation-to-application

Add operation description
  [Arguments]  ${schema}  ${description}  ${toggle}=False
  Run keyword if  ${toggle}  Scroll and click test id  toggle-identifiers-${schema}
  Fill test id  op-description-editor-${schema}  ${description}
  Wait for jQuery

Fetch fake buildings and logout
  ${app-id}=  Get Element Attribute  jquery=span[data-test-id=application-id]  data-test-value
  Go to  ${SERVER}/dev/fake-buildings/${app-id}
  Wait until  Page should contain  Buildings faked
  Go to  ${lOGOUT URL}
  Logout
