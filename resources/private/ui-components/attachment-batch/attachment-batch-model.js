// Parameters [optional]:
// upload:       Upload model
// [typeGroups]: Type groups available in the type selector (default all).
// [disabledCols]: Columns that won't be shown. Supported values: drawing, sign
// [defaults]: Default binding values (all optional): target, type,
// group. Values can be observables.
// [batchId]: Storage key for the BatchService (default value is
// calculated from the page path).
// [remember]: If true, the batch data is stored into BatchService and
// restored when the batch model is reinitialized (default true).
LUPAPISTE.AttachmentBatchModel = function(params) {
  "use strict";
  var self = this;

  var PAGE_SIZE = 50;
  var ajaxWaiting = ko.observable();

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  var service = lupapisteApp.services.attachmentsService;
  var batchService = lupapisteApp.services.batchService;
  var batchId = params.batchId || batchService.pageBatchId();

  self.upload      = params.upload;
  self.uploadFinished = ko.observable( true );
  self.typeGroups  = params.typeGroups;
  var defaults     = params.defaults || {};
  var disabledCols = params.disabledCols;
  var rememberBatch = params.remember !== false;

  self.currentPage = rememberBatch ? batchService.currentPage( batchId ) : ko.observable( 0 );

  self.password = ko.observable();

  var uploadedFiles = ko.observableArray( rememberBatch
                                          ? _.cloneDeep(batchService.files( batchId ))
                                          : []);

  self.pageCount = self.disposedPureComputed( function() {
    return _.ceil( uploadedFiles().length / PAGE_SIZE );
  });

  self.fileList = uploadedFiles;

  self.pageStart = self.disposedComputed( function() {
    if( self.currentPage() >= self.pageCount() ) {
      self.currentPage( 0 );
    }
    return self.currentPage() * PAGE_SIZE;
  });

  self.pageEnd = self.disposedPureComputed( function() {
    return self.pageStart() + PAGE_SIZE;
  });


  // Access from the template.
  // self.fileList = self.disposedComputed( function() {
  //   return uploadedFiles.slice(start, start + PAGE_SIZE) ;
  // });

  self.disposedSubscribe( self.upload.files,
                          function( files ) {
                            _.each( files, function( file ) {
                              if( !_.find( uploadedFiles(), {fileId: file.fileId} )) {
                                // The properties are combination of
                                // file upload and upload model proxy
                                // properties.
                                var newFile = _( file )
                                              .pick (["fileId", "filename",
                                                      "type", "contents",
                                                      "drawingNumber", "group",
                                                      "backendId", "target",
                                                      "existsWithSameName",
                                                      "contentType", "size"])
                                              .mapValues( ko.unwrap)
                                              .value();
                                batchService.storeFile( batchId, newFile );
                                uploadedFiles.push( newFile );
                              }
                            });
                          });

  function clearFile( fileId, remove ) {
    batchService.deleteFile( batchId, fileId );
    uploadedFiles.remove( function( file ) {
      return file.fileId === fileId;
    });
    if( remove ) {
      self.upload.removeFile( {fileId: fileId} );
    } else {
      self.upload.clearFile( fileId );
    }
  }

  self.removeFile = function( data ) {
    clearFile( data.fileId, true );
  };

  //var authModel = lupapisteApp.models.applicationAuthModel;
  self.showConstruction = self.disposedPureComputed( _.wrap( "set-attachment-as-construction-time",
                                                             service.authModel.ok));
  self.showSign = self.disposedPureComputed(function() {
    return service.authModel.ok("sign-attachments") && (!_.isArray(disabledCols) || !_.includes(disabledCols, "sign"));
  });

  self.showDrawing = self.disposedPureComputed(function() {
    return !_.isArray(disabledCols) || !_.includes(disabledCols, "drawing");
  });

  self.isArchivingProject = service.isArchivingProject;

  self.contentsPrevEntriesKey = self.disposedPureComputed(function() {
    return self.isArchivingProject() ? "contents" : null;
  });

  var currentHover = ko.observable();

  self.fillEvents = function( file, column ) {
    var showFn = _.wrap( {fileId: file.fileId,
                          column: column},
                         currentHover);
    var hideFn = _.wrap( {}, currentHover);
        return {mouseover: showFn,
                mouseout: hideFn,
                focusin: showFn};
  };

  self.colspan = self.disposedPureComputed(function() {
    var span = 4;
    if (self.isArchivingProject()) {
      span = span + 2;
    }
    if (self.showSign()) {
      span = span + 1;
    }
    if (self.showConstruction()) {
      span = span + 1;
    }
    if (self.showDrawing()) {
      span = span + 1;
    }
    return span;
  });

  // Rows is {fileId: {column: Cell}}
  var rows = ko.observable({});

  function Cell( valueObs, required ) {
    this.value = valueObs;
    this.isOk = function() {
      return !required || !_.isEmpty(valueObs());
    };
  }

  function rowData( file ) {
    return _.get( rows(), file.fileId );
  }

  self.cell = function ( file, column ) {
    // The weird default value is needed in order to gracefully handle
    // file removal or rather the resulting "phantom" cell query.
    return _.get( rowData( file ), column, {value: _.noop});
  };

  self.rowDisabled = function( file ) {
    return util.getIn( rowData( file ), ["disabled"] );
  };

  self.groupSelectorDisabled = function( file ) {
    return function() {
      return util.getIn( rowData( file ), ["disabled"] ) ||
        !service.authModel.ok("set-attachment-group-enabled");
    };
  };

  function disableRows( flag ) {
    _.each( _.values( rows()),
            function( row ) {
              row.disabled( flag );
            });
  }

  function someSelected( column ) {
    return _.some( _.values( rows() ),
                   function( row ) {
                     return util.getIn( row, [column, "value"]);
                   });
  }

  function titledDefaultType(type) {
    var t = ko.unwrap(type);
    if (t && !t.title) {
      t.title = loc(["attachmentType", t["type-group"], t["type-id"]]);
    }
    return t;
  }

  function newRow(initialType, initialContents, drawingNumber,
                  group, initialBackendId, target, stored ) {
    stored = stored || {};
    var defaultType = titledDefaultType(defaults.type) || {};
    var type = ko.observable(stored.type || initialType || defaultType );
    var grouping = ko.observable(stored.grouping || group
                                                 || ko.unwrap(defaults.group)
                                                 || {});
    var backendId = ko.observable(stored.backendId
                                || initialBackendId
                                || ko.unwrap(service.getDefaultBackendId()));
    var contentsValue = ko.observable(stored.contents
                                    || ko.unwrap(defaults.contents)
                                    || defaultType.title
                                    || initialContents);
    var contentsList = ko.observableArray();
    self.disposedSubscribe( type, function( type ) {
      var contents = service.contentsData( type );
      contentsList( contents.list );
      contentsValue(initialContents || contents.defaultValue);
      grouping({});
      grouping(service.getDefaultGroupingForType(type));
    } );
    var contentsCell = new Cell( contentsValue, true );
    contentsCell.list = contentsList;
    var row = { disabled: ko.observable( stored.disabled ),
                type: new Cell( type, true ),
                target: stored.target || target || ko.unwrap(defaults.target),
                contents: contentsCell,
                drawing: new Cell( ko.observable(stored.drawing
                                               || drawingNumber)),
                grouping: new Cell( grouping ),
                backendId: new Cell ( backendId ),
                sign: new Cell( ko.observable( stored.sign )),
                construction: new Cell( ko.observable( stored.construction)),
                disableResell: new Cell( ko.observable( stored.disableResell))
    };
    return row;
  }

  function storeRow( fileId, row ) {
    if( rememberBatch ) {
      _.each( row, function( v, i ) {
        var obs = null;
        if( v instanceof Cell) {
          obs = v.value;
        }
        if( ko.isObservable( v ) ) {
          obs = v;
        }
        if( obs ) {
          self.disposedSubscribe( obs,
                                  _.partial( batchService.storeProperty,
                                             batchId, fileId, i ));
        } else {
          batchService.storeProperty( batchId, fileId, i, v );
        }
      });
    }
  }


  function processFiles( files ) {
    var oldRows = rows();
    var newRows = {};
    var keepRows = {};
    _.each( files, function( file ) {
      var fileId = file.fileId;
      if( oldRows[fileId]) {
        keepRows[fileId] = oldRows[fileId];
      } else {
        if (_.isObject(file.type)) {
          file.type.title = loc(["attachmentType", file.type["type-group"], file.type["type-id"]].join("."));
          file.contents = file.contents || _.get(service.contentsData(file.type), "defaultValue");
        }
        newRows[fileId] = newRow(file.type, file.contents, file.drawingNumber,
                                 file.group, file.backendId, file.target,
                                 file.properties);
        storeRow( fileId, newRows[fileId]);
      }
      file.duplicateFile = file.duplicateFile || ko.observable(false);
      file.duplicateFile(false);
      file.duplicateFileInApplication = ko.observable( ko.unwrap( file.duplicateFileInApplication)
                                                       || file.existsWithSameName );
    });
    rows( _.merge( keepRows, newRows ));

    var fileNames = _.map(uploadedFiles(), function (file) {
      return file.filename;
    });

    var duplicateFiles = _.filter(fileNames, function (name, index, files) {
      return _.includes(files, name, index + 1);
    });

    if (!_.isEmpty(duplicateFiles)) {
       _.map(uploadedFiles(), function (file) {
        if (_.includes(duplicateFiles, file.filename)) {
          file.duplicateFile(true);
          return file;
        }
      });
    }
  }

  self.disposedSubscribe( uploadedFiles, processFiles );
  // Initialization in case the files have been restored.
  processFiles( uploadedFiles() );


  self.badFiles = ko.observableArray();
  self.bindFailed = ko.observable(false);

  self.fileCount = self.disposedPureComputed( _.flow( uploadedFiles,
                                                      _.size ));

  self.upload.listenService("badFile", function( event ) {
    self.badFiles.push( _.pick( event, ["message", "file"]));
  });

   self.upload.init();

  // The fill/copy down functionality works as follows:
  // 1. The filling is possible if the file is not the last one, the
  //    current cell has a value and the row is enabled.
  // 2. The filling action is determined by policy. If number then the
  //    filling is done with fillNumber, otherwise the fill value is just
  //    a copy of the current cell value.
  // 3. The filling action is executed for every following file.

  function fileIndex( file ) {
    return _.findIndex( uploadedFiles(), file);
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
    _.each( _.drop( uploadedFiles(), index + 1 ),
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
      if(!self.rowDisabled( file ) &&
         _.isEqual( currentHover(), {fileId: file.fileId,
                                     column: column })) {
        var index = fileIndex( file );
        var value = self.cell( file, column ).value();

        // Null value is accepted for grouping.
        return (column === "grouping" || (!_.isEmpty( value )
                                          && !_.isBlank( value )))
          && (index < _.size( uploadedFiles()) - 1);
      }
    });
  };

  self.signingSelected = self.disposedPureComputed( _.wrap( "sign",
                                                            someSelected));

  self.passwordState = ko.observable( null );

  self.disposedComputed(function() {
    self.passwordState( null );
    if( self.password() ) {
      ajax.command( "check-password", {password: self.password()})
        .success( _.wrap( true, self.passwordState))
        .error( _.wrap( false, self.passwordState))
        .call();
    }
  }).extend({rateLimit: {method: "notifyWhenChangesStop", timeout: 1000}});

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
              if( !row.disabled()) {
                row[column].value( flag );
              }
            });
  };

  self.footText = function( column ) {
    return self.disposedPureComputed( function() {
      return  "attachment.batch-"
        + (someSelected( column) ? "clear" : "select");
    });
  };

  self.footLabel = function( column ) {
    return sprintf( "%s: %s",
                    loc( column === "sign"
                         ? "signAttachment.select"
                         : "attachment.label.manuallySetConstructionTime"),
                    loc( self.footText( column )() ));
  };

  // Bind and cancel

  self.waiting = self.disposedPureComputed( function() {
    return  self.upload.waiting() || ajaxWaiting();
  });

  self.canBind = self.disposedPureComputed( function() {
    var cellsOk = _.every( _.values( rows()),
                           function( obj ) {
                             return _( _.values( obj ))
                               .filter( function( a ) {
                                 return a instanceof Cell;
                               })
                               .every( function( cell ) {
                                 return cell.isOk();
                               });
                           });
    return cellsOk && (self.signingSelected()
                       ? self.passwordState()
                       : true );
  });

  var jobStatuses = batchService.jobStatuses( batchId );

  self.disposedComputed(function() {
    _.forEach(jobStatuses(), function(status, fileId) {
      if (status() === service.JOB_DONE) {
        clearFile( fileId );
      } else if ( service.pollJobStatusFinished(status) ) {
        self.bindFailed(true);
        if (rows()[fileId] !== undefined && rows()[fileId] !== null) {
          rows()[fileId].disabled(false);
        }
      }
    });
    ajaxWaiting( _.some(jobStatuses(), function(status) { return !service.pollJobStatusFinished(status); }) );
  });

  function groupParam( value ) {
    var g = /operation-(.+)/.exec( value.groupType );
    return g ? {groupType: "operation", id: _.last( g )} : value;
  }

  self.bind = function() {
    disableRows( true );
    self.bindFailed(false);
    self.badFiles.removeAll();

    var statuses = service.bindAttachments( _.map(rows(), function(data, fileId) {
      return { fileId: fileId,
               type: _.pick( data.type.value(), ["type-group", "type-id"] ),
               group: groupParam(data.grouping.value() || {groupType: null} ),
               target: data.target,
               contents: data.contents.value(),
               drawingNumber: data.drawing.value(),
               sign: data.sign.value(),
               constructionTime: data.construction.value(),
               disableResell: data.disableResell.value(),
               backendId: data.backendId.value()
      };
    }), self.password() );

    jobStatuses(statuses);
  };

  self.cancel = function() {
    self.upload.cancel();
    self.badFiles.removeAll();
    uploadedFiles.removeAll();
    rows( {});
    batchService.clearBatch( batchId );
  };

  // Cancel the batch just in case when leaving the application.
  // Without this, something weird happens:
  // Uncaught Error: 'Too much recursion' after processing 5000 task
  // groups.
  // Part of the problem is the fact that the tab is not disposed when
  // leaving the application.
  self.addHubListener( "contextService::leave", self.cancel );
};
