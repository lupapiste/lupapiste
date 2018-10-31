*** Settings ***

Documentation   Contract templates for YA
Suite Setup     Apply pate-phrases fixture now
Suite Teardown  Logout
Resource       ../../common_resource.robot
Resource       pate_resource.robot

*** Test Cases ***

Kuopio YA admin logs in
  Kuopio-ya logs in
  Go to page  pate-verdict-templates

Only Pate-supported categories are selectable
  Test id select values are  category-select  contract  ya

No templates yet, let's create new one
  Wait test id visible  add-template
  No templates
  Select from test id  category-select  contract
  Click visible test id  add-template

Initial template status
  Template not published
  Test id text is  template-name-text  Sopimuspohja
  Wait test id visible  required-template


Template cannot be published without filling required information
  Test id text is  template-state  Sopimuspohjaa ei ole vielä julkaistu.
  Test id disabled  publish-template

Fills required information
  Select from test id  language  fi

Publish template
  Scroll and click test id  publish-template
  Test id should contain  template-state  Julkaistu viimeksi
  Test id disabled  publish-template
  Click back

The template is listed in published templates with the correct published date
  ${today}=  Execute Javascript  return util.finnishDate( moment() );
  Test id text is  published-template-0  ${today}



*** Keywords ***

No templates
  Wait until  Element should not be visible  jquery=table.pate-templates-table

Template not published
  Test id disabled  publish-template
  Test id text is  template-state  Sopimuspohjaa ei ole vielä julkaistu.
