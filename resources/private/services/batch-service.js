// Storage service for AttachmentBatchModel.
LUPAPISTE.BatchService = function() {
  "use strict";
  var self = this;

  var stored = {};

  self.files = function( batchId ) {
    var files =  _.get( stored, batchId);
    if( !files ) {
      _.set( stored, batchId, []);
    }
    return files || _.get( stored, batchId );
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
    delete stored[batchId];
  };

  self.deleteFile = function( batchId, fileId ) {
    _.remove( _.get( stored, batchId), {fileId: fileId});
  };

  hub.subscribe( "ContextService::leave",
               function() {
                 stored = {};
               });

};
