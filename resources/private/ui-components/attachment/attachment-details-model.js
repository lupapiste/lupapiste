LUPAPISTE.AttachmentDetailsModel = function(params) {
  "use strict";
  var self = this;
  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

  self.attachment = params.attachmentModel;
  self.signingModel = params.signingModel;

  self.id = self.attachment().id;
  self.application = lupapisteApp.models.application._js;
  self.applicationId = self.application.id;
  self.applicationTitle = self.application.title;
  self.allowedAttachmentTypes = self.application.allowedAttachmentTypes;

  var service = lupapisteApp.services.attachmentsService;
  var authModel = self.attachment().authModel; // No need to be computed since does not change for attachment

  self.groupTypes   = service.groupTypes;
  self.scales       = ko.observableArray(LUPAPISTE.config.attachmentScales);
  self.sizes        = ko.observableArray(LUPAPISTE.config.attachmentSizes);
  self.visibilities = ko.observableArray(LUPAPISTE.config.attachmentVisibilities);

  self.name = self.disposedComputed(function() {
    return "attachmentType." + self.attachment().typeString();
  });

  function querySelf() {
    service.queryOne(self.id);
  }

  function trackClick(eventName) {
    hub.send("track-click", {category:"Attachments", label: "", event: eventName});
  }

  // Navigation
  self.backToApplication = function() {
    trackClick("backToApplication");
    lupapisteApp.models.application.open("attachments");
  };

  self.nextAttachmentId = service.nextAttachmentId(self.id);
  self.previousAttachmentId = service.previousAttachmentId(self.id);

  self.openNextAttachment = _.noop;
  self.openPreviousAttachment = _.noop;

  self.showHelp = ko.observable(_.isEmpty(self.attachment().versions));

  // Approve and reject
  self.approveAttachment = _.partial(service.approveAttachment, self.id, { onSuccess: querySelf });
  self.rejectAttachment =  _.partial(service.rejectAttachment,  self.id, { onSuccess: querySelf });
  self.isApproved =   function() { return self.attachment().state === service.APPROVED; };
  self.isApprovable = function() { return authModel.ok("approve-attachment"); };
  self.isRejected =   function() { return self.attachment().state === service.REJECTED; };
  self.isRejectable = function() { return authModel.ok("reject-attachment"); };

  self.approval = {approval: self.disposedComputed(function() {
    return self.attachment().approved;
  })};

  var editable = ko.observable(true); // TODO: find out use cases from old implementation

  // Type
  self.showChangeTypeDialog = function() {
    self.disablePreview(true);
    LUPAPISTE.ModalDialog.open("#change-type-dialog");
  };
  self.changingTypeAllowed = function() { return authModel.ok("set-attachment-type") && editable(); };
  self.addEventListener("attachments", "change-attachment-type", function(data) {
    self.attachment().type(data.attachmentType);
  });

  // Delete attachment
  self.deleteAttachment = function() {
    self.disablePreview(true);
    var deleteFn = function() {
      trackClick("deleteAttachment");
      lupapisteApp.models.application.open("attachments");
      service.removeAttachment(self.id);
    };
    hub.send("show-dialog", {ltitle: "attachment.delete.header",
                             size: "medium",
                             component: "yes-no-dialog",
                             componentParams: {ltext: _.isEmpty(self.attachment().versions) ? "attachment.delete.message.no-versions" : "attachment.delete.message",
                                               yesFn: deleteFn }});
  };
  self.isDeletable = function() { return authModel.ok("delete-attachment") && editable(); };

  // Versions
  self.hasVersion = function() { return !_.isEmpty(self.attachment().versions); };
  self.showAttachmentVersionHistory = ko.observable(false);
  self.disposedSubscribe(self.hasVersion, function(val) {
    self.showHelp(!val);
    self.showAttachmentVersionHistory(val);
  });

  self.newAttachmentVersion = function() {
    self.disablePreview(true);
    attachment.initFileUpload({
      applicationId: self.applicationId,
      attachmentId: self.id,
      attachmentType: self.attachment().typeString(),
      group: util.getIn(self.attachment(), ["groupType"]),
      operationId: util.getIn(self.attachment(), ["op", "id"]),
      typeSelector: false,
      archiveEnabled: authModel.ok("permanent-archive-enabled")
    });
    // Upload dialog is opened manually here, because click event binding to
    // dynamic content rendered by Knockout is not possible
    LUPAPISTE.ModalDialog.open("#upload-dialog");
  };
  self.addHubListener("upload-done", querySelf);
  self.uploadingAllowed = function() { return authModel.ok("upload-attachment") && editable(); };

  self.deleteAttachmentVersionAllowed = function() { return authModel.ok("delete-attachment-version") && editable(); };
  self.deleteVersion = function(fileModel) {
    var fileId = fileModel.fileId;
    var originalFileId = fileModel.originalFileId;
    var deleteFn = function() {
      trackClick("deleteAttachmentVersion");
      service.removeAttachmentVersion(self.id, fileId, originalFileId, { onSuccess: querySelf });
    };
    self.disablePreview(true);
    hub.send("show-dialog", {ltitle: "attachment.delete.version.header",
                             size: "medium",
                             component: "yes-no-dialog",
                             componentParams: {ltext: "attachment.delete.version.message",
                                               yesFn: deleteFn }});
  };

  // RAM
  self.isRamAttachment =    function() { return Boolean(self.attachment().ramLink); };
  self.creatingRamAllowed = function() { return authModel.ok("create-ram-attachemnt"); };

  // Meta
  function groupToString(group) {
    return _.filter([_.get(group, "groupType"), _.get(group, "id")], _.isString).join("-");
  }
  self.selectableGroups = self.disposedComputed(function() {
    // Replace corresponding option with selected value to initialize selection properly
    var selectedValueString = groupToString(self.attachment().group());
    return _.map(self.groupTypes(), function(group) {
      return _.isEqual(groupToString(group), selectedValueString) ? self.attachment().group() : group;
    });
  });
  self.hasOperationSelector = _.get(self.application, ["primaryOperation", "attachment-op-selector"]);
  self.getGroupOptionsText = function(item) {
    if (_.get(item, "groupType") === "operation") {
      return item.description ? loc([item.name, "_group_label"]) + " - " + item.description : loc([item.name, "_group_label"]);
    } else if (_.get(item, "groupType")) {
      return loc([item.groupType, "_group_label"]);
    }
  };
  self.getScaleOptionsText = function(item) { return item === "muu" ? loc("select-other") : item; };
  self.metaUpdateAllowed = function() { return authModel.ok("set-attachment-meta") && editable(); };

  // For Printing
  self.setForPrintingAllowed = function() { return authModel.ok("set-attachments-as-verdict-attachment"); };

  // Visibility
  self.getVibilityOptionsText = function(val) { return loc("attachment.visibility." + val); };
  self.setVisibilityAllowed = function() { return authModel.ok("set-attachment-visibility") && editable(); };

  // Permanent archive
  self.permanentArchiveEnabled = function() { return authModel.ok("permanent-archive-enabled"); };

  // Signatures
  self.hasSignature = function() { return !_.isEmpty(self.attachment().signatures); };
  self.sign = function() {
    self.disablePreview(true);
    self.signingModel.init({id: self.applicationId, attachments:[self.attachment]});
  };
  self.signingAllowed = function() { return authModel.ok("sign-attachments"); };

  // TOS
  self.showTosMetadata = ko.observable(false);
  self.tosOperationsEnabled = function() { return authModel.ok("tos-operations-enabled"); };

  // Preview
  var imgRegex = /^image\/(jpeg|png|gif|bmp)$/;
  self.previewIs = function(fileType) {
    var contentType = util.getIn(self.attachment, ["latestVersion", "contentType"]);
    switch (fileType) {
      case "image": return contentType && imgRegex.test(contentType);
      case "pdf": return contentType === "application/pdf";
      case "plainText": return contentType === "text/plain";
      default: return false;
    }
  };
  self.disablePreview = ko.observable(false);
  self.showPreview = ko.observable(false);
  self.hasPreview = function() {
    return !self.disablePreview() && _.some(["image", "pdf", "plainText"], self.previewIs);
  };

  self.rotationAllowed = function() { return authModel.ok("rotate-pdf"); };

  self.previewUrl = self.disposedComputed(function() {
    var fileId = util.getIn(self.attachment(), ["latestVersion", "fileId"]);
    return "/api/raw/view-attachment?attachment-id=" + fileId;
  });

  self.rotate = function(rotation) {
    $("#file-preview-iframe").attr("src","/lp-static/img/ajax-loader.gif");
    service.rotatePdf(self.id, rotation, { onComplete: querySelf });
  };

  self.disposedSubscribe(self.previewUrl, function(url) {
    if (self.showPreview()) {
      $("#file-preview-iframe").attr("src", url);
    }
  });

  // Common hub listeners
  self.addHubListener("dialog-close", _.partial(self.disablePreview, false));

  self.addHubListener("side-panel-open", _.partial(self.disablePreview, true));
  self.addHubListener("side-panel-close", _.partial(self.disablePreview, false));

};
