var statement = (function() {
  "use strict";

  var applicationId = null;
  var statementId = null;

  function StatementModel() {
    var self = this;

    self.data = ko.observable();
    self.application = ko.observable();

    self.statuses = ['open', 'closed'];
    self.selectedStatus = ko.observable();

    self.refresh = function(application) {
      self.application(ko.mapping.fromJS(application));
      var statement = application.statements && _.find(application.statements, function(statement) { return statement.id === statementId; });
      self.data(ko.mapping.fromJS(statement));
    };

    self.openDeleteDialog = function() {
      LUPAPISTE.ModalDialog.open("#dialog-confirm-delete-statement");
    };
  }

  function deleteStatementFromServer() {
    ajax
      .command("delete-statement", {id: applicationId, statementId: statementId})
      .success(function() {
        repository.load(applicationId);
        window.location.hash = "!/application/"+applicationId+"/statement";
        return false;
      })
      .call();
    return false;
  }

  var statementModel = new StatementModel();
  var authorizationModel = authorization.create();

  repository.loaded(function(event) {
    var application = event.applicationDetails.application;
    if (applicationId === application.id) {
      authorizationModel.refresh(application);
      statementModel.refresh(application);
    }
  });

  hub.onPageChange("statement", function(e) {
    applicationId = e.pagePath[0];
    statementId = e.pagePath[1];
    repository.load(applicationId);
  });

  $(function() {
    ko.applyBindings({
      statementModel: statementModel,
      authorization: authorizationModel
    }, $("#statement")[0]);

    LUPAPISTE.ModalDialog.newYesNoDialog("dialog-confirm-delete-statement",
      loc("statement.delete.header"), loc("statement.delete.message"), loc("yes"), deleteStatementFromServer, loc("no"));

  });

})();
