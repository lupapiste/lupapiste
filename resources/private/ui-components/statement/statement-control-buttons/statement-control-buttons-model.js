LUPAPISTE.StatementControlButtonsModel = function(params) {
  "use strict";
  var self = this;

  var applicationId = params.applicationId;
  var statementId = params.statementId;

  self.authModel = params.authModel;
  self.tab = params.selectedTab;

  self.disabled = ko.pureComputed(function() {
    return !params.submitAllowed()[self.tab()];
  });

  self.submitVisible = ko.pureComputed(function() {
    return self.authModel.ok({
      "statement": "give-statement",
      "reply": "reply-statement",
      "reply-request": "request-for-statement-reply"
    }[self.tab()]);
  });

  self.refreshVisible = self.submitVisible;

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
