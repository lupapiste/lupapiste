LUPAPISTE.StatementAttachmentsModel = function(params) {
  "use strict";
  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel(params));

  var application = params.application;
  var applicationId = params.applicationId;
  var statementId = params.statementId;
  var authModel = params.authModel;

  self.tab = params.selectedTab;

  self.attachments = ko.observableArray([]);

  self.disposedComputed(function() {
    self.attachments(
      _(util.getIn(application, ["attachments"]))
        .filter(function(attachment) {
          return _.isEqual(attachment.target, {type: "statement", id: statementId()});
        })
        .map(function(attachment) {
          var comments = _.filter(util.getIn(application, ["comments"]), function(comment) {
            return comment.target.id === attachment.id;
          });
          return _.extend({comment: _.head(comments).text}, attachment);
        })
        .value()
    );
  });

  self.canDeleteAttachment = function(attachment) {
    return authModel.ok("delete-attachment") &&
           (!attachment.requestedByAuthority || lupapisteApp.models.currentUser.isAuthority());
  };

  self.canAddAttachment = function() {
    return authModel.ok("upload-attachment") && authModel.ok("give-statement");
  };

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

  self.deleteAttachment = function(attachmentId) {
    var deleteAttachmentFromServerProxy = function() { deleteAttachmentFromServer(attachmentId); };
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
};
