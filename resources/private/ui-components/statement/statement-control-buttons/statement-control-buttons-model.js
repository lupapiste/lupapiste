LUPAPISTE.StatementControlButtonsModel = function(params) {
  var self = this;

  var applicationId = params.applicationId;
  var statementId = params.statementId;
  
  self.authModel = params.authModel;
  self.tab = params.selectedTab;

  self.disabled = ko.pureComputed(function() {
    return !params.submitAllowed();
  });

  self.giveStatement = function() {
    hub.send("statement::give-statement", {
      applicationId: applicationId(),
      statementId: statementId()
    });
  }
};