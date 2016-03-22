// Appeals listing and creation support for a verdit.
// See VerdictAppealBubbleModel for the creation/edit bubble.
// Params:
//  id:  Verdict ID.
LUPAPISTE.VerdictAppealModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  // Finnish date format
  var FMT = "D.M.YYYY";
  var service = lupapisteApp.services.verdictAppealService;

  var verdictId = params.id;

  self.appeals = ko.observableArray();

  function appealsFromService() {
    self.appeals( _.map( service .appeals( verdictId ),
                         function( a ) {
                           var appeal = _.assign( frontAppeal( a ),
                                                  {showEdit: a.editable && ko.observable(),
                                                   showExtra: !a.editable && ko.observable()});

                           // TODO: remove mock attachments after backend returns files.
                           return _.assign( appeal,
                                            {files:[
                                              {id: _.uniqueId( "dummyfile"),
                                               filename: "filename.pdf",
                                               contentType: "application/pdf",
                                               size: 123456
                                              },
                                              {id: _.uniqueId( "dummyfile"),
                                               filename: "filename.doc",
                                               contentType: "application/msword",
                                               size: 88888
                                              }]});
                                                }));
  }


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
      files: frontObj.files
    };
    var authorKey =  appeal.type === "appealVerdict" ? "giver" : "appellant";
    appeal[authorKey] = frontObj.authors;
    return appeal;
  }

  // The model is passed to the VerdictAppealBubbleModel
  // as a model parameter.
  self.bubbleModel = function( appealId ) {
    var model = _.mapValues( frontAppeal(),
                             function( v ) {
                               return _.isArray( v )
                                 ? ko.observableArray( v )
                                 : ko.observable( v );
                             });

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
        error( msg );
        self.newBubbleVisible( Boolean( msg ));
      }
      var message = backAppeal( model );
      if( appealId ) {
        message.appealId = appealId;
      }
      self.sendEvent( service.serviceName,
                      "upsert-appeal",
                      {message: message,
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
    console.log( "delete:", appeal );
  };
};
