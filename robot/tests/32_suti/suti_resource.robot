*** Settings ***

Documentation  Suti resources
Resource       ../../common_resource.robot

*** Keywords ***

Suti server
  [Arguments]  ${url}  ${username}  ${password}
  Go to page  backends
  Scroll to test id  suti-password  
  Fill test id  suti-url  ${url}
  Fill test id  suti-username  ${username}
  Fill test id  suti-password  ${password}

  Positive indicator should not be visible
  Scroll and click test id  suti-send
  Positive indicator should be visible

Toggle Suti
  Go to page  backends
  Scroll to  label[for=suti-enabled]
  Click label  suti-enabled
  Positive indicator should be visible

Toggle Suti operation
  [Arguments]  ${operation}
  Go to page  operations
  Scroll to  label[for=suti-${operation}]
  Click label  suti-${operation}  

Set Suti id
  [Arguments]  ${id}
  Fill test id  suti-display-id  ${id}
  Focus  jquery=label[for=suti-display-added]
  
Clear Suti id
  Set Suti id  ${EMPTY}
  Test id disabled  suti-display-link

Suti id and note
  [Arguments]  ${id}  ${note}  ${error}=False
  Focus test id  suti-display-id
  Set Suti id  ${id}
  Wait Until  Test id text is  suti-display-note  ${note}
  Run Keyword If  ${error}  With error style  suti-display-note
  
With error style
  [Arguments]  ${id}
  Element should be visible  jquery=.suti-display--error[data-test-id=${id}]

No error style
  [Arguments]  ${id}
  Element should not be visible  jquery=.suti-display--error[data-test-id=${id}]

Check row
  [Arguments]  ${index}  ${name}  ${state}  ${expirydate}  ${downloaded}
  Test id text is  suti-display-name-${index}  ${name}
  Test id text is  suti-display-state-${index}  ${state}
  Run Keyword If  '${state}' == 'Vanhentunut'  With error style  suti-display-state-${index}
  Test id text is  suti-display-expirydate-${index}  ${expirydate}
  Run Keyword If  '${state}' == 'Vanhentunut'  With error style  suti-display-expirydate-${index}
  Test id text is  suti-display-downloaded-${index}  ${downloaded}
  
Check default products
  Check row  0  One  Voimassa  ${EMPTY}  ${EMPTY}
  Check row  1  Two  Vanhentunut  5.7.2016  1.7.2016
  Check row  2  Three  Voimassa  ${EMPTY}  ${EMPTY}
