// Shows UploadModel progress.
// Parameter:
//   upload: UploadModel instance.
LUPAPISTE.UploadProgressModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  var upload = params.upload;
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
    targetCount.increment();
    updateTarget( event );
  });

  upload.listenService( "filesUploadingProgress", updateTarget);

  upload.listenService( "cancel", function() {
    canceled = true;
    targets({});
    targetCount( 0 );
  });

  upload.listenService( "fileCleared",
                        _.bind( targetCount.decrement, targetCount ));

  self.isFinished = self.disposedPureComputed( function() {
    return _.includes( [100, Infinity], self.progress());
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
                           [_.isFinite, _.constant],
                           // Infinity result interpretation comes from IE9 that does
                           // not support progress info.
                           [_.stubTrue,_.wrap( 100, _.constant)]]);
    return castFun( _.round( loaded / total * 100 ))();

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
