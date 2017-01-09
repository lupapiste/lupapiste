// Appeals listing and creation support for a verdit.
// See VerdictAppealBubbleModel for the creation/edit bubble.
// Params:
//  id:  Verdict ID.
//  index: Verdict index, used in data-test-ids.
LUPAPISTE.VerdictAppealModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  // Finnish date format
  var FMT = "D.M.YYYY";
  var service = lupapisteApp.services.verdictAppealService;

  self.params = params;
  var verdictId = params.id;

  self.canEdit = function( appeal ) {
    // If appeal is not given then the check is
    // for new appeal.
    var flag = appeal ? appeal.editable : true;
    return flag && service.editsAllowed();
  };

  self.appeals = ko.observableArray();

  function appealsFromService() {
    self.appeals( _.map( service .appeals( verdictId ),
                         function( a ) {
                           return _.assign( frontAppeal( a ),
                                            {showEdit: ko.observable(),
                                             showExtra:ko.observable()});
                         }));
  }

  // Initial fetch
  appealsFromService();

  self.addEventListener( service.serviceName, "appeals-updated", appealsFromService);

  self.newBubbleVisible = ko.observable();

  // Backend appeal into frontend format.
  // If the argument is omitted, new empty
  // appeal is created.
  // Note: the field values are not observalbe
  function frontAppeal( backObj ) {
    var back = backObj || {};
    var appeal = {
      appealType: back.type,
      authors: back.appellant || back.giver,
      date:  back.datestamp ? moment.unix( back.datestamp ).format ( FMT ) : "",
      extra: back.text,
      files: back.files || [],
      editable: back.editable
    };
    if( back.id ) {
      appeal.appealId = back.id;
    }
    return appeal;
  }

  // Transform frontend appeal to backend format.
  // Note: The field values can be observable, but plain values are supported as well.
  function backAppeal( frontObj ) {
    frontObj = ko.mapping.toJS( frontObj );
    var appeal = {
      verdictId: verdictId,
      type: frontObj.appealType,
      datestamp: moment( frontObj.date, FMT, true).unix(),
      text: frontObj.extra,
      fileIds: _.map( frontObj.files, function( f ) { return f.fileId; })
    };
    if( frontObj.appealId ) {
      appeal.appealId = frontObj.appealId;
    }
    var authorKey =  appeal.type === "appealVerdict" ? "giver" : "appellant";
    appeal[authorKey] = frontObj.authors;
    return appeal;
  }

  // The model is passed to the VerdictAppealBubbleModel
  // as a model parameter.
  self.bubbleModel = function( bubbleVisible, appealId ) {
    var model = _.mapValues( frontAppeal(),
                             function( v ) {
                               return _.isArray( v )
                                 ? ko.observableArray( v )
                                 : ko.observable( v );
                             });
    if( appealId ) {
        model.appealId = appealId;
    }
    var error = ko.observable();
    var waiting = ko.observable();

    function initFun() {
      var data = _.defaults( _.find( self.appeals(), {appealId: appealId}),
                             frontAppeal());
      _.each( data, function(v, k ) {
        if( ko.isObservable( model[k])) {
          model[k]( v );
        }
      } );
      error( "");
      waiting( false );
    }

    function okFun() {
      function cb( msg ) {
        waiting( false );
        error( msg );
        bubbleVisible( Boolean( msg ));
      }
      waiting( true );
      self.sendEvent( service.serviceName,
                      "upsert-appeal",
                      {message: backAppeal( model ),
                       callback: cb} );
    }

    return {
      model: model,
      initFun: initFun,
      okFun: okFun,
      error: error,
      waiting: waiting
    };
  };

  self.deleteAppeal = function( appeal ) {
    self.sendEvent( service.serviceName, "delete-appeal", {message: backAppeal(appeal)});
  };

  self.showTitle = self.disposedPureComputed( function() {
    return _.size( self.appeals()) || self.canEdit();
  });
};
