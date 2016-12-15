LUPAPISTE.AttachmentsOperationButtonsModel = function() {
  "use strict";
  var self = this;
  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

  var service = lupapisteApp.services.attachmentsService;
  var appModel = lupapisteApp.models.application;

  self.authModel = lupapisteApp.models.applicationAuthModel;

  var attachments = service.attachments;

  self.newAttachment = function() {
    hub.send( "add-attachment-file", {} );
  };

  self.attachmentTemplatesAdd = function() {
    hub.send("show-dialog", { ltitle: "attachments.require-attachments",
                              size: "medium",
                              component: "attachments-require"});
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
    hub.send("start-stamping", {application: appModel});
  };

  self.canStamp = function() {
    return self.authModel.ok("stamp-attachments");
  };

  self.signAttachments = function() {
    hub.send("sign-attachments", {application: appModel, attachments: attachments});
  };

  self.canSign = function() {
    return self.authModel.ok("sign-attachments");
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

  self.downloadAll = _.partial( self.sendEvent,
                                service.serviceName,
                                "downloadAllAttachments" );

  self.hasFiles = function() {
    return _.some( service.attachments(),
                   _.ary( _.partialRight( util.getIn,
                                          ["latestVersion", "fileId"] ),
                          1));
  };
};
