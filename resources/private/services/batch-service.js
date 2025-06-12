// Storage service for AttachmentBatchModel.
LUPAPISTE.BatchService = function() {
  "use strict";
  var self = this;

  var stored = {};

  // Generates batchId based on the current page path.
  self.pageBatchId = function() {
    return pageutil.getPagePath().join( "-" );
  };

  function filesObs( batchId ) {
    return _.get( stored, batchId );
  }

  self.files = function( batchId ) {
    var fobs = filesObs( batchId );
    if( !fobs ) {
      _.set( stored, batchId, ko.observableArray());
    }
    return ko.unwrap( fobs || filesObs( batchId ));
  };

  self.storeFile = function( batchId, file ) {
    self.files( batchId ).push( _.clone( file ) );
  };

  self.storeProperty = function( batchId, fileId, property, value ) {
    var file = _.find( self.files( batchId ), {fileId: fileId});
    if( file ) {
      _.set( file, ["properties", property], value);
    }
  };

  self.clearBatch = function( batchId ) {
    var files =  stored[batchId];
    if( files ) {
      files.removeAll();
    }
    delete stored[batchId];
  };

  self.deleteFile = function( batchId, fileId ) {
    var fobs = filesObs( batchId );
    if( fobs ) {
      fobs.remove( function( file ) {
        return file.fileId === fileId;
      });
    }
  };

  self.batchEmpty = function( batchId, upload ) {
    batchId = batchId || self.pageBatchId();
    return _.isEmpty( self.files( batchId ))
        && _.isEmpty( _.get( upload, "files", _.noop)());
  };

  var batchModels = {};

  function getModel( batchId, k ) {
    return  _.get( batchModels, [batchId, k]);
  }

  function setModel( batchId, k, model ) {
    _.set( batchModels, [batchId, k], model );
    return model;
  }

  self.currentPage  = function( batchId ) {
    return getModel( batchId, "currentPage" )
      || setModel( batchId, "currentPage", ko.observable( 0 ));
  };

  self.jobStatuses = function( batchId ) {
    return getModel( batchId, "jobStatuses")
      || setModel( batchId, "jobStatuses", ko.observable({}));
  };

  hub.subscribe( "contextService::leave",
               function() {
                 _.each( _.keys( stored ), self.clearBatch );
                 batchModels = {};
               });

};
