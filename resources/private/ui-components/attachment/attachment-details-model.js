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

  // Navigation
  self.backToApplication = function() {
    hub.send("track-click", {category:"Attachments", label: "", event:"backToApplication"});
    lupapisteApp.models.application.open("attachments");
  };

  self.nextAttachmentId = service.nextAttachmentId(self.id);
  self.previousAttachmentId = service.previousAttachmentId(self.id);

  self.openNextAttachment = _.noop;
  self.openPreviousAttachment = _.noop;

  self.showHelp = ko.observable(_.isEmpty(self.attachment().versions));

  // Approve and reject
  self.approveAttachment = _.ary(_.partial(service.approveAttachment, self.id), 0);
  self.rejectAttachment = _.ary(_.partial(service.rejectAttachment, self.id), 0);
  self.isApproved = self.disposedComputed(function() {
    return self.attachment().state === service.APPROVED;
  });
  self.isApprovable = function() { return authModel.ok("approve-attachment"); };
  self.isRejected = self.disposedComputed( function() {
    return self.attachment().state === service.REJECTED;
  });
  self.isRejectable = function() { return authModel.ok("reject-attachment"); };

  //self.approval = ko.observable(self.attachment().approved); // TODO: ???

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



  self.deleteAttachment = function() {
    // TODO: Dialog
    service.deleteAttachment(self.id);
    // TODO: Return to listing
  };
  self.isDeletable = function() { return authModel.ok("delete-attachment") && editable(); };

  // Version
  self.showAttachmentVersionHistory = ko.observable(false);
  self.newAttachmentVersion = _.noop;
  self.uploadingAllowed = function() { return authModel.ok("upload-attachment") && editable(); };
  self.areVersionsDeletable = function() { return authModel.ok("delete-attachment-version") && editable(); };

  // RAM
  self.isRamAttachment = self.disposedComputed( function() {
    return Boolean(self.attachment().ramLink);
  });
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

  self.rotationAllowed = function() { return authModel.ok("rotate-pdf") && self.previewIs("pdf"); }; // TODO: check file type in pre-check

  self.rotate = function(rotation) {
    var iframe$ = $("#file-preview-iframe");
    iframe$.attr("src","/lp-static/img/ajax-loader.gif");
    service.rotatePdf(self.id, rotation); // TODO: success handler
      //.success(function() {
      //  applicationModel.reload();
      //  hub.subscribe("attachment-loaded", function() {
      //    model.previewVisible(true);
      //      iframe$.attr("src", model.previewUrl());
      //  }, true);
  };

  self.addHubListener("dialog-close", _.partial(self.disablePreview, false));

  self.addHubListener("side-panel-open", _.partial(self.disablePreview, true));
  self.addHubListener("side-panel-close", _.partial(self.disablePreview, false));



  // Type
  //var applicationModel = lupapisteApp.models.application;

  //var signingModel = new LUPAPISTE.SigningModel("#dialog-sign-attachment", false);



  // //// Subscriptions ////
  // Set attachment type
  // Set groupType and op
  // Set for printing
  // Set visibility
  // Set meta - contents, scale, size








  // function subscribe() {
  //   model.subscriptions.push(model.attachmentType.subscribe(function(attachmentType) {
  //     var type = model.type();
  //     var prevAttachmentType = type["type-group"] + "." + type["type-id"];
  //     var loader$ = $("#attachment-type-select-loader");
  //     if (prevAttachmentType !== attachmentType) {
  //       loader$.show();
  //       ajax
  //         .command("set-attachment-type",
  //           {id:              applicationId,
  //            attachmentId:    attachmentId,
  //            attachmentType:  attachmentType})
  //         .success(function() {
  //           loader$.hide();
  //           repository.load(applicationId, undefined, undefined, true);
  //         })
  //         .error(function(e) {
  //           loader$.hide();
  //           repository.load(applicationId, undefined, undefined, true);
  //           error(e.text);
  //         })
  //         .call();
  //     }
  //   }));

  //   function applySubscription(label) {
  //     model.subscriptions.push(model[label].subscribe(_.debounce(function(value) {
  //       if (value || value === "") {
  //         var data = {};
  //         data[label] = value;
  //         saveLabelInformation(label, {meta: data});
  //       }
  //     }, 500)));
  //   }

  //   model.subscriptions.push(model.selectedGroup.subscribe(function(group) {
  //     // Update attachment group type + operation when new group is selected
  //     if (util.getIn(group, ["id"]) !== util.getIn(model, ["operation", "id"]) ||
  //         util.getIn(group, ["groupType"]) !== util.getIn(model, ["groupType"])) {

  //       group = _.pick(group, ["id", "name", "groupType"]);
  //       saveLabelInformation("group", {meta: {group: !_.isEmpty(group) ? group : null}});

  //       model.operation(util.getIn(group, ["id"]) ? _.omit(group, "groupType") : null);
  //       model.groupType(util.getIn(group, ["groupType"]));
  //     }
  //   }));

  //   model.subscriptions.push(model.isVerdictAttachment.subscribe(function(isVerdictAttachment) {
  //     ajax.command("set-attachments-as-verdict-attachment", {
  //       id: applicationId,
  //       selectedAttachmentIds: isVerdictAttachment ? [attachmentId] : [],
  //       unSelectedAttachmentIds: isVerdictAttachment ? [] : [attachmentId]
  //     })
  //     .success(function() {
  //       hub.send("indicator-icon", {style: "positive"});
  //       repository.load(applicationId, undefined, undefined, true);
  //     })
  //     .error(function(e) {
  //       error(e.text);
  //       notify.error(loc("error.dialog.title"), loc("attachment.set-attachments-as-verdict-attachment.error"));
  //       repository.load(applicationId, undefined, undefined, true);
  //     })
  //     .call();
  //   }));

  //   model.subscriptions.push(model.visibility.subscribe(function(data) {
  //     if (authorizationModel.ok("set-attachment-visibility")) {
  //       ajax.command("set-attachment-visibility", {
  //         id: applicationId,
  //         attachmentId: model.id(),
  //         value: data
  //       })
  //       .success(function() {
  //         model.dirty = true;
  //         hub.send("indicator-icon", {style: "positive"});
  //       })
  //       .call();
  //     }
  //   }));



  //   applySubscription("contents");
  //   applySubscription("scale");
  //   applySubscription("size");
  // }


};
