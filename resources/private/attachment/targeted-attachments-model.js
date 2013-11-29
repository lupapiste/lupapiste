LUPAPISTE.TargetedAttachmentsModel = function(attachmentTarget) {
  var self = this;
  self.target = attachmentTarget;

  self.attachments = ko.observableArray([]);

  self.refresh = function(application, target) {
    if (target) {
      self.target = target;
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
    attachment.initFileUpload(applicationId, null, "muut.muu", false, self.target, true);
  };
};
