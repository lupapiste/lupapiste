LUPAPISTE.ExportAttachmentsModel = function(params) {
  "use strict";
  var self = this;

  self.authorizationModel = lupapisteApp.models.applicationAuthModel;
  self.appModel = lupapisteApp.models.application;

  function unsentAttachmentFound(attachments) {
    return _.some(attachments, function(a) {
      var lastVersion = _.last(a.versions);
      return lastVersion &&
             (!a.sent || lastVersion.created > a.sent) &&
             (!a.target || (a.target.type !== "statement" && a.target.type !== "verdict"));
    });
  }

  self.exportAttachmentsToBackingSystem = ko.pureComputed(function() {
    return self.authorizationModel.ok("move-attachments-to-backing-system") &&
          self.appModel.hasAttachment() && unsentAttachmentFound(lupapisteApp.models.application._js.attachments);
  });

  self.exportAttachmentsToAsianhallinta = ko.pureComputed(function() {
    return self.authorizationModel.ok("attachments-to-asianhallinta") &&
           self.appModel.hasAttachment() && unsentAttachmentFound(lupapisteApp.models.application._js.attachments);
  });

  self.startExportingAttachmentsToBackingSystem = function() {
    hub.send("start-moving-attachments-to-backing-system");
  };

  self.startExportingAttachmentsToAsianhallinta = function() {
    hub.send("start-moving-attachments-to-case-management");
  };
};

