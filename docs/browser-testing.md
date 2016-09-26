# Browser tests

We use [Robot Framework](http://robotframework.org/) and it's
[Selenium 2 Library](https://github.com/robotframework/Selenium2Library)
for running automated browser tests.

See [Robot Framework documentation](http://robotframework.org/robotframework/).

## Setup

1. Install [Firefox Extended Support Release](https://www.mozilla.org/en-US/firefox/organizations/all/) version
2. Install [Python version **2.7.x**](http://www.python.org/getit/) **NOT 3.x**
    1.  On Windows, append installation directory C:\Python27 and C:\Python27\Scripts to PATH environment variable
3. Install [pip](http://www.pip-installer.org/en/latest/installing.html)
4. Install Robot Framework: `pip install robotframework`
5. Install Selenium 2 Library: `pip install robotframework-selenium2library`
6. Install [Robotframework-DebugLibrary](https://github.com/xyb/robotframework-debuglibrary): `pip install robotframework-debuglibrary`
7. Download drivers and include them in PATH
    1.  [Chrome](http://chromedriver.storage.googleapis.com/index.html)
    2.  [IE](http://selenium-release.storage.googleapis.com/index.html)

Optional:

1.  Install [RIDE](https://github.com/robotframework/ride)

If you're using [Eclipse](http://www.eclipse.org/):

1.  Install [RobotFramework-EclipseIDE plugin](https://github.com/NitorCreations/RobotFramework-EclipseIDE/wiki/Installation)
2.  Set Eclipse to open *.robot files with Robot Framework Textfile Editor (Window -> Preferences, General -> Editors -> File Associations)

## Running the tests

Go to [robot](../robot) directory on the project root

### Tests against a server running on http://localhost:8000/

To run the main test set, execute `local.sh` or  `local.bat`.
You can run a single suite or file from main set by executing `local-standalone.sh test\00_suite` or
`local-standalone.bat test\00_suite\00_file.robot`

To run the integration test set, execute `local-integration.sh` or  `local-integration.bat`.
For running single files or suites there is the `standalone-int.bat`.

### Problems with Firefox?

Selenium supports the latest and the previous version of Firefox, that are available at the time Selenium version is released.
Selenium might not work with newer versions of Firefox. In that case, run:

    pip install --upgrade selenium

Also, ensure that you have [Firefox ESR](https://www.mozilla.org/en-US/firefox/organizations/all/) installed.
(At the time of writing the webdriver interface is in flux in the latest release.)

## Writing tests

  - [User Guide](http://robotframework.org/robotframework/latest/RobotFrameworkUserGuide.html)
  - [BuiltIn](http://robotframework.org/robotframework/latest/libraries/BuiltIn.html)
  - [Selenium2Library](http://robotframework.org/Selenium2Library/Selenium2Library.html)

**Always remember to separate keywords and arguments with two spaces!**

### Use data-test-id attributes

Add `data-test-id` attributes to elements that you want to check.
IDs and classes are OK too, but it's also OK to change them while writing CSS.

If you wish to generate a dynamic data-test-id, use the `testId` knockout-binding. For example:

    <span data-bind="text: user.firstName, testId: 'first-name-for-' + user.email()"></span>

TestId is a custom binding that is defined in [ko.init.js](../resources/private/common/ko.init.js).

### Use 'Wait Until' when checking dynamically rendered data

Robot Framework has the build in `Wait Until Keyword Succeeds` keyword.
When our default retry settings are OK, you can use the `Wait Until`  keyword
(defined in common_resource.robot).

### Interactive development

Run `pybot repl.robot` in robot directory to start a browser and a test case REPL.
See comments in repl.robot for more information.

Test XPaths in browser JavaScript console. `$x` function works at least in Chrome and Firefox. For example:

    $x("//div[@data-test-id='some-element']")

### Debugging

Put `Library  DebugLibrary` temporarily to test file Settings and use `Debug` keyword to create a breakpoint.

### Tests directory layout

    tests/
    00_suite_name/        # groups tests for a bigger part of the UI or usage scenario
      01_scenario1.robot  # own files for different flows
      02_scenario1.robot
      suite_name_resource.robot # Defines reusable keywords ("functions") for repeated steps
                                        like checks and navigating in the UI
      variables.py # Defines variables, may include logic and resource management

### Typical .robot file

    *** Settings ***

    Documentation   Some description
    Suite Setup     Apply minimal fixture now   # Clear the DB and create test users etc. Defined in common_resource.robot
    Resource       ../../common_resource.robot  # Defines common utilities

    *** Test Cases ***

    Authority admin goes to the authority admin page
      # We start by logging in
      Sipoo logs in
      Wait until page contains  Organisaation viranomaiset

    Authority admin creates two users
      # Defining log XPaths as variable cleans up tests and makes them easier to modify
      Set Suite Variable  ${userRowXpath}  //div[@class='admin-users-table']//table/tbody/tr

      # Elements are rendered asynchronously after AJAX-calls are completed.
      # When you add `Wait Until`  in front of a test, the test is retried after one second, up to 10 times
      Wait Until  Element Should Be Visible  ${userRowXpath}

      # Although the DB was cleared, we wish to avoid making assumptions.
      ${userCount} =  Get Matching Xpath Count  ${userRowXpath}

      Create user  heikki.virtanen@example.com  Heikki  Virtanen  Lukuoikeus
      Create user  hessu.kesa@example.com  Hessu  Kesa  Muutosoikeus

      ${userCountAfter} =  Evaluate  ${userCount} + 2
      User count is  ${userCountAfter}

      # By default, a failing keyword short circuits the test case evaluation.
      # Usually it's a good idea to cleanup anyway, for example by logging out.
      [Teardown]  Logout

    Hessu activates account via email
      # There is an utility for checking the last email the server sent
      Open last email

      # The email page is rendered in server side, so a `Wait until` should not be needed here
      Page Should Contain  hessu.kesa@example.com

      # First link on the page
      Click link  xpath=//a
      # Continue testing that we'll land on a correnct page...

    *** Keywords ***

      # Here we define custom keywords that are used only in this file.
      # If they are more reusable, we would place them in suite's resource file
      # or the common_resource.robot.

    Create user
      [Arguments]  ${email}  ${firstName}  ${lastName}  ${role}
      ${authority} =   Set Variable  xpath=//div[@id="add-user-to-organization-dialog"]//input[@value="authority"]
      ${reader} =   Set Variable  xpath=//div[@id="add-user-to-organization-dialog"]//input[@value="reader"]

      # Common resource includes helpers for elements with data-test-id attributes
      Click enabled by test id  authadmin-add-authority

      Wait until  Element should be visible  //label[@for='auth-admin.admins.add.email']

      Checkbox Should Be Selected  ${authority}
      Checkbox Should Not Be Selected  ${reader}
      # ...

    User count is
      [Arguments]  ${amount}
      # Here we refer to the suite variable that was set in the second test case
      Wait Until  Xpath Should Match X Times  ${userRowXpath}  ${amount}
