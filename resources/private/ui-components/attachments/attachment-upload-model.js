LUPAPISTE.AttachmentUploadModel = function( params ) {
  "use strict";
  var self = this;

  var service = lupapisteApp.services.attachmentsService;

  self.componentTemplate = "attachment-upload-link-template";
  self.id = params.id;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  self.upload = new LUPAPISTE.UploadModel(self, { allowMultiple:false });
  self.upload.init();

  self.disposedSubscribe(self.upload.files, function(files) {
    if (!_.isEmpty(files)) {
      var fileId = _.last(files).fileId;
      var status = service.bindAttachment( self.id, fileId );
      var statusSubscription = self.disposedSubscribe(status, function(status) {
        if ( service.pollJobStatusFinished(status) ) {
          self.upload.clearFile( fileId );
          self.unsubscribe(statusSubscription);
          self.sendEvent("attachment-upload", "finished", { ok: ko.unwrap(status) === service.JOB_DONE,
                                                            attachmentId: self.id });
        }
      });
    }
  });

};
