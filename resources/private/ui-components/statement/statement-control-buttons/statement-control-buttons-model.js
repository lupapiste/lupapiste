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

  self.submit = function() {
    LUPAPISTE.ModalDialog.showDynamicYesNo( loc( "statement.confirm.title"),
                                            loc( "statement.confirm.body"),
                                          {title: loc( "yes"),
                                           fn: _.partial(hub.send,
                                                         "statement::submit",
                                                         {applicationId: applicationId(),
                                                           statementId: statementId(),
                                                           tab: self.tab()})},
                                          {title: loc( "cancel")});
  };
};
