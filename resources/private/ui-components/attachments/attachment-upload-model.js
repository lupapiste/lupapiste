LUPAPISTE.AttachmentUploadModel = function( params ) {
  "use strict";
  var self = this;

  var service = lupapisteApp.services.attachmentsService;

  self.componentTemplate = params.template || "attachment-upload-button-template";

  self.id = params.id;
  self.ltext = params.ltext;

  if (params.uploadModel && !params.uploadModel.batchMode) {
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

  function enrichFileForBatchTable(file) {
    file.attachmentId = self.id;
    file.type = ko.unwrap(params.type);
    file.group = ko.unwrap(params.group);
    return file;
  }

  if (_.get(params.uploadModel, "batchMode")) {
    self.disposedSubscribe(self.upload.files, function(files) {
      if (!_.isEmpty(files)) {
        var f = _.last(files);
        var inputId = params.uploadModel.fileInputId;
        hub.send("fileuploadService::fileAdded", {file: f, input: inputId});
        hub.send("fileuploadService::filesUploadingProgress", {file: {name: f.filename}, input: inputId, progress: 100, loaded: f.size, size: f.size});
        hub.send("fileuploadService::filesUploaded", {input: inputId, status: "success", files: [enrichFileForBatchTable(f)]});
      }
    });
  } else {
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
  }

};
