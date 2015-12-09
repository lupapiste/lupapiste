LUPAPISTE.StatementAttachmentsModel = function(params) {
  "use strict";
  var self = this;

  var applicationId = params.applicationId;
  var statementId = params.statementId;
  var authModel = params.authModel;

  // this function is mutated over in the attachement.deleteVersion
  var deleteAttachmentFromServerProxy;

  function deleteAttachmentFromServer(attachmentId) {
    ajax
      .command("delete-attachment", {id: applicationId(), attachmentId: attachmentId})
      .success(function() {
        repository.load(applicationId());
        return false;
      })
      .call();
    return false;
  }

  self.attachments = ko.observableArray([]);

  function refresh(application) {
    self.attachments(_.filter(application.attachments, function(attachment) {
      return _.isEqual(attachment.target, {type: "statement", id: statementId()});
    }));
  };

  self.canDeleteAttachment = function(attachment) {
    return authModel.ok("delete-attachment") &&
           authModel.ok("give-statement") &&
           (!attachment.requestedByAuthority || lupapisteApp.models.currentUser.isAuthority());
  };

  self.canAddAttachment = function() {
    return authModel.ok("upload-attachment") && authModel.ok('give-statement');
  };

  self.deleteAttachment = function(attachmentId) {
    deleteAttachmentFromServerProxy = function() { deleteAttachmentFromServer(attachmentId); };
    LUPAPISTE.ModalDialog.showDynamicYesNo(
      loc("attachment.delete.version.header"),
      loc("attachment.delete.version.message"),
      {title: loc("yes"), fn: deleteAttachmentFromServerProxy},
      {title: loc("no")}
    );
  };

  self.newAttachment = function() {
    // created file is authority-file if created by authority
    attachment.initFileUpload({
      applicationId: applicationId(),
      attachmentId: null,
      attachmentType: "muut.muu",
      typeSelector: false,
      target: {type: "statement", id: statementId()},
      locked: true
    });
    LUPAPISTE.ModalDialog.open("#upload-dialog");
  };

  repository.loaded(["statement"], function(application) {
    if (applicationId === application.id) {
      authModel.refresh(application, {statementId: statementId()}, function() {
        refresh(application);
      });
    }
  });
};