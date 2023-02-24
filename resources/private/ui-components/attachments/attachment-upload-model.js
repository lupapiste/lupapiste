// Parameters [optional]:
//
// upload: Upload model
//
// [template]: Template name. See attachment-upload-template.html for
// some alternatives (default attachment-upload-button-template).
//
// [proxy]: If a proxy object is given, a separate proxy upload model
// is used (single, no drop-zone). The uploaded file is passed to the
// original upload model after encriching (merging) them with proxy
// object. Typical proxy keys are attachmentId, type and group, but
// there are no restrictions. The values can be observables..
//
// [id]: Attachment id. If given and proxy is false, the file is
// immediately bound. Note: if proxy is gen, id is ignored.
//
// [ltext]: Link/button ltext (components have sensible defaults).
LUPAPISTE.AttachmentUploadModel = function( params ) {
  "use strict";
  var self = this;

  var service = lupapisteApp.services.attachmentsService;

  self.componentTemplate = params.template || "attachment-upload-button-template";

  var attachmentId = params.id;
  self.ltext = params.ltext;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  self.bindWaiting = ko.observable();
  self.upload = params.upload;

  if( params.proxy ) {
    var upload = self.upload;
    self.upload = new LUPAPISTE.UploadModel( self,
                                             {allowMultiple: false,
                                              readOnly: self.disposedPureComputed(function() {
                                                return !service.authModel.ok( "bind-attachment");
                                               }),
                                              badFileHandler: _.noop} );
    self.upload.init();

    self.disposedSubscribe( self.upload.files, function( files ) {
      var file = _.last(files );
      if( file && !file.attachmentId ) {
        upload.files.push( _.merge( _.clone( file),
                                  ko.mapping.toJS( params.proxy )));
      }
    });
  }

  if( !params.proxy && attachmentId ) {
    self.disposedSubscribe(self.upload.files, function(files) {
      if (!_.isEmpty(files)) {
        var fileId = _.last(files).fileId;
        self.bindWaiting(true);
        var status = service.bindAttachment( attachmentId, fileId );
        var statusSubscription = self.disposedSubscribe(status, function(status) {
          if ( service.pollJobStatusFinished(status) ) {
            self.bindWaiting(false);
            self.upload.clearFile( fileId );
            self.unsubscribe(statusSubscription);
            self.sendEvent("attachment-upload",
                           "finished",
                           { ok: ko.unwrap(status) === service.JOB_DONE,
                             attachmentId: attachmentId,
                             id: service.applicationId});
          }
        });
      }
    });
  }

  self.waiting = self.disposedComputed(function() {
    return self.upload.waiting() || self.bindWaiting();
  });
};
