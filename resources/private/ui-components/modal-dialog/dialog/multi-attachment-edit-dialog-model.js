// Model for dialog used to update multiple files associated with attachments at once.
//
// Parameters [optional]:
// [lyesTitle]: text for a common dialog yes button.
// [lnoTitle]: text for a common dialog no button.
// [yesFn]: function for a common dialog yes button.
// [noFn]: function for a common dialog no button.

LUPAPISTE.MultiAttachmentEditDialogModel = function (params) {
  "use strict";
  var self = this;

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel(params));
  self.yesTitle = params.lyesTitle ? loc(params.lyesTitle) : params.yesTitle;
  self.noTitle = params.lnoTitle ? loc(params.lnoTitle) : params.noTitle;
  self.yes = params.yesFn || _.noop;
  self.no = params.noFn || _.noop;

  var service = lupapisteApp.services.attachmentsService;
  self.attachments = service.attachments;
  self.authModel = lupapisteApp.models.applicationAuthModel;
  self.dialogState = ko.observable({show: "upload",
                                    message: "update-attachments.message"});
  self.updateButton = ko.observable(true);
  var waiting = ko.observable(false);
  // Each of the files observable array items have are objects with at
  // least filename property.
  // Suitable candidate files for the update
  self.goodFiles = ko.observableArray();
  // Unsuitable candidates (e.g., no unique matches or attachment cannot be updated).
  self.ignoredFiles = ko.observableArray();
  // Files that cannot be loaded (e.g., wrong type, too large)
  self.badFiles = ko.observableArray();
  // Files for which the bind failed.
  self.unboundFiles = ko.observableArray();
  self.upload = new LUPAPISTE.UploadModel(self,
                                          {allowMultiple: true,
                                           badFileHandler: function( file ) {
                                             self.badFiles( _(self.badFiles())
                                                            .push( {filename: file.file.name} )
                                                            .uniqBy( "filename")
                                                            .value());
                                           }});
  self.upload.init();

  self.waiting = self.disposedPureComputed( function() {
    return waiting() || self.upload.waiting();
  });

  self.updateAttachments = function(){
    waiting(true);
    var jobStatus = service.bindAttachments( _.map( self.goodFiles(),
                                                    _.partialRight( _.pick, ["attachmentId",
                                                                             "fileId"] ) ));
    self.disposedComputed( function() {
      var statuses = _.map( _.values( jobStatus ), ko.unwrap );
      if( !_.some( statuses, _.partial( _.isEqual, "running") )) {
        var unbounds = [];
        _.forEach( jobStatus, function( status, fileId ) {
          if( status() === "error" ) {
            unbounds.push( _.pick( _.find(self.goodFiles(), {fileId: fileId}),
                                   "filename"));
          }
        });
        self.unboundFiles( _.uniqBy( unbounds, "filename" ));
        self.dialogState( {show: "success"}) ;
        waiting( false );
      }
    });
  };

  self.disposedSubscribe(self.upload.files, function( files ){
    waiting( true );
    ajax.command( "resolve-multi-attachment-updates",
                  {id: lupapisteApp.models.application.id(),
                   files: _.map( files, _.partialRight( _.pick, ["fileId", "filename"] ))})
      .success( function( res ) {
        self.goodFiles( _.map( res.updates, function( u ) {
          return _.set( u, "filename", _.find( files, {fileId: u.fileId}).filename);
        }));
        self.ignoredFiles( _( files )
                           .reject( function( f ) {
                             return _.some( res.updates, {fileId: f.fileId});
                           })
                           .value());
        self.updateButton( _.size( self.goodFiles()) );
        self.dialogState( {show: "confirm"});
        waiting( false );
      })
      .call();
  });
};
