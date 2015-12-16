LUPAPISTE.StatementControlButtonsModel = function(params) {
  var self = this;

  var applicationId = params.applicationId;
  var statementId = params.statementId;
  
  self.authModel = params.authModel;
  self.tab = params.selectedTab;

  self.disabled = ko.pureComputed(function() {
    return !params.submitAllowed()[self.tab()];
  });

  self.visible = ko.pureComputed(function() {
    return self.authModel.ok({
      "statement": "give-statement",
      "reply": "reply-statement",
      "reply-request": "request-for-statement-reply",
    }[self.tab()]);
  });

  self.submit = function() {
    hub.send("statement::submit", {
      applicationId: applicationId(),
      statementId: statementId(),
      tab: self.tab()
    });
  };

  self.refresh = function() {
    hub.send("statement::refresh");
  };
};