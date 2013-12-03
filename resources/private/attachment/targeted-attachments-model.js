LUPAPISTE.TargetedAttachmentsModel = function(attachmentTarget, attachmentType) {
  var self = this;
  self.target = attachmentTarget;
  self.attachmentType = attachmentType;

  self.applicationId = null;
  self.attachments = ko.observableArray([]);

  self.refresh = function(application, target) {
    if (target) {
      self.target = target;
    }

    self.applicationId = application.id;
    if (typeof application.id === "function") {
      self.applicationId = application.id();
    }

    var attachments = application.attachments;
    if (typeof attachments === "function") {
      attachments = ko.toJS(attachments);
    }

    self.attachments(_.filter(attachments, function(attachment) {
      return _.isEqual(attachment.target, self.target);
    }));
  };

  self.newAttachment = function() {
    attachment.initFileUpload(self.applicationId, null, self.attachmentType, false, self.target, true);
    LUPAPISTE.ModalDialog.open("#upload-dialog");
  };
};
