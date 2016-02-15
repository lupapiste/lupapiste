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

  self.disabled = ko.pureComputed(function() {
    return !submitAllowed[self.tab()]();
  });

  self.submitVisible = ko.pureComputed(function() {
    return self.authModel.ok(util.getIn(commands, [self.tab(), "submit"]));
  });

  self.refreshVisible = self.submitVisible;

  function send() {
    hub.send( "statement::submit",
              {applicationId: applicationId(),
               statementId: statementId(),
               tab: self.tab()});
  }

  self.submit = function() {
    if( commands[self.tab()].confirm ) {
      LUPAPISTE.ModalDialog.showDynamicYesNo( loc( self.tab() + ".confirm.title"),
                                              loc( self.tab() + ".confirm.body"),
                                              {title: loc( "yes"),
                                                fn: send },
                                               {title: loc( "cancel")});
    }
  };

  self.refresh = function() {
    hub.send("statement::refresh");
  };
};
