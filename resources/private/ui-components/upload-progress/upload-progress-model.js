LUPAPISTE.UploadProgressModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  var upload = params.upload;
  var targets = ko.observable({});
  // We keep the count separately in order to handle the filename
  // conflicts somewhat successfully.
  var targetCount = ko.observable(0);

  function updateTarget( data ) {
    var file = data.file || {};
    if( file.name ) {
      var m = targets();
      m[file.name] = {loaded: data.loaded,
                      size: file.size};
      targets( m );
    }
  }

  function hubscribe( message, fun ) {
    self.addHubListener( message, function( event ) {
      if( event.input === upload.fileInputId ) {
        fun( event );
      }
    });
  }

  upload.listenService( "fileAdded", function( event ) {
    targetCount.increment();
    updateTarget( event );
  });
  upload.listenService( "filesUploadingProgress", updateTarget);
  hubscribe( "uploadProgress::reset", function() {
    targets({});
    targetCount( 0 );
  });

  self.isFinished = self.disposedPureComputed( function() {
    return self.progress() === 100;
  });

  self.percentage = self.disposedPureComputed( function () {
    return self.progress() + "%";
  });

  self.progress = self.disposedPureComputed( function() {
    var loaded = 0;
    var total = 0;
    _.each( targets(), function( file ) {
      loaded += file.loaded || 0;
      total += file.size || 0;
    } );
    var result = _.round( loaded / total * 100 );
    return _.isNaN( result ) ? 0 : result;
  });

  self.title = self.disposedPureComputed( function() {
    var count = targetCount();
    if( count ) {
      return loc( sprintf( "progress.%s.%s",
                           self.progress() < 100 ? "add" : "ready",
                           count > 1 ? "many" : "one"),
                  count);

    }
  });
};
