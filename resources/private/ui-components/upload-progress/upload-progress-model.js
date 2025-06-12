// Shows UploadModel progress.
// Parameters [optional]:
//   upload: UploadModel instance.
//   [callback]: Called with falsey argument, when uploads are ongoing and
//   with truthy argument when all uploads are finished. Can be
//   observable.
LUPAPISTE.UploadProgressModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  var upload = params.upload;
  var callback = params.callback || _.noop;
  var targets = ko.observable({});
  // We keep the count separately in order to handle the filename
  // conflicts somewhat successfully.
  var targetCount = ko.observable(0);
  var canceled = false;

  function updateTarget( data ) {
    var file = data.file || {};
    if( !canceled && file.name ) {
      var m = targets();
      m[file.name] = {loaded: data.loaded,
                      size: file.size};
      targets( m );
    }
  }

  upload.listenService( "fileAdded", function( event ) {
    canceled = false;
    callback (false );
    targetCount.increment();
    updateTarget( event );
  });

  upload.listenService( "filesUploaded", function ( event ) {
    // zip files can return more than one file so targetCount is
    // incremented accordingly
    if (event.status === "success" &&
        event.files.length > 1) {
      targetCount.increment(event.files.length - 1);
    }
  });

  upload.listenService( "filesUploadingProgress", updateTarget);

  upload.listenService( "cancel", function() {
    canceled = true;
    targets({});
    targetCount( 0 );
  });

  upload.listenService( "fileCleared", function() {
    // We do not keep tabs, which file has been cleared.
    targetCount( Math.max( targetCount() - 1, 0));
    if( !targetCount()) {
      targets({});
    }
  });

  self.percentage = self.disposedPureComputed( function () {
    return  self.progress() + "%";
  });

  self.progress = self.disposedPureComputed( function() {
    var loaded = 0;
    var total = 0;
    _.each( targets(), function( file ) {
      loaded += file.loaded || 0;
      total += file.size || 0;
    } );

    var castFun = _.cond( [[_.isNaN, _.wrap( 0, _.constant )],
                           // We cap the percentage just in case
                           [_.isFinite, _.flow( _.partial( Math.min, 100), _.constant)],
                           // Infinity result interpretation comes from IE9 that does
                           // not support progress info.
                           [_.stubTrue,_.wrap( 100, _.constant)]]);
    return castFun( _.round( loaded / total * 100 ))();

  });

  self.isFinished = self.disposedComputed( function() {
    var finished =  _.includes( [100, Infinity], self.progress());
    if( finished ) {
      callback( true );
    }
    return finished;
  });

  self.title = self.disposedPureComputed( function() {
    var count = targetCount();
    if( count ) {
      return loc( sprintf( "progress.%s.%s",
                           self.isFinished() ? "ready" : "add",
                           count > 1 ? "many" : "one"),
                  count);

    }
  });
};
