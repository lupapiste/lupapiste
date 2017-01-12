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

  self.upload = new LUPAPISTE.UploadModel(self, {allowMultiple:false, dropZone: "section#attachment"});
  self.upload.init();

  var service = lupapisteApp.services.attachmentsService;
  var authModel = self.attachment().authModel; // No need to be computed since does not change for attachment

  var filterSet = service.getFilters( "attachments-listing" );

  self.visibilities = ko.observableArray(LUPAPISTE.config.attachmentVisibilities);

  self.name = self.disposedComputed(function() {
    return "attachmentType." + self.attachment().typeString();
  });

  function querySelf(hubParams) {
    service.queryOne(self.id, hubParams);
  }

  function trackClick(eventName) {
    hub.send("track-click", {category:"Attachments", label: "", event: eventName});
  }

  function trackClickWrap(eventName, fn /* & args */) {
    var args = _.drop(arguments, 2);
    return function() {
      trackClick(eventName);
      _.spread(fn)(args);
    };
  }

  self.contentsList = self.disposedPureComputed( function() {
    var fullType = _.find( service.attachmentTypes(),
                           self.attachment().type());

    return fullType ? service.contentsData( fullType ).list : [];
  });

  function addUpdateListener(commandName, params, fn) {
    self.addEventListener(service.serviceName, _.merge({eventType: "update", attachmentId: self.id, commandName: commandName}, params), fn);
  }

  // Show indicator always when attachment update fails
  self.addEventListener(service.serviceName, {eventType: "update", attachmentId: self.id, ok: false}, util.showSavedIndicator);

  // Navigation
  self.backToApplication = trackClickWrap("backToApplication", lupapisteApp.models.application.open, "attachments");

  self.nextAttachmentId = service.nextFilteredAttachmentId(self.id, filterSet);
  self.previousAttachmentId = service.previousFilteredAttachmentId(self.id, filterSet);

  self.openNextAttachment = trackClickWrap("nextAttachment", pageutil.openPage, "attachment", self.applicationId + "/" + self.nextAttachmentId );
  self.openPreviousAttachment = trackClickWrap("previousAttachment", pageutil.openPage, "attachment", self.applicationId + "/" + self.previousAttachmentId );

  self.showHelp = ko.observable(_.isEmpty(self.attachment().versions));

  // Approve and reject
  self.approveAttachment = trackClickWrap("approveAttachment", service.approveAttachment, self.id);
  self.rejectAttachment  = trackClickWrap("rejectAttachment",  service.rejectAttachment,  self.id);
  addUpdateListener("approve-attachment", {ok: true}, _.ary(querySelf, 0));
  addUpdateListener("reject-attachment",  {ok: true}, _.ary(querySelf, 0));
  self.isApproved   = _.wrap( self.attachment, service.isApproved );
  self.isApprovable = function() { return authModel.ok("approve-attachment"); };
  self.isRejected   = _.wrap( self.attachment, service.isRejected );
  self.isRejectable = function() { return authModel.ok("reject-attachment"); };

  self.approval = self.disposedPureComputed(function () {
    return  service.attachmentApproval( self.attachment ) ;
  });

  var editable = self.disposedComputed(function() {
    return !service.processing() && !self.attachment().processing();
  });

  // Type
  self.showChangeTypeDialog = function() {
    self.disablePreview(true);
    LUPAPISTE.ModalDialog.open("#change-type-dialog");
  };
  self.changingTypeAllowed = function() { return authModel.ok("set-attachment-type"); };
  self.addEventListener("attachments", "change-attachment-type", function(data) {
    self.attachment().type(data.attachmentType);
  });
  addUpdateListener("set-attachment-type", {ok: true}, util.showSavedIndicator);

  // Delete attachment
  self.deleteAttachment = function() {
    self.disablePreview(true);
    hub.send("show-dialog", {ltitle: "attachment.delete.header",
                             size: "medium",
                             component: "yes-no-dialog",
                             componentParams: {ltext: _.isEmpty(self.attachment().versions) ? "attachment.delete.message.no-versions" : "attachment.delete.message",
                                               yesFn: trackClickWrap("deleteAttachment", service.removeAttachment, self.id) }});
  };
  self.addEventListener(service.serviceName, {eventType: "remove", attachmentId: self.id}, _.ary(_.partial(lupapisteApp.models.application.open, "attachments")));
  self.isDeletable = function() { return authModel.ok("delete-attachment"); };

  // Versions
  self.hasVersion = self.disposedComputed(function() { return !_.isEmpty(self.attachment().versions); });
  self.showAttachmentVersionHistory = ko.observable(false);
  self.disposedSubscribe(self.hasVersion, function(val) {
    self.showHelp(!val);
    self.showAttachmentVersionHistory(val);
  });

  self.versions = self.disposedPureComputed( function() {
    return _( self.attachment().versions)
      .map( function( v ) {
        var approval = service.attachmentApproval( self.attachment, v.fileId ) || {};
        var authority = lupapisteApp.models.currentUser.isAuthority();
        var rejected = approval.state === service.REJECTED;
        var approved = approval.state === service.APPROVED;
        // Authority sees every version note, applicant only the rejected.
        return _.merge( {note: (authority || rejected) && approval.note,
                         approved: approved,
                         rejected: rejected,
                         // Applicant can only delete version without approval status.
                         canDelete: authModel.ok( "delete-attachment-version")
                         && (authority || !(approved || rejected))},
                        v);
      })
      .reverse()
      .value();
  });

  // If postfix is not given, we use approved/rejected
  self.versionTestId = function( prefix, version, postfix ) {
    var nums = version.version;
    if( !postfix) {
      postfix = (version.approved && "approved")
        || (version.rejected && "rejected")
        || "neutral";
    }
    return _( [prefix, nums.major, nums.minor, postfix])
      .map( ko.unwrap )
      .join(  "-" );
  };

  // Versions - add
  self.addEventListener("attachment-upload", { eventType: "finished", attachmentId: self.id }, util.showSavedIndicator);

  self.uploadingAllowed = function() { return authModel.ok("bind-attachment"); };

  // Versions - delete
  self.deleteVersion = function(fileModel) {
    var fileId = fileModel.fileId;
    var originalFileId = fileModel.originalFileId;
    var deleteFn = trackClickWrap("deleteAttachmentVersion", service.removeAttachmentVersion, self.id, fileId, originalFileId);
    self.disablePreview(true);
    hub.send("show-dialog", {ltitle: "attachment.delete.version.header",
                             size: "medium",
                             component: "yes-no-dialog",
                             componentParams: {ltext: "attachment.delete.version.message",
                                               yesFn: deleteFn }});
  };

  addUpdateListener("delete-attachment-version", {ok: true}, _.ary(querySelf, 0));

  // RAM
  self.newRamAttachment   = function() { self.sendEvent("ramService", "new", {id: self.applicationId, attachmentId: self.id} ); };
  self.isRamAttachment    = function() { return Boolean(self.attachment().ramLink); };
  self.creatingRamAllowed = function() { return authModel.ok("create-ram-attachment"); };

  // Meta
  self.metaUpdateAllowed = function() { return authModel.ok("set-attachment-meta") && editable(); };

  self.operationSelectorDisabled = self.disposedPureComputed(function() {
    return !self.metaUpdateAllowed() || !_.get(self.application, ["primaryOperation", "attachment-op-selector"]);
  });

  addUpdateListener("set-attachment-meta", {ok: true}, util.showSavedIndicatorIcon);

  // For Printing
  self.setForPrintingAllowed = function() { return authModel.ok("set-attachments-as-verdict-attachment"); };
  addUpdateListener("set-attachments-as-verdict-attachment", {ok: true}, util.showSavedIndicatorIcon);

  // Not needed
  self.setNotNeededAllowed = function() { return authModel.ok("set-attachment-not-needed"); };
  self.setNotNeededEnabled = editable;
  addUpdateListener("set-attachment-not-needed", {ok: true}, util.showSavedIndicatorIcon);

  // Visibility
  self.getVibilityOptionsText = function(val) { return loc("attachment.visibility." + val); };
  self.setVisibilityAllowed = function() { return authModel.ok("set-attachment-visibility") && editable(); };
  addUpdateListener("set-attachment-visibility", {ok: true}, util.showSavedIndicatorIcon);

  // Manual construction time toggle
  self.setConstructionTimeEnabled = function() { return authModel.ok("set-attachment-as-construction-time"); };
  self.setConstructionTimeVisible = function() { return self.attachment().manuallySetConstructionTime() || self.setConstructionTimeEnabled(); };
  addUpdateListener("set-attachment-as-construction-time", {ok: true}, util.showSavedIndicatorIcon);

  // Permanent archive
  self.permanentArchiveEnabled = function() { return authModel.ok("permanent-archive-enabled"); };

  // Signatures
  self.hasSignature = function() { return !_.isEmpty(self.attachment().signatures); };
  self.sign = function() {
    self.disablePreview(true);
    self.signingModel.init({id: self.applicationId}, [self.attachment]);
  };
  self.signingAllowed = function() { return authModel.ok("sign-attachments"); };
  self.addHubListener({eventType: "attachments-signed", id: self.applicationId}, function(params) {
    if (_.includes(params.attachments, self.id)) {
      querySelf();
    }
  });

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
    service.rotatePdf(self.id, rotation);
  };
  addUpdateListener("rotate-pdf", {ok: true}, _.ary(querySelf, 0));

  self.disposedSubscribe(self.showPreview, function(val) {
    if (val) {
      trackClick("previewVisible");
    }
  });

  self.disposedSubscribe(self.previewUrl, function(url) {
    if (self.showPreview()) {
      $("#file-preview-iframe").attr("src", url);
    }
  });

  // Common hub listeners
  self.addHubListener("dialog-close", _.partial(self.disablePreview, false));

  self.addHubListener("side-panel-open", _.partial(self.disablePreview, true));
  self.addHubListener("side-panel-close", _.partial(self.disablePreview, false));

  // Initial refresh just in case
  querySelf();
};
