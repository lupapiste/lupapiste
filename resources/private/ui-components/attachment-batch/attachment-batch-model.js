// Parameters:
// upload: Upload model
LUPAPISTE.AttachmentBatchModel = function(params) {
  "use strict";
  var self = this;

  var ajaxWaiting = ko.observable();

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  var service = lupapisteApp.services.attachmentsService;

  self.upload = params.upload;

  self.password = ko.observable();
  self.showConstruction = self.disposedPureComputed( _.wrap( "set-attachment-as-construction-time",
                                                             lupapisteApp.models.applicationAuthModel.ok));

  var currentHover = ko.observable();

  self.fillEvents = function( file, column ) {
    return {mouseover: _.wrap( {fileId: file.fileId,
                                column: column},
                               currentHover),
            mouseout: _.wrap( {}, currentHover)};
  };

  // Rows is {fileId: {column: Cell}}
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

  self.cell = function ( file, column ) {
    // The weird default value is needed in order to gracefully handle
    // file removal or rather the resulting "phantom" cell query.
    return _.get(rows(), sprintf( "%s.%s", file.fileId, column), {value: _.noop});
  };

  function someSelected( column ) {
    return _.some( _.values( rows() ),
                   function( row ) {
                     return util.getIn( row, [column, "value"]);
                   });
  }

  function newRow() {
    var type = ko.observable();
    var contentsList = ko.observableArray();
    var grouping = ko.observable({});
    self.disposedSubscribe( type, function( data ) {
      var metadata = data.metadata || {};
      contentsList(_.map( metadata.contents,
                          function( id ) {
                            return loc( "attachments.contents." + id);
                          }));

      grouping({groupType: metadata.grouping === "operation"
                ? "operation-" + _.find (service.groupTypes(),
                                         {groupType: "operation"}).id
                : metadata.grouping});
    } );
    var contentsCell = new Cell( ko.observable(), true );
    contentsCell.list = contentsList;
    var row = {type: new Cell( type, true ),
               contents: contentsCell,
               drawing: new Cell( ko.observable()),
               grouping: new Cell( grouping ),
               sign: new Cell( ko.observable())};
    return self.showConstruction
      ? _.set( row, "construction", new Cell( ko.observable()))
      : row;
  }

  self.disposedSubscribe( self.upload.files, function( files ) {
    var oldRows = rows();
    var newRows = {};
    var keepRows = {};

    _.each( files, function( file ) {
      var fileId = file.fileId;
      if( oldRows[fileId]) {
        keepRows[fileId] = oldRows[fileId];
      } else {
        newRows[fileId] = newRow();
      }
    });
    rows( _.merge( keepRows, newRows ));
  });

  self.badFiles = ko.observableArray();

  self.fileCount = self.disposedPureComputed( _.flow( self.upload.files,
                                                      _.size ));

  self.upload.listenService("badFile", function( event ) {
    self.badFiles.push( _.pick( event, ["message", "file"]));
  });

  self.upload.init();


  self.waiting = self.disposedPureComputed( function() {
    return self.upload.waiting() || ajaxWaiting();
  });

  self.done = function() {

  };

  self.cancel = function() {
    self.upload.cancel();
    self.badFiles.removeAll();
    rows( {});
  };

  // The fill/copy down functionality works as follows:
  // 1. The filling is possible if the file is not the last and the
  //    current cell has a value.
  // 2. The filling action is determined by policy. If number then the
  //    filling is done with fillNumber, otherwise the fill value is just
  //    a copy of the current cell value.
  // 3. The filling action is executed for every following file.

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
    var fillFun = policy === "number" ? fillNumber( fill ) : _.constant(fill);
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
      if(_.isEqual( currentHover(), {fileId: file.fileId,
                                     column: column })) {
        var index = fileIndex( file );
        return _.trim( self.cell( file, column ).value())
          && (index < _.size( self.upload.files()) - 1);
      }
    });
  };

  self.signingSelected = self.disposedPureComputed( _.wrap( "sign",
                                                            someSelected));

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


  self.footClick = function( column ) {
    var flag = !someSelected( column );
    _.each( _.values( rows() ),
            function( row ) {
              row[column].value( flag );
            });
  };

  self.footText = function( column ) {
    return self.disposedPureComputed( function() {
      return  "attachment.batch-"
        + (someSelected( column) ? "clear" : "select");
    });
  };


  // Cancel the batch just in case when leaving the application.
  // Without this, something weird happens:
  // Uncaught Error: 'Too much recursion' after processing 5000 task
  // groups.
  // Part of the problem is the fact that the tab is not disposed when
  // leaving the application.
  self.addHubListener( "contextService::leave", self.cancel );
};
