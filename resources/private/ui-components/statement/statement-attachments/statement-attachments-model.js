LUPAPISTE.StatementAttachmentsModel = function(params) {
  "use strict";
  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel(params));

  var applicationId = params.applicationId;
  var statementId = params.statementId;
  var authModel = params.authModel;

  self.tab = params.selectedTab;

  self.attachments = ko.observableArray([]);

  self.disposedComputed(function() {
    var attachments = lupapisteApp.services.attachmentsService.attachments();
    self.attachments(
      _(attachments)
        .filter(function(attachment) {
          var targetType = util.getIn(attachment, ["target", "type"]);
          var targetId = util.getIn(attachment, ["target", "id"]);
          return targetType === "statement" && targetId === statementId();
        })
        .map(function(a) {
          var attachment = ko.mapping.toJS(a);
          var comments = _.filter(lupapisteApp.services.commentService.comments(), function(comment) {
            return comment.target.id === attachment.id;
          });
          // Comments are not loaded synchronously after with attachments after upload,
          // so the comment might be missing
          var text = _.isEmpty(comments) ? "" : _.head(comments).text;
          return _.extend({comment: text}, attachment);
        })
        .value()
    );
  });

  self.canDeleteAttachment = function(attachment) {
    return attachment.authModel.ok("delete-attachment");
  };

  self.canAddAttachment = function() {
    return authModel.ok("statement-attachment-allowed");
  };

  self.deleteAttachment = function(attachmentId) {
    LUPAPISTE.ModalDialog.showDynamicYesNo(
      loc("attachment.delete.version.header"),
      loc("attachment.delete.version.message"),
      {title: loc("yes"), fn: _.partial(lupapisteApp.services.attachmentsService.removeAttachment, attachmentId)},
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
