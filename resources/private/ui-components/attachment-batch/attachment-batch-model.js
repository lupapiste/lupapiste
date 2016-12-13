LUPAPISTE.AttachmentBatchModel = function() {
  "use strict";
  var self = this;

  var ajaxWaiting = ko.observable();

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  self.password = ko.observable();

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
    // The weird default value is needed in order to gracefully handle
    // file removal or rather the resulting "phantom" cell query.
    return _.get(rows(), sprintf( "%s.%s", file.fileId, name), {value: _.noop});
  };

  function badFileHandler( event ) {
    self.badFiles.push( _.pick( event, ["message", "file"]));
  }

  self.upload = new LUPAPISTE.UploadModel( self,
                                         {dropZone: "#application-attachments-tab",
                                          allowMultiple: true,
                                          errorHandler: badFileHandler});

  self.rowFiles = self.disposedComputed( function() {
    var oldRows = rows();
    var newRows = {};
    var keepRows = {};

    _.each( self.upload.files(), function( file ) {
      var fileId = file.fileId;
      if( oldRows[fileId]) {
        keepRows[fileId] = oldRows[fileId];
      } else {
        newRows[fileId] = {type: new Cell( ko.observable(), true ),
                           content: new Cell( ko.observable(), true ),
                           drawing: new Cell( ko.observable()),
                           context: new Cell( ko.observable, true ),
                           sign: new Cell( ko.observable()),
                           construction: new Cell( ko.observable())}
      }
    });
    rows( _.merge( keepRows, newRows ));
    return self.upload.files();
  });

  self.buttonOptions = { buttonClass: "positive caps",
                         buttonText: "attachment.add-multiple",
                         upload: self.upload };

  self.badFiles = ko.observableArray();

  self.fileCount = self.disposedPureComputed( _.flow( self.upload.files,
                                                      _.size ));

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

  self.signingSelected = self.disposedPureComputed( function() {
    return _.some( _.values( rows()), function( cell ) {
      return cell.sign.value();
    });
  });

  self.passwordState = ko.observable( null );

  self.disposedComputed( function()  {
    self.passwordState( null );
    if( self.password() ) {
      ajax.command( "check-password", {password: self.password()})
        .success( _.wrap( true, self.passwordState))
        .error( _.wrap( false, self.passwordState))
        .call();
    }
  } );

  self.passwordIconClass = self.disposedPureComputed( function() {
    var flag = self.passwordState();
    var icon = "flag";
    if( _.isBoolean( flag )) {
      icon = flag ? "check" : "warning";
    }
    return "lupicon-" + icon;
  });
};
