LUPAPISTE.AttachmentBatchModel = function() {
  "use strict";
  var self = this;

  var ajaxWaiting = ko.observable();

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  // Rows is {fileId: {name: Cell}}
  var rows = ko.observable({});

  function Cell( valueObs, required ) {
    this.value = valueObs;
    this.isOK = function() {
      return !required || valueObs();
    };

    this.asParam = function( name ) {
      var v = valueObs();
      if( _.isBoolean( v ) || _.trim( v )) {
        return _.fromPairs([[name, v]] );
      }
    };
  }

  self.cell = function ( file, name ) {
    return rows()[file.fileId][name];
  };

  function rowPair( file ) {
    return [file.fileId, {type: new Cell( ko.observable(), true ),
                          content: new Cell( ko.observable(), true ),
                          drawing: new Cell( ko.observable()),
                          context: new Cell( ko.observable, true ),
                          sign: new Cell( ko.observable()),
                          construction: new Cell( ko.observable())}];
  }

  function badFileHandler( event ) {
    self.badFiles.push( _.pick( event, ["message", "file"]));
  }

  self.upload = new LUPAPISTE.UploadModel( self,
                                         {dropZone: "#application-attachments-tab",
                                          allowMultiple: true,
                                          errorHandler: badFileHandler});

  self.disposedSubscribe( self.upload.files, function( files ) {
    var newRows = _(files )
        .reject( function( file ) {
          return rows()[file.fileId];
        })
        .map( rowPair )
        .fromPairs()
        .value();
    rows( _.merge( rows(), newRows ));
  } );


  self.buttonOptions = { buttonClass: "positive caps",
                         buttonText: "attachment.add-multiple",
                         upload: self.upload };

  self.badFiles = ko.observableArray();

  self.upload.init();

  self.waiting = self.disposedPureComputed( function() {
    return self.upload.waiting() || ajaxWaiting();
  });

  self.done = function() {
    console.log( "Done!");
  };

  self.cancel = function() {
    self.upload.cancel();
    self.badFiles.removeAll();
    rows( {});
  };

  function fileIndex( file ) {
    return _.findIndex( self.upload.files(), file);
  }

  function fillNumber( fill ) {
    var current = util.parseFloat( fill );
    if( _.isNaN( current )) {
      return _.constant( fill );
    }
    var parts = _.split( current.toString(), "." );
    var precision = _.size(_.get( parts, "1" ));
    var step = precision ? 1 / (Math.pow( 10, precision )) : 1;

    return function() {
      current = _.round( current + step, precision );
      return sprintf( "%." + precision + "f", current );
    };
  }

  function fillDown( column, file, policy ) {
    var fill = self.cell( file, column ).value();
    var fillFun = policy === "number" ? fillNumber( fill ) : _.constant( fill );
    var index = fileIndex( file );
    _.each( _.drop( self.upload.files(), index + 1 ),
            function( f ) {
              self.cell( f, column ).value( fillFun() );
            });
  }

  self.filler = function( column, file, policy ) {
    return self.disposedComputed({
      read: _.noop,
      write: _.partial( fillDown, column, file, policy )});
  };

  self.canFillDown = function( column, file  ) {
    return self.disposedPureComputed( function() {
      var index = fileIndex( file );
      return _.trim( self.cell( file, column ).value())
        && (index < _.size( self.upload.files()) - 1);
    });
  };
};
