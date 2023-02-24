// Buttons for statemen, reply and reply request dialogs.
// Parameters are defined in StatementService.
LUPAPISTE.StatementControlButtonsModel = function(params) {
  "use strict";
  var self = this;

  var applicationId = params.applicationId;
  var statementId = params.statementId;
  var commands = params.commands;
  var submitAllowed = params.submitAllowed;

  self.authModel = params.authModel;
  self.tab = params.selectedTab;
  self.waiting = params.waiting || ko.observable(false);

  self.disabled = ko.pureComputed(function() {
    return !submitAllowed[self.tab()]();
  });

  self.submitVisible = ko.pureComputed(function() {
    return self.authModel.ok(util.getIn(commands, [self.tab(), "submit"]));
  });

  self.refreshVisible = self.submitVisible;

  self.buttonText = ko.pureComputed(function() {
    return loc("statement.submit." + self.tab());
  });

  function send() {
    hub.send( "statement::submit",
              {applicationId: applicationId(),
               statementId: statementId(),
               tab: self.tab()});
  }

  self.submit = function() {
    if( commands[self.tab()].confirm ) {
      hub.send("show-dialog",
               {component: "yes-no-dialog",
                ltitle: self.tab() + ".confirm.title",
                size: "medium",
                componentParams: {ltext: self.tab() + ".confirm.body",
                                  yesFn: send}});
    } else {
      send();
    }
  };
};
