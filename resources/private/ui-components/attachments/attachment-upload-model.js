LUPAPISTE.AttachmentUploadModel = function( params ) {
  "use strict";
  var self = this;

  var service = lupapisteApp.services.attachmentsService;

  self.componentTemplate = params.template || "attachment-upload-button-template";

  self.id = params.id;
  self.ltext = params.ltext;
  if (params.uploadModel) {
    self.upload = params.uploadModel;
  } else {
    self.upload = new LUPAPISTE.UploadModel(self, { allowMultiple:false });
    self.upload.init();
  }

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  self.bindWaiting = ko.observable();

  self.waiting = self.disposedComputed(function() {
    return self.upload.waiting() || self.bindWaiting();
  });

  self.disposedSubscribe(self.upload.files, function(files) {
    if (!_.isEmpty(files)) {
      var fileId = _.last(files).fileId;
      self.bindWaiting(true);
      var status = service.bindAttachment( self.id, fileId );
      var statusSubscription = self.disposedSubscribe(status, function(status) {
        if ( service.pollJobStatusFinished(status) ) {
          self.bindWaiting(false);
          self.upload.clearFile( fileId );
          self.unsubscribe(statusSubscription);
          self.sendEvent("attachment-upload", "finished", { ok: ko.unwrap(status) === service.JOB_DONE,
                                                            attachmentId: self.id });
        }
      });
    }
  });

};
