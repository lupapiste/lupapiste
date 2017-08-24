*** Settings ***

Documentation   Authority admin creates users
Suite Setup     Apply minimal fixture now
Resource       ../../common_resource.robot


*** Test Cases ***


Basic construction waste report is in use in Sipoo
  Sonja logs in
  Create application the fast way  Jätekatu  753-1-1-2  teollisuusrakennus
  Wait until  Element should be visible  jquery=section.accordion span[data-test-id=rakennusjatesuunnitelma-accordion-title-text]
  Element should not be visible  jquery=section.accordion span[data-test-id=laajennettuRakennusjateselvitys-accordion-title-text]
  Logout

Authority admin goes to the application page
  Sipoo logs in
  Go to page  applications

Sees waste reports are not enabled
  Checkbox wrapper not selected by test id  extended-construction-waste-report-enabled

Enable extended construction waste report for organization
  Click label by test id  extended-construction-waste-report-enabled-label
  Wait until  Positive indicator should be visible
  Checkbox wrapper selected by test id  extended-construction-waste-report-enabled

Checkbox remains checked after refresh
  Reload page and kill dev-box
  Checkbox wrapper selected by test id  extended-construction-waste-report-enabled

Authority admin logs out
  Logout

Sonja logs in and sees nothing changed on existing application
  Sonja logs in
  Open application  Jätekatu  753-1-1-2
  Wait until  Element should be visible  jquery=section.accordion span[data-test-id=rakennusjatesuunnitelma-accordion-title-text]
  Element should not be visible  jquery=section.accordion span[data-test-id=laajennettuRakennusjateselvitys-accordion-title-text]

Sonja creates another application, it contains extended construction waste report
  Create application the fast way  Jätekatu  753-1-1-3  teollisuusrakennus
  Wait until  Element should be visible  jquery=section.accordion span[data-test-id=laajennettuRakennusjateselvitys-accordion-title-text]
  Element should not be visible  jquery=section.accordion span[data-test-id=rakennusjatesuunnitelma-accordion-title-text]
  Logout
