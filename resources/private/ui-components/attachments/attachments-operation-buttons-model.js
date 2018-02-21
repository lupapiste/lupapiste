// Parameters:
// upload: Upload model
LUPAPISTE.AttachmentsOperationButtonsModel = function(params) {
  "use strict";
  var self = this;
  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

  var service = lupapisteApp.services.attachmentsService;
  var appModel = lupapisteApp.models.application;

  self.authModel = lupapisteApp.models.applicationAuthModel;

  var attachments = service.attachments;

  self.upload = params.upload;
  self.processing = appModel.processing;

  self.batchEmpty = self.disposedComputed( _.partial( lupapisteApp.services
                                                      .batchService.batchEmpty,
                                                      null, self.upload));

  self.requireAttachmentsBubbleVisible = ko.observable(false);

  self.attachmentTemplatesAdd = function() {
    self.requireAttachmentsBubbleVisible(!self.requireAttachmentsBubbleVisible());
  };

  self.copyUserAttachments = function() {
    hub.send("show-dialog", {ltitle: "application.attachmentsCopyOwn",
                             size: "medium",
                             component: "yes-no-dialog",
                             componentParams: {ltext: "application.attachmentsCopyOwn.confirmationMessage",
                                               yesFn: service.copyUserAttachments}});
  };

  self.canCopyUserAttachments = function() {
    return self.authModel.ok("copy-user-attachments-to-application");
  };

  self.startStamping = function() {
    pageutil.openPage("stamping", appModel.id());
  };

  self.canStamp = function() {
    return self.authModel.ok("stamp-attachments");
  };

  self.signAttachments = function() {
    var filterSet = service.getFilters( "attachments-listing" );
    var filteredAttachments = filterSet.apply(attachments());
    hub.send("sign-attachments", {application: appModel, attachments: filteredAttachments});
  };

  self.canSign = function() {
    return self.authModel.ok("signing-possible");
  };

  self.markVerdictAttachments = function() {
    hub.send("start-marking-verdict-attachments", {application: appModel});
  };

  self.canMarkVerdictAttachments = function() {
    return self.authModel.ok("set-attachments-as-verdict-attachment");
  };

  self.orderAttachmentPrints = function() {
    hub.send("order-attachment-prints", {application: appModel});
  };

  self.canOrderAttachmentPrints = function() {
    return self.authModel.ok("order-verdict-attachment-prints");
  };

  self.startPrintingOrder = function() {
    pageutil.openPage("printing-order", appModel.id());
  };

  self.printingOrderEnabled = function() {
    return self.authModel.ok("attachments-for-printing-order");
  };

  self.downloadAll = _.partial( self.sendEvent,
                                service.serviceName,
                                "downloadAllAttachments" );

  self.hasFiles = function() {
    return _.some( service.attachments(),
                   _.ary( _.partialRight( util.getIn,
                                          ["latestVersion", "filename"] ),
                          1));
  };

};
