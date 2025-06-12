LUPAPISTE.AttachmentsViewModel = function() {
  "use strict";
  var self = this;
  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

  var service = lupapisteApp.services.attachmentsService;
  var appModel = lupapisteApp.models.application;

  self.applicationId = appModel.id;
  self.authModel = lupapisteApp.models.applicationAuthModel;

  hub.send( "scrollService::follow", {hashRe: /\/attachments$/} );

  self.upload = new LUPAPISTE.UploadModel( self,
    {dropZone: "#application-attachments-tab",
      allowMultiple: true,
      readOnly: self.disposedPureComputed(function() {
        return !self.authModel.ok( "bind-attachment"); }),
      badFileHandler: _.noop} );

  self.batchEmpty = self.disposedComputed( _.partial( lupapisteApp.services
      .batchService.batchEmpty,
    null, self.upload));

  self.addEventListener(service.serviceName, {eventType: "update", commandName: "approve-attachment"}, function(params) {
    service.queryOne(params.attachmentId);
  });
  self.addEventListener(service.serviceName, {eventType: "update", commandName: "reject-attachment"}, function(params) {
    service.queryOne(params.attachmentId);
  });
    self.addEventListener(service.serviceName, {eventType: "update", commandName: "reset-attachment"}, function(params) {
    service.queryOne(params.attachmentId);
  });

  self.addEventListener(service.serviceName, "remove", util.showSavedIndicator);
  self.addEventListener(service.serviceName, "copy-user-attachments", util.showSavedIndicator);


};
