var statement = (function() {
  "use strict";

  var applicationId = null;
  var statementId = null;

  function StatementModel() {
    var self = this;

    self.data = ko.observable();
    self.application = ko.observable();

    self.statuses = ['yes', 'no', 'condition'];
    self.selectedStatus = ko.observable();
    self.text = ko.observable();

    self.refresh = function(application) {
      self.application(ko.mapping.fromJS(application));
      var statement = application.statements && _.find(application.statements, function(statement) { return statement.id === statementId; });
      if(statement) {
        self.data(ko.mapping.fromJS(statement));
        self.selectedStatus(statement.status);
        self.text(statement.text);
      } else {
        window.location.hash = "!/404";
      }
    };

    self.openDeleteDialog = function() {
      LUPAPISTE.ModalDialog.open("#dialog-confirm-delete-statement");
    };

    self.submit = function() {
      ajax
        .command("give-statement", {id: applicationId, statementId: statementId, status: self.selectedStatus(), text: self.text()})
        .success(function() {
          repository.load(applicationId);
          window.location.hash = "!/application/"+applicationId+"/statement";
          return false;
        })
        .call();
      return false;
    };

    self.disabled = ko.computed(function() {
      return !self.selectedStatus() || !self.text();
    });
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

  function AttachmentsModel() {
    var self = this;

    self.attachments = ko.observableArray([]);

    self.refresh = function(application) {
      self.attachments(_.filter(application.attachments,function(attachment) {
        return _.isEqual(attachment.target, {type: "statement", id: statementId});
      }));
    };

    self.newAttachment = function() {
      attachment.initFileUpload(applicationId, null, "muut.muu", false, {type: "statement", id: statementId});
    };
  }


  var statementModel = new StatementModel();
  var authorizationModel = authorization.create();
  var commentsModel = new comments.create();
  var attachmentsModel = new AttachmentsModel();

  repository.loaded(["statement"], function(application) {
    if (applicationId === application.id) {
      authorizationModel.refresh(application, {statementId: statementId});
      statementModel.refresh(application);
      attachmentsModel.refresh(application);
      commentsModel.refresh(application, {type: "statement", id: statementId});
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
      authorization: authorizationModel,
      commentsModel: commentsModel,
      attachmentsModel: attachmentsModel
    }, $("#statement")[0]);

    LUPAPISTE.ModalDialog.newYesNoDialog("dialog-confirm-delete-statement",
      loc("statement.delete.header"), loc("statement.delete.message"), loc("yes"), deleteStatementFromServer, loc("no"));

  });

})();
