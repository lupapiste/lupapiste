LUPAPISTE.TargetedAttachmentsModel = function(attachmentTarget, attachmentType, typeSelector) {
  "use strict";
  var self = this;
  self.target = attachmentTarget;
  self.attachmentType = attachmentType;
  self.typeSelector = typeSelector;

  self.applicationId = null;
  self.attachments = ko.observableArray([]);

  self.refresh = function(application, target, attachmentType) {
    if (target) {
      self.target = target;
    }

    if (attachmentType) {
      self.attachmentType = attachmentType;
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
      return _.isEqual(_.pick(attachment.target, ["id", "type"]),
                       _.pick(self.target, ["id", "type"]));
    }));
  };

  self.newAttachment = function() {
    attachment.initFileUpload({
      applicationId: self.applicationId,
      attachmentId: null,
      attachmentType: self.attachmentType,
      typeSelector: self.typeSelector,
      target: self.target,
      locked: lupapisteApp.models.currentUser.isAuthority()
    });
    LUPAPISTE.ModalDialog.open("#upload-dialog");
  };
};
