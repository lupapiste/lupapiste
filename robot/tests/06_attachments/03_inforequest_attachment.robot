*** Settings ***

Documentation  Mikko adds an attachment to inforequest
Suite teardown  Logout
Resource       ../../common_resource.robot
Variables      variables.py

*** Test Cases ***

Mikko creates an inforequests
  Mikko logs in
  ${secs} =  Get Time  epoch
  Set Suite Variable  ${inforequest}  ir-attach${secs}
  Set Suite Variable  ${propertyId}  753-416-6-3
  Create inforequest the fast way  ${inforequest}  360603.153  6734222.95  ${propertyId}  kerrostalo-rivitalo  Liitetesti

Mikko adds an attachment
  Add attachment  inforequest  ${TXT_TESTFILE_PATH}  ${TXT_TESTFILE_DESCRIPTION}  Asuinkerrostalon tai rivitalon rakentaminen
