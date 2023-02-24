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
  self.processing = self.attachment().processing;
  self.organizationId = lupapisteApp.models.application.organization();

  var authModel = self.attachment().authModel; // No need to be computed since does not change for attachment

  self.readOnly = self.disposedComputed( function() {
    return !authModel.ok("bind-attachment");
  });

  self.upload = new LUPAPISTE.UploadModel(self,
                                          {allowMultiple:false,
                                           dropZone: "section#attachment",
                                           readOnly: self.readOnly
                                          });
  self.upload.init();

  var service = lupapisteApp.services.attachmentsService;

  var ramService = lupapisteApp.services.ramService;

  self.showBackendId = service.isArchivingProject;

  self.visibilities = ko.observableArray(LUPAPISTE.config.attachmentVisibilities);

  self.name = self.disposedComputed(function() {
    return "attachmentType." + self.attachment().typeString();
  });

  function querySelf(hubParams) {
    service.queryOne(self.id, hubParams);
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
  self.backToApplication = _.partial(lupapisteApp.models.application.open, "attachments");

  self.nextAttachmentId = service.nextAttachmentId( self.id );
  self.previousAttachmentId = service.previousAttachmentId( self.id );

  self.openNextAttachment = _.partial(pageutil.openPage, "attachment", self.applicationId + "/" + self.nextAttachmentId );
  self.openPreviousAttachment = _.partial(pageutil.openPage, "attachment", self.applicationId + "/" + self.previousAttachmentId );

  self.showHelp = ko.observable(_.isEmpty(self.attachment().versions));

  // Approve and reject
  addUpdateListener("approve-attachment", {ok: true}, _.ary(querySelf, 0));
  addUpdateListener("reset-attachment", {ok: true}, _.ary(querySelf, 0));
  addUpdateListener("reject-attachment",  {ok: true}, _.ary(querySelf, 0));
  self.isApproved   = _.wrap( self.attachment, service.isApproved );
  self.isApprovable = function() { return authModel.ok("approve-attachment"); };
  self.isRejected   = _.wrap( self.attachment, service.isRejected );
  self.isRejectable = function() { return authModel.ok("reject-attachment"); };

  self.approveAttachment = function() {
    if( self.isApproved() ) {
      service.resetAttachment( self.id );
    } else {
      service.approveAttachment( self.id );
    }
  };

  self.rejectAttachment = function() {
    if( self.isRejected() ) {
      service.resetAttachment( self.id );
    } else {
      service.rejectAttachment( self.id );
    }
  };

  self.approval = self.disposedPureComputed(function () {
    return  service.attachmentApproval( self.attachment ) ;
  });

  self.versionStateText = function( data ) {
    if( data.approved ) {
      return "document.approve";
    }
    if( data.rejected ) {
      return "attachment.reject";
    }
  };

  // Type
  self.showChangeTypeDialog = function() {
    self.disablePreview(true);
    hub.send( "show-dialog", {ltitle: "attachment.changeType",
                              size: "medium",
                              minContentHeight: "10em",
                              component: "attachments-change-type",
                              componentParams : {
                                attachmentType: self.attachment().type,
                                authModel: self.attachment().authModel,
                                attachmentId: self.attachment().id,
                                allowedAttachmentTypes: self.allowedAttachmentTypes}});
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
                                               yesFn: _.partial(service.removeAttachment, self.id) }});
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
        var hovertext = null;
        if (approval.user && approval.timestamp) {
          hovertext = util.partyFullName(approval.user) + " " + util.finnishDateAndTime(approval.timestamp);
        }
        var readOnly = self.attachment().readOnly;
        // Authority sees every version note, applicant only the rejected.
        return _.merge( {note: (authority || rejected) && approval.note,
                         approved: approved,
                         rejected: rejected,
                         hovertext: hovertext,
                         canDelete: authModel.ok( "delete-attachment-version")
                         // Applicant can only delete version without approval status.
                         && ((!readOnly && (authority || !(approved || rejected)))
                             // Authority can delete stamped version of
                             // readOnly attachments in stamping states.
                             || (authority && v.stamped && readOnly && authModel.ok( "stamp-attachments")))
                        },
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

  // Versions - delete
  self.deleteVersion = function(fileModel) {
    var fileId = fileModel.fileId;
    var originalFileId = fileModel.originalFileId;
    var deleteFn = _.partial(service.removeAttachmentVersion, self.id, fileId, originalFileId);
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

  var ramMsg = ko.observable();

  self.ramMessage = self.disposedPureComputed( function () {
    // We want to make only one query.
    if( authModel.ok( "ram-disabled-message")) {
      if(  !_.isString( ramMsg() )) {
        ramMsg( "" );
        ramService.fetchRamMessage(self.id, ramMsg );
      }
    } else {
      ramMsg( null );
    }
    return ramMsg();
  });


  // Meta
  self.metaUpdateAllowed = function() {
    return Boolean(authModel.ok("set-attachment-meta"));
  };

  self.operationSelectorDisabled = self.disposedPureComputed(function() {
    return !self.metaUpdateAllowed() || !authModel.ok("set-attachment-group-enabled");
  });

  addUpdateListener("set-attachment-meta", {ok: true}, util.showSavedIndicatorIcon);

  // For Printing
  self.setForPrintingAllowed = function() { return authModel.ok("set-attachments-as-verdict-attachment"); };
  addUpdateListener("set-attachments-as-verdict-attachment", {ok: true}, util.showSavedIndicatorIcon);

  // Not needed
  self.setNotNeededAllowed = function() { return authModel.ok("set-attachment-not-needed"); };
  addUpdateListener("set-attachment-not-needed", {ok: true}, util.showSavedIndicatorIcon);

  // Visibility
  self.getVibilityOptionsText = function(val) { return loc("attachment.visibility." + val); };
  self.setVisibilityAllowed = function() { return authModel.ok("set-attachment-visibility"); };
  addUpdateListener("set-attachment-visibility", {ok: true}, function( res ) {
    util.showSavedIndicatorIcon( res );
    querySelf();
  });

  // Manual construction time toggle
  self.setConstructionTimeEnabled = function() { return authModel.ok("set-attachment-as-construction-time"); };
  self.setConstructionTimeVisible = function() { return self.attachment().manuallySetConstructionTime() || self.setConstructionTimeEnabled(); };
  addUpdateListener("set-attachment-as-construction-time", {ok: true}, util.showSavedIndicatorIcon);

  // Permanent archive
  self.permanentArchiveEnabled = function() { return authModel.ok("permanent-archive-enabled"); };
  self.showConversionLog = ko.observable(false);

  // Signatures
  self.hasSignature = function() { return !_.isEmpty(self.attachment().signatures); };
  self.beginSign = function() {
    self.disablePreview(true);
    self.signingModel.init({id: self.applicationId}, [self.attachment]);
  };
  self.signingAllowed = function() { return authModel.ok("sign-attachments"); };
  self.addHubListener({eventType: "attachments-signed", id: self.applicationId, currentPage: "attachment"}, function(params) {
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
    return "/api/raw/latest-attachment-version?attachment-id=" + self.id;
  });

  function togglePreview( showPreview ) {
    if (self.showPreview()) {
      $("#file-preview-iframe").attr("src",
                                     showPreview
                                           ? self.previewUrl()
                                           : "/lp-static/img/ajax-loader.gif");
    }
  }

  self.isArchived = self.disposedComputed(function() {
    return util.getIn(self.attachment(), ["metadata", "tila"]) === "arkistoitu";
  });

  self.rotate = function(rotation) {
    togglePreview( false );
    service.rotatePdf(self.id, rotation);
  };
  addUpdateListener("rotate-pdf", {ok: true}, function() {
    querySelf();
    togglePreview( true );
  });

  self.archivabilityText = self.disposedPureComputed(function() {
    var latestVersion = util.getIn(self.attachment, ["latestVersion"]);
    if (latestVersion.archivable && latestVersion.autoConversion) {
      return "attachment.archivable.converted";
    } else if (latestVersion.archivable) {
      return "attachment.archivability";
    } else if (!latestVersion.archivable && !latestVersion.archivabilityError) {
      return "attachment.archivable.notConverted";
    } else {
      return latestVersion.archivabilityError;
    }
  });

  self.convertableToPdfA = self.disposedPureComputed(function() {
    return authModel.ok("convert-to-pdfa");
  });

  self.convertToPdfA = function() {
    service.convertToPdfA(self.id);
  };

 // Common hub listeners
  self.addHubListener("dialog-close", _.partial(self.disablePreview, false));

  self.addHubListener("side-panel-open", _.partial(self.disablePreview, true));
  self.addHubListener("side-panel-close", _.partial(self.disablePreview, false));



  // Initial refresh just in case, but not if we're still processing
  if (!self.processing()) {
    querySelf();
  }
};
