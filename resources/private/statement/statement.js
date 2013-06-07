(function() {
  "use strict";

  var applicationId = null;
  var statementId = null;

  // this function is mutated over in the attachement.deleteVersion
  var deleteAttachmentFromServerProxy;

  function deleteAttachmentFromServer(attachmentId) {
    ajax
      .command("delete-attachment", {id: applicationId, attachmentId: attachmentId})
      .success(function() {
        repository.load(applicationId);
        return false;
      })
      .call();
    return false;
  }

  function StatementModel() {
    var self = this;

    self.data = ko.observable();
    self.application = ko.observable();

    self.statuses = ['yes', 'no', 'condition'];
    self.selectedStatus = ko.observable();
    self.text = ko.observable();
    self.submitting = ko.observable(false);
    self.dirty = ko.observable(false);

    self.text.subscribe(function(value) {
      if(self.data() && self.data().text() !== value) { self.dirty(true); }
    });

    self.selectedStatus.subscribe(function(value) {
      if(self.data() && self.data().status() !== value) { self.dirty(true); }
    });

    self.clear = function() {
      self.data(null);
      self.application(null);
      self.selectedStatus(null);
      self.text(null);
      return self;
    };

    self.refresh = function(application) {
      self.application(ko.mapping.fromJS(application));
      var statement = application.statements && _.find(application.statements, function(statement) { return statement.id === statementId; });
      if(statement) {
        self.data(ko.mapping.fromJS(statement));

        // LUPA-482 part II
        if (statement.status && !self.dirty()) {
          self.selectedStatus(statement.status);
          self.dirty(false);
        }
        if (statement.text && !self.dirty()) {
          self.text(statement.text);
          self.dirty(false);
        }

      } else {
        window.location.hash = "!/404";
      }
    };

    self.openDeleteDialog = function() {
      LUPAPISTE.ModalDialog.open("#dialog-confirm-delete-statement");
    };

    self.submit = function() {
      self.submitting(true);
      ajax
        .command("give-statement", {id: applicationId, statementId: statementId, status: self.selectedStatus(), text: self.text()})
        .success(function() {
          window.location.hash = "!/application/"+applicationId+"/statement";
          repository.load(applicationId);
          return false;
        })
        .complete(function() { self.submitting(false); })
        .call();
      return false;
    };

    self.disabled = ko.computed(function() {
      return !self.selectedStatus() || !self.text() || self.submitting() || !self.dirty();
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

    self.canDeleteAttachment = function(attachment) {
      return authorizationModel.ok("delete-attachment") && (!attachment.authority || currentUser.isAuthority());
    };

    self.canAddAttachment = function() {
      return authorizationModel.ok("upload-attachment") && currentUser.isAuthority();
    };

    self.deleteAttachment = function(attachmentId) {
      deleteAttachmentFromServerProxy = function() { deleteAttachmentFromServer(attachmentId); };
      LUPAPISTE.ModalDialog.open("#dialog-confirm-delete-statement-attachment");
    };

    self.newAttachment = function() {
      // created file is authority-file if created by authority
      attachment.initFileUpload(applicationId, null, "muut.muu", false, {type: "statement", id: statementId}, true, currentUser.isAuthority());
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
    statementModel.clear();
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

    LUPAPISTE.ModalDialog.newYesNoDialog("dialog-confirm-delete-statement-attachment",
      loc("attachment.delete.version.header"), loc("attachment.delete.version.message"), loc("yes"), function() { deleteAttachmentFromServerProxy(); }, loc("no"));
  });

})();
