LUPAPISTE.ApplicationSummaryModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel() );

  var service = lupapisteApp.services.summaryService;
  var authOk = service.authOk;

  self.editMode = service.editMode;

  function boxCss( conditionWidth ) {
    return conditionWidth ? _.set( {}, "box--" + conditionWidth, true ) : null;
  }

  function stateBox( app, edit ) {
    var editing = authOk( "state") && edit;
    return {
      visible: true,
      boxCss: boxCss( editing && 2),
      component: "summary-state",
      params: {editing: editing}
    };
  }

  function propertyBox( app, edit ) {
    var warning = app.propertyIdSource() === "user";
    var editing = authOk( "location" ) && edit;
    return {
      visible: true,
      boxCss: boxCss( warning && !editing && 2),
      component: "summary-property",
      params: {warning: warning,
               editing: editing}
    };
  }

  function textBox( app, visible, ltext, text ) {
    return {visible: visible && !_.isBlank( text ),
            params: {ltext: ltext,
                     text: text}};
  }

  function dateBox( app, visible, ltext, timestamp, extra ) {
    var datestr = util.finnishDate( timestamp );
    var box = textBox( app, visible, ltext, datestr );
    return _.isBlank( extra )
      ? box
      : _.set( box, "params.text", datestr + " " + extra);
  }

  function handlersBox( app, edit ) {
    var editing = authOk( "handlers") && edit;
    var hasHandlers = _.some( lupapisteApp.services
                              .handlerService.applicationHandlers() );
    return {
      visible: editing || hasHandlers,
      boxCss: boxCss( hasHandlers && 2),
      component: "summary-handlers",
      params: {editing: editing}
    };
  }

  function operationsBox( app ) {
    return {
      visible: _.some( app.secondaryOperations()),
      boxCss: boxCss( 2 ),
      component: "summary-operations"
    };
  }

  function subtypeBox( app ) {
    var editing = authOk( "subtype" );
    var hasValue = !_.isBlank( app.permitSubtype() );
    var required = app.permitSubtypeMandatory() && !hasValue;
    var visible = _.some( app.permitSubtypes())
        && (editing || hasValue || required );
    return {
      visible: visible,
      boxCss: boxCss( editing && 2 ),
      component: "summary-subtype",
      params: {
        editing: editing,
        required: required
      }
    };
  }

  function tosFunctionBox( app, edit ) {
    var tosFunctions = params.tosFunctions() || [];
    var visible = authOk( "tosFunction" )
        && _.some( tosFunctions );
    var required = _.isBlank( app.tosFunction() );

    return {
      visible: visible,
      boxCss: boxCss( 2 ),
      component: "summary-tos-function",
      params: {
        editing: edit,
        required: required,
        tosFunctions: _.sortBy( tosFunctions, "name" )
      }
    };
  }

  function linkPermitsBox( app, edit ) {
    var visible = _.some( app.linkPermitData() );
    var editing = authOk( "linkPermits" ) && edit;
    return {
      visible: visible,
      boxCss: boxCss( 5 ),
      component: "summary-link-permits",
      params: {
        editing: editing,
      }
    };
  }

  function linksBox( app ) {
    return {
      visible: _.some( app.appsLinkingToUs() ),
      boxCss: boxCss( 5 ),
      component: "summary-links"
    };
  }

  function requesterBox( app ) {
    var name = util.partyFullName( app.creator());
    var firm = util.nonBlankJoin( app.applicantCompanies(), ", " );
    var tel = loc( "phone.short") + app.applicantPhone();
    var box = textBox( app, true, "application.asker",
                       util.nonBlankJoin( [name, firm, tel], "\n"));
    return _.set( _.set( box, "params.spanCss", "ws--pre-wrap" ),
                  "boxCss", boxCss( 2 ));
  }


  self.boxes  = self.disposedComputed( function() {
    var app = lupapisteApp.models.application;
    if( app.id() ) {
      var edit = self.editMode();
      var stBox = stateBox( app, edit );
      var propBox = propertyBox( app, edit);
      var muniBox = textBox( app, true, "application.municipality",
                             loc( ["municipality", app.municipality() ]));
      var idBox = textBox( app, true, "application.id", app.id());
      var handBox = handlersBox( app, edit );
      var boxes = app.infoRequest()
          ? [propBox,
             muniBox,
             requesterBox( app ),
             stBox,
             dateBox( app, true,
                      "inforequest.created",
                      app.created()),
             idBox,
             handBox
            ]
          : [stBox,
             propBox,
             muniBox,
             dateBox( app,  !app.isArchivingProject(),
                      "application.submissionDate",
                      app.submitted()),
             dateBox( app, app.showExpiryDate(),
                      "application.expiryDate",
                      app.expiryDate(),
                      app.showContinuationDate()
                      ? loc( "application.continuationPeriod" )
                      : null),
             idBox,
             textBox( app, true, "application.kuntalupatunnus",
                      _.join( _.filter( app.kuntalupatunnukset()), ", ")),
             textBox( app, true, "application.handling-time",
                      app.handlingTimeText()),
             handBox,
             operationsBox( app ),
             subtypeBox( app ),
             tosFunctionBox( app, edit ),
             linkPermitsBox( app, edit ),
             linksBox( app )
            ];
      return _(boxes)
        .map( function( box ) {
          var params = _.defaults( box.params,
                                   {application: app});
          return _.defaults( _.set(box, "params", params),
                             {component: "summary-text"});
        })
        .filter( "visible" )
        .value();
    }
  });

};
